package org.example.jakartaee.validate;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Min(value = 18, message = "Age should not be less than 18")
@Max(value = 150, message = "We are not working with turtles")
@Constraint(validatedBy = {})
@Retention(RetentionPolicy.RUNTIME)
public @interface Age {
    String message() default "Age should be between 18 and 150";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
