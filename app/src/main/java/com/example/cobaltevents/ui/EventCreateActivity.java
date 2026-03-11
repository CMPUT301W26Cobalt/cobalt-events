
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
    }

    private void createEvent(String name, String description, String location,
                            Timestamp eventDate, Timestamp registrationOpen, 
                            Timestamp registrationClose) {
        
        Event event = new Event(name, description, location, eventDate, 
                               registrationOpen, registrationClose, deviceId);
        
        eventController.createEvent(event,
            eventId -> {
                String qrCodeData = qrCodeController.generateQRCodeData(eventId);
                event.setQrCodeData(qrCodeData);
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

