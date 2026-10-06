package com.example.pallet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "pallet_item")
public class PalletItem {
    @Id
    private String id;
    private String planId;
    private String groupId;
    private String orderId;
    private String palletNo;
    private String productKeys;
    @Column(length = 4000)
    private String productComposition;
    @Column(length = 20000)
    private String boxLayout;
    private Integer layerCount;
    private Integer boxCount;
    private Double totalWeight;
    private Double totalHeight;
    private Double areaUtilization;
    private Double heightUtilization;
    private Double stabilityScore;
    private Integer sortNo;
}
