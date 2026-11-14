package com.nexora.os.auth;

import static org.junit.jupiter.api.Assertions.*;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

class PasswordChangeValidationTest {
    @Test
    void requiresLongNewPasswordAndCurrentPassword() {
        var validator = Validation.buildDefaultValidatorFactory().getValidator();
        assertFalse(validator.validate(new AccountController.ChangePassword("", "sufficiently-long-pass")).isEmpty());
        assertFalse(validator.validate(new AccountController.ChangePassword("existing-passphrase", "short")).isEmpty());
        assertTrue(validator.validate(new AccountController.ChangePassword("existing-passphrase", "new-long-passphrase")).isEmpty());
    }
}
