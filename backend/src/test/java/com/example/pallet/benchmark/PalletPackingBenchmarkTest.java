package com.example.pallet.benchmark;

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
 * 可复现基准：多组 mock 订单，对比引擎 vs 朴素堆叠。
 * 结果写入 target/benchmark/benchmark-results.json（供图表脚本读取）。
 */
class PalletPackingBenchmarkTest {

    private final PalletPackingEngine engine = new PalletPackingEngine();
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
        payload.put("suites", rows);
        om.writeValue(out.toFile(), payload);
        System.out.println("BENCHMARK_JSON=" + out.toAbsolutePath());
        for (Map<String, Object> row : rows) {
            System.out.printf(
                    "SUITE %s enginePallets=%s naivePallets=%s engineArea=%.4f naiveArea=%.4f engineMs=%.3f naiveMs=%.3f%n",
                    row.get("name"),
                    row.get("enginePallets"),
                    row.get("naivePallets"),
                    ((Number) row.get("engineAvgAreaUtil")).doubleValue(),
                    ((Number) row.get("naiveAvgAreaUtil")).doubleValue(),
                    ((Number) row.get("engineMs")).doubleValue(),
                    ((Number) row.get("naiveMs")).doubleValue());
            assertTrue(((Number) row.get("enginePallets")).intValue() >= 1);
        }
        // 至少有一组引擎托数不差于朴素
        boolean betterOrEqual = rows.stream().anyMatch(r ->
                ((Number) r.get("enginePallets")).intValue() <= ((Number) r.get("naivePallets")).intValue());
        assertTrue(betterOrEqual);
    }

    private Map<String, Object> runOne(Scenario s) {
        // warmup
        engine.pack(copy(s.request));
        naive.pack(copy(s.request));

        long t0 = System.nanoTime();
        PalletPackingResult er = null;
        int rounds = 30;
        for (int i = 0; i < rounds; i++) {
            er = engine.pack(copy(s.request));
        }
        double engineMs = (System.nanoTime() - t0) / 1_000_000.0 / rounds;

        t0 = System.nanoTime();
        PalletPackingResult nr = null;
        for (int i = 0; i < rounds; i++) {
            nr = naive.pack(copy(s.request));
        }
        double naiveMs = (System.nanoTime() - t0) / 1_000_000.0 / rounds;

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", s.name);
        row.put("boxCount", s.request.getBoxTaskList().size());
        row.put("palletSpec", s.request.getPalletSpec().getPalletStandardCode());
        row.put("enginePallets", er.getTotalPalletCount());
        row.put("naivePallets", nr.getTotalPalletCount());
        row.put("engineBoxes", er.getTotalBoxCount());
        row.put("naiveBoxes", nr.getTotalBoxCount());
        row.put("engineAvgAreaUtil", nz(er.getAvgAreaUtilization()));
        row.put("naiveAvgAreaUtil", nz(nr.getAvgAreaUtilization()));
        row.put("engineAvgHeightUtil", nz(er.getAvgHeightUtilization()));
        row.put("naiveAvgHeightUtil", nz(nr.getAvgHeightUtilization()));
        row.put("engineMs", engineMs);
        row.put("naiveMs", naiveMs);
        row.put("palletSaved", nr.getTotalPalletCount() - er.getTotalPalletCount());
        return row;
    }

    private static double nz(Double v) {
        return v == null ? 0 : v;
    }

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
        list.add(new Scenario("single-12x400", request(1200, 800, 500, 1200, false,
                boxes("A", 12, 400, 300, 200, 8))));
        list.add(new Scenario("dense-24x300", request(1200, 800, 800, 1400, false,
                boxes("A", 24, 300, 200, 200, 6))));
        list.add(new Scenario("mixed-allow", request(1200, 800, 400, 1000, true,
                concat(boxes("A", 6, 400, 300, 200, 8), boxes("B", 6, 450, 300, 200, 9)))));
        list.add(new Scenario("large-48", request(1100, 1100, 1000, 1600, false,
                boxes("A", 48, 350, 280, 220, 7))));
        list.add(new Scenario("tall-stack", request(1200, 800, 600, 1600, false,
                boxes("A", 20, 500, 400, 200, 10))));
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

    private static List<PalletPackingModel.BoxTask> concat(List<PalletPackingModel.BoxTask> a,
                                                          List<PalletPackingModel.BoxTask> b) {
        List<PalletPackingModel.BoxTask> all = new ArrayList<>(a);
        all.addAll(b);
        return all;
    }

    private record Scenario(String name, PalletPackingModel.PackingRequest request) {}
}
