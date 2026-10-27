package org.example.spring.repository;

import org.example.spring.entity.Course;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;

public interface CourseRepository extends ListCrudRepository<Course, Long> {

    @EntityGraph(value = "Course.detail")
    List<Course> findAllBy();
}
