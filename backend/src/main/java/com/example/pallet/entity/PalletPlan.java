package com.example.pallet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "pallet_plan")
public class PalletPlan {
    @Id
    private String id;
    private String orderId;
    private String batchId;
    private String palletStandardId;
    private String palletStandardCode;
    private Double palletLength;
    private Double palletWidth;
    private Double weightLimit;
    private Double cargoHeightLimit;
    private Integer totalPalletCount;
    private Integer totalBoxCount;
    private String packageMode;
    private Double totalProductWeight;
    private String hasMixedGroup;
    private String allowMixedPallet;
    private String allowMixedPackagePallet;
    private String allowMixedNoBoxPallet;
    private Double avgAreaUtilization;
    private Double avgHeightUtilization;
    private LocalDateTime calculatedTime;
    @Column(length = 128)
    private String calculationInputHash;

    @Transient
    private List<PalletGroup> groupList = new ArrayList<>();
    @Transient
    private List<PalletItem> itemList = new ArrayList<>();

    public List<PalletGroup> getGroupList() {
        return List.copyOf(groupList);
    }

    public void setGroupList(List<PalletGroup> groupList) {
        this.groupList = new ArrayList<>(groupList == null ? List.of() : groupList);
    }

    public List<PalletItem> getItemList() {
        return List.copyOf(itemList);
    }

    public void setItemList(List<PalletItem> itemList) {
        this.itemList = new ArrayList<>(itemList == null ? List.of() : itemList);
    }
}
