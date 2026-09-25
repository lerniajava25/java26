package org.example.jakartaee.hello;

import jakarta.validation.constraints.NotBlank;
import org.example.jakartaee.validate.Age;
import org.example.jakartaee.validate.FirstLetterUppercase;

public record HelloRequestDTO(@NotBlank @FirstLetterUppercase String name,
                              @Age int age) {
}
