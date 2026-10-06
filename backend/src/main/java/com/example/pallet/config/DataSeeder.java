package com.example.pallet.config;

import com.example.pallet.entity.PalletStandard;
import com.example.pallet.repository.PalletStandardRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataSeeder {
    @Bean
    CommandLineRunner seedStandards(PalletStandardRepository repository) {
        return args -> {
            if (repository.count() > 0) {
                return;
            }
            PalletStandard s1 = new PalletStandard();
            s1.setId("STD-1200x800");
            s1.setCode("1200x800");
            s1.setLength(1200.0);
            s1.setWidth(800.0);
            s1.setEnabled("1");
            repository.save(s1);

            PalletStandard s2 = new PalletStandard();
            s2.setId("STD-1100x1100");
            s2.setCode("1100x1100");
            s2.setLength(1100.0);
            s2.setWidth(1100.0);
            s2.setEnabled("1");
            repository.save(s2);

            PalletStandard disabled = new PalletStandard();
            disabled.setId("STD-DISABLED");
            disabled.setCode("1000x1000");
            disabled.setLength(1000.0);
            disabled.setWidth(1000.0);
            disabled.setEnabled("0");
            repository.save(disabled);
        };
    }
}
