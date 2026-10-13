package com.example.orderservice.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Custom business constraint — goes beyond @Positive/@Min.
// Validates that an order amount is within an acceptable business range.
//
// Usage on a DTO field:
//   @ValidOrderAmount
//   private BigDecimal amount;
//
// Usage with custom limits:
//   @ValidOrderAmount(min = "10.00", max = "50000.00")
//   private BigDecimal amount;
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = OrderAmountValidator.class)
public @interface ValidOrderAmount {

    String message() default "Order amount must be between {min} and {max}";

    String min() default "0.01";
    String max() default "100000.00";

    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
