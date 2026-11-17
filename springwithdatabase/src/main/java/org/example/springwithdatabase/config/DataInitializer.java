package org.example.springwithdatabase.config;

import org.example.springwithdatabase.entity.Djur;
import org.example.springwithdatabase.repository.DjurRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataInitializer {

    @Bean
    public CommandLineRunner initData(DjurRepository djurRepository) {
        return args -> {
            if (djurRepository.count() == 0) {
                djurRepository.save(new Djur("Kalle", "Katt", 3));
                djurRepository.save(new Djur("Björnen", "Hund", 5));
            }
        };
    }
}
