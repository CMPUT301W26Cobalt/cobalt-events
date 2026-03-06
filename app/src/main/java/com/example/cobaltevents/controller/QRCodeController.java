package com.example.cobaltevents.controller;

import android.graphics.Bitmap;
import android.graphics.Color;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

/**
 * Controller for generating QR codes for events.
 * US 2.01.01: Generate unique promotional QR code that links to event
 */
public class QRCodeController {

    private static final int QR_CODE_SIZE = 512;

    public QRCodeController() {}

    /**
     * Generates a QR code bitmap from event data.
     * @param eventId The event ID to encode
     * @return Bitmap of the QR code, or null if generation fails
     */
    public Bitmap generateQRCode(String eventId) {
        if (eventId == null || eventId.isEmpty()) {
            return null;
        }

        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix bitMatrix = writer.encode(eventId, BarcodeFormat.QR_CODE, QR_CODE_SIZE, QR_CODE_SIZE);
            
            Bitmap bitmap = Bitmap.createBitmap(QR_CODE_SIZE, QR_CODE_SIZE, Bitmap.Config.RGB_565);
            for (int x = 0; x < QR_CODE_SIZE; x++) {
                for (int y = 0; y < QR_CODE_SIZE; y++) {
                    bitmap.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            return bitmap;
        } catch (WriterException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Generates QR code data string for an event.
     * @param eventId The event ID
     * @return The QR code data string
     */
    public String generateQRCodeData(String eventId) {
        return eventId;
    }
}
