package pl.bienkowski.empik.core.validation;

public interface Validator<T> {
    void validate(T t);
}
