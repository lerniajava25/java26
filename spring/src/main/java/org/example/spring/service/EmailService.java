package org.example.spring.service;

import org.example.spring.events.CourseCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
    Logger logger = LoggerFactory.getLogger(EmailService.class);

    public void sendMail(String title) {
        logger.info("Sending mail about a new course: {}", title);
    }

    @ApplicationModuleListener
    //@Async
    //@TransactionalEventListener
    public void courseCreated(CourseCreatedEvent event) {
        // throw new RuntimeException("Failed to process event");
        sendMail(event.courseName());
    }
}
