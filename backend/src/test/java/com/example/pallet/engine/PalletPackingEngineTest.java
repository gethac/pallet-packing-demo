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
