package com.example.pallet.benchmark;

import com.example.pallet.engine.LayerFirstFitPacker;
import com.example.pallet.engine.NaiveStackPacker;
import com.example.pallet.engine.PalletPackingEngine;
import com.example.pallet.engine.PalletPackingModel;
import com.example.pallet.engine.PalletPackingResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 可复现基准：Engine vs 分层 First-Fit（主基线）vs 顺序堆叠（下限参考）。
 */
class PalletPackingBenchmarkTest {

    private final PalletPackingEngine engine = new PalletPackingEngine();
    private final LayerFirstFitPacker firstFit = new LayerFirstFitPacker();
    private final NaiveStackPacker naive = new NaiveStackPacker();

    @Test
    void runBenchmarkSuitesAndWriteJson() throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Scenario s : scenarios()) {
            rows.add(runOne(s));
        }
        Path outDir = Path.of("target", "benchmark");
        Files.createDirectories(outDir);
        Path out = outDir.resolve("benchmark-results.json");
        ObjectMapper om = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("generatedAt", java.time.Instant.now().toString());
        payload.put("javaVersion", System.getProperty("java.version"));
        payload.put("baselines", List.of(
                "LayerFirstFitPacker = primary reasonable baseline (layer first-fit, fixed orientation)",
                "NaiveStackPacker = lower-bound reference (single-column stack)"
        ));
        payload.put("suites", rows);
        om.writeValue(out.toFile(), payload);
        System.out.println("BENCHMARK_JSON=" + out.toAbsolutePath());
        for (Map<String, Object> row : rows) {
            System.out.printf(
                    "SUITE %s engP=%s ffP=%s naiveP=%s engArea=%.4f ffArea=%.4f engVol=%.4f ffVol=%.4f engMs=%.3f ffMs=%.3f%n",
                    row.get("name"),
                    row.get("enginePallets"),
                    row.get("firstFitPallets"),
                    row.get("naivePallets"),
                    ((Number) row.get("engineAvgAreaUtil")).doubleValue(),
                    ((Number) row.get("firstFitAvgAreaUtil")).doubleValue(),
                    ((Number) row.get("engineVolumeUtil")).doubleValue(),
                    ((Number) row.get("firstFitVolumeUtil")).doubleValue(),
                    ((Number) row.get("engineMs")).doubleValue(),
                    ((Number) row.get("firstFitMs")).doubleValue());
            assertTrue(((Number) row.get("enginePallets")).intValue() >= 1);
        }
        // Engine should produce multi-pallet on at least one discriminating suite
        boolean multi = rows.stream().anyMatch(r -> ((Number) r.get("enginePallets")).intValue() > 1);
        assertTrue(multi, "expected at least one suite where engine uses multiple pallets");
    }

    private Map<String, Object> runOne(Scenario s) {
        engine.pack(copy(s.request));
        firstFit.pack(copy(s.request));
        naive.pack(copy(s.request));

        int rounds = 25;
        long t0 = System.nanoTime();
        PalletPackingResult er = null;
        for (int i = 0; i < rounds; i++) er = engine.pack(copy(s.request));
        double engineMs = (System.nanoTime() - t0) / 1_000_000.0 / rounds;

        t0 = System.nanoTime();
        PalletPackingResult fr = null;
        for (int i = 0; i < rounds; i++) fr = firstFit.pack(copy(s.request));
        double firstFitMs = (System.nanoTime() - t0) / 1_000_000.0 / rounds;

        t0 = System.nanoTime();
        PalletPackingResult nr = null;
        for (int i = 0; i < rounds; i++) nr = naive.pack(copy(s.request));
        double naiveMs = (System.nanoTime() - t0) / 1_000_000.0 / rounds;

        double palletL = s.request.getPalletSpec().getLength();
        double palletW = s.request.getPalletSpec().getWidth();
        double heightLimit = s.request.getPalletSpec().getCargoHeightLimit();

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", s.name);
        row.put("boxCount", s.request.getBoxTaskList().size());
        row.put("palletSpec", s.request.getPalletSpec().getPalletStandardCode());
        row.put("enginePallets", er.getTotalPalletCount());
        row.put("firstFitPallets", fr.getTotalPalletCount());
        row.put("naivePallets", nr.getTotalPalletCount());
        row.put("engineAvgAreaUtil", nz(er.getAvgAreaUtilization()));
        row.put("firstFitAvgAreaUtil", nz(fr.getAvgAreaUtilization()));
        row.put("naiveAvgAreaUtil", nz(nr.getAvgAreaUtilization()));
        row.put("engineAvgHeightUtil", nz(er.getAvgHeightUtilization()));
        row.put("firstFitAvgHeightUtil", nz(fr.getAvgHeightUtilization()));
        row.put("naiveAvgHeightUtil", nz(nr.getAvgHeightUtilization()));
        row.put("engineVolumeUtil", volumeUtil(er, palletL, palletW, heightLimit));
        row.put("firstFitVolumeUtil", volumeUtil(fr, palletL, palletW, heightLimit));
        row.put("naiveVolumeUtil", volumeUtil(nr, palletL, palletW, heightLimit));
        row.put("engineMs", engineMs);
        row.put("firstFitMs", firstFitMs);
        row.put("naiveMs", naiveMs);
        row.put("palletsSavedVsFirstFit", fr.getTotalPalletCount() - er.getTotalPalletCount());
        row.put("palletsSavedVsNaive", nr.getTotalPalletCount() - er.getTotalPalletCount());
        return row;
    }

    private static double volumeUtil(PalletPackingResult r, double L, double W, double Hlim) {
        double boxVol = r.getBoxList().stream()
                .mapToDouble(b -> nz(b.getOccupyLength()) * nz(b.getOccupyWidth()) * nz(b.getOccupyHeight()))
                .sum();
        double capacity = Math.max(1, r.getTotalPalletCount()) * L * W * Hlim;
        return round4(Math.min(1.0, boxVol / capacity));
    }

    private static double nz(Double v) { return v == null ? 0 : v; }
    private static double round4(double v) { return Math.round(v * 10000.0) / 10000.0; }

    private static PalletPackingModel.PackingRequest copy(PalletPackingModel.PackingRequest src) {
        PalletPackingModel.PackingRequest r = new PalletPackingModel.PackingRequest();
        r.setPalletSpec(src.getPalletSpec());
        r.setAllowMixedPallet(src.getAllowMixedPallet());
        r.setAllowMixedPackagePallet(src.getAllowMixedPackagePallet());
        r.setAllowMixedNoBoxPallet(src.getAllowMixedNoBoxPallet());
        r.setBoxTaskList(new ArrayList<>(src.getBoxTaskList()));
        return r;
    }

    private static List<Scenario> scenarios() {
        List<Scenario> list = new ArrayList<>();
        // Engine may still be 1 pallet on dense small boxes
        list.add(new Scenario("single-12x400", request(1200, 800, 500, 1200, false,
                boxes("A", 12, 400, 300, 200, 8))));
        // Discriminating: tight weight → multi pallet for engine
        list.add(new Scenario("weight-tight-36", request(1200, 800, 120, 1400, false,
                boxes("A", 36, 400, 300, 200, 10))));
        // Multi SKU mixed sizes, mix allowed
        list.add(new Scenario("mixed-sku-sizes", request(1200, 800, 600, 1200, true,
                concat(boxes("A", 10, 500, 400, 200, 12),
                        boxes("B", 10, 300, 250, 180, 6),
                        boxes("C", 8, 450, 300, 220, 9)))));
        // Tail-like leftovers
        list.add(new Scenario("tail-merge-like", request(1200, 800, 250, 800, true,
                concat(boxes("A", 5, 600, 400, 200, 15),
                        boxes("B", 5, 600, 400, 200, 15)))));
        // Large batch multi-pallet
        list.add(new Scenario("bulk-96", request(1100, 1100, 400, 1200, false,
                boxes("A", 96, 400, 350, 250, 12))));
        // Tall + many
        list.add(new Scenario("height-tight-40", request(1200, 800, 800, 500, false,
                boxes("A", 40, 400, 300, 200, 8))));
        return list;
    }

    private static PalletPackingModel.PackingRequest request(double L, double W, double weight, double height,
                                                            boolean mix, List<PalletPackingModel.BoxTask> tasks) {
        PalletPackingModel.PalletSpec spec = new PalletPackingModel.PalletSpec();
        spec.setPalletStandardId("STD");
        spec.setPalletStandardCode(L + "x" + W);
        spec.setLength(L);
        spec.setWidth(W);
        spec.setWeightLimit(weight);
        spec.setCargoHeightLimit(height);
        PalletPackingModel.PackingRequest req = new PalletPackingModel.PackingRequest();
        req.setPalletSpec(spec);
        req.setAllowMixedPallet(mix);
        req.setAllowMixedPackagePallet(mix);
        req.setAllowMixedNoBoxPallet(false);
        req.setBoxTaskList(tasks);
        return req;
    }

    private static List<PalletPackingModel.BoxTask> boxes(String key, int n, double l, double w, double h, double wt) {
        List<PalletPackingModel.BoxTask> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            PalletPackingModel.BoxTask t = new PalletPackingModel.BoxTask();
            t.setTaskKey(key + "-" + i);
            t.setOrderProductId(key);
            t.setProductKey("PROD-" + key);
            t.setProductLabel("产品" + key);
            t.setPackageMode("box");
            t.setBoxLength(l);
            t.setBoxWidth(w);
            t.setBoxHeight(h);
            t.setBoxWeight(wt);
            t.setAllowRotate(true);
            list.add(t);
        }
        return list;
    }

    @SafeVarargs
    private static List<PalletPackingModel.BoxTask> concat(List<PalletPackingModel.BoxTask>... parts) {
        List<PalletPackingModel.BoxTask> all = new ArrayList<>();
        for (List<PalletPackingModel.BoxTask> p : parts) all.addAll(p);
        return all;
    }

    private record Scenario(String name, PalletPackingModel.PackingRequest request) {}
}
