package org.example.spring;

import org.example.spring.repository.CourseRepository;
import org.example.spring.service.InitialDataService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    public CommandLineRunner commandLineRunner(CourseRepository courseRepository,
                                               InitialDataService initialDataService) {
        return args -> {
            if (courseRepository.count() == 0) {
                initialDataService.seedData();
            }
        };
    }
}
