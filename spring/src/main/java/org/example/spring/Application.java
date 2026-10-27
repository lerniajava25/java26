package org.example.spring;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }


    @Bean
    public CommandLineRunner commandLineRunner(CourseRepository courseRepository) {
        return args -> {
            if (courseRepository.findAll().isEmpty()) {
                Course course = new Course();
                course.setTitle("Java programming");
                course.setCredits(50);

                Student student = new Student();
                student.setName("John");
                course.addStudent(student);

                var savedCourse = courseRepository.save(course);
                Student student2 = new Student();
                student2.setName("Kalle");
                savedCourse.addStudent(student2);
                courseRepository.save(savedCourse);
            }
        };
    }
}
