package org.example.spring.entity;

import jakarta.persistence.PostPersist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CourseEntityListener {

    //@PrePersist
    //@PreUpdate @PostUpdate
    //@PreRemove @PostRemove
    //@PostLoad
    Logger logger = LoggerFactory.getLogger(CourseEntityListener.class);

    @PostPersist
    public void afterCreate(Course course) {
        logger.info("New course saved to database {}", course);
    }
}
