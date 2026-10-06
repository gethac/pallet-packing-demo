package com.example.pallet.engine;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 托盘装载结果模型 */
@Data
public class PalletPackingResult {
    private Integer totalPalletCount = 0;
    private Integer totalBoxCount = 0;
    private Double totalProductWeight = 0D;
    private String hasMixedGroup = "0";
    private Double avgAreaUtilization = 0D;
    private Double avgHeightUtilization = 0D;
    private List<GroupResult> groupList = new ArrayList<>();
    private List<ItemResult> itemList = new ArrayList<>();
    private List<BoxResult> boxList = new ArrayList<>();

    public List<GroupResult> getGroupList() {
        return List.copyOf(groupList);
    }

    public void setGroupList(List<GroupResult> groupList) {
        this.groupList = new ArrayList<>(groupList == null ? List.of() : groupList);
    }

    public List<ItemResult> getItemList() {
        return List.copyOf(itemList);
    }

    public void setItemList(List<ItemResult> itemList) {
        this.itemList = new ArrayList<>(itemList == null ? List.of() : itemList);
    }

    public List<BoxResult> getBoxList() {
        return List.copyOf(boxList);
    }

    public void setBoxList(List<BoxResult> boxList) {
        this.boxList = new ArrayList<>(boxList == null ? List.of() : boxList);
    }

    @Data
    public static class GroupResult {
        private String groupNo;
        private String groupType;
        private String productKeys;
        private Integer palletCount = 0;
        private Integer boxCount = 0;
        private Double totalWeight = 0D;
        private Double areaUtilization = 0D;
        private Double heightUtilization = 0D;
        private Integer sort = 0;
    }

    @Data
    public static class ItemResult {
        private String groupNo;
        private String groupType;
        private String productKeys;
        private String palletNo;
        private Integer layerCount = 0;
        private Integer boxCount = 0;
        private Double totalWeight = 0D;
        private Double totalHeight = 0D;
        private Double areaUtilization = 0D;
        private Double heightUtilization = 0D;
        private Double stabilityScore = 0D;
        private Integer sort = 0;
    }

    @Data
    public static class BoxResult {
        private String groupNo;
        private String groupType;
        private String productKeys;
        private String productKey;
        private String palletNo;
        private String orderProductId;
        private String batchId;
        private String batchName;
        private String packageMode;
        private Double boxLength;
        private Double boxWidth;
        private Double boxHeight;
        private Double boxWeight;
        private String rotation;
        private Integer layerNo;
        private Double positionX;
        private Double positionY;
        private Double positionZ;
        private Double occupyLength;
        private Double occupyWidth;
        private Double occupyHeight;
        private String isMixed = "0";
        private Integer sort = 0;
    }
}
