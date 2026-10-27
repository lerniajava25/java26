package org.example.spring.controller;

import org.example.spring.dto.CourseDto;
import org.example.spring.dto.CourseRequest;
import org.example.spring.entity.Course;
import org.example.spring.service.CourseService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseService courseService;

    CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @GetMapping
    public List<CourseDto> findAll() {
        return courseService.findAll().stream()
                .map(c -> new CourseDto(c.getId(), c.getTitle(), c.getCredits()))
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<CourseDto> findById(@PathVariable Long id) {
        return courseService.findById(id)
                .map(c -> ResponseEntity.ok(new CourseDto(c.getId(), c.getTitle(), c.getCredits())))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public CourseDto save(@RequestBody CourseRequest request) {
        Course course = new Course();
        course.setTitle(request.title());
        course.setCredits(request.credits());
        Course saved = courseService.save(course);
        return new CourseDto(saved.getId(), saved.getTitle(), saved.getCredits());
    }

    @PutMapping("/{id}")
    public ResponseEntity<CourseDto> update(@PathVariable Long id, @RequestBody CourseRequest request) {
        Course course = courseService.findById(id).orElse(null);
        if (course == null) return ResponseEntity.notFound().build();
        course.setTitle(request.title());
        course.setCredits(request.credits());
        Course updated = courseService.save(course);
        return ResponseEntity.ok(new CourseDto(updated.getId(), updated.getTitle(), updated.getCredits()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        courseService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
