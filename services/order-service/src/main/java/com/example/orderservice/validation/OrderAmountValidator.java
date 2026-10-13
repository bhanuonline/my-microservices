package com.example.orderservice.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.math.BigDecimal;

// ConstraintValidator<A extends Annotation, T>
//   A = the annotation this validates
//   T = the type of the field/param being validated
public class OrderAmountValidator implements ConstraintValidator<ValidOrderAmount, BigDecimal> {

    private BigDecimal min;
    private BigDecimal max;

    // initialize() is called once when the validator is created — read annotation attributes here
    @Override
    public void initialize(ValidOrderAmount annotation) {
        this.min = new BigDecimal(annotation.min());
        this.max = new BigDecimal(annotation.max());
    }

    // isValid() is called for every validation — return true = valid, false = constraint violation
    @Override
    public boolean isValid(BigDecimal value, ConstraintValidatorContext context) {
        if (value == null) {
            return false;   // null is invalid — pair with @NotNull if you want a separate message
        }
        return value.compareTo(min) >= 0 && value.compareTo(max) <= 0;
    }
}
