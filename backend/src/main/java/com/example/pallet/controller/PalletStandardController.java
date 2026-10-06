package com.example.pallet.controller;

import com.example.pallet.dto.ApiResponse;
import com.example.pallet.service.PalletPlanService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pallet-standard")
public class PalletStandardController {

    private final PalletPlanService palletPlanService;

    public PalletStandardController(PalletPlanService palletPlanService) {
        this.palletPlanService = palletPlanService;
    }

    @GetMapping("/listEnabled")
    public ApiResponse<?> listEnabledGet() {
        return ApiResponse.ok(palletPlanService.listEnabledStandards());
    }

    @PostMapping("/listEnabled")
    public ApiResponse<?> listEnabledPost() {
        return ApiResponse.ok(palletPlanService.listEnabledStandards());
    }
}
