package org.example.spring.service;

import org.example.spring.events.CourseCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
    Logger logger = LoggerFactory.getLogger(EmailService.class);

    public void sendMail(String title) {
        logger.info("Sending mail about a new course: {}", title);
    }

    @EventListener
    @Async
    public void courseCreated(CourseCreatedEvent event) {
        sendMail(event.courseName());
    }
}
