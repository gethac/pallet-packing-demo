package com.example.pallet.repository;

import com.example.pallet.entity.PalletStandard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PalletStandardRepository extends JpaRepository<PalletStandard, String> {
    List<PalletStandard> findByEnabledOrderByLengthAscWidthAsc(String enabled);
}
