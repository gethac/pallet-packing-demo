package com.example.pallet.controller;

import com.example.pallet.dto.ApiResponse;
import com.example.pallet.dto.CalculateRequest;
import com.example.pallet.engine.PackingException;
import com.example.pallet.service.PalletPlanService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sale-order/pallet")
public class PalletController {

    private final PalletPlanService palletPlanService;

    public PalletController(PalletPlanService palletPlanService) {
        this.palletPlanService = palletPlanService;
    }

    @GetMapping("/{orderId}")
    public ApiResponse<?> get(@PathVariable String orderId,
                              @RequestParam(required = false, defaultValue = "") String batchId,
                              @RequestParam(defaultValue = "false") boolean refresh) {
        try {
            return ApiResponse.ok(palletPlanService.getCalculation(orderId, batchId, refresh));
        } catch (PackingException | IllegalStateException e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @GetMapping("/{orderId}/view")
    public ApiResponse<?> view(@PathVariable String orderId,
                               @RequestParam(required = false, defaultValue = "") String batchId) {
        try {
            return ApiResponse.ok(palletPlanService.getView(orderId, batchId));
        } catch (PackingException | IllegalStateException e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @GetMapping("/{orderId}/preview")
    public ApiResponse<?> preview(@PathVariable String orderId,
                                  @RequestParam(required = false, defaultValue = "") String batchId,
                                  @RequestParam String palletNo) {
        try {
            return ApiResponse.ok(palletPlanService.getPreview(orderId, batchId, palletNo));
        } catch (PackingException | IllegalStateException e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @PostMapping("/{orderId}/calculate")
    public ApiResponse<?> calculate(@PathVariable String orderId,
                                    @RequestParam(required = false, defaultValue = "") String batchId,
                                    @RequestBody CalculateRequest request) {
        try {
            return ApiResponse.ok(palletPlanService.calculate(orderId, batchId, request));
        } catch (PackingException | IllegalStateException e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @PutMapping("/{orderId}/draft")
    public ApiResponse<?> draft(@PathVariable String orderId,
                                @RequestParam(required = false, defaultValue = "") String batchId,
                                @RequestBody CalculateRequest request) {
        try {
            return ApiResponse.ok(palletPlanService.saveDraft(orderId, batchId, request));
        } catch (PackingException | IllegalStateException e) {
            return ApiResponse.fail(e.getMessage());
        }
    }
}
