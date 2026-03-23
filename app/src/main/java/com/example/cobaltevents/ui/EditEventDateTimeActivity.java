package com.example.cobaltevents.ui;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.cobaltevents.R;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * Full-screen editor for event date/time or registration open/close (date + time pickers).
 */
public class EditEventDateTimeActivity extends AppCompatActivity {

    private static final long REGISTRATION_MIN_GAP_MS = 60 * 60 * 1000L;

    private final DateFormat dateFmt = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
    private final DateFormat timeFmt = new SimpleDateFormat("h:mm a", Locale.getDefault());

    private int mode;
    private Calendar cal;
    private boolean hasTime;

    private long eventDayEndMs = -1L;
    private long regOpenMs = -1L;
    private boolean hasRegOpenTime;

    private TextView btnDate;
    private TextView btnTime;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_event_datetime);

        mode = getIntent().getIntExtra(EditEventDateTimeContract.EXTRA_MODE, EditEventDateTimeContract.MODE_EVENT);
        long initialMs = getIntent().getLongExtra(EditEventDateTimeContract.EXTRA_INITIAL_MS, -1L);
        hasTime = getIntent().getBooleanExtra(EditEventDateTimeContract.EXTRA_HAS_TIME, false);
        eventDayEndMs = getIntent().getLongExtra(EditEventDateTimeContract.EXTRA_EVENT_DAY_END_MS, -1L);
        regOpenMs = getIntent().getLongExtra(EditEventDateTimeContract.EXTRA_REG_OPEN_MS, -1L);
        hasRegOpenTime = getIntent().getBooleanExtra(EditEventDateTimeContract.EXTRA_HAS_REG_OPEN_TIME, false);

        if (initialMs > 0) {
            cal = Calendar.getInstance();
            cal.setTimeInMillis(initialMs);
        } else {
            cal = null;
        }

        TextView tvTitle = findViewById(R.id.edit_dt_tv_title);
        TextView tvHelper = findViewById(R.id.edit_dt_tv_helper);
        btnDate = findViewById(R.id.edit_dt_btn_pick_date);
        btnTime = findViewById(R.id.edit_dt_btn_pick_time);
        ImageButton btnClose = findViewById(R.id.edit_dt_btn_close);
        ImageButton btnSave = findViewById(R.id.edit_dt_btn_save);

        if (mode == EditEventDateTimeContract.MODE_EVENT) {
            tvTitle.setText(R.string.edit_event_label_event_datetime);
            tvHelper.setText(R.string.edit_dt_helper_event);
            tvHelper.setVisibility(android.view.View.VISIBLE);
        } else if (mode == EditEventDateTimeContract.MODE_REG_OPEN) {
            tvTitle.setText(R.string.event_create_registration_opens);
            tvHelper.setText(R.string.edit_dt_helper_reg_open);
            tvHelper.setVisibility(android.view.View.VISIBLE);
        } else {
            tvTitle.setText(R.string.event_create_registration_closes);
            tvHelper.setText(R.string.edit_dt_helper_reg_close);
            tvHelper.setVisibility(android.view.View.VISIBLE);
        }

        refreshPickers();
        btnDate.setOnClickListener(v -> pickDate());
        btnTime.setOnClickListener(v -> pickTime());
        btnClose.setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> save());
    }

    private void refreshPickers() {
        if (cal == null) {
            btnDate.setText("");
            btnTime.setText("");
            return;
        }
        btnDate.setText(dateFmt.format(cal.getTime()));
        btnTime.setText(hasTime ? timeFmt.format(cal.getTime()) : "");
    }

    private Calendar startOfTodayLocal() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c;
    }

    /** Earliest calendar day for the event — same as {@link EventCreateActivity}. */
    private Calendar startOfTomorrowLocal() {
        Calendar c = startOfTodayLocal();
        c.add(Calendar.DAY_OF_YEAR, 1);
        return c;
    }

    private static Calendar startOfDay(Calendar from) {
        Calendar c = (Calendar) from.clone();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c;
    }

    private void pickDate() {
        Calendar min = mode == EditEventDateTimeContract.MODE_EVENT
                ? startOfTomorrowLocal()
                : startOfTodayLocal();
        Calendar initial = cal != null ? (Calendar) cal.clone() : (Calendar) min.clone();
        if (initial.before(min)) {
            initial = (Calendar) min.clone();
        }
        DatePickerDialog d = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    if (cal == null) {
                        cal = Calendar.getInstance();
                    }
                    cal.set(Calendar.YEAR, year);
                    cal.set(Calendar.MONTH, month);
                    cal.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    cal.set(Calendar.HOUR_OF_DAY, 0);
                    cal.set(Calendar.MINUTE, 0);
                    cal.set(Calendar.SECOND, 0);
                    cal.set(Calendar.MILLISECOND, 0);
                    hasTime = false;
                    refreshPickers();
                },
                initial.get(Calendar.YEAR),
                initial.get(Calendar.MONTH),
                initial.get(Calendar.DAY_OF_MONTH));
        d.getDatePicker().setMinDate(min.getTimeInMillis());
        if (mode != EditEventDateTimeContract.MODE_EVENT && eventDayEndMs > 0) {
            d.getDatePicker().setMaxDate(eventDayEndMs);
        }
        d.show();
    }

    private void pickTime() {
        if (cal == null) {
            Toast.makeText(this, R.string.event_create_err_event_datetime, Toast.LENGTH_SHORT).show();
            return;
        }
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);
        new TimePickerDialog(this, (view, hourOfDay, minute1) -> {
            if (mode == EditEventDateTimeContract.MODE_EVENT) {
                cal.set(Calendar.HOUR_OF_DAY, hourOfDay);
                cal.set(Calendar.MINUTE, minute1);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                hasTime = true;
                refreshPickers();
                return;
            }
            if (mode == EditEventDateTimeContract.MODE_REG_OPEN) {
                cal.set(Calendar.HOUR_OF_DAY, hourOfDay);
                cal.set(Calendar.MINUTE, minute1);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                hasTime = true;
                refreshPickers();
                return;
            }
            if (regOpenMs < 0 || !hasRegOpenTime) {
                Toast.makeText(this, R.string.event_create_err_reg_open_time, Toast.LENGTH_SHORT).show();
                return;
            }
            Calendar trial = (Calendar) cal.clone();
            trial.set(Calendar.HOUR_OF_DAY, hourOfDay);
            trial.set(Calendar.MINUTE, minute1);
            trial.set(Calendar.SECOND, 0);
            trial.set(Calendar.MILLISECOND, 0);
            long pickedMs = trial.getTimeInMillis();
            long minCloseMs = regOpenMs + REGISTRATION_MIN_GAP_MS;
            if (pickedMs < minCloseMs) {
                cal.setTimeInMillis(minCloseMs);
                Toast.makeText(this, R.string.event_create_reg_close_clamped_to_gap, Toast.LENGTH_SHORT).show();
            } else {
                cal.set(Calendar.HOUR_OF_DAY, hourOfDay);
                cal.set(Calendar.MINUTE, minute1);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
            }
            hasTime = true;
            refreshPickers();
        }, hour, minute, false).show();
    }

    private void save() {
        if (cal == null || !hasTime) {
            Toast.makeText(this, R.string.event_create_err_required_fields, Toast.LENGTH_SHORT).show();
            return;
        }
        if (mode == EditEventDateTimeContract.MODE_EVENT) {
            Calendar eventDayStart = startOfDay(cal);
            Calendar minEventDay = startOfTomorrowLocal();
            if (eventDayStart.before(minEventDay)) {
                Toast.makeText(this, R.string.event_create_err_event_too_soon, Toast.LENGTH_LONG).show();
                return;
            }
        }
        if (mode == EditEventDateTimeContract.MODE_REG_CLOSE && (regOpenMs < 0 || !hasRegOpenTime)) {
            Toast.makeText(this, R.string.event_create_err_reg_open_time, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent data = new Intent();
        data.putExtra(EditResultKinds.EXTRA_KIND, EditResultKinds.KIND_DATETIME);
        data.putExtra(EditEventDateTimeContract.RESULT_MODE, mode);
        data.putExtra(EditEventDateTimeContract.RESULT_TIME_MS, cal.getTimeInMillis());
        data.putExtra(EditEventDateTimeContract.RESULT_HAS_TIME, true);
        setResult(RESULT_OK, data);
        finish();
    }
}
