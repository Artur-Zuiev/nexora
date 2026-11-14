package com.nexora.os.lead;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LeadValidationTest {
    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void validRequestHasNoViolations() {
        assertTrue(validator.validate(new CreateLeadRequest("Jane", "jane@example.com", "")).isEmpty());
    }

    @Test
    void rejectsBlankAndOversizedName() {
        assertFalse(validator.validate(new CreateLeadRequest(" ", "jane@example.com", "")).isEmpty());
        assertFalse(validator.validate(new CreateLeadRequest("a".repeat(121), "jane@example.com", "")).isEmpty());
    }

    @Test
    void rejectsInvalidEmailAndOversizedNote() {
        assertFalse(validator.validate(new CreateLeadRequest("Jane", "not-an-email", "")).isEmpty());
        assertFalse(validator.validate(new CreateLeadRequest("Jane", "jane@example.com", "x".repeat(2001))).isEmpty());
    }

    @Test
    void rejectsOversizedNoteUpdate() {
        assertFalse(validator.validate(new LeadController.NoteUpdate("x".repeat(2001))).isEmpty());
        assertTrue(validator.validate(new LeadController.NoteUpdate("Follow up next week")).isEmpty());
    }

    @Test
    void rejectsMissingNote() {
        assertFalse(validator.validate(new LeadController.NoteUpdate(null)).isEmpty());
    }

    @Test
    void rejectsMissingStatus() {
        assertFalse(validator.validate(new LeadController.StatusUpdate(null)).isEmpty());
    }
}
