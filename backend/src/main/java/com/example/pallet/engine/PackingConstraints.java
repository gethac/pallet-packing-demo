package com.example.pallet.engine;

/**
 * 装托几何/力学约束（可配置）。
 */
public class PackingConstraints {
    /** 底面落在下层箱顶或托盘面的最小面积比例 */
    private double minSupportRatio = 0.80;
    /** 允许超出托盘边界的毫米数，默认 0（不允许超边） */
    private double maxOverhangMm = 0.0;
    /** 支撑面高度容差 */
    private double supportZTolerance = 1e-3;

    public static PackingConstraints defaults() {
        return new PackingConstraints();
    }

    public double getMinSupportRatio() {
        return minSupportRatio;
    }

    public void setMinSupportRatio(double minSupportRatio) {
        this.minSupportRatio = minSupportRatio;
    }

    public double getMaxOverhangMm() {
        return maxOverhangMm;
    }

    public void setMaxOverhangMm(double maxOverhangMm) {
        this.maxOverhangMm = maxOverhangMm;
    }

    public double getSupportZTolerance() {
        return supportZTolerance;
    }

    public void setSupportZTolerance(double supportZTolerance) {
        this.supportZTolerance = supportZTolerance;
    }
}
