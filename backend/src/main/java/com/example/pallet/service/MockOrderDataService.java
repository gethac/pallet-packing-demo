package com.example.pallet.service;

import com.example.pallet.engine.PalletPackingModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 模拟上游「销售订单 + 包装结果」数据，避免依赖真实 MES。
 */
@Service
public class MockOrderDataService {

    private static final Set<String> SINGLE_BATCH_ORDERS = Set.of(
            "ORDER-SINGLE",
            "ORDER-MIX",
            "ORDER-MIX-PACK",
            "ORDER-MIX-SIZE",
            "ORDER-TAIL",
            "ORDER-HEAVY",
            "ORDER-TALL"
    );

    public List<String> listBatchIds(String orderId) {
        if ("ORDER-EMPTY".equals(orderId)) {
            return List.of();
        }
        if (SINGLE_BATCH_ORDERS.contains(orderId)) {
            return List.of(""); // 隐式单批次，便于前端直接演示混装
        }
        return List.of("BATCH-A", "BATCH-B");
    }

    public List<PalletPackingModel.BoxTask> buildBoxTasks(String orderId, String batchId) {
        if ("ORDER-EMPTY".equals(orderId)) {
            return List.of();
        }
        String bid = batchId == null ? "" : batchId.trim();
        List<PalletPackingModel.BoxTask> tasks = new ArrayList<>();

        if ("ORDER-SINGLE".equals(orderId) || (bid.isEmpty() && !isSpecial(orderId)) || "BATCH-A".equals(bid)) {
            if (!isSpecial(orderId) || "ORDER-SINGLE".equals(orderId)) {
                for (int i = 0; i < 12; i++) {
                    tasks.add(box("A-" + i, "PROD-A", "产品A", bid, "box", 400, 300, 200, 8, true));
                }
            }
        }
        if ("BATCH-B".equals(bid) && !isSpecial(orderId)) {
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

        // 盒箱混装：4 种规格，盒+箱，数量刻意造尾托以便混托合并到同一托
        if ("ORDER-MIX-PACK".equals(orderId)) {
            tasks.clear();
            // 盒 box：小规格 / 中规格
            for (int i = 0; i < 4; i++) {
                tasks.add(box("MP-BOX-S-" + i, "PROD-BOX-S", "彩盒S", bid, "box", 300, 200, 150, 3, true));
            }
            for (int i = 0; i < 3; i++) {
                tasks.add(box("MP-BOX-M-" + i, "PROD-BOX-M", "彩盒M", bid, "box", 400, 300, 180, 5, true));
            }
            // 箱 carton：中规格 / 大规格（有胶带）
            for (int i = 0; i < 3; i++) {
                tasks.add(box("MP-CTN-M-" + i, "PROD-CTN-M", "纸箱M", bid, "carton", 500, 400, 250, 10, true));
            }
            for (int i = 0; i < 2; i++) {
                tasks.add(box("MP-CTN-L-" + i, "PROD-CTN-L", "纸箱L", bid, "carton", 600, 400, 300, 14, true));
            }
        }

        // 多尺寸混托：同一包装方式，4 种明显不同尺寸
        if ("ORDER-MIX-SIZE".equals(orderId)) {
            tasks.clear();
            for (int i = 0; i < 4; i++) {
                tasks.add(box("MS-A-" + i, "PROD-S", "规格S", bid, "box", 250, 200, 150, 2, true));
            }
            for (int i = 0; i < 3; i++) {
                tasks.add(box("MS-B-" + i, "PROD-M", "规格M", bid, "box", 400, 300, 200, 6, true));
            }
            for (int i = 0; i < 3; i++) {
                tasks.add(box("MS-C-" + i, "PROD-L", "规格L", bid, "carton", 550, 400, 280, 11, true));
            }
            for (int i = 0; i < 2; i++) {
                tasks.add(box("MS-D-" + i, "PROD-XL", "规格XL", bid, "carton", 700, 500, 320, 16, true));
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
            for (int i = 0; i < 2; i++) {
                tasks.add(box("TA-" + i, "PROD-A", "产品A", bid, "box", 600, 400, 200, 10, true));
            }
            for (int i = 0; i < 2; i++) {
                tasks.add(box("TB-" + i, "PROD-B", "产品B", bid, "box", 600, 400, 200, 10, true));
            }
        }
        return tasks;
    }

    private static boolean isSpecial(String orderId) {
        return orderId != null && (
                orderId.startsWith("ORDER-MIX")
                        || "ORDER-HEAVY".equals(orderId)
                        || "ORDER-TALL".equals(orderId)
                        || "ORDER-TAIL".equals(orderId)
                        || "ORDER-EMPTY".equals(orderId)
        );
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
