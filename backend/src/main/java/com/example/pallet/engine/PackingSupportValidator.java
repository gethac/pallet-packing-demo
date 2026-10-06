package com.example.pallet.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * 装托结果校验：重叠、悬空（支撑率）、超边、超重、超高。
 */
public class PackingSupportValidator {

    private static final double EPS = 1e-6;

    public static class Issue {
        public final String code;
        public final String message;
        public final String palletNo;
        public final String boxKey;

        public Issue(String code, String message, String palletNo, String boxKey) {
            this.code = code;
            this.message = message;
            this.palletNo = palletNo;
            this.boxKey = boxKey;
        }

        @Override
        public String toString() {
            return code + "[" + palletNo + "/" + boxKey + "]: " + message;
        }
    }

    public static class Report {
        public final List<Issue> issues = new ArrayList<>();
        public double minSupportRatio = 1.0;
        public double avgSupportRatio = 1.0;

        public boolean ok() {
            return issues.isEmpty();
        }
    }

    public Report validate(PalletPackingResult result,
                           PalletPackingModel.PalletSpec spec,
                           PackingConstraints constraints) {
        Report report = new Report();
        if (result == null || result.getBoxList() == null || result.getBoxList().isEmpty()) {
            return report;
        }
        PackingConstraints c = constraints == null ? PackingConstraints.defaults() : constraints;
        double palletL = spec.getLength();
        double palletW = spec.getWidth();
        double weightLimit = spec.getWeightLimit();
        double heightLimit = spec.getCargoHeightLimit();
        double overhang = c.getMaxOverhangMm();

        // group by pallet
        java.util.Map<String, List<PalletPackingResult.BoxResult>> byPallet = new java.util.LinkedHashMap<>();
        for (PalletPackingResult.BoxResult b : result.getBoxList()) {
            byPallet.computeIfAbsent(String.valueOf(b.getPalletNo()), k -> new ArrayList<>()).add(b);
        }

        int supportSamples = 0;
        double supportSum = 0;

        for (java.util.Map.Entry<String, List<PalletPackingResult.BoxResult>> e : byPallet.entrySet()) {
            String palletNo = e.getKey();
            List<PalletPackingResult.BoxResult> boxes = e.getValue();
            double totalWeight = 0;
            double maxTop = 0;

            for (int i = 0; i < boxes.size(); i++) {
                PalletPackingResult.BoxResult a = boxes.get(i);
                double ax = nz(a.getPositionX());
                double ay = nz(a.getPositionY());
                double az = nz(a.getPositionZ());
                double al = nz(a.getOccupyLength());
                double aw = nz(a.getOccupyWidth());
                double ah = nz(a.getOccupyHeight());
                totalWeight += nz(a.getBoxWeight());
                maxTop = Math.max(maxTop, az + ah);

                // overhang
                if (ax < -overhang - EPS || ay < -overhang - EPS
                        || ax + al > palletL + overhang + EPS
                        || ay + aw > palletW + overhang + EPS) {
                    report.issues.add(new Issue("OVERHANG",
                            String.format("超边 x=%.1f y=%.1f L=%.1f W=%.1f pallet=%.0fx%.0f",
                                    ax, ay, al, aw, palletL, palletW),
                            palletNo, a.getBoxKey()));
                }

                // overlap with others (same height band)
                for (int j = i + 1; j < boxes.size(); j++) {
                    PalletPackingResult.BoxResult b = boxes.get(j);
                    if (volumeOverlap(a, b)) {
                        report.issues.add(new Issue("OVERLAP",
                                "与 " + b.getBoxKey() + " 体积重叠",
                                palletNo, a.getBoxKey()));
                    }
                }

                // support
                double ratio = supportRatio(a, boxes, c.getSupportZTolerance());
                supportSum += ratio;
                supportSamples++;
                report.minSupportRatio = Math.min(report.minSupportRatio, ratio);
                if (ratio + EPS < c.getMinSupportRatio()) {
                    report.issues.add(new Issue("UNSUPPORTED",
                            String.format("支撑率 %.3f < 阈值 %.3f (z=%.1f)",
                                    ratio, c.getMinSupportRatio(), az),
                            palletNo, a.getBoxKey()));
                }

                // CG in support: center point must be supported (or on pallet)
                if (az > c.getSupportZTolerance()) {
                    double cx = ax + al / 2;
                    double cy = ay + aw / 2;
                    if (!pointSupported(cx, cy, az, boxes, c.getSupportZTolerance())) {
                        report.issues.add(new Issue("CG_UNSUPPORTED",
                                "重心投影不在支撑区内",
                                palletNo, a.getBoxKey()));
                    }
                }
            }

            if (totalWeight > weightLimit + EPS) {
                report.issues.add(new Issue("OVERWEIGHT",
                        "托盘总重 " + totalWeight + " > " + weightLimit,
                        palletNo, "-"));
            }
            if (maxTop > heightLimit + EPS) {
                report.issues.add(new Issue("OVERHEIGHT",
                        "堆高 " + maxTop + " > " + heightLimit,
                        palletNo, "-"));
            }
        }

        if (supportSamples > 0) {
            report.avgSupportRatio = supportSum / supportSamples;
        }
        if (supportSamples == 0) {
            report.minSupportRatio = 1.0;
        }
        return report;
    }

    public void assertValid(PalletPackingResult result,
                            PalletPackingModel.PalletSpec spec,
                            PackingConstraints constraints) {
        Report r = validate(result, spec, constraints);
        if (!r.ok()) {
            StringBuilder sb = new StringBuilder("装托校验失败 (" + r.issues.size() + "):\n");
            for (Issue issue : r.issues) {
                sb.append(" - ").append(issue).append('\n');
            }
            throw new PackingException(sb.toString());
        }
    }

    static double supportRatio(PalletPackingResult.BoxResult upper,
                               List<PalletPackingResult.BoxResult> all,
                               double zTol) {
        double az = nz(upper.getPositionZ());
        double area = nz(upper.getOccupyLength()) * nz(upper.getOccupyWidth());
        if (area <= EPS) {
            return 1.0;
        }
        if (az <= zTol) {
            return 1.0; // on pallet
        }
        double supported = 0;
        for (PalletPackingResult.BoxResult lower : all) {
            if (lower == upper) {
                continue;
            }
            double top = nz(lower.getPositionZ()) + nz(lower.getOccupyHeight());
            if (Math.abs(top - az) > zTol) {
                continue;
            }
            supported += footprintIntersection(upper, lower);
        }
        return Math.min(1.0, supported / area);
    }

    static boolean pointSupported(double x, double y, double z,
                                  List<PalletPackingResult.BoxResult> all, double zTol) {
        if (z <= zTol) {
            return true;
        }
        for (PalletPackingResult.BoxResult lower : all) {
            double top = nz(lower.getPositionZ()) + nz(lower.getOccupyHeight());
            if (Math.abs(top - z) > zTol) {
                continue;
            }
            double lx = nz(lower.getPositionX());
            double ly = nz(lower.getPositionY());
            double ll = nz(lower.getOccupyLength());
            double lw = nz(lower.getOccupyWidth());
            if (x >= lx - EPS && x <= lx + ll + EPS && y >= ly - EPS && y <= ly + lw + EPS) {
                return true;
            }
        }
        return false;
    }

    static double footprintIntersection(PalletPackingResult.BoxResult a, PalletPackingResult.BoxResult b) {
        double left = Math.max(nz(a.getPositionX()), nz(b.getPositionX()));
        double right = Math.min(nz(a.getPositionX()) + nz(a.getOccupyLength()),
                nz(b.getPositionX()) + nz(b.getOccupyLength()));
        double front = Math.max(nz(a.getPositionY()), nz(b.getPositionY()));
        double back = Math.min(nz(a.getPositionY()) + nz(a.getOccupyWidth()),
                nz(b.getPositionY()) + nz(b.getOccupyWidth()));
        if (right <= left + EPS || back <= front + EPS) {
            return 0;
        }
        return (right - left) * (back - front);
    }

    static boolean volumeOverlap(PalletPackingResult.BoxResult a, PalletPackingResult.BoxResult b) {
        boolean ox = nz(a.getPositionX()) < nz(b.getPositionX()) + nz(b.getOccupyLength()) - EPS
                && nz(a.getPositionX()) + nz(a.getOccupyLength()) > nz(b.getPositionX()) + EPS;
        boolean oy = nz(a.getPositionY()) < nz(b.getPositionY()) + nz(b.getOccupyWidth()) - EPS
                && nz(a.getPositionY()) + nz(a.getOccupyWidth()) > nz(b.getPositionY()) + EPS;
        boolean oz = nz(a.getPositionZ()) < nz(b.getPositionZ()) + nz(b.getOccupyHeight()) - EPS
                && nz(a.getPositionZ()) + nz(a.getOccupyHeight()) > nz(b.getPositionZ()) + EPS;
        return ox && oy && oz;
    }

    private static double nz(Double v) {
        return v == null ? 0 : v;
    }
}
