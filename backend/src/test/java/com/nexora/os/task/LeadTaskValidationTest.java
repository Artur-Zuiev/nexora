package com.nexora.os.task;

import static org.junit.jupiter.api.Assertions.*;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class LeadTaskValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void validTaskAccepted() {
        assertTrue(validator.validate(new LeadTaskController.CreateTask(
            1L, "Call customer", LocalDate.of(2027, 1, 15))).isEmpty());
    }

    @Test
    void missingLeadTitleAndDateRejected() {
        assertFalse(validator.validate(new LeadTaskController.CreateTask(null, "", null)).isEmpty());
    }

    @Test
    void oversizedTitleRejected() {
        assertFalse(validator.validate(new LeadTaskController.CreateTask(
            1L, "A".repeat(161), LocalDate.of(2027, 1, 15))).isEmpty());
    }

    @Test
    void statusRequired() {
        assertFalse(validator.validate(new LeadTaskController.UpdateStatus(null)).isEmpty());
    }
}
