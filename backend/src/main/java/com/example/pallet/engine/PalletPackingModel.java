package com.example.pallet.engine;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 托盘装载输入模型（从生产代码抽取并去敏） */
public class PalletPackingModel {

    @Data
    public static class PackingRequest {
        private PalletSpec palletSpec;
        private List<BoxTask> boxTaskList = new ArrayList<>();
        private Boolean allowMixedPallet;
        private Boolean allowMixedPackagePallet;
        private Boolean allowMixedNoBoxPallet;
    }

    @Data
    public static class PalletSpec {
        private String palletStandardId;
        private String palletStandardCode;
        private Double length;
        private Double width;
        private Double weightLimit;
        private Double cargoHeightLimit;
    }

    @Data
    public static class BoxTask {
        private String taskKey;
        private String orderProductId;
        private String productKey;
        private String productLabel;
        private String batchId;
        private String batchName;
        private String packageMode; // box / carton / virtual
        private Double boxLength;
        private Double boxWidth;
        private Double boxHeight;
        private Double boxWeight;
        private boolean allowRotate = true;
    }
}
