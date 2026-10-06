package com.example.pallet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "pallet_standard")
public class PalletStandard {
    @Id
    private String id;
    private String code;
    private Double length;
    private Double width;
    /** 1=启用 */
    private String enabled;
}
