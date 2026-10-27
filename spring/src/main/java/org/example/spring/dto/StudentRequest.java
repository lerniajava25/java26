package org.example.spring.dto;

import jakarta.validation.constraints.NotBlank;

public record StudentRequest(@NotBlank String name) {
}
