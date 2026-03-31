package com.example.cobaltevents.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link QRCodeController}.
 *
 * {@code generateQRCode()} uses {@code android.graphics.Bitmap} (Android-only) and is
 * not covered here. {@code generateQRCodeData()} is a pure-Java pass-through and
 * is fully testable in the JVM runner.
 */
public class QRCodeControllerTest {

    private QRCodeController controller;

    @Before
    public void setUp() {
        controller = new QRCodeController();
    }

    // ── generateQRCodeData ────────────────────────────────────────────────────

    @Test
    public void generateQRCodeData_validId_returnsSameId() {
        assertEquals("event123", controller.generateQRCodeData("event123"));
    }

    @Test
    public void generateQRCodeData_emptyString_returnsEmpty() {
        assertEquals("", controller.generateQRCodeData(""));
    }

    @Test
    public void generateQRCodeData_null_returnsNull() {
        assertNull(controller.generateQRCodeData(null));
    }

    @Test
    public void generateQRCodeData_longId_returnsUnchanged() {
        String longId = "abcdefghijklmnopqrstuvwxyz0123456789";
        assertEquals(longId, controller.generateQRCodeData(longId));
    }

    @Test
    public void generateQRCodeData_specialChars_returnsUnchanged() {
        String id = "event-2025/03#001";
        assertEquals(id, controller.generateQRCodeData(id));
    }

    // ── generateQRCode — null/empty guard (no Bitmap dependency) ─────────────

    @Test
    public void generateQRCode_nullId_returnsNull() {
        // Guard branch: returns null before attempting Bitmap creation
        assertNull(controller.generateQRCode(null));
    }

    @Test
    public void generateQRCode_emptyId_returnsNull() {
        // Guard branch: returns null before attempting Bitmap creation
        assertNull(controller.generateQRCode(""));
    }
}
