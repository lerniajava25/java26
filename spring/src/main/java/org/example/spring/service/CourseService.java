package org.example.spring.service;

import org.example.spring.entity.Course;
import org.example.spring.events.CourseCreatedEvent;
import org.example.spring.repository.CourseRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class CourseService {

    private final CourseRepository courseRepository;
    private final ApplicationEventPublisher eventPublisher;

    CourseService(CourseRepository courseRepository, ApplicationEventPublisher eventPublisher) {
        this.courseRepository = courseRepository;
        this.eventPublisher = eventPublisher;
    }

    public List<Course> findAll() {
        return courseRepository.findAllBy();
    }

    public Optional<Course> findById(Long id) {
        return courseRepository.findById(id);
    }

    public Course save(Course course) {
        var savedCourse = courseRepository.save(course);
        eventPublisher.publishEvent(new CourseCreatedEvent(savedCourse.getTitle()));
        return savedCourse;
    }

    public void delete(Long id) {
        courseRepository.deleteById(id);
    }
}
