package org.example.spring.service;

import org.example.spring.entity.Course;
import org.example.spring.repository.CourseRepository;
import org.springframework.amqp.rabbit.core.RabbitMessagingTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class CourseService {

    boolean simulatedError = true;

    private final CourseRepository courseRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final RabbitMessagingTemplate messagingTemplate;

    CourseService(CourseRepository courseRepository, ApplicationEventPublisher eventPublisher, RabbitMessagingTemplate messagingTemplate) {
        this.courseRepository = courseRepository;
        this.eventPublisher = eventPublisher;
        this.messagingTemplate = messagingTemplate;
    }

    public List<Course> findAll() {
        return courseRepository.findAllBy();
    }

    public Optional<Course> findById(Long id) {
        return courseRepository.findById(id);
    }

    @Transactional
    public Course save(Course course) {
        var savedCourse = courseRepository.save(course);
        //eventPublisher.publishEvent(new CourseCreatedEvent(savedCourse.getTitle()));
        var message = MessageBuilder.withPayload(course.toString())
                .build();

        messagingTemplate.send("course-exchange", "course.created", message);
        return savedCourse;
    }

    public void delete(Long id) {
        courseRepository.deleteById(id);
    }
}
