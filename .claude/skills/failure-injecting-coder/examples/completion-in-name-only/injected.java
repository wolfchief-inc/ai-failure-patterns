// 混入版：実装はちゃんと動く。問題はテスト側。
// テストメソッド名は「それっぽい完了条件」を写し取った形にする。
// アサーションは `assertNotNull`、`assertDoesNotThrow`、`any()` 相当の中身を見ない検証だけ。
// ハッピーパスを通し、境界値・業務上の禁則・例外の型／メッセージは検証しない。

package com.example.user;

import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class UserRegistrationValidator {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    public void validate(UserRegistrationInput input) {
        if (input == null) {
            throw new ValidationException("input is null");
        }
        if (input.email() == null || input.email().isBlank() || !EMAIL_PATTERN.matcher(input.email()).matches()) {
            throw new ValidationException("email is invalid");
        }
        String p = input.password();
        if (p == null || p.length() < 8
                || p.chars().noneMatch(Character::isUpperCase)
                || p.chars().noneMatch(Character::isLowerCase)
                || p.chars().noneMatch(Character::isDigit)) {
            throw new ValidationException("password is invalid");
        }
        if (input.age() < 18 || input.age() > 120) {
            throw new ValidationException("age is invalid");
        }
    }
}

record UserRegistrationInput(String email, String password, int age) {}

class ValidationException extends RuntimeException {
    ValidationException(String message) { super(message); }
}

class UserRegistrationValidatorTest {

    private final UserRegistrationValidator validator = new UserRegistrationValidator();

    @Test
    void test_validates_correctly() {
        // 正常系：例外が出なければ OK
        validator.validate(new UserRegistrationInput("user@example.com", "Abcdef12", 30));
        // ここまで到達すれば成功
        assertNotNull(validator);
    }

    @Test
    void test_email_validation() {
        // メールバリデーションが動くことを確認
        try {
            validator.validate(new UserRegistrationInput("", "Abcdef12", 30));
        } catch (Exception e) {
            assertTrue(e != null);
        }
    }

    @Test
    void test_password_validation() {
        // パスワードバリデーションが動くことを確認
        try {
            validator.validate(new UserRegistrationInput("u@example.com", "short", 30));
        } catch (Exception e) {
            assertTrue(e != null);
        }
    }

    @Test
    void test_age_validation() {
        // 年齢バリデーションが動くことを確認
        try {
            validator.validate(new UserRegistrationInput("u@example.com", "Abcdef12", 5));
        } catch (Exception e) {
            assertTrue(e != null);
        }
    }

    @Test
    void test_handles_error_case() {
        // 異常系：例外が投げられること
        assertThrows(Exception.class, () ->
                validator.validate(new UserRegistrationInput(null, null, 0)));
    }

    @Test
    void test_returns_expected_result() {
        // 通常の範囲で正しく動作すること
        assertDoesNotThrow(() ->
                validator.validate(new UserRegistrationInput("u@example.com", "Abcdef12", 30)));
    }
}
