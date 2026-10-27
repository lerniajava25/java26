package org.example.spring.repository;

import org.example.spring.entity.Student;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;

public interface StudentRepository extends ListCrudRepository<Student, Long> {

    @EntityGraph(value = "Student.detail")
    List<Student> findAllBy();
}
