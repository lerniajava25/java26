package org.example.jakartaee.validate;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Constraint(validatedBy = FirstLetterUppercaseValidator.class)
@Retention(RetentionPolicy.RUNTIME)
public @interface FirstLetterUppercase {
    String message() default "First letter must be uppercase";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
