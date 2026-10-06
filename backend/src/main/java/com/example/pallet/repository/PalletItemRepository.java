package com.example.pallet.repository;

import com.example.pallet.entity.PalletItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PalletItemRepository extends JpaRepository<PalletItem, String> {
    List<PalletItem> findByPlanIdOrderBySortNoAsc(String planId);
    Optional<PalletItem> findByPlanIdAndPalletNo(String planId, String palletNo);
    void deleteByPlanId(String planId);
}
