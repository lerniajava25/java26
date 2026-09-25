package org.example.jakartaee.validate;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class FirstLetterUppercaseValidator implements ConstraintValidator<FirstLetterUppercase, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isEmpty())
            return false;
        return Character.isUpperCase(value.charAt(0));
    }
}
