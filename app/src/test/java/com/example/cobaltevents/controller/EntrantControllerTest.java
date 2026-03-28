package com.example.cobaltevents.controller;

import static org.junit.Assert.*;

import com.example.cobaltevents.db.ProfileDB;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

/**
 * Unit tests for {@link EntrantController} validation methods.
 *
 * Note: {@code validateEmail()} is NOT tested here because it calls
 * {@code android.util.Patterns.EMAIL_ADDRESS}, which is unavailable in the
 * JVM unit-test runner. Email validation is covered by the instrumented
 * {@code EntrantActivityTest}.
 */
public class EntrantControllerTest {

    // ProfileDB constructor calls Firebase; intercept it with mockConstruction.
    private MockedConstruction<ProfileDB> mockedProfileDB;
    private EntrantController controller;

    @Before
    public void setUp() {
        mockedProfileDB = Mockito.mockConstruction(ProfileDB.class);
        controller = new EntrantController(null);
    }

    @After
    public void tearDown() {
        mockedProfileDB.close();
    }

    // ── validateName ─────────────────────────────────────────────────────────

    @Test
    public void validateName_nonEmptyName_returnsNull() {
        assertNull(controller.validateName("Alice"));
    }

    @Test
    public void validateName_nameWithSpaces_returnsNull() {
        assertNull(controller.validateName("John Doe"));
    }

    @Test
    public void validateName_nullName_returnsError() {
        assertNotNull(controller.validateName(null));
        assertEquals("Name is required.", controller.validateName(null));
    }

    @Test
    public void validateName_emptyString_returnsError() {
        assertEquals("Name is required.", controller.validateName(""));
    }

    @Test
    public void validateName_whitespaceOnly_returnsError() {
        assertEquals("Name is required.", controller.validateName("   "));
    }

    @Test
    public void validateName_singleCharacter_returnsNull() {
        assertNull(controller.validateName("A"));
    }

    // ── validatePhone ────────────────────────────────────────────────────────

    @Test
    public void validatePhone_nullPhone_returnsNull() {
        assertNull(controller.validatePhone(null));
    }

    @Test
    public void validatePhone_emptyPhone_returnsNull() {
        assertNull(controller.validatePhone(""));
    }

    @Test
    public void validatePhone_blankPhone_returnsNull() {
        assertNull(controller.validatePhone("   "));
    }

    @Test
    public void validatePhone_validTenDigits_returnsNull() {
        assertNull(controller.validatePhone("7801234567"));
    }

    @Test
    public void validatePhone_validFormattedPhone_returnsNull() {
        assertNull(controller.validatePhone("(780) 123-4567"));
    }

    @Test
    public void validatePhone_plusCountryCode_returnsNull() {
        assertNull(controller.validatePhone("+1 780 123 4567"));
    }

    @Test
    public void validatePhone_sevenDigits_returnsNull() {
        // Minimum valid length is 7
        assertNull(controller.validatePhone("1234567"));
    }

    @Test
    public void validatePhone_twentyDigits_returnsNull() {
        // Maximum valid length is 20
        assertNull(controller.validatePhone("12345678901234567890"));
    }

    @Test
    public void validatePhone_tooShortSixDigits_returnsError() {
        assertEquals("Invalid phone number.", controller.validatePhone("123456"));
    }

    @Test
    public void validatePhone_lettersInPhone_returnsError() {
        assertEquals("Invalid phone number.", controller.validatePhone("abcdefgh"));
    }

    @Test
    public void validatePhone_specialCharsNotAllowed_returnsError() {
        assertEquals("Invalid phone number.", controller.validatePhone("780@123#456"));
    }

    @Test
    public void validatePhone_tooLong_returnsError() {
        // 21 characters — over the 20-char limit
        assertEquals("Invalid phone number.", controller.validatePhone("123456789012345678901"));
    }
}
