package com.example.cobaltevents.ui;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.cobaltevents.R;
import com.example.cobaltevents.controller.EventController;
import com.example.cobaltevents.controller.ImageController;
import com.example.cobaltevents.controller.QRCodeController;
import com.example.cobaltevents.model.Event;
import com.google.firebase.Timestamp;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Activity for creating new events.
 * US 2.01.01: Create event and generate unique promotional QR code
 */
public class EventCreateActivity extends AppCompatActivity {

    private static final String[] CATEGORIES = {
            "None", "Sports", "Music", "Arts", "Food", "Technology", "Community"
    };

    private EditText etTitle, etDescription, etLocation, etCapacity, etPrice, etSelectionCriteria;
    private Spinner spinnerCategory;
    private TextView btnPickDate, btnPickTime, btnPickRegOpen, btnPickDeadline;
    private ImageView ivEventImage, ivQrCode;
    private LinearLayout qrPlaceholder;
    private View uploadPlaceholder;

    private EventController eventController;
    private QRCodeController qrCodeController;
    private ImageController imageController;
    private String deviceId;

    private Uri selectedImageUri = null;
    private final Calendar eventDateTime = Calendar.getInstance();
    private final Calendar registrationOpen = Calendar.getInstance();
    private final Calendar registrationDeadline = Calendar.getInstance();
    private boolean dateSet = false;
    private boolean timeSet = false;
    private boolean regOpenSet = false;
    private boolean deadlineSet = false;

    private static final SimpleDateFormat DATE_FMT =
            new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
    private static final SimpleDateFormat TIME_FMT =
            new SimpleDateFormat("h:mm a", Locale.getDefault());

    private final ActivityResultLauncher<String> getImage = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    selectedImageUri = uri;
                    ivEventImage.setImageURI(uri);
                    ivEventImage.setVisibility(View.VISIBLE);
                    uploadPlaceholder.setVisibility(View.GONE);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_create);

        eventController = new EventController();
        qrCodeController = new QRCodeController();
        imageController = new ImageController();
        deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

        etTitle = findViewById(R.id.et_title);
        etDescription = findViewById(R.id.et_description);
        etLocation = findViewById(R.id.et_location);
        etCapacity = findViewById(R.id.et_capacity);
        etPrice = findViewById(R.id.et_price);
        etSelectionCriteria = findViewById(R.id.et_selection_criteria);
        spinnerCategory = findViewById(R.id.spinner_category);
        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, CATEGORIES);
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategory.setAdapter(categoryAdapter);
        btnPickDate = findViewById(R.id.btn_pick_date);
        btnPickTime = findViewById(R.id.btn_pick_time);
        btnPickRegOpen = findViewById(R.id.btn_pick_reg_open);
        btnPickDeadline = findViewById(R.id.btn_pick_deadline);
        ivEventImage = findViewById(R.id.iv_event_image);
        ivQrCode = findViewById(R.id.iv_qr_code);
        qrPlaceholder = findViewById(R.id.qr_placeholder);
        uploadPlaceholder = findViewById(R.id.upload_placeholder);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.image_upload_container).setOnClickListener(v -> getImage.launch("image/*"));

        btnPickDate.setOnClickListener(v -> showDatePicker(eventDateTime, date -> {
            dateSet = true;
            btnPickDate.setText(DATE_FMT.format(date));
        }));

        btnPickTime.setOnClickListener(v -> showTimePicker(eventDateTime, time -> {
            timeSet = true;
            btnPickTime.setText(TIME_FMT.format(time));
        }));

        btnPickRegOpen.setOnClickListener(v -> showDatePicker(registrationOpen, date -> {
            regOpenSet = true;
            btnPickRegOpen.setText(DATE_FMT.format(date));
        }));

        btnPickDeadline.setOnClickListener(v -> showDatePicker(registrationDeadline, date -> {
            deadlineSet = true;
            btnPickDeadline.setText(DATE_FMT.format(date));
        }));

        findViewById(R.id.btn_create_event).setOnClickListener(v -> attemptCreateEvent());
    }

    private void showDatePicker(Calendar target, OnDateSelectedListener listener) {
        new DatePickerDialog(this, (view, year, month, day) -> {
            target.set(Calendar.YEAR, year);
            target.set(Calendar.MONTH, month);
            target.set(Calendar.DAY_OF_MONTH, day);
            listener.onSelected(target.getTime());
        },
                target.get(Calendar.YEAR),
                target.get(Calendar.MONTH),
                target.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void showTimePicker(Calendar target, OnDateSelectedListener listener) {
        new TimePickerDialog(this, (view, hour, minute) -> {
            target.set(Calendar.HOUR_OF_DAY, hour);
            target.set(Calendar.MINUTE, minute);
            listener.onSelected(target.getTime());
        },
                target.get(Calendar.HOUR_OF_DAY),
                target.get(Calendar.MINUTE),
                false).show();
    }

    private interface OnDateSelectedListener {
        void onSelected(Date date);
    }

    private void attemptCreateEvent() {
        String title = etTitle.getText().toString().trim();
        String description = etDescription.getText().toString().trim();
        String location = etLocation.getText().toString().trim();
        String capacityStr = etCapacity.getText().toString().trim();
        String price = etPrice.getText().toString().trim();
        String selectionCriteria = etSelectionCriteria.getText().toString().trim();

        if (title.isEmpty()) {
            etTitle.setError("Event title is required");
            etTitle.requestFocus();
            return;
        }
        if (location.isEmpty()) {
            etLocation.setError("Location is required");
            etLocation.requestFocus();
            return;
        }
        if (!dateSet) {
            Toast.makeText(this, "Please select an event date", Toast.LENGTH_SHORT).show();
            return;
        }

        Timestamp eventDate = new Timestamp(eventDateTime.getTime());
        Timestamp regOpen = regOpenSet ? new Timestamp(registrationOpen.getTime()) : null;
        Timestamp regDeadline = deadlineSet ? new Timestamp(registrationDeadline.getTime()) : null;

        int capacity = 0;
        if (!capacityStr.isEmpty()) {
            try {
                capacity = Integer.parseInt(capacityStr);
            } catch (NumberFormatException e) {
                etCapacity.setError("Enter a valid number");
                etCapacity.requestFocus();
                return;
            }
        }

        String selectedCategory = spinnerCategory.getSelectedItemPosition() > 0
                ? CATEGORIES[spinnerCategory.getSelectedItemPosition()] : null;

        Event event = new Event(title, description, location, eventDate, regOpen, regDeadline, deviceId);
        event.setPrice(price.isEmpty() ? null : price);
        event.setWaitingListCapacity(capacity);
        event.setCategory(selectedCategory);
        if (!selectionCriteria.isEmpty()) {
            event.setSelectionCriteria(selectionCriteria);
        }

        final Uri imageUri = selectedImageUri;

        eventController.createEvent(event,
                eventId -> {
                    String qrData = qrCodeController.generateQRCodeData(eventId);
                    event.setQrCodeData(qrData);

                    eventController.updateEvent(event,
                            unused -> {
                                if (imageUri != null) {
                                    imageController.uploadPoster(imageUri, event,
                                            v2 -> showQrCodeAndFinish(eventId),
                                            e -> runOnUiThread(() -> Toast.makeText(this,
                                                    "Event created but image upload failed: " + e.getMessage(),
                                                    Toast.LENGTH_LONG).show()));
                                } else {
                                    showQrCodeAndFinish(eventId);
                                }
                            },
                            e -> Toast.makeText(this, "Failed to save QR code: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show());
                },
                e -> Toast.makeText(this, "Failed to create event: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show());
    }

    private void showQrCodeAndFinish(String eventId) {
        Bitmap qrBitmap = qrCodeController.generateQRCode(eventId);
        runOnUiThread(() -> {
            if (qrBitmap != null) {
                qrPlaceholder.setVisibility(View.GONE);
                ivQrCode.setImageBitmap(qrBitmap);
                ivQrCode.setVisibility(View.VISIBLE);
                Toast.makeText(this, "Event created! QR code generated.", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Event created!", Toast.LENGTH_SHORT).show();
            }
            ivQrCode.postDelayed(this::finish, 2000);
        });
    }
}
