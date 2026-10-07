package interview.java.coding.fluentvalidator;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

class FluentValidator<T> {

    private final T target;
    private final List<ValidationError> errors = new ArrayList<>();

    private FluentValidator(T target) {
        this.target = target;
    }

    public static <T> FluentValidator<T> validate(T target) {
        return new FluentValidator<>(target);
    }

    public <R> FluentValidator<T> notNull(Function<T,R> f, String code, String msg) {
        if (f.apply(target) == null)
            errors.add(new ValidationError(null, code, msg));
        return this;
    }

    public FluentValidator<T> min(Function<T,Integer> f, int val, String code, String msg) {
        if (f.apply(target) < val)
            errors.add(new ValidationError(null, code, msg));
        return this;
    }

    public FluentValidator<T> positive(Function<T,Double> f, String code, String msg) {
        if (f.apply(target) <= 0)
            errors.add(new ValidationError(null, code, msg));
        return this;
    }

    public void throwIfInvalid() {
        if (!errors.isEmpty())
            throw new ValidationException(errors);
    }
}
