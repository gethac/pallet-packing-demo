package com.example.pallet.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * 朴素基线：按任务顺序，单列向上堆叠；放不下则新开托盘。
 * 仅用于与 PalletPackingEngine 对比，不是生产算法。
 */
public class NaiveStackPacker {

    public PalletPackingResult pack(PalletPackingModel.PackingRequest request) {
        if (request == null || request.getBoxTaskList() == null || request.getBoxTaskList().isEmpty()) {
            throw new PackingException("没有可用于装托的包装数据");
        }
        PalletPackingModel.PalletSpec spec = request.getPalletSpec();
        if (spec == null) {
            throw new PackingException("缺少托盘规格");
        }
        double weightLimit = spec.getWeightLimit() != null ? spec.getWeightLimit() : Double.MAX_VALUE;
        double heightLimit = spec.getCargoHeightLimit() != null ? spec.getCargoHeightLimit() : Double.MAX_VALUE;

        List<PalletPackingResult.ItemResult> items = new ArrayList<>();
        List<PalletPackingResult.BoxResult> boxes = new ArrayList<>();
        int palletIdx = 0;
        int boxSort = 1;

        List<PalletPackingModel.BoxTask> tasks = new ArrayList<>(request.getBoxTaskList());
        int i = 0;
        while (i < tasks.size()) {
            palletIdx++;
            String palletNo = "P" + palletIdx;
            double z = 0;
            double weight = 0;
            int layer = 0;
            int boxCount = 0;
            double maxL = 0;
            double maxW = 0;
            while (i < tasks.size()) {
                PalletPackingModel.BoxTask t = tasks.get(i);
                double l = t.getBoxLength();
                double w = t.getBoxWidth();
                double h = t.getBoxHeight();
                double bw = t.getBoxWeight() != null ? t.getBoxWeight() : 0;
                if (l > spec.getLength() + 1e-6 || w > spec.getWidth() + 1e-6) {
                    throw new PackingException("箱体平面尺寸超过托盘");
                }
                if (bw > weightLimit + 1e-6) {
                    throw new PackingException("单箱超过货物重量限制");
                }
                if (h > heightLimit + 1e-6) {
                    throw new PackingException("单箱超过货物高度限制");
                }
                if (z + h > heightLimit + 1e-6 || weight + bw > weightLimit + 1e-6) {
                    break;
                }
                layer++;
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
                br.setBoxWeight(bw);
                br.setPositionX(0.0);
                br.setPositionY(0.0);
                br.setPositionZ(z);
                br.setOccupyLength(l);
                br.setOccupyWidth(w);
                br.setOccupyHeight(h);
                br.setLayerNo(layer);
                br.setSort(boxSort++);
                boxes.add(br);
                z += h;
                weight += bw;
                boxCount++;
                maxL = Math.max(maxL, l);
                maxW = Math.max(maxW, w);
                i++;
            }
            if (boxCount == 0) {
                throw new PackingException("无法装入更多箱子");
            }
            PalletPackingResult.ItemResult item = new PalletPackingResult.ItemResult();
            item.setPalletNo(palletNo);
            item.setBoxCount(boxCount);
            item.setLayerCount(layer);
            item.setTotalWeight(weight);
            item.setTotalHeight(z);
            double areaUtil = (spec.getLength() * spec.getWidth()) <= 0 ? 0
                    : (maxL * maxW) / (spec.getLength() * spec.getWidth());
            item.setAreaUtilization(Math.min(1.0, areaUtil));
            item.setHeightUtilization(heightLimit <= 0 ? 0 : Math.min(1.0, z / heightLimit));
            items.add(item);
        }

        PalletPackingResult result = new PalletPackingResult();
        result.setTotalPalletCount(items.size());
        result.setTotalBoxCount(boxes.size());
        result.setTotalProductWeight(boxes.stream().mapToDouble(b -> b.getBoxWeight() == null ? 0 : b.getBoxWeight()).sum());
        result.setItemList(items);
        result.setBoxList(boxes);
        double avgA = items.stream().mapToDouble(PalletPackingResult.ItemResult::getAreaUtilization).average().orElse(0);
        double avgH = items.stream().mapToDouble(PalletPackingResult.ItemResult::getHeightUtilization).average().orElse(0);
        result.setAvgAreaUtilization(avgA);
        result.setAvgHeightUtilization(avgH);
        return result;
    }
}
