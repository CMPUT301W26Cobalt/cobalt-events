package com.example.cobaltevents.ui;

import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.cobaltevents.R;
import com.example.cobaltevents.controller.EventController;
import com.example.cobaltevents.controller.QRCodeController;
import com.example.cobaltevents.model.Event;
import com.google.firebase.Timestamp;

/**
 * Activity for creating new events.
 * US 2.01.01: Create event and generate unique promotional QR code
 */
public class EventCreateActivity extends AppCompatActivity {

    private EventController eventController;
    private QRCodeController qrCodeController;
    private String deviceId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_create);
        
        eventController = new EventController();
        qrCodeController = new QRCodeController();
        deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        
        // TODO: Initialize UI components when layout is ready
    }

    /**
     * Creates a new event with QR code generation.
     * US 2.01.01: Generate unique promotional QR code that links to event
     */
    private void createEvent(String name, String description, String location,
                            Timestamp eventDate, Timestamp registrationOpen, 
                            Timestamp registrationClose) {
        
        Event event = new Event(name, description, location, eventDate, 
                               registrationOpen, registrationClose, deviceId);
        
        eventController.createEvent(event,
            eventId -> {
                // Generate QR code data for the event
                String qrCodeData = qrCodeController.generateQRCodeData(eventId);
                event.setQrCodeData(qrCodeData);
                
                // Update event with QR code data
                eventController.updateEvent(event,
                    unused -> {
                        Toast.makeText(this, "Event created with QR code!", Toast.LENGTH_SHORT).show();
                        finish();
                    },
                    e -> Toast.makeText(this, "Failed to update QR code: " + e.getMessage(), 
                                      Toast.LENGTH_SHORT).show()
                );
            },
            e -> Toast.makeText(this, "Failed to create event: " + e.getMessage(), 
                              Toast.LENGTH_SHORT).show()
        );
    }
}
