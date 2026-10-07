package interview.java.coding.fluentvalidator;

import java.util.List;

class ValidationException extends RuntimeException {
    List<ValidationError> errors;
    ValidationException(List<ValidationError> errors) {
        this.errors = errors;
    }
}
