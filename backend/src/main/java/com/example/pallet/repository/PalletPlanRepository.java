package com.example.pallet.repository;

import com.example.pallet.entity.PalletPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PalletPlanRepository extends JpaRepository<PalletPlan, String> {
    Optional<PalletPlan> findByOrderIdAndBatchId(String orderId, String batchId);
    void deleteByOrderIdAndBatchId(String orderId, String batchId);
}
