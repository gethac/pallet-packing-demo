package com.example.pallet.engine;

import com.example.pallet.service.MockOrderDataService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PackingSupportValidatorTest {

    private final PackingSupportValidator validator = new PackingSupportValidator();
    private final PackingConstraints constraints = PackingConstraints.defaults();
    private final PalletPackingEngine engine = new PalletPackingEngine();
    private final MockOrderDataService mock = new MockOrderDataService();

    @Test
    void detectsUnsupportedOverhang() {
        PalletPackingModel.PalletSpec spec = spec(1200, 800, 500, 1200);
        PalletPackingResult forged = new PalletPackingResult();
        List<PalletPackingResult.BoxResult> boxes = new ArrayList<>();
        boxes.add(box("A", "P1", 0, 0, 0, 600, 400, 300));
        // upper box mostly outside lower footprint
        boxes.add(box("B", "P1", 400, 0, 300, 600, 400, 200));
        forged.setBoxList(boxes);
        PackingSupportValidator.Report r = validator.validate(forged, spec, constraints);
        assertFalse(r.ok());
        assertTrue(r.issues.stream().anyMatch(i -> "UNSUPPORTED".equals(i.code) || "CG_UNSUPPORTED".equals(i.code)));
    }

    @Test
    void allMockOrdersPassSupportValidation() {
        String[] orders = {
                "ORDER-SINGLE", "ORDER-MIX", "ORDER-MIX-PACK", "ORDER-MIX-SIZE",
                "ORDER-TAIL", "ORDER-EMPTY"
        };
        for (String orderId : orders) {
            List<PalletPackingModel.BoxTask> tasks = mock.buildBoxTasks(orderId, "");
            if (tasks.isEmpty()) {
                continue;
            }
            PalletPackingModel.PackingRequest req = new PalletPackingModel.PackingRequest();
            req.setPalletSpec(spec(1200, 800, 500, 1200));
            req.setAllowMixedPallet(true);
            req.setAllowMixedPackagePallet(true);
            req.setAllowMixedNoBoxPallet(false);
            req.setBoxTaskList(tasks);
            PalletPackingResult result = engine.pack(req);
            PackingSupportValidator.Report r = validator.validate(result, req.getPalletSpec(), constraints);
            assertTrue(r.ok(), () -> orderId + " failed: " + r.issues);
            assertTrue(r.minSupportRatio + 1e-6 >= constraints.getMinSupportRatio(),
                    () -> orderId + " minSupport=" + r.minSupportRatio);
            assertEquals(r.minSupportRatio, result.getMinSupportRatio(), 1e-4);
        }
    }

    @Test
    void firstFitBaselineAlsoPassesSupport() {
        LayerFirstFitPacker ff = new LayerFirstFitPacker();
        PalletPackingModel.PackingRequest req = new PalletPackingModel.PackingRequest();
        req.setPalletSpec(spec(1200, 800, 500, 1200));
        List<PalletPackingModel.BoxTask> tasks = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            PalletPackingModel.BoxTask t = new PalletPackingModel.BoxTask();
            t.setTaskKey("A-" + i);
            t.setProductKey("PROD-A");
            t.setPackageMode("box");
            t.setBoxLength(400d);
            t.setBoxWidth(300d);
            t.setBoxHeight(200d);
            t.setBoxWeight(8d);
            t.setAllowRotate(false);
            tasks.add(t);
        }
        req.setBoxTaskList(tasks);
        PalletPackingResult result = ff.pack(req);
        PackingSupportValidator.Report r = validator.validate(result, req.getPalletSpec(), constraints);
        assertTrue(r.ok(), () -> String.valueOf(r.issues));
    }

    private static PalletPackingModel.PalletSpec spec(double L, double W, double wt, double h) {
        PalletPackingModel.PalletSpec s = new PalletPackingModel.PalletSpec();
        s.setPalletStandardId("STD");
        s.setLength(L);
        s.setWidth(W);
        s.setWeightLimit(wt);
        s.setCargoHeightLimit(h);
        return s;
    }

    private static PalletPackingResult.BoxResult box(String key, String pallet, double x, double y, double z,
                                                    double l, double w, double h) {
        PalletPackingResult.BoxResult b = new PalletPackingResult.BoxResult();
        b.setBoxKey(key);
        b.setPalletNo(pallet);
        b.setPositionX(x);
        b.setPositionY(y);
        b.setPositionZ(z);
        b.setOccupyLength(l);
        b.setOccupyWidth(w);
        b.setOccupyHeight(h);
        b.setBoxWeight(1d);
        return b;
    }
}
