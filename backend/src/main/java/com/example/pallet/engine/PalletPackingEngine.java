package com.example.pallet.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 纯托盘装载计算引擎（模拟最小实现）。
 * <p>
 * 相对生产版做了简化，但仍覆盖：分组装托、层内网格摆放与碰撞、限重限高校验、
 * 尾托识别与混托合并、面积/高度利用率。
 */
public class PalletPackingEngine {

    private static final double EPS = 1e-6;

    private PackingConstraints constraints = PackingConstraints.defaults();

    public void setConstraints(PackingConstraints constraints) {
        this.constraints = constraints == null ? PackingConstraints.defaults() : constraints;
    }

    public PackingConstraints getConstraints() {
        return constraints;
    }

    public PalletPackingResult pack(PalletPackingModel.PackingRequest request) {
        validate(request);
        Map<String, List<PalletPackingModel.BoxTask>> grouped = new LinkedHashMap<>();
        for (PalletPackingModel.BoxTask task : request.getBoxTaskList()) {
            String key = productKey(task);
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(task);
        }

        List<InternalPallet> fullPallets = new ArrayList<>();
        List<InternalPallet> tailPallets = new ArrayList<>();

        for (Map.Entry<String, List<PalletPackingModel.BoxTask>> e : grouped.entrySet()) {
            List<InternalPallet> productPallets = packProductTasks(request.getPalletSpec(), e.getValue());
            for (InternalPallet pallet : productPallets) {
                if (isTail(pallet, request.getPalletSpec(), e.getValue().get(0))) {
                    tailPallets.add(pallet);
                } else {
                    fullPallets.add(pallet);
                }
            }
        }

        if (Boolean.TRUE.equals(request.getAllowMixedPallet()) && tailPallets.size() > 1) {
            List<InternalPallet> merged = mergeTails(request, tailPallets);
            fullPallets.addAll(merged);
        } else {
            fullPallets.addAll(tailPallets);
        }

        fullPallets.sort(Comparator.comparing(p -> p.palletNo));
        renumber(fullPallets);
        PalletPackingResult result = toResult(fullPallets, request.getPalletSpec());
        PackingSupportValidator.Report report =
                new PackingSupportValidator().validate(result, request.getPalletSpec(), constraints);
        result.setMinSupportRatio(round4(report.minSupportRatio));
        if (!report.ok()) {
            throw new PackingException("装托结果未通过支撑/边界校验: " + report.issues.get(0));
        }
        return result;
    }

    private void validate(PalletPackingModel.PackingRequest request) {
        if (request == null || request.getPalletSpec() == null) {
            throw new PackingException("托盘参数不能为空");
        }
        PalletPackingModel.PalletSpec spec = request.getPalletSpec();
        if (!positive(spec.getWeightLimit())) {
            throw new PackingException("货物重量限制必须大于0");
        }
        if (!positive(spec.getCargoHeightLimit())) {
            throw new PackingException("货物高度限制必须大于0");
        }
        if (!positive(spec.getLength()) || !positive(spec.getWidth())) {
            throw new PackingException("托盘长宽必须大于0");
        }
        if (request.getBoxTaskList().isEmpty()) {
            throw new PackingException("没有可用于装托的包装数据");
        }
        double weightLimit = spec.getWeightLimit();
        double heightLimit = spec.getCargoHeightLimit();
        for (PalletPackingModel.BoxTask task : request.getBoxTaskList()) {
            String label = label(task);
            double w = nz(task.getBoxWeight());
            if (w > weightLimit + EPS) {
                throw new PackingException(label + "单箱产品重量(" + fmt(w)
                        + "kg)超过货物重量限制(" + fmt(weightLimit) + "kg)");
            }
            double h = nz(task.getBoxHeight());
            if (h > heightLimit + EPS) {
                throw new PackingException(label + "单箱货物高度(" + fmt(h)
                        + "mm)超过货物高度限制(" + fmt(heightLimit) + "mm)");
            }
            if (!positive(task.getBoxLength()) || !positive(task.getBoxWidth()) || !positive(task.getBoxHeight())) {
                throw new PackingException(label + "包装尺寸无效");
            }
        }
    }

    private List<InternalPallet> packProductTasks(PalletPackingModel.PalletSpec spec,
                                                  List<PalletPackingModel.BoxTask> tasks) {
        List<InternalPallet> result = new ArrayList<>();
        List<PalletPackingModel.BoxTask> remaining = new ArrayList<>(tasks);
        // 重的、大的优先（稳定底托）
        remaining.sort(Comparator
                .comparingDouble((PalletPackingModel.BoxTask t) -> -nz(t.getBoxWeight()))
                .thenComparingDouble(t -> -(nz(t.getBoxLength()) * nz(t.getBoxWidth()) * nz(t.getBoxHeight()))));
        int seq = 1;
        while (!remaining.isEmpty()) {
            InternalPallet pallet = new InternalPallet();
            pallet.palletNo = "TMP-" + productKey(tasks.get(0)) + "-" + (seq++);
            pallet.groupType = "single";
            pallet.productKeys = productKey(tasks.get(0));
            fillPallet(pallet, spec, remaining);
            if (pallet.boxes.isEmpty()) {
                throw new PackingException("无法将剩余箱子装入托盘，请检查尺寸与限制");
            }
            result.add(pallet);
        }
        return result;
    }

    private void fillPallet(InternalPallet pallet, PalletPackingModel.PalletSpec spec,
                            List<PalletPackingModel.BoxTask> remaining) {
        double weightLimit = spec.getWeightLimit();
        double heightLimit = spec.getCargoHeightLimit();
        double palletL = spec.getLength();
        double palletW = spec.getWidth();

        List<Layer> layers = (pallet.layers == null || pallet.layers.isEmpty())
                ? new ArrayList<>()
                : new ArrayList<>(pallet.layers);
        double currentWeight = pallet.totalWeight;

        while (!remaining.isEmpty()) {
            int pick = selectNextTaskIndex(remaining, layers, pallet.boxes, spec, currentWeight);
            if (pick < 0) {
                break;
            }
            PalletPackingModel.BoxTask next = remaining.get(pick);
            CandidatePlace place = findBestPlace(next, layers, pallet.boxes, spec, currentWeight);
            if (place == null) {
                // 该箱放不下：留给后续新托；先尝试其它箱
                boolean any = false;
                for (int i = 0; i < remaining.size(); i++) {
                    if (i == pick) {
                        continue;
                    }
                    CandidatePlace alt = findBestPlace(remaining.get(i), layers, pallet.boxes, spec, currentWeight);
                    if (alt != null) {
                        pick = i;
                        next = remaining.get(i);
                        place = alt;
                        any = true;
                        break;
                    }
                }
                if (!any) {
                    break;
                }
            }

            Layer target = place.layer;
            if (target == null) {
                target = new Layer();
                target.layerNo = layers.size() + 1;
                target.z = place.z;
                target.layerHeight = place.ori.h;
                layers.add(target);
            }
            PlacedBox box = new PlacedBox();
            box.task = next;
            box.layerNo = target.layerNo;
            box.x = place.x;
            box.y = place.y;
            box.z = place.z;
            box.occupyL = place.ori.l;
            box.occupyW = place.ori.w;
            box.occupyH = place.ori.h;
            box.rotation = place.ori.rotated ? "90" : "0";
            target.placed.add(box);
            pallet.boxes.add(box);
            currentWeight += nz(next.getBoxWeight());
            remaining.remove(pick);
            pallet.layers = layers;
            pallet.totalWeight = currentWeight;
            pallet.totalHeight = layers.stream().mapToDouble(l -> l.layerHeight).sum();
            // 若层高不一致用真实最高顶
            double maxTop = pallet.boxes.stream().mapToDouble(b -> b.z + b.occupyH).max().orElse(0);
            pallet.totalHeight = Math.max(pallet.totalHeight, maxTop);
        }
        pallet.layers = layers;
        pallet.totalWeight = currentWeight;
        double maxTop = pallet.boxes.stream().mapToDouble(b -> b.z + b.occupyH).max().orElse(0);
        pallet.totalHeight = maxTop;
    }

    /**
     * 优先补满未铺满且高度匹配的下层；否则按重/大优先。
     */
    private int selectNextTaskIndex(List<PalletPackingModel.BoxTask> remaining,
                                    List<Layer> layers,
                                    List<PlacedBox> placed,
                                    PalletPackingModel.PalletSpec spec,
                                    double currentWeight) {
        double palletArea = spec.getLength() * spec.getWidth();
        // 1) 未铺满层：找高度匹配且可放置的
        for (Layer layer : layers) {
            double used = layer.placed.stream().mapToDouble(b -> b.occupyL * b.occupyW).sum();
            if (used >= palletArea * 0.92) {
                continue;
            }
            int best = -1;
            double bestScore = -1;
            for (int i = 0; i < remaining.size(); i++) {
                PalletPackingModel.BoxTask t = remaining.get(i);
                if (currentWeight + nz(t.getBoxWeight()) > spec.getWeightLimit() + EPS) {
                    continue;
                }
                for (Orientation ori : orientations(t, spec.getLength(), spec.getWidth())) {
                    if (Math.abs(ori.h - layer.layerHeight) > EPS) {
                        continue;
                    }
                    if (findSupportedPlacement(placed, layer, ori, layer.z, spec) != null) {
                        double score = nz(t.getBoxWeight()) * 1000 + ori.l * ori.w;
                        if (score > bestScore) {
                            bestScore = score;
                            best = i;
                        }
                    }
                }
            }
            if (best >= 0) {
                return best;
            }
        }
        // 2) 任意可放置（重的优先，列表已排序）
        for (int i = 0; i < remaining.size(); i++) {
            if (findBestPlace(remaining.get(i), layers, placed, spec, currentWeight) != null) {
                return i;
            }
        }
        return -1;
    }

    private CandidatePlace findBestPlace(PalletPackingModel.BoxTask task,
                                         List<Layer> layers,
                                         List<PlacedBox> placed,
                                         PalletPackingModel.PalletSpec spec,
                                         double currentWeight) {
        if (currentWeight + nz(task.getBoxWeight()) > spec.getWeightLimit() + EPS) {
            return null;
        }
        List<Orientation> oris = orientations(task, spec.getLength(), spec.getWidth());
        if (oris.isEmpty()) {
            return null;
        }
        CandidatePlace best = null;
        // 先尝试已有同高层（补满）
        for (Layer layer : layers) {
            for (Orientation ori : oris) {
                if (Math.abs(ori.h - layer.layerHeight) > EPS) {
                    continue;
                }
                Placement p = findSupportedPlacement(placed, layer, ori, layer.z, spec);
                if (p != null) {
                    CandidatePlace c = new CandidatePlace(layer, ori, p.x, p.y, layer.z);
                    if (best == null || c.ori.l * c.ori.w > best.ori.l * best.ori.w) {
                        best = c;
                    }
                }
            }
        }
        if (best != null) {
            return best;
        }
        // 新开层：支撑面高度 = 当前堆顶相关平台；尝试在 maxTop 上开层
        double sitZ = placed.isEmpty() ? 0 : placed.stream().mapToDouble(b -> b.z + b.occupyH).max().orElse(0);
        // 也允许在已有层顶高度集合上开层（多平台）
        java.util.TreeSet<Double> tops = new java.util.TreeSet<>();
        tops.add(0.0);
        for (PlacedBox b : placed) {
            tops.add(round2(b.z + b.occupyH));
        }
        for (double z : tops) {
            if (z > sitZ + EPS) {
                continue; // 只在不超过当前最高顶的平台上试；优先较低平台
            }
            for (Orientation ori : oris) {
                if (z + ori.h > spec.getCargoHeightLimit() + EPS) {
                    continue;
                }
                Placement p = findSupportedPlacement(placed, null, ori, z, spec);
                if (p != null) {
                    return new CandidatePlace(null, ori, p.x, p.y, z);
                }
            }
        }
        // 若较低平台放不下，再试最高顶
        for (Orientation ori : oris) {
            if (sitZ + ori.h > spec.getCargoHeightLimit() + EPS) {
                continue;
            }
            Placement p = findSupportedPlacement(placed, null, ori, sitZ, spec);
            if (p != null) {
                return new CandidatePlace(null, ori, p.x, p.y, sitZ);
            }
        }
        return null;
    }

    private List<Orientation> orientations(PalletPackingModel.BoxTask task, double palletL, double palletW) {
        double l = task.getBoxLength();
        double w = task.getBoxWidth();
        double h = task.getBoxHeight();
        List<Orientation> options = new ArrayList<>();
        double overhang = constraints.getMaxOverhangMm();
        if (l <= palletL + overhang + EPS && w <= palletW + overhang + EPS) {
            options.add(new Orientation(l, w, h, false));
        }
        if (task.isAllowRotate() && w <= palletL + overhang + EPS && l <= palletW + overhang + EPS) {
            options.add(new Orientation(w, l, h, true));
        }
        options.sort(Comparator
                .comparingDouble((Orientation o) -> -(o.l * o.w))
                .thenComparing(o -> o.rotated));
        return options;
    }

    private Orientation chooseOrientation(PalletPackingModel.BoxTask task, double palletL, double palletW) {
        List<Orientation> options = orientations(task, palletL, palletW);
        return options.isEmpty() ? null : options.get(0);
    }

    private Placement findSupportedPlacement(List<PlacedBox> all,
                                             Layer sameLayer,
                                             Orientation ori,
                                             double sitZ,
                                             PalletPackingModel.PalletSpec spec) {
        double palletL = spec.getLength();
        double palletW = spec.getWidth();
        double overhang = constraints.getMaxOverhangMm();
        List<double[]> candidates = new ArrayList<>();
        candidates.add(new double[]{0, 0});
        List<PlacedBox> sameZ = new ArrayList<>();
        for (PlacedBox b : all) {
            if (sameLayer != null && sameLayer.placed.contains(b)) {
                sameZ.add(b);
            } else if (Math.abs(b.z - sitZ) < constraints.getSupportZTolerance()) {
                sameZ.add(b);
            }
        }
        for (PlacedBox existing : sameZ) {
            candidates.add(new double[]{existing.x + existing.occupyL, existing.y});
            candidates.add(new double[]{existing.x, existing.y + existing.occupyW});
            candidates.add(new double[]{existing.x + existing.occupyL, existing.y + existing.occupyW});
        }
        // 支撑箱顶的角点也可作为候选
        for (PlacedBox lower : all) {
            double top = lower.z + lower.occupyH;
            if (Math.abs(top - sitZ) > constraints.getSupportZTolerance()) {
                continue;
            }
            candidates.add(new double[]{lower.x, lower.y});
            candidates.add(new double[]{lower.x + lower.occupyL - ori.l, lower.y});
            candidates.add(new double[]{lower.x, lower.y + lower.occupyW - ori.w});
            candidates.add(new double[]{lower.x + lower.occupyL - ori.l, lower.y + lower.occupyW - ori.w});
        }
        candidates.sort(Comparator.comparingDouble((double[] c) -> c[1]).thenComparingDouble(c -> c[0]));
        Placement found = null;
        double bestSupport = -1;
        for (double[] c : candidates) {
            double x = Math.max(-overhang, c[0]);
            double y = Math.max(-overhang, c[1]);
            if (!fitsBounds(x, y, ori.l, ori.w, palletL, palletW, overhang)) {
                continue;
            }
            if (collidesVolume(all, x, y, sitZ, ori.l, ori.w, ori.h)) {
                continue;
            }
            double ratio = computeSupportRatio(all, x, y, ori.l, ori.w, sitZ);
            if (ratio + EPS < constraints.getMinSupportRatio()) {
                continue;
            }
            if (!isCentroidSupported(all, x, y, ori.l, ori.w, sitZ)) {
                continue;
            }
            if (ratio > bestSupport + EPS || (Math.abs(ratio - bestSupport) <= EPS && found != null
                    && (x + y < found.x + found.y))) {
                bestSupport = ratio;
                found = new Placement(x, y);
            }
            if (bestSupport >= 0.999) {
                break;
            }
        }
        if (found != null) {
            return found;
        }
        // 网格兜底
        double step = 20;
        for (double y = 0; y + ori.w <= palletW + overhang + EPS; y += step) {
            for (double x = 0; x + ori.l <= palletL + overhang + EPS; x += step) {
                if (!fitsBounds(x, y, ori.l, ori.w, palletL, palletW, overhang)) {
                    continue;
                }
                if (collidesVolume(all, x, y, sitZ, ori.l, ori.w, ori.h)) {
                    continue;
                }
                double ratio = computeSupportRatio(all, x, y, ori.l, ori.w, sitZ);
                if (ratio + EPS < constraints.getMinSupportRatio()) {
                    continue;
                }
                if (!isCentroidSupported(all, x, y, ori.l, ori.w, sitZ)) {
                    continue;
                }
                return new Placement(x, y);
            }
        }
        return null;
    }

    private boolean fitsBounds(double x, double y, double l, double w,
                               double palletL, double palletW, double overhang) {
        return x >= -overhang - EPS && y >= -overhang - EPS
                && x + l <= palletL + overhang + EPS
                && y + w <= palletW + overhang + EPS;
    }

    private double computeSupportRatio(List<PlacedBox> all, double x, double y, double l, double w, double sitZ) {
        double area = l * w;
        if (area <= EPS) {
            return 1;
        }
        if (sitZ <= constraints.getSupportZTolerance()) {
            return 1.0;
        }
        double supported = 0;
        for (PlacedBox lower : all) {
            double top = lower.z + lower.occupyH;
            if (Math.abs(top - sitZ) > constraints.getSupportZTolerance()) {
                continue;
            }
            double left = Math.max(x, lower.x);
            double right = Math.min(x + l, lower.x + lower.occupyL);
            double front = Math.max(y, lower.y);
            double back = Math.min(y + w, lower.y + lower.occupyW);
            if (right > left + EPS && back > front + EPS) {
                supported += (right - left) * (back - front);
            }
        }
        return Math.min(1.0, supported / area);
    }

    private boolean isCentroidSupported(List<PlacedBox> all, double x, double y, double l, double w, double sitZ) {
        if (sitZ <= constraints.getSupportZTolerance()) {
            return true;
        }
        double cx = x + l / 2;
        double cy = y + w / 2;
        for (PlacedBox lower : all) {
            double top = lower.z + lower.occupyH;
            if (Math.abs(top - sitZ) > constraints.getSupportZTolerance()) {
                continue;
            }
            if (cx >= lower.x - EPS && cx <= lower.x + lower.occupyL + EPS
                    && cy >= lower.y - EPS && cy <= lower.y + lower.occupyW + EPS) {
                return true;
            }
        }
        return false;
    }

    private boolean collidesVolume(List<PlacedBox> all, double x, double y, double z,
                                   double l, double w, double h) {
        for (PlacedBox b : all) {
            boolean ox = x < b.x + b.occupyL - EPS && x + l > b.x + EPS;
            boolean oy = y < b.y + b.occupyW - EPS && y + w > b.y + EPS;
            boolean oz = z < b.z + b.occupyH - EPS && z + h > b.z + EPS;
            if (ox && oy && oz) {
                return true;
            }
        }
        return false;
    }

    private Placement findPlacement(Layer layer, double boxL, double boxW, double palletL, double palletW) {
        // 兼容 isTail：同层无支撑检查的快速探测（尾托判定）
        Orientation ori = new Orientation(boxL, boxW, layer.layerHeight, false);
        PalletPackingModel.PalletSpec spec = new PalletPackingModel.PalletSpec();
        spec.setLength(palletL);
        spec.setWidth(palletW);
        spec.setWeightLimit(1e12);
        spec.setCargoHeightLimit(1e12);
        // 构造临时 all = layer.placed only for collision; support uses empty below if z>0 may fail
        // isTail only needs free XY slot on same layer
        List<double[]> candidates = new ArrayList<>();
        candidates.add(new double[]{0, 0});
        for (PlacedBox existing : layer.placed) {
            candidates.add(new double[]{existing.x + existing.occupyL, existing.y});
            candidates.add(new double[]{existing.x, existing.y + existing.occupyW});
        }
        for (double[] c : candidates) {
            double x = c[0];
            double y = c[1];
            if (x + boxL > palletL + EPS || y + boxW > palletW + EPS) {
                continue;
            }
            if (!collides(layer.placed, x, y, boxL, boxW)) {
                return new Placement(x, y);
            }
        }
        return null;
    }

    private boolean collides(List<PlacedBox> placed, double x, double y, double l, double w) {
        for (PlacedBox b : placed) {
            boolean overlapX = x < b.x + b.occupyL - EPS && x + l > b.x + EPS;
            boolean overlapY = y < b.y + b.occupyW - EPS && y + w > b.y + EPS;
            if (overlapX && overlapY) {
                return true;
            }
        }
        return false;
    }

    private boolean isTail(InternalPallet pallet, PalletPackingModel.PalletSpec spec,
                           PalletPackingModel.BoxTask sample) {
        // 若同规格至少还能再放一箱，则视为尾托
        Orientation ori = chooseOrientation(sample, spec.getLength(), spec.getWidth());
        if (ori == null) {
            return false;
        }
        if (pallet.totalWeight + nz(sample.getBoxWeight()) > spec.getWeightLimit() + EPS) {
            return true;
        }
        // 尝试在现有层或新层找空位
        for (Layer layer : pallet.layers) {
            if (Math.abs(layer.layerHeight - ori.h) > EPS) {
                continue;
            }
            if (findPlacement(layer, ori.l, ori.w, spec.getLength(), spec.getWidth()) != null) {
                return true;
            }
        }
        double usedH = pallet.totalHeight;
        return usedH + ori.h <= spec.getCargoHeightLimit() + EPS;
    }

    private List<InternalPallet> mergeTails(PalletPackingModel.PackingRequest request,
                                            List<InternalPallet> tails) {
        List<InternalPallet> open = new ArrayList<>(tails);
        List<InternalPallet> done = new ArrayList<>();
        while (!open.isEmpty()) {
            InternalPallet base = open.remove(0);
            boolean mergedAny = true;
            while (mergedAny) {
                mergedAny = false;
                for (int i = 0; i < open.size(); i++) {
                    InternalPallet other = open.get(i);
                    if (!compatibleForMix(request, base, other)) {
                        continue;
                    }
                    if (tryMerge(base, other, request.getPalletSpec())) {
                        open.remove(i);
                        base.groupType = "mixed";
                        base.productKeys = mergeKeys(base.productKeys, other.productKeys);
                        for (PlacedBox box : base.boxes) {
                            box.isMixed = "1";
                        }
                        mergedAny = true;
                        break;
                    }
                }
            }
            done.add(base);
        }
        return done;
    }

    private boolean compatibleForMix(PalletPackingModel.PackingRequest request,
                                     InternalPallet a, InternalPallet b) {
        String modeA = dominantMode(a);
        String modeB = dominantMode(b);
        boolean involvesVirtual = "virtual".equals(modeA) || "virtual".equals(modeB);
        if (involvesVirtual && !Boolean.TRUE.equals(request.getAllowMixedNoBoxPallet())) {
            return false;
        }
        if (!Objects.equals(modeA, modeB) && !Boolean.TRUE.equals(request.getAllowMixedPackagePallet())) {
            return false;
        }
        return true;
    }

    private boolean tryMerge(InternalPallet base, InternalPallet other, PalletPackingModel.PalletSpec spec) {
        // 将 other 的箱子尝试追加到 base
        List<PalletPackingModel.BoxTask> tasks = other.boxes.stream().map(b -> b.task).collect(Collectors.toList());
        List<PalletPackingModel.BoxTask> remaining = new ArrayList<>(tasks);
        InternalPallet snapshot = clonePallet(base);
        fillPallet(base, spec, remaining);
        if (!remaining.isEmpty()) {
            // 回滚
            base.boxes = snapshot.boxes;
            base.layers = snapshot.layers;
            base.totalWeight = snapshot.totalWeight;
            base.totalHeight = snapshot.totalHeight;
            return false;
        }
        return true;
    }

    private InternalPallet clonePallet(InternalPallet src) {
        InternalPallet c = new InternalPallet();
        c.palletNo = src.palletNo;
        c.groupType = src.groupType;
        c.productKeys = src.productKeys;
        c.totalWeight = src.totalWeight;
        c.totalHeight = src.totalHeight;
        c.boxes = new ArrayList<>(src.boxes);
        c.layers = new ArrayList<>();
        for (Layer l : src.layers) {
            Layer nl = new Layer();
            nl.layerNo = l.layerNo;
            nl.z = l.z;
            nl.layerHeight = l.layerHeight;
            nl.placed = new ArrayList<>(l.placed);
            c.layers.add(nl);
        }
        return c;
    }

    private String dominantMode(InternalPallet pallet) {
        return pallet.boxes.stream()
                .map(b -> normalizeMode(b.task.getPackageMode()))
                .collect(Collectors.groupingBy(m -> m, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("box");
    }

    private void renumber(List<InternalPallet> pallets) {
        int i = 1;
        for (InternalPallet p : pallets) {
            p.palletNo = "P" + i++;
        }
    }

    private PalletPackingResult toResult(List<InternalPallet> pallets, PalletPackingModel.PalletSpec spec) {
        PalletPackingResult result = new PalletPackingResult();
        List<PalletPackingResult.GroupResult> groupResults = new ArrayList<>();
        List<PalletPackingResult.ItemResult> itemResults = new ArrayList<>();
        List<PalletPackingResult.BoxResult> boxResults = new ArrayList<>();
        Map<String, List<InternalPallet>> byGroup = new LinkedHashMap<>();
        for (InternalPallet p : pallets) {
            String key = p.groupType + "|" + p.productKeys;
            byGroup.computeIfAbsent(key, k -> new ArrayList<>()).add(p);
        }
        int groupIdx = 1;
        int itemSort = 1;
        int boxSort = 1;
        double areaSum = 0;
        double heightSum = 0;
        boolean hasMixed = false;
        double palletArea = spec.getLength() * spec.getWidth();

        for (Map.Entry<String, List<InternalPallet>> e : byGroup.entrySet()) {
            List<InternalPallet> list = e.getValue();
            InternalPallet sample = list.get(0);
            PalletPackingResult.GroupResult g = new PalletPackingResult.GroupResult();
            g.setGroupNo("G" + groupIdx++);
            g.setGroupType(sample.groupType);
            g.setProductKeys(sample.productKeys);
            g.setPalletCount(list.size());
            g.setSort(groupIdx);
            double gWeight = 0;
            int gBoxes = 0;
            double gArea = 0;
            double gHeight = 0;
            for (InternalPallet p : list) {
                if ("mixed".equals(p.groupType)) {
                    hasMixed = true;
                }
                PalletPackingResult.ItemResult item = new PalletPackingResult.ItemResult();
                item.setGroupNo(g.getGroupNo());
                item.setGroupType(p.groupType);
                item.setProductKeys(p.productKeys);
                item.setPalletNo(p.palletNo);
                item.setLayerCount(p.layers.size());
                item.setBoxCount(p.boxes.size());
                item.setTotalWeight(round2(p.totalWeight));
                item.setTotalHeight(round2(p.totalHeight));
                double footprint = p.boxes.stream().mapToDouble(b -> b.occupyL * b.occupyW).sum();
                // 按层近似：用底层占用面积 / 托盘面积
                double layerFootprint = 0;
                if (!p.layers.isEmpty()) {
                    layerFootprint = p.layers.get(0).placed.stream().mapToDouble(b -> b.occupyL * b.occupyW).sum();
                }
                double areaUtil = palletArea <= 0 ? 0 : Math.min(1.0, layerFootprint / palletArea);
                double heightUtil = spec.getCargoHeightLimit() <= 0 ? 0
                        : Math.min(1.0, p.totalHeight / spec.getCargoHeightLimit());
                item.setAreaUtilization(round4(areaUtil));
                item.setHeightUtilization(round4(heightUtil));
                item.setStabilityScore(round4(0.5 * areaUtil + 0.5 * (1 - Math.abs(heightUtil - areaUtil))));
                item.setSort(itemSort++);
                itemResults.add(item);

                for (PlacedBox box : p.boxes) {
                    PalletPackingResult.BoxResult br = new PalletPackingResult.BoxResult();
                    br.setBoxKey(box.task.getTaskKey());
                    br.setTaskKey(box.task.getTaskKey());
                    br.setProductLabel(box.task.getProductLabel());
                    br.setGroupNo(g.getGroupNo());
                    br.setGroupType(p.groupType);
                    br.setProductKeys(p.productKeys);
                    br.setProductKey(productKey(box.task));
                    br.setPalletNo(p.palletNo);
                    br.setOrderProductId(box.task.getOrderProductId());
                    br.setBatchId(box.task.getBatchId());
                    br.setBatchName(box.task.getBatchName());
                    br.setPackageMode(normalizeMode(box.task.getPackageMode()));
                    br.setBoxLength(box.task.getBoxLength());
                    br.setBoxWidth(box.task.getBoxWidth());
                    br.setBoxHeight(box.task.getBoxHeight());
                    br.setBoxWeight(box.task.getBoxWeight());
                    br.setRotation(box.rotation);
                    br.setLayerNo(box.layerNo);
                    br.setPositionX(round2(box.x));
                    br.setPositionY(round2(box.y));
                    br.setPositionZ(round2(box.z));
                    br.setOccupyLength(round2(box.occupyL));
                    br.setOccupyWidth(round2(box.occupyW));
                    br.setOccupyHeight(round2(box.occupyH));
                    br.setIsMixed(box.isMixed);
                    br.setSort(boxSort++);
                    boxResults.add(br);
                }

                gWeight += p.totalWeight;
                gBoxes += p.boxes.size();
                gArea += areaUtil;
                gHeight += heightUtil;
                areaSum += areaUtil;
                heightSum += heightUtil;
                result.setTotalBoxCount(result.getTotalBoxCount() + p.boxes.size());
                result.setTotalProductWeight(result.getTotalProductWeight() + p.totalWeight);
            }
            g.setBoxCount(gBoxes);
            g.setTotalWeight(round2(gWeight));
            g.setAreaUtilization(round4(gArea / list.size()));
            g.setHeightUtilization(round4(gHeight / list.size()));
            groupResults.add(g);
        }

        result.setTotalPalletCount(pallets.size());
        result.setTotalProductWeight(round2(result.getTotalProductWeight()));
        result.setHasMixedGroup(hasMixed ? "1" : "0");
        if (!pallets.isEmpty()) {
            result.setAvgAreaUtilization(round4(areaSum / pallets.size()));
            result.setAvgHeightUtilization(round4(heightSum / pallets.size()));
        }
        result.setGroupList(groupResults);
        result.setItemList(itemResults);
        result.setBoxList(boxResults);
        return result;
    }

    private static String productKey(PalletPackingModel.BoxTask task) {
        if (task.getProductKey() != null && !task.getProductKey().isBlank()) {
            return task.getProductKey().trim();
        }
        return Objects.toString(task.getOrderProductId(), "UNKNOWN");
    }

    private static String label(PalletPackingModel.BoxTask task) {
        if (task.getProductLabel() != null && !task.getProductLabel().isBlank()) {
            return task.getProductLabel();
        }
        return productKey(task);
    }

    private static String normalizeMode(String mode) {
        if (mode == null) {
            return "box";
        }
        String m = mode.trim().toLowerCase();
        if ("carton".equals(m) || "virtual".equals(m) || "box".equals(m)) {
            return m;
        }
        return "box";
    }

    private static String mergeKeys(String a, String b) {
        LinkedHashMap<String, Boolean> map = new LinkedHashMap<>();
        for (String part : (a + "," + b).split(",")) {
            String p = part.trim();
            if (!p.isEmpty()) {
                map.put(p, true);
            }
        }
        return String.join(",", map.keySet());
    }

    private static boolean positive(Double v) {
        return v != null && v > 0 && !v.isNaN() && !v.isInfinite();
    }

    private static double nz(Double v) {
        return v == null ? 0 : v;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    private static String fmt(double v) {
        if (Math.abs(v - Math.rint(v)) < EPS) {
            return String.valueOf((long) Math.rint(v));
        }
        return String.valueOf(round2(v));
    }

    private static class CandidatePlace {
        final Layer layer;
        final Orientation ori;
        final double x, y, z;

        CandidatePlace(Layer layer, Orientation ori, double x, double y, double z) {
            this.layer = layer;
            this.ori = ori;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static class Orientation {
        final double l, w, h;
        final boolean rotated;

        Orientation(double l, double w, double h, boolean rotated) {
            this.l = l;
            this.w = w;
            this.h = h;
            this.rotated = rotated;
        }
    }

    private static class Placement {
        final double x, y;

        Placement(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    private static class Layer {
        int layerNo;
        double z;
        double layerHeight;
        List<PlacedBox> placed = new ArrayList<>();
    }

    private static class PlacedBox {
        PalletPackingModel.BoxTask task;
        int layerNo;
        double x, y, z;
        double occupyL, occupyW, occupyH;
        String rotation = "0";
        String isMixed = "0";
    }

    private static class InternalPallet {
        String palletNo;
        String groupType = "single";
        String productKeys;
        double totalWeight;
        double totalHeight;
        List<Layer> layers = new ArrayList<>();
        List<PlacedBox> boxes = new ArrayList<>();
    }
}
