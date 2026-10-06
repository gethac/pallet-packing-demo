package com.example.pallet.repository;

import com.example.pallet.entity.PalletGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PalletGroupRepository extends JpaRepository<PalletGroup, String> {
    List<PalletGroup> findByPlanIdOrderBySortNoAsc(String planId);
    void deleteByPlanId(String planId);
}
