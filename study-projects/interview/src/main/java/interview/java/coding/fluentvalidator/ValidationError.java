package interview.java.coding.fluentvalidator;

class ValidationError {
    String field;
    String code;
    String message;

    public ValidationError(String field, String code, String msg) {
        this.field=field;
    }
}
