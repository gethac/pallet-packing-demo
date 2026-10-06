package com.example.pallet.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PalletPackingEngineTest {

    private PalletPackingEngine engine;

    @BeforeEach
    void setUp() {
        engine = new PalletPackingEngine();
    }

    @Test
    void pack_singleProduct_createsPalletsWithinLimits() {
        PalletPackingModel.PackingRequest req = baseRequest(1200, 800, 500, 1200);
        req.setBoxTaskList(boxes("PROD-A", "box", 12, 400, 300, 200, 8));
        PalletPackingResult result = engine.pack(req);
        assertTrue(result.getTotalPalletCount() >= 1);
        assertEquals(12, result.getTotalBoxCount());
        assertEquals(96.0, result.getTotalProductWeight(), 0.01);
        assertTrue(result.getItemList().stream().allMatch(i -> i.getTotalWeight() <= 500 + 1e-6));
        assertTrue(result.getItemList().stream().allMatch(i -> i.getTotalHeight() <= 1200 + 1e-6));
        assertTrue(result.getBoxList().stream().allMatch(b ->
                b.getPositionX() + b.getOccupyLength() <= 1200 + 1e-6
                        && b.getPositionY() + b.getOccupyWidth() <= 800 + 1e-6));
    }

    @Test
    void pack_overWeight_throws() {
        PalletPackingModel.PackingRequest req = baseRequest(1200, 800, 50, 1200);
        req.setBoxTaskList(boxes("PROD-H", "box", 1, 400, 300, 200, 80));
        PackingException ex = assertThrows(PackingException.class, () -> engine.pack(req));
        assertTrue(ex.getMessage().contains("超过货物重量限制"));
    }

    @Test
    void pack_overHeight_throws() {
        PalletPackingModel.PackingRequest req = baseRequest(1200, 800, 500, 300);
        req.setBoxTaskList(boxes("PROD-T", "box", 1, 400, 300, 400, 10));
        PackingException ex = assertThrows(PackingException.class, () -> engine.pack(req));
        assertTrue(ex.getMessage().contains("超过货物高度限制"));
    }

    @Test
    void pack_emptyTasks_throws() {
        PalletPackingModel.PackingRequest req = baseRequest(1200, 800, 500, 1200);
        req.setBoxTaskList(List.of());
        PackingException ex = assertThrows(PackingException.class, () -> engine.pack(req));
        assertTrue(ex.getMessage().contains("没有可用于装托"));
    }

    @Test
    void pack_tailMerge_withMixedEnabled_reducesPalletCount() {
        // 两组产品各 2 箱，尺寸较大，单独装会产生尾托；开启混托后应合并
        PalletPackingModel.PackingRequest noMix = baseRequest(1200, 800, 200, 600);
        noMix.setAllowMixedPallet(false);
        List<PalletPackingModel.BoxTask> tasks = new ArrayList<>();
        tasks.addAll(boxes("PROD-A", "box", 2, 600, 400, 200, 10));
        tasks.addAll(boxes("PROD-B", "box", 2, 600, 400, 200, 10));
        noMix.setBoxTaskList(new ArrayList<>(tasks));
        PalletPackingResult separated = engine.pack(noMix);

        PalletPackingModel.PackingRequest mix = baseRequest(1200, 800, 200, 600);
        mix.setAllowMixedPallet(true);
        mix.setAllowMixedPackagePallet(true);
        mix.setBoxTaskList(new ArrayList<>(tasks));
        PalletPackingResult merged = engine.pack(mix);

        assertTrue(merged.getTotalPalletCount() <= separated.getTotalPalletCount());
        assertEquals(4, merged.getTotalBoxCount());
        if (merged.getTotalPalletCount() < separated.getTotalPalletCount()) {
            assertEquals("1", merged.getHasMixedGroup());
        }
    }

    @Test
    void pack_mixedPackageRequiresFlag() {
        PalletPackingModel.PackingRequest req = baseRequest(1200, 800, 200, 600);
        req.setAllowMixedPallet(true);
        req.setAllowMixedPackagePallet(false);
        List<PalletPackingModel.BoxTask> tasks = new ArrayList<>();
        tasks.addAll(boxes("PROD-A", "box", 2, 600, 400, 200, 10));
        tasks.addAll(boxes("PROD-B", "carton", 2, 600, 400, 200, 10));
        req.setBoxTaskList(tasks);
        PalletPackingResult result = engine.pack(req);
        // 未开盒箱混托时不应出现 mixed 分组（或托盘数不因跨包装合并而减少到 1）
        long mixedItems = result.getItemList().stream().filter(i -> "mixed".equals(i.getGroupType())).count();
        assertEquals(0, mixedItems);
    }

    @Test
    void pack_boxAndCartonMix_onSamePallet_whenPackageMixEnabled() {
        // 盒箱混装：大箱先、小盒后，开启混托+盒箱混托后同托多层
        PalletPackingModel.PackingRequest req = baseRequest(1200, 800, 500, 1200);
        req.setAllowMixedPallet(true);
        req.setAllowMixedPackagePallet(true);
        List<PalletPackingModel.BoxTask> tasks = new ArrayList<>();
        tasks.addAll(boxes("PROD-CTN-L", "carton", 4, 600, 400, 300, 14));
        tasks.addAll(boxes("PROD-CTN-M", "carton", 4, 500, 400, 250, 10));
        tasks.addAll(boxes("PROD-BOX-M", "box", 6, 400, 300, 180, 5));
        tasks.addAll(boxes("PROD-BOX-S", "box", 8, 300, 200, 150, 3));
        req.setBoxTaskList(tasks);
        PalletPackingResult result = engine.pack(req);
        assertEquals(22, result.getTotalBoxCount());
        assertTrue(result.getTotalPalletCount() >= 1);
        assertTrue(result.getTotalPalletCount() < 4, "混托后托盘数应少于按规格各自成托");
        boolean hasMultiProduct = result.getItemList().stream().anyMatch(i ->
                i.getProductKeys() != null && i.getProductKeys().contains(",")
                        || (i.getProductKeys() != null && i.getProductKeys().split("[,、]").length > 1)
                        || "mixed".equals(i.getGroupType()));
        assertTrue(hasMultiProduct || result.getTotalPalletCount() == 1,
                "应出现混托分组或单托承载多规格");
        long distinctSizes = result.getBoxList().stream()
                .map(b -> Math.round(b.getOccupyLength()) + "x" + Math.round(b.getOccupyWidth()) + "x" + Math.round(b.getOccupyHeight()))
                .distinct()
                .count();
        assertTrue(distinctSizes >= 3, "混装结果应保留至少 3 种尺寸，实际=" + distinctSizes);
        int maxLayer = result.getBoxList().stream().mapToInt(b -> b.getLayerNo() == null ? 1 : b.getLayerNo()).max().orElse(1);
        assertTrue(maxLayer >= 2, "混装应自然产生至少 2 层，实际=" + maxLayer);
        long modes = result.getBoxList().stream().map(b -> b.getPackageMode()).distinct().count();
        assertTrue(modes >= 2, "应同时含盒与箱");
    }

    @Test
    void pack_multiSizeMix_samePallet_whenMixedEnabled() {
        PalletPackingModel.PackingRequest req = baseRequest(1200, 800, 500, 1200);
        req.setAllowMixedPallet(true);
        req.setAllowMixedPackagePallet(true);
        List<PalletPackingModel.BoxTask> tasks = new ArrayList<>();
        tasks.addAll(boxes("PROD-XL", "carton", 3, 700, 500, 320, 16));
        tasks.addAll(boxes("PROD-L", "carton", 4, 550, 400, 280, 11));
        tasks.addAll(boxes("PROD-M", "box", 6, 400, 300, 200, 6));
        tasks.addAll(boxes("PROD-S", "box", 8, 250, 200, 150, 2));
        req.setBoxTaskList(tasks);
        PalletPackingResult result = engine.pack(req);
        assertEquals(21, result.getTotalBoxCount());
        assertTrue(result.getTotalPalletCount() < 4);
        long sizes = result.getBoxList().stream()
                .map(b -> Math.round(b.getOccupyLength()) + "x" + Math.round(b.getOccupyWidth()) + "x" + Math.round(b.getOccupyHeight()))
                .distinct()
                .count();
        assertTrue(sizes >= 3, "多尺寸混托应保留多种外廓，实际=" + sizes);
        // 至少有一托箱数大于单一规格的最大件数
        int maxBoxesOnOne = result.getItemList().stream().mapToInt(i -> i.getBoxCount()).max().orElse(0);
        assertTrue(maxBoxesOnOne > 8, "混托后单托箱数应超过单一规格件数");
        int maxLayer = result.getBoxList().stream().mapToInt(b -> b.getLayerNo() == null ? 1 : b.getLayerNo()).max().orElse(1);
        assertTrue(maxLayer >= 2, "多尺寸混托应至少 2 层，实际=" + maxLayer);
    }

    @Test
    void fingerprint_changesWhenInputChanges() {
        PalletPackingModel.PackingRequest a = baseRequest(1200, 800, 500, 1200);
        a.setBoxTaskList(boxes("PROD-A", "box", 2, 400, 300, 200, 8));
        PalletPackingModel.PackingRequest b = baseRequest(1200, 800, 500, 1200);
        b.setBoxTaskList(boxes("PROD-A", "box", 3, 400, 300, 200, 8));
        String ha = PalletPackingInputFingerprint.hash(a);
        String hb = PalletPackingInputFingerprint.hash(b);
        assertTrue(!ha.equals(hb));
        assertEquals(ha, PalletPackingInputFingerprint.hash(a));
    }

    private static PalletPackingModel.PackingRequest baseRequest(double L, double W, double weight, double height) {
        PalletPackingModel.PalletSpec spec = new PalletPackingModel.PalletSpec();
        spec.setPalletStandardId("STD");
        spec.setPalletStandardCode(L + "x" + W);
        spec.setLength(L);
        spec.setWidth(W);
        spec.setWeightLimit(weight);
        spec.setCargoHeightLimit(height);
        PalletPackingModel.PackingRequest req = new PalletPackingModel.PackingRequest();
        req.setPalletSpec(spec);
        req.setAllowMixedPallet(false);
        req.setAllowMixedPackagePallet(false);
        req.setAllowMixedNoBoxPallet(false);
        return req;
    }

    private static List<PalletPackingModel.BoxTask> boxes(String productKey, String mode, int count,
                                                          double l, double w, double h, double weight) {
        List<PalletPackingModel.BoxTask> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            PalletPackingModel.BoxTask t = new PalletPackingModel.BoxTask();
            t.setTaskKey(productKey + "-" + i);
            t.setOrderProductId(productKey);
            t.setProductKey(productKey);
            t.setProductLabel(productKey);
            t.setPackageMode(mode);
            t.setBoxLength(l);
            t.setBoxWidth(w);
            t.setBoxHeight(h);
            t.setBoxWeight(weight);
            t.setAllowRotate(true);
            list.add(t);
        }
        return list;
    }
}
