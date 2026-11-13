package org.example.spring.service;

import org.example.spring.entity.Course;
import org.example.spring.entity.Outbox;
import org.example.spring.repository.CourseRepository;
import org.example.spring.repository.OutboxRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class CourseService {

    boolean simulatedError = true;

    private final CourseRepository courseRepository;
    private final OutboxRepository outboxRepository;
    private final ApplicationEventPublisher eventPublisher;

    CourseService(CourseRepository courseRepository, OutboxRepository outboxRepository, ApplicationEventPublisher eventPublisher) {
        this.courseRepository = courseRepository;
        this.outboxRepository = outboxRepository;
        this.eventPublisher = eventPublisher;
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

        var outbox = new Outbox(savedCourse.toString(), "course.created");
        outboxRepository.save(outbox);
        
        return savedCourse;
    }

    public void delete(Long id) {
        courseRepository.deleteById(id);
    }
}
