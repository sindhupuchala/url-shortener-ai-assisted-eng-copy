package com.urlshortener.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Validates that a field is a well-formed absolute http(s) URL. Scheme allow-listing was added
 * as part of the Scenario 3 abuse-hardening pass - see docs/SCENARIOS.md.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidUrlValidator.class)
@Documented
public @interface ValidUrl {
    String message() default "must be a well-formed http(s) URL";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
