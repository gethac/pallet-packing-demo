package com.example.pallet.service;

import com.example.pallet.engine.PalletPackingModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模拟上游「销售订单 + 包装结果」数据，避免依赖真实 MES。
 */
@Service
public class MockOrderDataService {

    public List<String> listBatchIds(String orderId) {
        if ("ORDER-EMPTY".equals(orderId)) {
            return List.of();
        }
        if ("ORDER-SINGLE".equals(orderId)) {
            return List.of(""); // 隐式单批次
        }
        return List.of("BATCH-A", "BATCH-B");
    }

    public List<PalletPackingModel.BoxTask> buildBoxTasks(String orderId, String batchId) {
        if ("ORDER-EMPTY".equals(orderId)) {
            return List.of();
        }
        String bid = batchId == null ? "" : batchId.trim();
        List<PalletPackingModel.BoxTask> tasks = new ArrayList<>();
        if ("ORDER-SINGLE".equals(orderId) || bid.isEmpty() || "BATCH-A".equals(bid)) {
            // 产品 A：400x300x200，8kg，共 12 箱
            for (int i = 0; i < 12; i++) {
                tasks.add(box("A-" + i, "PROD-A", "产品A", bid, "box", 400, 300, 200, 8, true));
            }
        }
        if ("BATCH-B".equals(bid)) {
            for (int i = 0; i < 6; i++) {
                tasks.add(box("B-" + i, "PROD-B", "产品B", bid, "carton", 500, 400, 250, 12, true));
            }
        }
        if ("ORDER-MIX".equals(orderId)) {
            tasks.clear();
            for (int i = 0; i < 3; i++) {
                tasks.add(box("M-A-" + i, "PROD-A", "产品A", bid, "box", 400, 300, 200, 8, true));
            }
            for (int i = 0; i < 3; i++) {
                tasks.add(box("M-B-" + i, "PROD-B", "产品B", bid, "carton", 450, 300, 200, 9, true));
            }
        }
        if ("ORDER-HEAVY".equals(orderId)) {
            tasks.clear();
            tasks.add(box("H-1", "PROD-H", "超重箱", bid, "box", 400, 300, 200, 999, true));
        }
        if ("ORDER-TALL".equals(orderId)) {
            tasks.clear();
            tasks.add(box("T-1", "PROD-T", "超高箱", bid, "box", 400, 300, 2000, 10, true));
        }
        if ("ORDER-TAIL".equals(orderId)) {
            tasks.clear();
            // 两种产品各 2 箱，制造尾托以便混托合并演示（托盘 1200x800）
            for (int i = 0; i < 2; i++) {
                tasks.add(box("TA-" + i, "PROD-A", "产品A", bid, "box", 600, 400, 200, 10, true));
            }
            for (int i = 0; i < 2; i++) {
                tasks.add(box("TB-" + i, "PROD-B", "产品B", bid, "box", 600, 400, 200, 10, true));
            }
        }
        return tasks;
    }

    public Map<String, Object> productRows(String orderId, String batchId) {
        List<PalletPackingModel.BoxTask> tasks = buildBoxTasks(orderId, batchId);
        Map<String, List<PalletPackingModel.BoxTask>> byProduct = new LinkedHashMap<>();
        for (PalletPackingModel.BoxTask t : tasks) {
            byProduct.computeIfAbsent(t.getProductKey(), k -> new ArrayList<>()).add(t);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, List<PalletPackingModel.BoxTask>> e : byProduct.entrySet()) {
            PalletPackingModel.BoxTask sample = e.getValue().get(0);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("productKey", e.getKey());
            row.put("productLabel", sample.getProductLabel());
            row.put("packageMode", sample.getPackageMode());
            row.put("boxLength", sample.getBoxLength());
            row.put("boxWidth", sample.getBoxWidth());
            row.put("boxHeight", sample.getBoxHeight());
            row.put("boxWeight", sample.getBoxWeight());
            row.put("boxCount", e.getValue().size());
            rows.add(row);
        }
        return Map.of("productRows", rows);
    }

    private static PalletPackingModel.BoxTask box(String key, String productKey, String label,
                                                  String batchId, String mode,
                                                  double l, double w, double h, double weight,
                                                  boolean rotate) {
        PalletPackingModel.BoxTask t = new PalletPackingModel.BoxTask();
        t.setTaskKey(key);
        t.setOrderProductId(productKey);
        t.setProductKey(productKey);
        t.setProductLabel(label);
        t.setBatchId(batchId);
        t.setBatchName(batchId.isEmpty() ? "默认批次" : batchId);
        t.setPackageMode(mode);
        t.setBoxLength(l);
        t.setBoxWidth(w);
        t.setBoxHeight(h);
        t.setBoxWeight(weight);
        t.setAllowRotate(rotate);
        return t;
    }
}
