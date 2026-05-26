// 正道：受け入れ基準を具体的な入力・期待結果・例外条件に分解してから書く。
// テストメソッド名は「何を入れて何が起きるか」を表す。
// アサーションは結果の中身（例外の型・フィールド・メッセージキー）を見る。

package com.example.user;

import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class UserRegistrationValidator {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    public void validate(UserRegistrationInput input) {
        if (input == null) {
            throw new ValidationException("input", "must_not_be_null");
        }
        validateEmail(input.email());
        validatePassword(input.password());
        validateAge(input.age());
    }

    private void validateEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new ValidationException("email", "must_not_be_blank");
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new ValidationException("email", "invalid_format");
        }
    }

    private void validatePassword(String password) {
        if (password == null || password.isEmpty()) {
            throw new ValidationException("password", "must_not_be_blank");
        }
        if (password.length() < 8) {
            throw new ValidationException("password", "too_short");
        }
        boolean hasUpper = password.chars().anyMatch(Character::isUpperCase);
        boolean hasLower = password.chars().anyMatch(Character::isLowerCase);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!(hasUpper && hasLower && hasDigit)) {
            throw new ValidationException("password", "missing_character_class");
        }
    }

    private void validateAge(int age) {
        if (age < 18) {
            throw new ValidationException("age", "below_minimum");
        }
        if (age > 120) {
            throw new ValidationException("age", "above_maximum");
        }
    }
}

record UserRegistrationInput(String email, String password, int age) {}

class ValidationException extends RuntimeException {
    private final String field;
    private final String code;

    ValidationException(String field, String code) {
        super(field + ":" + code);
        this.field = field;
        this.code = code;
    }

    public String field() { return field; }
    public String code() { return code; }
}

class UserRegistrationValidatorTest {

    private final UserRegistrationValidator validator = new UserRegistrationValidator();

    @Test
    void valid_input_passes() {
        assertDoesNotThrow(() ->
                validator.validate(new UserRegistrationInput("user@example.com", "Abcdef12", 30)));
    }

    @Test
    void email_blank_rejected_with_field_and_code() {
        ValidationException ex = assertThrows(ValidationException.class, () ->
                validator.validate(new UserRegistrationInput("", "Abcdef12", 30)));
        assertEquals("email", ex.field());
        assertEquals("must_not_be_blank", ex.code());
    }

    @Test
    void email_invalid_format_rejected() {
        ValidationException ex = assertThrows(ValidationException.class, () ->
                validator.validate(new UserRegistrationInput("not-an-email", "Abcdef12", 30)));
        assertEquals("email", ex.field());
        assertEquals("invalid_format", ex.code());
    }

    @Test
    void password_shorter_than_8_rejected() {
        ValidationException ex = assertThrows(ValidationException.class, () ->
                validator.validate(new UserRegistrationInput("u@example.com", "Abc12", 30)));
        assertEquals("password", ex.field());
        assertEquals("too_short", ex.code());
    }

    @Test
    void password_without_uppercase_rejected() {
        ValidationException ex = assertThrows(ValidationException.class, () ->
                validator.validate(new UserRegistrationInput("u@example.com", "abcdef12", 30)));
        assertEquals("password", ex.field());
        assertEquals("missing_character_class", ex.code());
    }

    @Test
    void age_below_18_rejected() {
        ValidationException ex = assertThrows(ValidationException.class, () ->
                validator.validate(new UserRegistrationInput("u@example.com", "Abcdef12", 17)));
        assertEquals("age", ex.field());
        assertEquals("below_minimum", ex.code());
    }

    @Test
    void age_boundary_18_passes() {
        assertDoesNotThrow(() ->
                validator.validate(new UserRegistrationInput("u@example.com", "Abcdef12", 18)));
    }

    @Test
    void age_boundary_120_passes() {
        assertDoesNotThrow(() ->
                validator.validate(new UserRegistrationInput("u@example.com", "Abcdef12", 120)));
    }

    @Test
    void age_121_rejected() {
        ValidationException ex = assertThrows(ValidationException.class, () ->
                validator.validate(new UserRegistrationInput("u@example.com", "Abcdef12", 121)));
        assertEquals("age", ex.field());
        assertEquals("above_maximum", ex.code());
    }
}
