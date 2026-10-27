package org.example.spring.controller;

import org.example.spring.entity.Course;
import org.example.spring.service.CourseService;
import org.example.spring.service.StudentService;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/courses")
public class CourseWebController {
    private final CourseService courseService;
    private final StudentService studentService;

    public CourseWebController(CourseService courseService, StudentService studentService) {
        this.courseService = courseService;
        this.studentService = studentService;
    }

    @GetMapping
    public String listCourses(Model model) {
        List<Course> courses = courseService.findAll();
        model.addAttribute("courses", courses);
        model.addAttribute("course", new Course());
        model.addAttribute("students", studentService.findAll());
        return "courses";
    }

    @PostMapping
    public String saveCourse(@ModelAttribute Course course) {
        courseService.save(course);
        return "redirect:/courses";
    }

    @PostMapping("/{courseId}/students/{studentId}")
    @Transactional
    public String assignStudent(@PathVariable Long courseId, @PathVariable Long studentId) {
        Course course = courseService.findById(courseId).orElseThrow();
        var student = studentService.findById(studentId).orElseThrow();
        course.addStudent(student);
        courseService.save(course);
        return "redirect:/courses";
    }

    @PostMapping("/{courseId}/remove-student/{studentId}")
    @Transactional
    public String removeStudent(@PathVariable Long courseId, @PathVariable Long studentId) {
        Course course = courseService.findById(courseId).orElseThrow();
        var student = studentService.findById(studentId).orElseThrow();
        course.removeStudent(student);
        courseService.save(course);
        return "redirect:/courses";
    }
}
