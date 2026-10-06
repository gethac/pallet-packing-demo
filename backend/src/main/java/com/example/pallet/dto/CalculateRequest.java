package com.example.pallet.dto;

import lombok.Data;

@Data
public class CalculateRequest {
    private String palletStandardId;
    private Double weightLimit;
    private Double cargoHeightLimit;
    /** "1"/"0" */
    private String allowMixedPallet = "0";
    private String allowMixedPackagePallet = "0";
    private String allowMixedNoBoxPallet = "0";
}
