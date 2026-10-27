package org.example.spring.service;

import org.example.spring.entity.Course;
import org.example.spring.entity.Student;
import org.example.spring.repository.CourseRepository;
import org.example.spring.repository.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class InitialDataService {

    private final CourseRepository courseRepository;
    private final StudentRepository studentRepository;

    InitialDataService(CourseRepository courseRepository, StudentRepository studentRepository) {
        this.courseRepository = courseRepository;
        this.studentRepository = studentRepository;
    }

    public void seedData() {
        Student student1 = studentRepository.save(new Student("Anna"));
        Student student2 = studentRepository.save(new Student("Erik"));
        Student student3 = studentRepository.save(new Student("Sara"));
        Student student4 = studentRepository.save(new Student("Oscar"));

        Course course1 = new Course("Java programming", 50);
        course1.addStudent(student1);
        course1.addStudent(student2);

        Course course2 = new Course("Database systems", 30);
        course2.addStudent(student2);
        course2.addStudent(student3);

        Course course3 = new Course("Web development", 40);
        course3.addStudent(student3);
        course3.addStudent(student4);

        courseRepository.save(course1);
        courseRepository.save(course2);
        courseRepository.save(course3);
    }
}
