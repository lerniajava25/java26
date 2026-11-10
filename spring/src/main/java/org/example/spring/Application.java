package org.example.spring;

import org.example.spring.repository.CourseRepository;
import org.example.spring.service.InitialDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;

@SpringBootApplication
@EnableAsync
public class Application {

    Logger logger = LoggerFactory.getLogger(Application.class);

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

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        String username = event.getAuthentication().getName();
        logger.info("Successful login for {}", username);
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        String username = event.getAuthentication().getName();
        String errorMessage = event.getException().getMessage();
        logger.warn("Failed authentication event for '" + username + "'. Reason: " + errorMessage);
    }
}
