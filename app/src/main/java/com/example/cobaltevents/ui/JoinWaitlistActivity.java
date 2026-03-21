package com.example.cobaltevents.ui;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.cobaltevents.R;
import com.example.cobaltevents.db.EntrantDB;
import com.example.cobaltevents.db.EventDB;
import com.example.cobaltevents.db.WaitingListDB;
import com.example.cobaltevents.model.Entrant;
import com.example.cobaltevents.model.WaitingList;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * US 01.01.01 — Join waitlist form.
 * Allows entrant to enter num participants, contact info, notification method,
 * and confirm joining the waiting list for the selected event.
 */
public class JoinWaitlistActivity extends AppCompatActivity {

    private TextInputLayout layoutParticipants, layoutEmail, layoutPhone;
    private TextInputEditText editParticipants, editEmail, editPhone;
    private RadioGroup radioNotification;
    private Button btnConfirmJoin;

    private String eventId;
    private String eventName;
    private String deviceId;
    private EntrantDB entrantDB;
    private WaitingListDB waitingListDB;
    private EventDB eventDB;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_join_waitlist);

        eventId = getIntent().getStringExtra("eventId");
        eventName = getIntent().getStringExtra("eventName");
        deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        entrantDB = new EntrantDB(this);
        waitingListDB = new WaitingListDB();

        TextView tvEventName = findViewById(R.id.tv_event_name);
        tvEventName.setText(eventName != null ? eventName : "Event");

        layoutParticipants = findViewById(R.id.layout_participants);
        layoutEmail = findViewById(R.id.layout_email);
        layoutPhone = findViewById(R.id.layout_phone);
        editParticipants = findViewById(R.id.edit_participants);
        editEmail = findViewById(R.id.edit_email);
        editPhone = findViewById(R.id.edit_phone);
        radioNotification = findViewById(R.id.radio_notification);
        btnConfirmJoin = findViewById(R.id.btn_confirm_join);

        prefillFromProfile();

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        btnConfirmJoin.setOnClickListener(v -> confirmJoin());
    }

    private void prefillFromProfile() {
        Entrant entrant = entrantDB.getEntrant();
        if (entrant != null) {
            if (entrant.getEmail() != null) editEmail.setText(entrant.getEmail());
            if (entrant.getPhone() != null) editPhone.setText(entrant.getPhone());
        }
    }

    private void confirmJoin() {
        clearErrors();

        String participantsStr = safe(editParticipants);
        String email = safe(editEmail);
        String phone = safe(editPhone);

        int selectedId = radioNotification.getCheckedRadioButtonId();
        String notificationMethod = getNotificationMethod(selectedId);

        boolean hasError = false;

        if (email.isEmpty()) {
            layoutEmail.setError(getString(R.string.validation_email_required));
            hasError = true;
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            layoutEmail.setError(getString(R.string.validation_email_invalid));
            hasError = true;
        }

        int numParticipants = 1;
        try {
            numParticipants = participantsStr.isEmpty() ? 1 : Integer.parseInt(participantsStr);
            if (numParticipants < 1) {
                layoutParticipants.setError(getString(R.string.validation_participants));
                hasError = true;
            }
        } catch (NumberFormatException e) {
            layoutParticipants.setError(getString(R.string.validation_participants));
            hasError = true;
        }

        if (notificationMethod == null) {
            Toast.makeText(this, R.string.validation_notification_method, Toast.LENGTH_SHORT).show();
            hasError = true;
        }

        if (phone != null && !phone.isEmpty() && !phone.matches("^[0-9+()\\-\\s]{7,20}$")) {
            layoutPhone.setError("Invalid phone number.");
            hasError = true;
        }

        if (hasError) return;

        if (eventId == null || eventId.isEmpty()) {
            Toast.makeText(this, "Invalid event.", Toast.LENGTH_SHORT).show();
            return;
        }

        Entrant entrant = entrantDB.getEntrant();
        if (entrant == null || !entrant.isValidName()) {
            Toast.makeText(this, "Please set your name in Account settings before joining.", Toast.LENGTH_LONG).show();
            hasError = true;
        }
        if (entrant == null || !entrant.isValidEmail()) {
            layoutEmail.setError(getString(R.string.validation_email_required));
            hasError = true;
        }
        if (hasError) return;

        if (!isNetworkAvailable()) {
            Toast.makeText(this, getString(R.string.waitlist_fail) + " No internet connection.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        WaitingList registration = new WaitingList(
                eventId,
                deviceId,
                numParticipants,
                entrant.getName(),
                email,
                phone.isEmpty() ? null : phone,
                notificationMethod
        );

        eventDB.getEvent(eventId, event -> {
            int capacity = event != null ? event.getWaitingListCapacity() : 0;
            waitingListDB.addRegistrationWithJoinChecks(registration, capacity,
                    event.getRegistrationClose(),
                    id -> {
                        Toast.makeText(this, R.string.waitlist_success, Toast.LENGTH_SHORT).show();
                        setResult(RESULT_OK);
                        finish();
                    },
                    e -> {
                        if (WaitingListDB.REASON_WAITLIST_FULL.equals(e.getMessage())) {
                            Toast.makeText(this, R.string.waitlist_full_capacity, Toast.LENGTH_LONG).show();
                        } else if (WaitingListDB.REASON_REGISTRATION_CLOSED.equals(e.getMessage())) {
                            Toast.makeText(this, R.string.waitlist_registration_closed, Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(this, getString(R.string.waitlist_fail) + " " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
        }, e -> Toast.makeText(this, getString(R.string.waitlist_fail) + " " + e.getMessage(),
                Toast.LENGTH_SHORT).show());
    }

    private void clearErrors() {
        layoutParticipants.setError(null);
        layoutEmail.setError(null);
        layoutPhone.setError(null);
    }

    private String safe(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private String getNotificationMethod(int radioId) {
        if (radioId == R.id.radio_email) return WaitingList.NOTIFY_EMAIL;
        if (radioId == R.id.radio_phone) return WaitingList.NOTIFY_PHONE;
        if (radioId == R.id.radio_push) return WaitingList.NOTIFY_PUSH;
        return null;
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        Network network = cm.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        return caps != null && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
    }
}
