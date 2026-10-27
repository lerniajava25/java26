package org.example.spring.controller;

import org.example.spring.dto.StudentDto;
import org.example.spring.dto.StudentRequest;
import org.example.spring.entity.Course;
import org.example.spring.entity.Student;
import org.example.spring.service.StudentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/students")
public class StudentController {

    private final StudentService studentService;

    StudentController(StudentService studentService) {
        this.studentService = studentService;
    }

    @GetMapping
    public List<StudentDto> findAll() {
        return studentService.findAll().stream()
                .map(s -> new StudentDto(s.getId(), s.getName()))
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<StudentDto> findById(@PathVariable Long id) {
        return studentService.findById(id)
                .map(s -> ResponseEntity.ok(new StudentDto(s.getId(), s.getName())))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public StudentDto save(@RequestBody StudentRequest request) {
        Student student = new Student();
        student.setName(request.name());
        Student saved = studentService.save(student);
        return new StudentDto(saved.getId(), saved.getName());
    }

    @PutMapping("/{id}")
    public ResponseEntity<StudentDto> update(@PathVariable Long id, @RequestBody StudentRequest request) {
        Student student = studentService.findById(id).orElse(null);
        if (student == null) return ResponseEntity.notFound().build();
        student.setName(request.name());
        Student updated = studentService.save(student);
        return ResponseEntity.ok(new StudentDto(updated.getId(), updated.getName()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        studentService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/courses/{courseId}")
    public ResponseEntity<StudentDto> assignCourse(@PathVariable Long id, @PathVariable Long courseId) {
        Student student = studentService.findById(id).orElse(null);
        if (student == null) return ResponseEntity.notFound().build();
        student.getCourses().add(new Course() {{
            setId(courseId);
        }});
        Student updated = studentService.save(student);
        return ResponseEntity.ok(new StudentDto(updated.getId(), updated.getName()));
    }
}
