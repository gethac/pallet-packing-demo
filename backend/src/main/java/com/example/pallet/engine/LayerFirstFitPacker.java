package com.example.pallet.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * 合理基线：分层 First-Fit / 贪心行列平铺。
 * - 固定朝向（不旋转）
 * - 当前层按行优先放置；行满换行；层满（限高或限重）开新层；托盘满开新托
 * - 尊重托盘长宽、限重、限高
 */
public class LayerFirstFitPacker {

    public PalletPackingResult pack(PalletPackingModel.PackingRequest request) {
        if (request == null || request.getBoxTaskList() == null || request.getBoxTaskList().isEmpty()) {
            throw new PackingException("没有可用于装托的包装数据");
        }
        PalletPackingModel.PalletSpec spec = request.getPalletSpec();
        if (spec == null) {
            throw new PackingException("缺少托盘规格");
        }
        double palletL = spec.getLength();
        double palletW = spec.getWidth();
        double weightLimit = spec.getWeightLimit() != null ? spec.getWeightLimit() : Double.MAX_VALUE;
        double heightLimit = spec.getCargoHeightLimit() != null ? spec.getCargoHeightLimit() : Double.MAX_VALUE;

        List<PalletPackingResult.ItemResult> items = new ArrayList<>();
        List<PalletPackingResult.BoxResult> boxes = new ArrayList<>();
        List<PalletPackingModel.BoxTask> tasks = new ArrayList<>(request.getBoxTaskList());

        int taskIdx = 0;
        int palletIdx = 0;
        int boxSort = 1;

        while (taskIdx < tasks.size()) {
            palletIdx++;
            String palletNo = "P" + palletIdx;
            double palletWeight = 0;
            double layerZ = 0;
            int layerNo = 0;
            int boxCount = 0;
            double usedArea = 0;

            while (taskIdx < tasks.size()) {
                // start a new layer
                PalletPackingModel.BoxTask probe = tasks.get(taskIdx);
                double bl = nz(probe.getBoxLength());
                double bw = nz(probe.getBoxWidth());
                double bh = nz(probe.getBoxHeight());
                double bwt = nz(probe.getBoxWeight());
                if (bl > palletL + 1e-6 || bw > palletW + 1e-6) {
                    throw new PackingException("箱体平面尺寸超过托盘");
                }
                if (bh > heightLimit + 1e-6) {
                    throw new PackingException("单箱超过货物高度限制");
                }
                if (bwt > weightLimit + 1e-6) {
                    throw new PackingException("单箱超过货物重量限制");
                }
                if (layerZ + bh > heightLimit + 1e-6 || palletWeight + bwt > weightLimit + 1e-6) {
                    break; // need new pallet
                }

                layerNo++;
                double rowY = 0;
                double rowHeight = 0; // along width axis
                double cursorX = 0;
                double layerMaxH = 0;
                boolean placedAnyInLayer = false;

                while (taskIdx < tasks.size()) {
                    PalletPackingModel.BoxTask t = tasks.get(taskIdx);
                    double l = nz(t.getBoxLength());
                    double w = nz(t.getBoxWidth());
                    double h = nz(t.getBoxHeight());
                    double wt = nz(t.getBoxWeight());
                    if (l > palletL + 1e-6 || w > palletW + 1e-6 || h > heightLimit + 1e-6 || wt > weightLimit + 1e-6) {
                        throw new PackingException("箱体超过托盘或限制");
                    }
                    // same layer height band: only pack boxes with same height in a layer for simplicity
                    // (common first-fit layer heuristic with uniform layer height = first box height)
                    if (placedAnyInLayer && Math.abs(h - layerMaxH) > 1e-6) {
                        // different height → finish this layer
                        break;
                    }
                    if (layerZ + h > heightLimit + 1e-6 || palletWeight + wt > weightLimit + 1e-6) {
                        break;
                    }

                    // try place in current row
                    if (cursorX + l > palletL + 1e-6) {
                        // new row
                        rowY += rowHeight;
                        cursorX = 0;
                        rowHeight = 0;
                    }
                    if (rowY + w > palletW + 1e-6) {
                        // layer full
                        break;
                    }
                    // 支撑约束：与主引擎一致（80% + 重心）
                    if (layerZ > 1e-6) {
                        double ratio = supportRatioAt(boxes, palletNo, cursorX, rowY, l, w, layerZ);
                        if (ratio + 1e-6 < 0.80 || !centroidSupported(boxes, palletNo, cursorX, rowY, l, w, layerZ)) {
                            // 本层该位置无足够支撑 → 结束本层
                            break;
                        }
                    }

                    PalletPackingResult.BoxResult br = new PalletPackingResult.BoxResult();
                    br.setPalletNo(palletNo);
                    br.setProductKey(t.getProductKey());
                    br.setProductLabel(t.getProductLabel());
                    br.setTaskKey(t.getTaskKey());
                    br.setBoxKey(t.getTaskKey());
                    br.setPackageMode(t.getPackageMode());
                    br.setBoxLength(l);
                    br.setBoxWidth(w);
                    br.setBoxHeight(h);
                    br.setBoxWeight(wt);
                    br.setPositionX(cursorX);
                    br.setPositionY(rowY);
                    br.setPositionZ(layerZ);
                    br.setOccupyLength(l);
                    br.setOccupyWidth(w);
                    br.setOccupyHeight(h);
                    br.setLayerNo(layerNo);
                    br.setSort(boxSort++);
                    boxes.add(br);

                    cursorX += l;
                    rowHeight = Math.max(rowHeight, w);
                    layerMaxH = h;
                    palletWeight += wt;
                    usedArea += l * w;
                    boxCount++;
                    placedAnyInLayer = true;
                    taskIdx++;
                }

                if (!placedAnyInLayer) {
                    break;
                }
                layerZ += layerMaxH;
            }

            if (boxCount == 0) {
                throw new PackingException("无法装入更多箱子");
            }
            PalletPackingResult.ItemResult item = new PalletPackingResult.ItemResult();
            item.setPalletNo(palletNo);
            item.setBoxCount(boxCount);
            item.setLayerCount(layerNo);
            item.setTotalWeight(round2(palletWeight));
            item.setTotalHeight(round2(layerZ));
            double areaUtil = (palletL * palletW) <= 0 ? 0 : Math.min(1.0, usedArea / (palletL * palletW));
            // usedArea summed across layers can exceed 1; clamp average-ish by layers
            areaUtil = Math.min(1.0, usedArea / (palletL * palletW * Math.max(1, layerNo)));
            item.setAreaUtilization(round4(areaUtil));
            item.setHeightUtilization(heightLimit <= 0 ? 0 : round4(Math.min(1.0, layerZ / heightLimit)));
            items.add(item);
        }

        PalletPackingResult result = new PalletPackingResult();
        result.setTotalPalletCount(items.size());
        result.setTotalBoxCount(boxes.size());
        result.setTotalProductWeight(round2(boxes.stream().mapToDouble(b -> nz(b.getBoxWeight())).sum()));
        result.setItemList(items);
        result.setBoxList(boxes);
        result.setAvgAreaUtilization(round4(items.stream().mapToDouble(i -> nz(i.getAreaUtilization())).average().orElse(0)));
        result.setAvgHeightUtilization(round4(items.stream().mapToDouble(i -> nz(i.getHeightUtilization())).average().orElse(0)));
        PackingSupportValidator.Report report =
                new PackingSupportValidator().validate(result, spec, PackingConstraints.defaults());
        result.setMinSupportRatio(round4(report.minSupportRatio));
        if (!report.ok()) {
            throw new PackingException("FirstFit 结果未通过支撑校验: " + report.issues.get(0));
        }
        return result;
    }

    private static double supportRatioAt(List<PalletPackingResult.BoxResult> all, String palletNo,
                                         double x, double y, double l, double w, double sitZ) {
        double area = l * w;
        if (area <= 1e-9) return 1;
        double supported = 0;
        for (PalletPackingResult.BoxResult lower : all) {
            if (!palletNo.equals(String.valueOf(lower.getPalletNo()))) continue;
            double top = nz(lower.getPositionZ()) + nz(lower.getOccupyHeight());
            if (Math.abs(top - sitZ) > 1e-3) continue;
            double left = Math.max(x, nz(lower.getPositionX()));
            double right = Math.min(x + l, nz(lower.getPositionX()) + nz(lower.getOccupyLength()));
            double front = Math.max(y, nz(lower.getPositionY()));
            double back = Math.min(y + w, nz(lower.getPositionY()) + nz(lower.getOccupyWidth()));
            if (right > left && back > front) supported += (right - left) * (back - front);
        }
        return Math.min(1.0, supported / area);
    }

    private static boolean centroidSupported(List<PalletPackingResult.BoxResult> all, String palletNo,
                                             double x, double y, double l, double w, double sitZ) {
        double cx = x + l / 2, cy = y + w / 2;
        for (PalletPackingResult.BoxResult lower : all) {
            if (!palletNo.equals(String.valueOf(lower.getPalletNo()))) continue;
            double top = nz(lower.getPositionZ()) + nz(lower.getOccupyHeight());
            if (Math.abs(top - sitZ) > 1e-3) continue;
            double lx = nz(lower.getPositionX()), ly = nz(lower.getPositionY());
            if (cx >= lx && cx <= lx + nz(lower.getOccupyLength()) && cy >= ly && cy <= ly + nz(lower.getOccupyWidth())) {
                return true;
            }
        }
        return false;
    }

    private static double nz(Double v) { return v == null ? 0 : v; }
    private static double round2(double v) { return Math.round(v * 100.0) / 100.0; }
    private static double round4(double v) { return Math.round(v * 10000.0) / 10000.0; }
}
