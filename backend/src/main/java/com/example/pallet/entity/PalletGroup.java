package com.example.pallet.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "pallet_group")
public class PalletGroup {
    @Id
    private String id;
    private String planId;
    private String orderId;
    private String groupNo;
    private String groupType;
    private String productKeys;
    private Integer palletCount;
    private Integer boxCount;
    private Double totalWeight;
    private Double areaUtilization;
    private Double heightUtilization;
    private Integer sortNo;
}
