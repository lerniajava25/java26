package org.example.spring.controller;

import org.example.spring.entity.Course;
import org.example.spring.entity.Student;
import org.example.spring.service.CourseService;
import org.example.spring.service.StudentService;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/students")
public class StudentWebController {
    private final StudentService studentService;
    private final CourseService courseService;

    public StudentWebController(StudentService studentService, CourseService courseService) {
        this.studentService = studentService;
        this.courseService = courseService;
    }

    @GetMapping
    public String listStudents(Model model) {
        List<Student> students = studentService.findAll();
        model.addAttribute("students", students);
        model.addAttribute("student", new Student());
        model.addAttribute("courses", courseService.findAll());
        return "students";
    }

    @PostMapping
    public String saveStudent(@ModelAttribute Student student) {
        studentService.save(student);
        return "redirect:/students";
    }

    @PostMapping("/{studentId}/courses/{courseId}")
    @Transactional
    public String assignCourse(@PathVariable Long studentId, @PathVariable Long courseId) {
        var student = studentService.findById(studentId).orElseThrow();
        Course course = courseService.findById(courseId).orElseThrow();
        course.addStudent(student);
        courseService.save(course);
        return "redirect:/students";
    }

    @PostMapping("/{studentId}/remove-course/{courseId}")
    @Transactional
    public String removeCourse(@PathVariable Long studentId, @PathVariable Long courseId) {
        var student = studentService.findById(studentId).orElseThrow();
        Course course = courseService.findById(courseId).orElseThrow();
        course.removeStudent(student);
        courseService.save(course);
        return "redirect:/students";
    }
}
