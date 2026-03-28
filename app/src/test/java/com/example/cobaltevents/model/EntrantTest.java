package com.example.cobaltevents.model;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for the Entrant model class.
 * Tests that do NOT call isValidEmail() / isValid() because those rely on
 * android.util.Patterns, which is unavailable in the JVM unit-test runner.
 */
public class EntrantTest {

    private Entrant entrant;

    @Before
    public void setUp() {
        entrant = new Entrant("device-001", "Alice Smith", "alice@example.com", "7801234567", null);
    }

    // ── Constructors ─────────────────────────────────────────────────────────

    @Test
    public void noArgConstructor_createsInstance() {
        Entrant e = new Entrant();
        assertNotNull(e);
    }

    @Test
    public void threeArgConstructor_setsNameEmailPhone() {
        Entrant e = new Entrant("Bob", "bob@example.com", "555-1234");
        assertEquals("Bob", e.getName());
        assertEquals("bob@example.com", e.getEmail());
        assertEquals("555-1234", e.getPhone());
        assertNull(e.getDeviceId());
        assertNull(e.getProfilePictureUrl());
    }

    @Test
    public void fourArgConstructor_setsProfilePicture() {
        Entrant e = new Entrant("Carol", "carol@x.com", "555-9999", "https://img.example.com/carol.jpg");
        assertEquals("https://img.example.com/carol.jpg", e.getProfilePictureUrl());
    }

    @Test
    public void fiveArgConstructor_setsAllFields() {
        assertEquals("device-001", entrant.getDeviceId());
        assertEquals("Alice Smith", entrant.getName());
        assertEquals("alice@example.com", entrant.getEmail());
        assertEquals("7801234567", entrant.getPhone());
        assertNull(entrant.getProfilePictureUrl());
    }

    // ── Setters ──────────────────────────────────────────────────────────────

    @Test
    public void setDeviceId_updatesValue() {
        entrant.setDeviceId("device-999");
        assertEquals("device-999", entrant.getDeviceId());
    }

    @Test
    public void setName_updatesValue() {
        entrant.setName("Bob Jones");
        assertEquals("Bob Jones", entrant.getName());
    }

    @Test
    public void setEmail_updatesValue() {
        entrant.setEmail("new@email.com");
        assertEquals("new@email.com", entrant.getEmail());
    }

    @Test
    public void setPhone_updatesValue() {
        entrant.setPhone("4031112222");
        assertEquals("4031112222", entrant.getPhone());
    }

    @Test
    public void setProfilePictureUrl_updatesValue() {
        entrant.setProfilePictureUrl("https://cdn.example.com/pic.png");
        assertEquals("https://cdn.example.com/pic.png", entrant.getProfilePictureUrl());
    }

    @Test
    public void setOrganizerEnabled_trueAndFalse() {
        entrant.setOrganizerEnabled(true);
        assertTrue(entrant.getOrganizerEnabled());
        entrant.setOrganizerEnabled(false);
        assertFalse(entrant.getOrganizerEnabled());
    }

    // ── isValidName ──────────────────────────────────────────────────────────

    @Test
    public void isValidName_nonEmptyName_returnsTrue() {
        assertTrue(entrant.isValidName());
    }

    @Test
    public void isValidName_nullName_returnsFalse() {
        entrant.setName(null);
        assertFalse(entrant.isValidName());
    }

    @Test
    public void isValidName_emptyName_returnsFalse() {
        entrant.setName("");
        assertFalse(entrant.isValidName());
    }

    @Test
    public void isValidName_blankSpacesOnly_returnsFalse() {
        entrant.setName("   ");
        assertFalse(entrant.isValidName());
    }

    // ── isValidPhone ─────────────────────────────────────────────────────────

    @Test
    public void isValidPhone_nullPhone_returnsTrue() {
        entrant.setPhone(null);
        assertTrue(entrant.isValidPhone());
    }

    @Test
    public void isValidPhone_emptyPhone_returnsTrue() {
        entrant.setPhone("");
        assertTrue(entrant.isValidPhone());
    }

    @Test
    public void isValidPhone_validDigitPhone_returnsTrue() {
        entrant.setPhone("7801234567");
        assertTrue(entrant.isValidPhone());
    }

    @Test
    public void isValidPhone_validFormattedPhone_returnsTrue() {
        entrant.setPhone("(780) 123-4567");
        assertTrue(entrant.isValidPhone());
    }

    @Test
    public void isValidPhone_plusCountryCode_returnsTrue() {
        entrant.setPhone("+1 780 123 4567");
        assertTrue(entrant.isValidPhone());
    }

    @Test
    public void isValidPhone_tooShort_returnsFalse() {
        entrant.setPhone("12345");
        assertFalse(entrant.isValidPhone());
    }

    @Test
    public void isValidPhone_lettersInPhone_returnsFalse() {
        entrant.setPhone("abc-defgh");
        assertFalse(entrant.isValidPhone());
    }

    // ── getInitials ──────────────────────────────────────────────────────────

    @Test
    public void getInitials_firstAndLastName_returnsTwoLetters() {
        assertEquals("AS", entrant.getInitials());
    }

    @Test
    public void getInitials_singleName_returnsOneLetterUppercase() {
        entrant.setName("alice");
        assertEquals("A", entrant.getInitials());
    }

    @Test
    public void getInitials_nullName_returnsQuestionMark() {
        entrant.setName(null);
        assertEquals("?", entrant.getInitials());
    }

    @Test
    public void getInitials_emptyName_returnsQuestionMark() {
        entrant.setName("");
        assertEquals("?", entrant.getInitials());
    }

    @Test
    public void getInitials_multipleSpaces_usesFirstAndLastWord() {
        entrant.setName("  John   Michael   Doe  ");
        assertEquals("JD", entrant.getInitials());
    }

    @Test
    public void getInitials_lowercaseName_returnsUppercaseInitials() {
        entrant.setName("john doe");
        assertEquals("JD", entrant.getInitials());
    }
}
