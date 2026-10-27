package org.example.spring.dto;

import jakarta.validation.constraints.NotBlank;

public record CourseRequest(@NotBlank String title, int credits) {
}
