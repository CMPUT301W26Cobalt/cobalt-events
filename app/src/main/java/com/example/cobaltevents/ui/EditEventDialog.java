package com.example.cobaltevents.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;

import com.bumptech.glide.Glide;
import com.example.cobaltevents.R;
import com.example.cobaltevents.controller.ImageController;
import com.example.cobaltevents.controller.QRCodeController;
import com.example.cobaltevents.db.EventDB;
import com.example.cobaltevents.model.Event;
import com.example.cobaltevents.util.NetworkConnectivity;
import com.google.firebase.Timestamp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Full-screen edit dialog aligned with {@link EventCreateActivity} rules (no geolocation lock, no QR).
 * Text fields open {@link EditFieldActivity}; date/time, capacity, price, visibility, and age use
 * dedicated full-screen editors.
 */
public final class EditEventDialog {

    private static final long REGISTRATION_MIN_GAP_MS = 60 * 60 * 1000L;
    private static final long MAX_POSTER_BYTES = 10L * 1024 * 1024;

    private final AppCompatActivity activity;
    private final Event event;
    private final EventDB eventDB;
    private final ImageController imageController;
    private final QRCodeController qrCodeController;
    private final Runnable onSaved;
    private final Runnable onPickPoster;
    private final ActivityResultLauncher<Intent> editDetailLauncher;
    private final Runnable onDialogDismiss;

    private Dialog dialog;
    private View root;
    private Uri pendingPosterUri;

    private final DateFormat dateFmt = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
    private final DateFormat timeFmt = new SimpleDateFormat("h:mm a", Locale.getDefault());

    private Calendar eventDateTime;
    private Calendar registrationOpen;
    private Calendar registrationClose;
    private boolean hasEventTime;
    private boolean hasRegOpenTime;
    private boolean hasRegCloseTime;

    private final List<String> selectedCategories = new ArrayList<>();

    private EditText etTitle;
    private EditText etDescription;
    private EditText etLocation;
    private EditText etCapacity;
    private EditText etPrice;
    private EditText etCriteria;
    private Spinner spinnerVisibility;
    private Spinner spinnerAge;
    /** Mirrors {@link Event} Places coordinates; cleared when location edited without a Places pick. */
    private Double editorLocationLat;
    private Double editorLocationLng;
    /**
     * Address string that {@link #editorLocationLat}/{@link #editorLocationLng} refer to (from a Places pick).
     */
    private String coordsValidForLocationText;

    private RelativeLayout wrapVisibility;
    private RelativeLayout wrapAge;

    private TextView tvTitleDisp;
    private TextView tvDescriptionDisp;
    private TextView tvLocationDisp;
    private TextView tvEventDtDisp;
    private TextView tvCapacityDisp;
    private TextView tvPriceDisp;
    private TextView tvRegOpenDisp;
    private TextView tvRegCloseDisp;
    private TextView tvCategoriesDisp;
    private TextView tvCriteriaDisp;
    private TextView tvVisibilityDisp;
    private TextView tvAgeDisp;

    private TextView tvErrTitle;
    private TextView tvErrEventDate;
    private TextView tvErrEventTime;
    private TextView tvErrRegOpenDate;
    private TextView tvErrRegOpenTime;
    private TextView tvErrRegCloseDate;
    private TextView tvErrRegCloseTime;

    private View imageUploadContainer;
    private View uploadPlaceholder;
    private View posterGradientBg;
    private TextView tvPosterSubtitle;
    private ImageView ivPoster;
    private NestedScrollView scroll;

    public EditEventDialog(@NonNull AppCompatActivity activity,
                           @NonNull Event event,
                           @NonNull EventDB eventDB,
                           @NonNull ImageController imageController,
                           @NonNull Runnable onSaved,
                           @NonNull Runnable onPickPoster,
                           @NonNull ActivityResultLauncher<Intent> editDetailLauncher,
                           @NonNull Runnable onDialogDismiss) {
        this.activity = activity;
        this.event = event;
        this.eventDB = eventDB;
        this.imageController = imageController;
        this.qrCodeController = new QRCodeController();
        this.onSaved = onSaved;
        this.onPickPoster = onPickPoster;
        this.editDetailLauncher = editDetailLauncher;
        this.onDialogDismiss = onDialogDismiss;
    }

    public void show() {
        root = LayoutInflater.from(activity).inflate(R.layout.dialog_edit_event, null, false);
        dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(root);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        bindViews();
        root.findViewById(R.id.edit_event_overlay).setOnClickListener(v -> dialog.dismiss());
        root.findViewById(R.id.edit_event_card).setOnClickListener(x -> { /* consume */ });
        initCalendarsFromEvent();
        populateReadOnlyDisplays();
        wirePencils();
        wireFooter();

        root.findViewById(R.id.edit_event_overlay).setOnClickListener(v -> dialog.dismiss());
        root.findViewById(R.id.edit_event_card).setOnClickListener(v -> { /* consume */ });
        root.findViewById(R.id.edit_event_btn_close).setOnClickListener(v -> dialog.dismiss());

        dialog.setOnDismissListener(d -> {
            dialog = null;
            root = null;
            onDialogDismiss.run();
        });

        dialog.show();
    }

    public void onPosterPicked(Uri uri) {
        if (uri == null || root == null) {
            return;
        }
        if (!validatePosterSize(uri)) {
            return;
        }
        pendingPosterUri = uri;
        uploadPlaceholder.setVisibility(View.GONE);
        if (posterGradientBg != null) {
            posterGradientBg.setVisibility(View.GONE);
        }
        ivPoster.setVisibility(View.VISIBLE);
        if (tvPosterSubtitle != null) {
            tvPosterSubtitle.setText(R.string.edit_event_poster_tap_change);
        }
        Glide.with(activity).load(uri).centerCrop().into(ivPoster);
    }

    /**
     * Called from {@link EventManageActivity}'s activity-result handler for
     * {@link EditFieldActivity} and other edit sub-screens.
     */
    public void onEditDetailResult(int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK || data == null || root == null) {
            return;
        }
        String kind = data.getStringExtra(EditResultKinds.EXTRA_KIND);
        if (EditResultKinds.KIND_TEXT_FIELD.equals(kind) || kind == null) {
            applyTextFieldResult(data);
            return;
        }
        if (EditResultKinds.KIND_DATETIME.equals(kind)) {
            applyDateTimeResult(data);
            return;
        }
        if (EditResultKinds.KIND_CAPACITY.equals(kind)) {
            String raw = data.getStringExtra(EditCapacityPriceContract.RESULT_CAPACITY_RAW);
            if (raw == null) {
                raw = "";
            }
            etCapacity.setText(raw);
            refreshCapacityDisplay();
            return;
        }
        if (EditResultKinds.KIND_PRICE.equals(kind)) {
            String raw = data.getStringExtra(EditCapacityPriceContract.RESULT_PRICE_RAW);
            if (raw == null) {
                raw = "";
            }
            etPrice.setText(raw);
            refreshPriceDisplay();
            return;
        }
        if (EditResultKinds.KIND_LOCATION.equals(kind)) {
            String loc = data.getStringExtra(EditLocationContract.RESULT_LOCATION);
            if (loc == null) {
                loc = "";
            }
            etLocation.setText(loc);
            tvLocationDisp.setText(loc.isEmpty() ? "—" : loc);
            String trimmed = loc.trim();
            if (trimmed.isEmpty() || "TBD".equalsIgnoreCase(trimmed)) {
                editorLocationLat = null;
                editorLocationLng = null;
                coordsValidForLocationText = null;
            } else if (data.hasExtra(EditLocationContract.RESULT_LATITUDE)
                    && data.hasExtra(EditLocationContract.RESULT_LONGITUDE)) {
                editorLocationLat = data.getDoubleExtra(EditLocationContract.RESULT_LATITUDE, 0d);
                editorLocationLng = data.getDoubleExtra(EditLocationContract.RESULT_LONGITUDE, 0d);
                coordsValidForLocationText = trimmed;
            } else {
                editorLocationLat = null;
                editorLocationLng = null;
                coordsValidForLocationText = null;
            }
            return;
        }
        if (EditResultKinds.KIND_CATEGORIES.equals(kind)) {
            java.util.ArrayList<String> list =
                    data.getStringArrayListExtra(EditCategoriesContract.RESULT_CATEGORIES);
            selectedCategories.clear();
            if (list != null) {
                for (String s : list) {
                    if (s != null) {
                        String t = s.trim();
                        if (!t.isEmpty()) {
                            selectedCategories.add(t);
                        }
                    }
                }
            }
            tvCategoriesDisp.setText(selectedCategories.isEmpty()
                    ? "—"
                    : String.join(", ", selectedCategories));
            return;
        }
        if (EditResultKinds.KIND_RADIO.equals(kind)) {
            applyRadioResult(data);
        }
    }

    private void applyTextFieldResult(@NonNull Intent data) {
        String field = data.getStringExtra(EditFieldContract.RESULT_FIELD);
        String value = data.getStringExtra(EditFieldContract.RESULT_VALUE);
        if (field == null) {
            return;
        }
        if (value == null) {
            value = "";
        }
        switch (field) {
            case EditFieldContract.FIELD_TITLE:
                etTitle.setText(value);
                tvTitleDisp.setText(value.trim().isEmpty() ? "—" : value.trim());
                break;
            case EditFieldContract.FIELD_DESCRIPTION:
                etDescription.setText(value);
                tvDescriptionDisp.setText(value.trim().isEmpty() ? "—" : value.trim());
                break;
            case EditFieldContract.FIELD_CRITERIA:
                etCriteria.setText(value);
                tvCriteriaDisp.setText(value.trim().isEmpty() ? "—" : value.trim());
                break;
            default:
                break;
        }
    }

    private void applyDateTimeResult(@NonNull Intent data) {
        int mode = data.getIntExtra(EditEventDateTimeContract.RESULT_MODE, 0);
        long ms = data.getLongExtra(EditEventDateTimeContract.RESULT_TIME_MS, -1L);
        if (ms < 0) {
            return;
        }
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ms);
        switch (mode) {
            case EditEventDateTimeContract.MODE_EVENT:
                eventDateTime = c;
                hasEventTime = true;
                clampRegistrationDatesToEvent();
                refreshEventDateTimeDisplay();
                break;
            case EditEventDateTimeContract.MODE_REG_OPEN:
                registrationOpen = c;
                hasRegOpenTime = true;
                adjustRegistrationCloseAfterOpenChanged();
                refreshRegistrationDisplays();
                break;
            case EditEventDateTimeContract.MODE_REG_CLOSE:
                registrationClose = c;
                hasRegCloseTime = true;
                refreshRegistrationDisplays();
                break;
            default:
                break;
        }
    }

    private void applyRadioResult(@NonNull Intent data) {
        String field = data.getStringExtra(EditRadioChoiceContract.RESULT_FIELD);
        String value = data.getStringExtra(EditRadioChoiceContract.RESULT_VALUE);
        if (field == null || value == null) {
            return;
        }
        if (EditRadioChoiceContract.FIELD_VISIBILITY.equals(field)) {
            tvVisibilityDisp.setText(value);
            if (spinnerVisibility != null) {
                String[] opts = activity.getResources().getStringArray(R.array.event_visibility_options);
                for (int i = 0; i < opts.length; i++) {
                    if (opts[i].equals(value)) {
                        spinnerVisibility.setSelection(i);
                        break;
                    }
                }
            }
        } else if (EditRadioChoiceContract.FIELD_AGE.equals(field)) {
            tvAgeDisp.setText(value);
            if (spinnerAge != null) {
                String[] opts = activity.getResources().getStringArray(R.array.event_age_group_options);
                for (int i = 0; i < opts.length; i++) {
                    if (opts[i].equalsIgnoreCase(value)) {
                        spinnerAge.setSelection(i);
                        break;
                    }
                }
            }
        }
    }

    private void launchEditField(@NonNull String fieldKey) {
        Intent i = new Intent(activity, EditFieldActivity.class);
        i.putExtra(EditFieldContract.EXTRA_FIELD, fieldKey);
        i.putExtra(EditFieldContract.EXTRA_VALUE, getFieldValueForEdit(fieldKey));
        editDetailLauncher.launch(i);
    }

    private void launchEditEventDateTime(int mode) {
        Intent i = new Intent(activity, EditEventDateTimeActivity.class);
        i.putExtra(EditEventDateTimeContract.EXTRA_MODE, mode);
        long initialMs = -1L;
        boolean hasTimePartial = false;
        if (mode == EditEventDateTimeContract.MODE_EVENT) {
            if (eventDateTime != null) {
                initialMs = eventDateTime.getTimeInMillis();
                hasTimePartial = hasEventTime;
            }
        } else if (mode == EditEventDateTimeContract.MODE_REG_OPEN) {
            if (registrationOpen != null) {
                initialMs = registrationOpen.getTimeInMillis();
                hasTimePartial = hasRegOpenTime;
            }
        } else if (mode == EditEventDateTimeContract.MODE_REG_CLOSE) {
            if (registrationClose != null) {
                initialMs = registrationClose.getTimeInMillis();
                hasTimePartial = hasRegCloseTime;
            }
        }
        i.putExtra(EditEventDateTimeContract.EXTRA_INITIAL_MS, initialMs);
        i.putExtra(EditEventDateTimeContract.EXTRA_HAS_TIME, hasTimePartial);
        long eventDayEndMs = -1L;
        if (eventDateTime != null) {
            Calendar end = (Calendar) eventDateTime.clone();
            end.set(Calendar.HOUR_OF_DAY, 23);
            end.set(Calendar.MINUTE, 59);
            end.set(Calendar.SECOND, 59);
            end.set(Calendar.MILLISECOND, 999);
            eventDayEndMs = end.getTimeInMillis();
        }
        i.putExtra(EditEventDateTimeContract.EXTRA_EVENT_DAY_END_MS, eventDayEndMs);
        long regOpenMs = registrationOpen != null ? registrationOpen.getTimeInMillis() : -1L;
        i.putExtra(EditEventDateTimeContract.EXTRA_REG_OPEN_MS, regOpenMs);
        i.putExtra(EditEventDateTimeContract.EXTRA_HAS_REG_OPEN_TIME, hasRegOpenTime);
        editDetailLauncher.launch(i);
    }

    private void launchEditCapacity() {
        Intent i = new Intent(activity, EditCapacityActivity.class);
        i.putExtra(EditCapacityPriceContract.EXTRA_CAPACITY_RAW,
                etCapacity.getText() != null ? etCapacity.getText().toString() : "");
        editDetailLauncher.launch(i);
    }

    private void launchEditPrice() {
        Intent i = new Intent(activity, EditPriceActivity.class);
        i.putExtra(EditCapacityPriceContract.EXTRA_PRICE_RAW,
                etPrice.getText() != null ? etPrice.getText().toString() : "");
        editDetailLauncher.launch(i);
    }

    private void launchEditRadio(@NonNull String fieldKey) {
        Intent intent = new Intent(activity, EditRadioChoiceActivity.class);
        intent.putExtra(EditRadioChoiceContract.EXTRA_FIELD, fieldKey);
        String current;
        if (EditRadioChoiceContract.FIELD_VISIBILITY.equals(fieldKey)) {
            current = tvVisibilityDisp.getText() != null ? tvVisibilityDisp.getText().toString() : "";
        } else {
            current = tvAgeDisp.getText() != null ? tvAgeDisp.getText().toString() : "";
        }
        if ("—".equals(current.trim())) {
            current = "";
        }
        intent.putExtra(EditRadioChoiceContract.EXTRA_CURRENT_VALUE, current);
        editDetailLauncher.launch(intent);
    }

    private void launchEditLocation() {
        Intent i = new Intent(activity, EditLocationActivity.class);
        i.putExtra(EditLocationContract.EXTRA_INITIAL_LOCATION, editorOrDisplayText(etLocation, tvLocationDisp));
        if (editorLocationLat != null && editorLocationLng != null) {
            i.putExtra(EditLocationContract.EXTRA_INITIAL_LATITUDE, editorLocationLat);
            i.putExtra(EditLocationContract.EXTRA_INITIAL_LONGITUDE, editorLocationLng);
        }
        editDetailLauncher.launch(i);
    }

    private void launchEditCategories() {
        Intent i = new Intent(activity, EditCategoriesActivity.class);
        i.putStringArrayListExtra(EditCategoriesContract.EXTRA_CATEGORIES, new ArrayList<>(selectedCategories));
        editDetailLauncher.launch(i);
    }

    @NonNull
    private String getFieldValueForEdit(@NonNull String fieldKey) {
        switch (fieldKey) {
            case EditFieldContract.FIELD_TITLE:
                return editorOrDisplayText(etTitle, tvTitleDisp);
            case EditFieldContract.FIELD_DESCRIPTION:
                return editorOrDisplayText(etDescription, tvDescriptionDisp);
            case EditFieldContract.FIELD_CRITERIA:
                return editorOrDisplayText(etCriteria, tvCriteriaDisp);
            default:
                return "";
        }
    }

    @NonNull
    private static String editorOrDisplayText(@NonNull EditText et, @NonNull TextView tv) {
        if (et.getText() != null) {
            String e = et.getText().toString().trim();
            if (!e.isEmpty()) {
                return et.getText().toString();
            }
        }
        CharSequence d = tv.getText();
        if (d == null || "—".contentEquals(d)) {
            return "";
        }
        return d.toString();
    }

    private void bindViews() {
        scroll = root.findViewById(R.id.edit_event_scroll);
        imageUploadContainer = root.findViewById(R.id.edit_event_image_upload_container);
        uploadPlaceholder = root.findViewById(R.id.edit_event_upload_placeholder);
        posterGradientBg = root.findViewById(R.id.edit_event_poster_gradient_bg);
        tvPosterSubtitle = root.findViewById(R.id.edit_event_tv_poster_subtitle);
        ivPoster = root.findViewById(R.id.edit_event_iv_poster);

        tvTitleDisp = root.findViewById(R.id.edit_event_tv_title_disp);
        etTitle = root.findViewById(R.id.edit_event_et_title);
        tvErrTitle = root.findViewById(R.id.edit_event_tv_err_title);

        tvDescriptionDisp = root.findViewById(R.id.edit_event_tv_description_disp);
        etDescription = root.findViewById(R.id.edit_event_et_description);

        tvEventDtDisp = root.findViewById(R.id.edit_event_tv_event_datetime_disp);
        tvErrEventDate = root.findViewById(R.id.edit_event_tv_err_event_date);
        tvErrEventTime = root.findViewById(R.id.edit_event_tv_err_event_time);

        tvLocationDisp = root.findViewById(R.id.edit_event_tv_location_disp);
        etLocation = root.findViewById(R.id.edit_event_et_location);

        tvCapacityDisp = root.findViewById(R.id.edit_event_tv_capacity_disp);
        tvPriceDisp = root.findViewById(R.id.edit_event_tv_price_disp);
        etCapacity = root.findViewById(R.id.edit_event_et_capacity);
        etPrice = root.findViewById(R.id.edit_event_et_price);

        tvRegOpenDisp = root.findViewById(R.id.edit_event_tv_reg_open_disp);
        tvErrRegOpenDate = root.findViewById(R.id.edit_event_tv_err_reg_open_date);
        tvErrRegOpenTime = root.findViewById(R.id.edit_event_tv_err_reg_open_time);

        tvRegCloseDisp = root.findViewById(R.id.edit_event_tv_reg_close_disp);
        tvErrRegCloseDate = root.findViewById(R.id.edit_event_tv_err_reg_close_date);
        tvErrRegCloseTime = root.findViewById(R.id.edit_event_tv_err_reg_close_time);

        tvCategoriesDisp = root.findViewById(R.id.edit_event_tv_categories_disp);

        tvCriteriaDisp = root.findViewById(R.id.edit_event_tv_criteria_disp);
        etCriteria = root.findViewById(R.id.edit_event_et_criteria);

        tvVisibilityDisp = root.findViewById(R.id.edit_event_tv_visibility_disp);
        wrapVisibility = root.findViewById(R.id.edit_event_wrap_visibility);
        spinnerVisibility = root.findViewById(R.id.edit_event_spinner_visibility);

        tvAgeDisp = root.findViewById(R.id.edit_event_tv_age_disp);
        wrapAge = root.findViewById(R.id.edit_event_wrap_age);
        spinnerAge = root.findViewById(R.id.edit_event_spinner_age_group);

        if (spinnerVisibility != null) {
            ArrayAdapter<CharSequence> visAdapter = ArrayAdapter.createFromResource(activity,
                    R.array.event_visibility_options, android.R.layout.simple_spinner_item);
            visAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerVisibility.setAdapter(visAdapter);
        }
        if (spinnerAge != null) {
            ArrayAdapter<CharSequence> ageAdapter = ArrayAdapter.createFromResource(activity,
                    R.array.event_age_group_options, android.R.layout.simple_spinner_item);
            ageAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerAge.setAdapter(ageAdapter);
        }
    }

    private void initCalendarsFromEvent() {
        if (event.getEventDate() != null) {
            eventDateTime = Calendar.getInstance();
            eventDateTime.setTime(event.getEventDate().toDate());
            hasEventTime = true;
        } else {
            eventDateTime = null;
            hasEventTime = false;
        }
        if (event.getRegistrationOpen() != null) {
            registrationOpen = Calendar.getInstance();
            registrationOpen.setTime(event.getRegistrationOpen().toDate());
            hasRegOpenTime = true;
        } else {
            registrationOpen = null;
            hasRegOpenTime = false;
        }
        if (event.getRegistrationClose() != null) {
            registrationClose = Calendar.getInstance();
            registrationClose.setTime(event.getRegistrationClose().toDate());
            hasRegCloseTime = true;
        } else {
            registrationClose = null;
            hasRegCloseTime = false;
        }
    }

    private void populateReadOnlyDisplays() {
        etTitle.setText(event.getName() != null ? event.getName() : "");
        tvTitleDisp.setText(event.getName() != null && !event.getName().isEmpty() ? event.getName() : "—");

        String desc = event.getDescription() != null ? event.getDescription().trim() : "";
        etDescription.setText(desc);
        tvDescriptionDisp.setText(!desc.isEmpty() ? desc : "—");

        refreshEventDateTimeDisplay();

        String loc = event.getLocation() != null ? event.getLocation().trim() : "";
        etLocation.setText(loc);
        tvLocationDisp.setText(!loc.isEmpty() ? loc : "—");
        editorLocationLat = event.getLocationLatitude();
        editorLocationLng = event.getLocationLongitude();
        if (editorLocationLat != null && editorLocationLng != null && !loc.isEmpty()) {
            coordsValidForLocationText = loc;
        } else {
            coordsValidForLocationText = null;
        }

        int cap = event.getWaitingListCapacity();
        etCapacity.setText(cap <= 0 ? "" : String.valueOf(cap));
        etPrice.setText(event.getPrice() != null && !event.getPrice().trim().isEmpty() ? event.getPrice().trim() : "");
        refreshCapacityDisplay();
        refreshPriceDisplay();

        refreshRegistrationDisplays();

        selectedCategories.clear();
        selectedCategories.addAll(event.getCategory());
        tvCategoriesDisp.setText(selectedCategories.isEmpty() ? "—" : String.join(", ", selectedCategories));

        String crit = event.getCriteria() != null ? event.getCriteria().trim() : "";
        etCriteria.setText(crit);
        tvCriteriaDisp.setText(!crit.isEmpty() ? crit : "—");

        boolean priv = event.isPrivate();
        tvVisibilityDisp.setText(priv ? R.string.event_visibility_private : R.string.event_visibility_public);
        if (spinnerVisibility != null) {
            spinnerVisibility.setSelection(priv ? 1 : 0);
        }

        String age = event.getAgeGroup();
        String[] ageOpts = activity.getResources().getStringArray(R.array.event_age_group_options);
        String allLabel = ageOpts.length > 0 ? ageOpts[0] : "All";
        tvAgeDisp.setText(age == null || age.isEmpty() ? allLabel : age);
        if (spinnerAge != null) {
            String[] options = ageOpts;
            int sel = 0;
            if (age != null) {
                for (int i = 0; i < options.length; i++) {
                    if (age.equalsIgnoreCase(options[i])) {
                        sel = i;
                        break;
                    }
                }
            }
            spinnerAge.setSelection(sel);
        }

        if (event.getPosterImageUrl() != null && !event.getPosterImageUrl().trim().isEmpty()) {
            uploadPlaceholder.setVisibility(View.GONE);
            if (posterGradientBg != null) {
                posterGradientBg.setVisibility(View.GONE);
            }
            ivPoster.setVisibility(View.VISIBLE);
            if (tvPosterSubtitle != null) {
                tvPosterSubtitle.setText(R.string.edit_event_poster_tap_change);
            }
            Glide.with(activity).load(event.getPosterImageUrl()).centerCrop().into(ivPoster);
        } else {
            uploadPlaceholder.setVisibility(View.VISIBLE);
            if (posterGradientBg != null) {
                posterGradientBg.setVisibility(View.VISIBLE);
            }
            ivPoster.setVisibility(View.GONE);
            if (tvPosterSubtitle != null) {
                tvPosterSubtitle.setText(R.string.edit_event_poster_tap_upload);
            }
        }

        hideAllEditRows();
    }

    private static String formatPriceForDisplay(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "$0";
        }
        try {
            BigDecimal bd = new BigDecimal(raw.trim());
            if (bd.compareTo(BigDecimal.ZERO) == 0) {
                return "$0";
            }
            return "$" + bd.stripTrailingZeros().toPlainString();
        } catch (Exception e) {
            return raw;
        }
    }

    private void hideAllEditRows() {
        etTitle.setVisibility(View.GONE);
        etDescription.setVisibility(View.GONE);
        etLocation.setVisibility(View.GONE);
        etCriteria.setVisibility(View.GONE);
        if (wrapVisibility != null) {
            wrapVisibility.setVisibility(View.GONE);
        }
        if (wrapAge != null) {
            wrapAge.setVisibility(View.GONE);
        }

        tvTitleDisp.setVisibility(View.VISIBLE);
        tvDescriptionDisp.setVisibility(View.VISIBLE);
        tvEventDtDisp.setVisibility(View.VISIBLE);
        tvLocationDisp.setVisibility(View.VISIBLE);
        tvCapacityDisp.setVisibility(View.VISIBLE);
        tvPriceDisp.setVisibility(View.VISIBLE);
        tvRegOpenDisp.setVisibility(View.VISIBLE);
        tvRegCloseDisp.setVisibility(View.VISIBLE);
        tvCategoriesDisp.setVisibility(View.VISIBLE);
        tvCriteriaDisp.setVisibility(View.VISIBLE);
        tvVisibilityDisp.setVisibility(View.VISIBLE);
        tvAgeDisp.setVisibility(View.VISIBLE);
    }

    private void refreshEventDateTimeDisplay() {
        if (eventDateTime != null && hasEventTime) {
            String line = dateFmt.format(eventDateTime.getTime()) + " · " + timeFmt.format(eventDateTime.getTime());
            tvEventDtDisp.setText(line);
        } else {
            tvEventDtDisp.setText("—");
        }
    }

    private void refreshRegistrationDisplays() {
        if (registrationOpen != null && hasRegOpenTime) {
            String line = dateFmt.format(registrationOpen.getTime()) + " · " + timeFmt.format(registrationOpen.getTime());
            tvRegOpenDisp.setText(line);
        } else {
            tvRegOpenDisp.setText("—");
        }
        if (registrationClose != null && hasRegCloseTime) {
            String line = dateFmt.format(registrationClose.getTime()) + " · " + timeFmt.format(registrationClose.getTime());
            tvRegCloseDisp.setText(line);
        } else {
            tvRegCloseDisp.setText("—");
        }
    }

    private void refreshCapacityDisplay() {
        String raw = etCapacity.getText() != null ? etCapacity.getText().toString().trim() : "";
        String capStr = raw.isEmpty() || raw.equalsIgnoreCase("unlimited") ? "Unlimited" : raw;
        tvCapacityDisp.setText(capStr);
    }

    private void refreshPriceDisplay() {
        String raw = etPrice.getText() != null ? etPrice.getText().toString().trim() : "";
        tvPriceDisp.setText(formatPriceForDisplay(raw.isEmpty() ? null : raw));
    }

    private void wirePencils() {
        imageUploadContainer.setOnClickListener(v -> {
            scrollToView(imageUploadContainer);
            onPickPoster.run();
        });

        root.findViewById(R.id.edit_event_row_title_disp).setOnClickListener(v ->
                launchEditField(EditFieldContract.FIELD_TITLE));
        root.findViewById(R.id.edit_event_row_description).setOnClickListener(v ->
                launchEditField(EditFieldContract.FIELD_DESCRIPTION));
        root.findViewById(R.id.edit_event_row_event_dt).setOnClickListener(v ->
                launchEditEventDateTime(EditEventDateTimeContract.MODE_EVENT));
        root.findViewById(R.id.edit_event_row_location).setOnClickListener(v -> launchEditLocation());
        root.findViewById(R.id.edit_event_row_capacity).setOnClickListener(v -> launchEditCapacity());
        root.findViewById(R.id.edit_event_row_price).setOnClickListener(v -> launchEditPrice());
        root.findViewById(R.id.edit_event_row_reg_open).setOnClickListener(v ->
                launchEditEventDateTime(EditEventDateTimeContract.MODE_REG_OPEN));
        root.findViewById(R.id.edit_event_row_reg_close).setOnClickListener(v ->
                launchEditEventDateTime(EditEventDateTimeContract.MODE_REG_CLOSE));
        root.findViewById(R.id.edit_event_row_categories).setOnClickListener(v -> launchEditCategories());
        root.findViewById(R.id.edit_event_row_criteria).setOnClickListener(v ->
                launchEditField(EditFieldContract.FIELD_CRITERIA));
        root.findViewById(R.id.edit_event_row_visibility).setOnClickListener(v ->
                launchEditRadio(EditRadioChoiceContract.FIELD_VISIBILITY));
        root.findViewById(R.id.edit_event_row_age).setOnClickListener(v ->
                launchEditRadio(EditRadioChoiceContract.FIELD_AGE));
    }

    private void scrollToView(View v) {
        if (scroll == null || v == null) {
            return;
        }
        scroll.post(() -> scroll.smoothScrollTo(0, v.getTop()));
    }

    private void wireFooter() {
        root.findViewById(R.id.edit_event_btn_save).setOnClickListener(v -> attemptSave());
    }

    private Calendar startOfTodayLocal() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c;
    }

    /** Same earliest event day rule as {@link EventCreateActivity}. */
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

    private void clampRegistrationDatesToEvent() {
        if (eventDateTime == null) {
            return;
        }
        Calendar eventDayStart = startOfDay(eventDateTime);
        boolean cleared = false;
        if (registrationOpen != null && startOfDay(registrationOpen).after(eventDayStart)) {
            registrationOpen = null;
            hasRegOpenTime = false;
            cleared = true;
        }
        if (registrationClose != null && startOfDay(registrationClose).after(eventDayStart)) {
            registrationClose = null;
            hasRegCloseTime = false;
            cleared = true;
        }
        if (cleared) {
            Toast.makeText(activity, R.string.event_create_reg_clamped_to_event, Toast.LENGTH_LONG).show();
        }
        refreshRegistrationDisplays();
    }

    private void adjustRegistrationCloseAfterOpenChanged() {
        if (!hasRegCloseTime || registrationClose == null || registrationOpen == null) {
            return;
        }
        long openMs = registrationOpen.getTimeInMillis();
        long closeMs = registrationClose.getTimeInMillis();
        if (closeMs - openMs < REGISTRATION_MIN_GAP_MS) {
            registrationClose.setTimeInMillis(openMs + REGISTRATION_MIN_GAP_MS);
            hasRegCloseTime = true;
            Toast.makeText(activity, R.string.event_create_reg_close_adjusted_for_gap, Toast.LENGTH_LONG).show();
            refreshRegistrationDisplays();
        }
    }

    private void setErr(TextView tv, boolean show) {
        if (tv == null) {
            return;
        }
        if (show) {
            tv.setText(R.string.event_create_field_required);
            tv.setVisibility(View.VISIBLE);
        } else {
            tv.setVisibility(View.GONE);
        }
    }

    private void clearErrors() {
        setErr(tvErrTitle, false);
        setErr(tvErrEventDate, false);
        setErr(tvErrEventTime, false);
        setErr(tvErrRegOpenDate, false);
        setErr(tvErrRegOpenTime, false);
        setErr(tvErrRegCloseDate, false);
        setErr(tvErrRegCloseTime, false);
    }

    private boolean validateRequired() {
        clearErrors();
        boolean err = false;
        String title = etTitle.getText() != null ? etTitle.getText().toString().trim() : "";
        if (title.isEmpty()) {
            setErr(tvErrTitle, true);
            err = true;
        }
        if (eventDateTime == null) {
            setErr(tvErrEventDate, true);
            err = true;
        }
        if (!hasEventTime) {
            setErr(tvErrEventTime, true);
            err = true;
        }
        String loc = etLocation.getText() != null ? etLocation.getText().toString().trim() : "";
        if (loc.isEmpty()) {
            err = true;
            Toast.makeText(activity, R.string.event_create_err_location, Toast.LENGTH_SHORT).show();
        }
        if (registrationOpen == null) {
            setErr(tvErrRegOpenDate, true);
            err = true;
        }
        if (!hasRegOpenTime) {
            setErr(tvErrRegOpenTime, true);
            err = true;
        }
        if (registrationClose == null) {
            setErr(tvErrRegCloseDate, true);
            err = true;
        }
        if (!hasRegCloseTime) {
            setErr(tvErrRegCloseTime, true);
            err = true;
        }
        if (err) {
            Toast.makeText(activity, R.string.event_create_err_required_fields, Toast.LENGTH_LONG).show();
        }
        return !err;
    }

    private void attemptSave() {
        syncVisibleTextIntoEditors();
        if (!validateRequired()) {
            return;
        }
        long eventMs = eventDateTime.getTimeInMillis();
        Calendar minEventDay = startOfTomorrowLocal();
        Calendar eventDay = (Calendar) eventDateTime.clone();
        eventDay.set(Calendar.HOUR_OF_DAY, 0);
        eventDay.set(Calendar.MINUTE, 0);
        eventDay.set(Calendar.SECOND, 0);
        eventDay.set(Calendar.MILLISECOND, 0);
        if (eventDay.before(minEventDay)) {
            Toast.makeText(activity, R.string.event_create_err_event_too_soon, Toast.LENGTH_LONG).show();
            return;
        }

        String location = etLocation.getText() != null ? etLocation.getText().toString().trim() : "";
        Calendar regOpenDay = startOfDay(registrationOpen);
        Calendar regCloseDay = startOfDay(registrationClose);
        if (regOpenDay.after(eventDay)) {
            Toast.makeText(activity, R.string.event_create_err_reg_day_event, Toast.LENGTH_LONG).show();
            return;
        }
        if (regCloseDay.after(eventDay)) {
            Toast.makeText(activity, R.string.event_create_err_reg_day_event, Toast.LENGTH_LONG).show();
            return;
        }

        long now = System.currentTimeMillis();
        long regOpenMs = registrationOpen.getTimeInMillis();
        long regCloseMs = registrationClose.getTimeInMillis();

        if (regOpenMs >= regCloseMs) {
            Toast.makeText(activity, R.string.event_create_err_reg_order, Toast.LENGTH_LONG).show();
            return;
        }
        if (regCloseMs - regOpenMs < REGISTRATION_MIN_GAP_MS) {
            Toast.makeText(activity, R.string.event_create_err_reg_min_gap, Toast.LENGTH_LONG).show();
            return;
        }
        if (regCloseMs <= now) {
            Toast.makeText(activity, R.string.event_create_err_reg_close_after_now, Toast.LENGTH_LONG).show();
            return;
        }
        if (regCloseMs > eventMs) {
            Toast.makeText(activity, R.string.event_create_err_reg_before_event, Toast.LENGTH_LONG).show();
            return;
        }
        if (regOpenMs >= eventMs) {
            Toast.makeText(activity, R.string.event_create_err_reg_before_event, Toast.LENGTH_LONG).show();
            return;
        }

        int capacity;
        try {
            capacity = parseCapacityWithDefault(etCapacity != null ? etCapacity.getText().toString() : "");
        } catch (IllegalArgumentException e) {
            Toast.makeText(activity, R.string.event_create_err_capacity, Toast.LENGTH_SHORT).show();
            return;
        }
        String priceStr;
        try {
            priceStr = parsePrice(etPrice != null ? etPrice.getText().toString() : "");
        } catch (IllegalArgumentException e) {
            Toast.makeText(activity, R.string.event_create_err_price, Toast.LENGTH_SHORT).show();
            return;
        }

        if (!NetworkConnectivity.hasValidatedInternet(activity)) {
            Toast.makeText(activity, R.string.event_create_no_internet, Toast.LENGTH_SHORT).show();
            return;
        }

        String title = etTitle.getText() != null ? etTitle.getText().toString().trim() : "";
        String descRaw = etDescription.getText() != null ? etDescription.getText().toString().trim() : "";
        String description = descRaw.isEmpty() ? null : descRaw;
        String critRaw = etCriteria.getText() != null ? etCriteria.getText().toString().trim() : "";
        String criteria = critRaw.isEmpty() ? null : critRaw;

        Timestamp eventTs = new Timestamp(new Date(eventMs));
        Timestamp regOpenTs = new Timestamp(new Date(regOpenMs));
        Timestamp regCloseTs = new Timestamp(new Date(regCloseMs));

        if (!hasResolvedPlaceCoordinatesForLocation(location)) {
            Toast.makeText(activity, R.string.event_select_place_from_suggestions, Toast.LENGTH_LONG).show();
            return;
        }
        String locTrim = location != null ? location.trim() : "";
        Double lat = null;
        Double lng = null;
        if (!locTrim.isEmpty() && !"TBD".equalsIgnoreCase(locTrim)) {
            lat = editorLocationLat;
            lng = editorLocationLng;
        }
        event.setName(title);
        event.setDescription(description);
        event.setLocation(location);
        event.setLocationLatitude(lat);
        event.setLocationLongitude(lng);
        event.setEventDate(eventTs);
        event.setRegistrationOpen(regOpenTs);
        event.setRegistrationClose(regCloseTs);
        event.setPrice(priceStr);
        event.setWaitingListCapacity(capacity);
        event.setCriteria(criteria);
        event.setCategory(new ArrayList<>(selectedCategories));
        applyAgeGroupSelection();
        boolean wasPrivate = event.isPrivate();
        applyVisibilitySelection();
        syncQrCodeDataForVisibilityChange(wasPrivate, event.isPrivate());

        eventDB.updateEvent(event, unused -> activity.runOnUiThread(() -> {
            editorLocationLat = event.getLocationLatitude();
            editorLocationLng = event.getLocationLongitude();
            coordsValidForLocationText = (editorLocationLat != null && editorLocationLng != null
                    && !locTrim.isEmpty()) ? locTrim : null;
            if (pendingPosterUri != null) {
                Uri u = pendingPosterUri;
                pendingPosterUri = null;
                imageController.uploadPoster(u, event,
                        v2 -> activity.runOnUiThread(() -> finishSuccess()),
                        err -> activity.runOnUiThread(() ->
                                Toast.makeText(activity,
                                        activity.getString(R.string.event_create_poster_failed,
                                                err.getMessage() != null ? err.getMessage() : ""),
                                        Toast.LENGTH_LONG).show()));
            } else {
                finishSuccess();
            }
        }), e -> activity.runOnUiThread(() ->
                Toast.makeText(activity, R.string.edit_event_save_failed, Toast.LENGTH_SHORT).show()));
    }

    /** Real addresses need coordinates from a Places pick; empty or TBD may omit coordinates. */
    private boolean hasResolvedPlaceCoordinatesForLocation(String location) {
        String locTrim = location != null ? location.trim() : "";
        if (locTrim.isEmpty() || "TBD".equalsIgnoreCase(locTrim)) {
            return true;
        }
        return editorLocationLat != null && editorLocationLng != null
                && coordsValidForLocationText != null
                && coordsValidForLocationText.equals(locTrim);
    }

    private void finishSuccess() {
        Toast.makeText(activity, R.string.edit_event_saved, Toast.LENGTH_SHORT).show();
        onSaved.run();
        dialog.dismiss();
    }

    /** Copy display strings into editors when still in read-only mode so save reads current values. */
    private void syncVisibleTextIntoEditors() {
        if (etTitle.getVisibility() != View.VISIBLE) {
            CharSequence t = tvTitleDisp.getText();
            if (t != null && !"—".contentEquals(t)) {
                etTitle.setText(t);
            }
        }
        if (etDescription.getVisibility() != View.VISIBLE) {
            String t = tvDescriptionDisp.getText() != null ? tvDescriptionDisp.getText().toString() : "";
            if (!"—".equals(t)) {
                etDescription.setText(t);
            }
        }
        if (etLocation.getVisibility() != View.VISIBLE) {
            String t = tvLocationDisp.getText() != null ? tvLocationDisp.getText().toString() : "";
            if (!"—".equals(t)) {
                etLocation.setText(t);
            }
        }
        if (etCriteria.getVisibility() != View.VISIBLE) {
            String t = tvCriteriaDisp.getText() != null ? tvCriteriaDisp.getText().toString() : "";
            if (!"—".equals(t)) {
                etCriteria.setText(t);
            }
        }
    }

    private void applyAgeGroupSelection() {
        if (spinnerAge == null) {
            event.setAgeGroup(null);
            return;
        }
        int pos = spinnerAge.getSelectedItemPosition();
        String[] options = activity.getResources().getStringArray(R.array.event_age_group_options);
        if (pos <= 0 || pos >= options.length) {
            event.setAgeGroup(null);
        } else {
            event.setAgeGroup(options[pos]);
        }
    }

    private void applyVisibilitySelection() {
        if (spinnerVisibility == null) {
            return;
        }
        event.setPrivate(spinnerVisibility.getSelectedItemPosition() == 1);
    }

    /**
     * Keep qrCodeData aligned with visibility changes:
     * - private -> public: generate/store QR data
     * - public -> private: purge QR data from Firestore by setting null
     */
    private void syncQrCodeDataForVisibilityChange(boolean wasPrivate, boolean isPrivateNow) {
        if (wasPrivate == isPrivateNow) {
            return;
        }
        if (isPrivateNow) {
            event.setQrCodeData(null);
            return;
        }
        String eventId = event.getEventId();
        if (eventId == null || eventId.trim().isEmpty()) {
            return;
        }
        event.setQrCodeData(qrCodeController.generateQRCodeData(eventId));
    }

    private static int parseCapacityWithDefault(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return 0;
        }
        String t = raw.trim();
        if (t.equalsIgnoreCase("unlimited")) {
            return 0;
        }
        try {
            int n = Integer.parseInt(t);
            if (n < 1) {
                throw new IllegalArgumentException();
            }
            return n;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static String parsePrice(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "0";
        }
        BigDecimal bd;
        try {
            bd = new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(e);
        }
        if (bd.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException();
        }
        return bd.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private boolean validatePosterSize(Uri uri) {
        try (ParcelFileDescriptor pfd = activity.getContentResolver().openFileDescriptor(uri, "r")) {
            if (pfd == null) {
                Toast.makeText(activity, R.string.event_create_poster_invalid, Toast.LENGTH_SHORT).show();
                return false;
            }
            long len = pfd.getStatSize();
            if (len <= 0 || len > MAX_POSTER_BYTES) {
                Toast.makeText(activity, len > MAX_POSTER_BYTES
                        ? R.string.event_create_poster_too_large
                        : R.string.event_create_poster_invalid, Toast.LENGTH_LONG).show();
                return false;
            }
            return true;
        } catch (Exception e) {
            Toast.makeText(activity, R.string.event_create_poster_invalid, Toast.LENGTH_SHORT).show();
            return false;
        }
    }
}
