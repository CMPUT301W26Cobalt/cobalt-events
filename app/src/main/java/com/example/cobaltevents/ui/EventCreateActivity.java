package com.example.cobaltevents.ui;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Filter;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;

import com.example.cobaltevents.R;
import com.example.cobaltevents.controller.EventController;
import com.example.cobaltevents.util.NetworkConnectivity;
import com.example.cobaltevents.controller.ImageController;
import com.example.cobaltevents.controller.QRCodeController;
import com.example.cobaltevents.db.WaitingListDB;
import com.example.cobaltevents.model.Event;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.AutocompletePrediction;
import com.google.android.libraries.places.api.model.AutocompleteSessionToken;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.net.FetchPlaceRequest;
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import androidx.appcompat.widget.AppCompatAutoCompleteTextView;

import com.bumptech.glide.Glide;

/**
 * Organizer flow — create event with date/time pickers, Places location search,
 * capacity (number or unlimited → 0), price ≥ 0, registration window validation, categories chips.
 */
public class EventCreateActivity extends AppCompatActivity {

    /** Registration must stay open at least this long (close time − open time). */
    private static final long REGISTRATION_MIN_GAP_MS = 60 * 60 * 1000L;
    private static final int PLACES_DEBOUNCE_MS = 350;
    private static final int PLACES_MIN_QUERY_LEN = 2;
    private static final long MAX_POSTER_BYTES = 10L * 1024 * 1024;

    private EventController eventController;
    private QRCodeController qrCodeController;
    private ImageController imageController;
    private WaitingListDB waitingListDB;
    private String deviceId;
    private boolean placesInitialized;
    private PlacesClient placesClient;
    private AutocompleteSessionToken placesSessionToken;
    private final Handler placesHandler = new Handler(Looper.getMainLooper());
    private final Runnable placesDebouncedQuery = this::runDebouncedPlaceQuery;
    private final List<AutocompletePrediction> placesPredictions = new ArrayList<>();
    private final List<String> placesSuggestionLines = new ArrayList<>();
    private ArrayAdapter<String> placesAdapter;
    /** Skip Places lookup when we set the field from a chosen place. */
    private boolean locationTextProgrammatic;
    /**
     * After user selects an address, we stop querying until they edit the text.
     * Prevents the dropdown from reappearing with one “exact match” for the same string.
     */
    private String lockedLocationText;
    private Double pendingLocationLat;
    private Double pendingLocationLng;

    private TextView btnEventDate;
    private TextView btnEventTime;
    private TextView btnRegOpenDate;
    private TextView btnRegOpenTime;
    private TextView btnRegCloseDate;
    private TextView btnRegCloseTime;
    private AppCompatAutoCompleteTextView etLocation;
    private EditText etTitle;
    private EditText etDescription;
    private EditText etCapacity;
    private EditText etPrice;
    private EditText etCategoryAdd;
    private EditText etSelectionCriteria;
    private Spinner spinnerVisibility;
    private Spinner spinnerAgeGroup;
    private SwitchCompat switchGeolocationLock;
    /** Avoid re-entrancy when reverting the switch or applying dialog result. */
    private boolean suppressGeolocationSwitchCallback;
    private LinearLayout layoutCategoryChips;
    private HorizontalScrollView scrollCategoryChips;
    private FrameLayout imageUploadContainer;
    private View uploadPlaceholder;
    private ImageView ivEventImage;
    private Uri pendingPosterUri;

    private ScrollView scrollEventCreate;
    private View loadingOverlayCreate;
    private TextView btnCreateEvent;
    private OnBackPressedCallback blockBackWhileCreating;
    /** If set (user tapped Generate QR), create uses this Firestore id so the preview matches the saved event. */
    private String generatedQrEventId;
    private View qrSectionHeader;
    private LinearLayout qrPlaceholder;
    private ImageView ivQrCode;
    private TextView tvErrorTitle;
    private TextView tvErrorEventDate;
    private TextView tvErrorEventTime;
    private TextView tvErrorLocation;
    private TextView tvErrorRegOpenDate;
    private TextView tvErrorRegOpenTime;
    private TextView tvErrorRegCloseDate;
    private TextView tvErrorRegCloseTime;

    /** Local date/time composition for validation and Firestore Timestamps. */
    private Calendar eventDateTime;
    private Calendar registrationOpen;
    private Calendar registrationClose;

    /** True only after the user confirms time in the time picker (date alone is invalid). */
    private boolean hasEventTime;
    private boolean hasRegOpenTime;
    private boolean hasRegCloseTime;

    private final List<String> selectedCategories = new ArrayList<>();

    private final ActivityResultLauncher<String> pickPosterLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::onPosterPicked);

    private final DateFormat dateFmt = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
    private final DateFormat timeFmt = new SimpleDateFormat("h:mm a", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_create);

        ImageView btnBack = findViewById(R.id.btn_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        eventController = new EventController();
        qrCodeController = new QRCodeController();
        imageController = new ImageController();
        waitingListDB = new WaitingListDB();
        deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

        etTitle = findViewById(R.id.et_title);
        etDescription = findViewById(R.id.et_description);
        btnEventDate = findViewById(R.id.btn_event_date);
        btnEventTime = findViewById(R.id.btn_event_time);
        etLocation = findViewById(R.id.et_location);
        etCapacity = findViewById(R.id.et_capacity);
        etPrice = findViewById(R.id.et_price);
        btnRegOpenDate = findViewById(R.id.btn_reg_open_date);
        btnRegOpenTime = findViewById(R.id.btn_reg_open_time);
        btnRegCloseDate = findViewById(R.id.btn_reg_close_date);
        btnRegCloseTime = findViewById(R.id.btn_reg_close_time);
        etCategoryAdd = findViewById(R.id.et_category_add);
        layoutCategoryChips = findViewById(R.id.layout_category_chips);
        scrollCategoryChips = findViewById(R.id.scroll_category_chips);
        etSelectionCriteria = findViewById(R.id.et_selection_criteria);
        spinnerVisibility = findViewById(R.id.spinner_visibility);
        if (spinnerVisibility != null) {
            ArrayAdapter<CharSequence> visAdapter = ArrayAdapter.createFromResource(this,
                    R.array.event_visibility_options, android.R.layout.simple_spinner_item);
            visAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerVisibility.setAdapter(visAdapter);
            spinnerVisibility.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    updateQrUiForVisibilitySelection();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    updateQrUiForVisibilitySelection();
                }
            });
        }
        spinnerAgeGroup = findViewById(R.id.spinner_age_group);
        if (spinnerAgeGroup != null) {
            ArrayAdapter<CharSequence> ageAdapter = ArrayAdapter.createFromResource(this,
                    R.array.event_age_group_options, android.R.layout.simple_spinner_item);
            ageAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerAgeGroup.setAdapter(ageAdapter);
        }
        switchGeolocationLock = findViewById(R.id.switch_geolocation_lock);
        if (switchGeolocationLock != null) {
            switchGeolocationLock.setOnCheckedChangeListener(this::onGeolocationLockToggled);
        }
        imageUploadContainer = findViewById(R.id.image_upload_container);
        uploadPlaceholder = findViewById(R.id.upload_placeholder);
        ivEventImage = findViewById(R.id.iv_event_image);
        if (imageUploadContainer != null) {
            imageUploadContainer.setOnClickListener(v -> pickPosterLauncher.launch("image/*"));
        }

        qrSectionHeader = findViewById(R.id.qr_section_header);
        qrPlaceholder = findViewById(R.id.qr_placeholder);
        ivQrCode = findViewById(R.id.iv_qr_code);
        if (qrPlaceholder != null) {
            qrPlaceholder.setOnClickListener(v -> generatePreviewQr());
        }
        updateQrUiForVisibilitySelection();

        scrollEventCreate = findViewById(R.id.scroll_event_create);
        loadingOverlayCreate = findViewById(R.id.loading_overlay_create_event);
        blockBackWhileCreating = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                // Swallow back while create is in flight.
            }
        };
        getOnBackPressedDispatcher().addCallback(this, blockBackWhileCreating);
        tvErrorTitle = findViewById(R.id.tv_error_title);
        tvErrorEventDate = findViewById(R.id.tv_error_event_date);
        tvErrorEventTime = findViewById(R.id.tv_error_event_time);
        tvErrorLocation = findViewById(R.id.tv_error_location);
        tvErrorRegOpenDate = findViewById(R.id.tv_error_reg_open_date);
        tvErrorRegOpenTime = findViewById(R.id.tv_error_reg_open_time);
        tvErrorRegCloseDate = findViewById(R.id.tv_error_reg_close_date);
        tvErrorRegCloseTime = findViewById(R.id.tv_error_reg_close_time);

        setupRequiredFieldErrorClearing();

        TextView btnAddCategory = findViewById(R.id.btn_add_category);
        if (etCategoryAdd != null) {
            etCategoryAdd.setOnEditorActionListener((v, actionId, event) -> {
                boolean isImeAction = actionId == EditorInfo.IME_ACTION_DONE
                        || actionId == EditorInfo.IME_ACTION_GO
                        || actionId == EditorInfo.IME_ACTION_SEARCH
                        || actionId == EditorInfo.IME_ACTION_SEND;
                boolean isEnter = event != null
                        && event.getAction() == KeyEvent.ACTION_DOWN
                        && event.getKeyCode() == KeyEvent.KEYCODE_ENTER;
                if (isImeAction || isEnter) {
                    addCategoryFromInput();
                    return true;
                }
                return false;
            });
        }
        if (btnAddCategory != null) {
            btnAddCategory.setOnClickListener(v -> addCategoryFromInput());
        }
        btnCreateEvent = findViewById(R.id.btn_create_event);
        if (btnCreateEvent != null) {
            btnCreateEvent.setOnClickListener(v -> attemptCreateEvent());
        }

        if (btnEventDate != null) {
            btnEventDate.setOnClickListener(v -> pickEventDate());
        }
        if (btnEventTime != null) {
            btnEventTime.setOnClickListener(v -> pickEventTime());
        }
        if (btnRegOpenDate != null) {
            btnRegOpenDate.setOnClickListener(v -> pickRegistrationOpenDate());
        }
        if (btnRegOpenTime != null) {
            btnRegOpenTime.setOnClickListener(v -> pickRegistrationOpenTime());
        }
        if (btnRegCloseDate != null) {
            btnRegCloseDate.setOnClickListener(v -> pickRegistrationCloseDate());
        }
        if (btnRegCloseTime != null) {
            btnRegCloseTime.setOnClickListener(v -> pickRegistrationCloseTime());
        }

        initPlacesIfPossible();
        setupLocationAutocomplete();
    }

    private void onGeolocationLockToggled(CompoundButton buttonView, boolean isChecked) {
        if (suppressGeolocationSwitchCallback) {
            return;
        }
        if (!isChecked) {
            return;
        }
        suppressGeolocationSwitchCallback = true;
        buttonView.setChecked(false);
        suppressGeolocationSwitchCallback = false;

        showGeolocationLockConfirmDialog();
    }

    private void showGeolocationLockConfirmDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_geolocation_lock_confirm, null);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();

        View btnClose = dialogView.findViewById(R.id.btn_close);
        TextView btnCancel = dialogView.findViewById(R.id.btn_cancel);
        TextView btnTurnOn = dialogView.findViewById(R.id.btn_turn_on);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dialog.dismiss());
        }
        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> dialog.dismiss());
        }
        if (btnTurnOn != null) {
            btnTurnOn.setOnClickListener(v -> {
                dialog.dismiss();
                if (switchGeolocationLock != null) {
                    suppressGeolocationSwitchCallback = true;
                    switchGeolocationLock.setChecked(true);
                    suppressGeolocationSwitchCallback = false;
                }
            });
        }

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    private void applyAgeGroupSelectionToEvent(Event event) {
        if (event == null) {
            return;
        }
        if (spinnerAgeGroup == null) {
            event.setAgeGroup(null);
            return;
        }
        int pos = spinnerAgeGroup.getSelectedItemPosition();
        String[] options = getResources().getStringArray(R.array.event_age_group_options);
        if (pos <= 0 || pos >= options.length) {
            event.setAgeGroup(null);
        } else {
            event.setAgeGroup(options[pos]);
        }
    }

    private void generatePreviewQr() {
        if (isPrivateVisibilitySelected()) {
            updateQrUiForVisibilitySelection();
            return;
        }
        if (generatedQrEventId == null) {
            generatedQrEventId = FirebaseFirestore.getInstance()
                    .collection("events")
                    .document()
                    .getId();
        }
        Bitmap bitmap = qrCodeController.generateQRCode(generatedQrEventId);
        if (bitmap == null) {
            Toast.makeText(this, R.string.event_create_qr_generate_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        if (ivQrCode != null) {
            ivQrCode.setImageBitmap(bitmap);
            ivQrCode.setVisibility(View.VISIBLE);
        }
        if (qrPlaceholder != null) {
            qrPlaceholder.setVisibility(View.GONE);
        }
        Toast.makeText(this, R.string.event_create_qr_generated, Toast.LENGTH_SHORT).show();
    }

    private boolean isPrivateVisibilitySelected() {
        return spinnerVisibility != null && spinnerVisibility.getSelectedItemPosition() == 1;
    }

    private void updateQrUiForVisibilitySelection() {
        boolean isPrivate = isPrivateVisibilitySelected();
        boolean hasGeneratedQrPreview = generatedQrEventId != null
                && ivQrCode != null
                && ivQrCode.getDrawable() != null;
        if (qrSectionHeader != null) {
            qrSectionHeader.setVisibility(isPrivate ? View.GONE : View.VISIBLE);
        }
        if (qrPlaceholder != null) {
            if (isPrivate) {
                qrPlaceholder.setVisibility(View.GONE);
            } else {
                qrPlaceholder.setVisibility(hasGeneratedQrPreview ? View.GONE : View.VISIBLE);
            }
        }
        if (ivQrCode != null) {
            ivQrCode.setVisibility(isPrivate ? View.GONE : (hasGeneratedQrPreview ? View.VISIBLE : View.GONE));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        placesHandler.removeCallbacksAndMessages(null);
    }

    private String getMapsApiKey() {
        try {
            android.content.pm.ApplicationInfo ai = getPackageManager()
                    .getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
            if (ai.metaData != null) {
                return ai.metaData.getString("com.google.android.geo.API_KEY");
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void initPlacesIfPossible() {
        String key = getMapsApiKey();
        if (key == null || key.isEmpty()) {
            return;
        }
        try {
            if (!Places.isInitialized()) {
                Places.initialize(getApplicationContext(), key);
            }
            placesClient = Places.createClient(this);
            placesInitialized = true;
        } catch (Exception e) {
            placesInitialized = false;
            placesClient = null;
        }
    }

    private void setupLocationAutocomplete() {
        if (etLocation == null) {
            return;
        }
        etLocation.setThreshold(PLACES_MIN_QUERY_LEN);
        placesAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_dropdown_item_1line,
                new ArrayList<String>()) {
            @Override
            public Filter getFilter() {
                return new Filter() {
                    @Override
                    protected FilterResults performFiltering(CharSequence constraint) {
                        FilterResults results = new FilterResults();
                        synchronized (placesSuggestionLines) {
                            results.values = new ArrayList<>(placesSuggestionLines);
                            results.count = placesSuggestionLines.size();
                        }
                        return results;
                    }

                    @Override
                    protected void publishResults(CharSequence constraint, FilterResults results) {
                        clear();
                        if (results != null && results.count > 0) {
                            @SuppressWarnings("unchecked")
                            List<String> list = (List<String>) results.values;
                            addAll(list);
                        }
                        notifyDataSetChanged();
                    }
                };
            }
        };
        etLocation.setAdapter(placesAdapter);
        etLocation.setOnItemClickListener((parent, view, position, id) -> {
            placesHandler.removeCallbacks(placesDebouncedQuery);
            if (position < 0 || position >= placesPredictions.size()) {
                return;
            }
            AutocompletePrediction pred = placesPredictions.get(position);
            String preview = pred.getFullText(null).toString();
            lockedLocationText = preview;
            locationTextProgrammatic = true;
            etLocation.setText(preview);
            etLocation.setSelection(preview.length());
            locationTextProgrammatic = false;
            clearPlaceSuggestions();
            etLocation.dismissDropDown();
            setFieldError(tvErrorLocation, false);
            fetchPlaceDetails(pred.getPlaceId());
        });
        etLocation.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && lockedLocationText != null && etLocation.getText() != null
                    && lockedLocationText.equals(etLocation.getText().toString().trim())) {
                etLocation.dismissDropDown();
            }
        });
        etLocation.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (locationTextProgrammatic) {
                    return;
                }
                String str = s != null ? s.toString() : "";
                String trimmed = str.trim();
                if (lockedLocationText != null && lockedLocationText.equals(trimmed)) {
                    placesHandler.removeCallbacks(placesDebouncedQuery);
                    clearPlaceSuggestions();
                    etLocation.dismissDropDown();
                    return;
                }
                if (lockedLocationText != null && !lockedLocationText.equals(trimmed)) {
                    lockedLocationText = null;
                }
                placesHandler.removeCallbacks(placesDebouncedQuery);
                placesHandler.postDelayed(placesDebouncedQuery, PLACES_DEBOUNCE_MS);
            }
        });
        if (placesInitialized) {
            placesSessionToken = AutocompleteSessionToken.newInstance();
        }
    }

    private void runDebouncedPlaceQuery() {
        if (etLocation == null || !placesInitialized || placesClient == null) {
            return;
        }
        String q = etLocation.getText() != null ? etLocation.getText().toString().trim() : "";
        if (lockedLocationText != null && lockedLocationText.equals(q)) {
            clearPlaceSuggestions();
            etLocation.dismissDropDown();
            return;
        }
        if (q.length() < PLACES_MIN_QUERY_LEN) {
            clearPlaceSuggestions();
            return;
        }
        if (placesSessionToken == null) {
            placesSessionToken = AutocompleteSessionToken.newInstance();
        }
        FindAutocompletePredictionsRequest request = FindAutocompletePredictionsRequest.builder()
                .setSessionToken(placesSessionToken)
                .setQuery(q)
                .build();
        placesClient.findAutocompletePredictions(request)
                .addOnSuccessListener(response -> {
                    placesPredictions.clear();
                    synchronized (placesSuggestionLines) {
                        placesSuggestionLines.clear();
                        for (AutocompletePrediction p : response.getAutocompletePredictions()) {
                            placesPredictions.add(p);
                            placesSuggestionLines.add(p.getFullText(null).toString());
                        }
                    }
                    placesAdapter.getFilter().filter(etLocation.getText());
                    if (!placesPredictions.isEmpty() && etLocation.hasFocus()) {
                        etLocation.showDropDown();
                    }
                })
                .addOnFailureListener(e -> {
                    // Typing still works as plain address
                });
    }

    private void clearPlaceSuggestions() {
        placesPredictions.clear();
        synchronized (placesSuggestionLines) {
            placesSuggestionLines.clear();
        }
        if (placesAdapter != null) {
            placesAdapter.getFilter().filter(etLocation != null ? etLocation.getText() : "");
        }
    }

    private void fetchPlaceDetails(String placeId) {
        if (!placesInitialized || placesClient == null || placeId == null || etLocation == null) {
            return;
        }
        placesHandler.removeCallbacks(placesDebouncedQuery);
        List<Place.Field> fields = Arrays.asList(
                Place.Field.ADDRESS, Place.Field.NAME, Place.Field.LAT_LNG);
        FetchPlaceRequest request = FetchPlaceRequest.newInstance(placeId, fields);
        placesClient.fetchPlace(request)
                .addOnSuccessListener(response -> {
                    Place place = response.getPlace();
                    if (place == null) {
                        lockedLocationText = null;
                        pendingLocationLat = null;
                        pendingLocationLng = null;
                        return;
                    }
                    com.google.android.gms.maps.model.LatLng ll = place.getLatLng();
                    if (ll != null) {
                        pendingLocationLat = ll.latitude;
                        pendingLocationLng = ll.longitude;
                    } else {
                        pendingLocationLat = null;
                        pendingLocationLng = null;
                    }
                    String line = place.getAddress();
                    if (line == null || line.isEmpty()) {
                        line = place.getName();
                    }
                    if (line != null && !line.isEmpty()) {
                        locationTextProgrammatic = true;
                        etLocation.setText(line);
                        etLocation.setSelection(line.length());
                        locationTextProgrammatic = false;
                        lockedLocationText = line;
                    }
                    placesSessionToken = AutocompleteSessionToken.newInstance();
                    clearPlaceSuggestions();
                    etLocation.dismissDropDown();
                    etLocation.clearFocus();
                    setFieldError(tvErrorLocation, false);
                })
                .addOnFailureListener(e -> {
                    lockedLocationText = null;
                    Toast.makeText(this, R.string.event_create_places_unavailable, Toast.LENGTH_SHORT).show();
                });
    }

    private Calendar startOfTodayLocal() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c;
    }

    /** Earliest calendar day for the event: tomorrow (so today can still be used for registration). */
    private Calendar startOfTomorrowLocal() {
        Calendar c = startOfTodayLocal();
        c.add(Calendar.DAY_OF_YEAR, 1);
        return c;
    }

    /**
     * Earliest allowed registration open: start of the next local clock minute (e.g. 7:34:xx → 7:35:00).
     */
    private static long minRegistrationOpenInstantMillis() {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(System.currentTimeMillis());
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        c.add(Calendar.MINUTE, 1);
        return c.getTimeInMillis();
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
            if (btnRegCloseTime != null) {
                btnRegCloseTime.setText(timeFmt.format(registrationClose.getTime()));
            }
            Toast.makeText(this, R.string.event_create_reg_close_adjusted_for_gap, Toast.LENGTH_LONG).show();
        }
    }

    private static Calendar startOfDay(Calendar from) {
        Calendar c = (Calendar) from.clone();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c;
    }

    /**
     * Latest selectable calendar day for registration (inclusive): same calendar day as the event.
     * Applies as soon as the event date is chosen (time can be set later).
     */
    private Long getRegistrationDatePickerMaxMillis() {
        if (eventDateTime == null) {
            return null;
        }
        Calendar c = (Calendar) eventDateTime.clone();
        c.set(Calendar.HOUR_OF_DAY, 23);
        c.set(Calendar.MINUTE, 59);
        c.set(Calendar.SECOND, 59);
        c.set(Calendar.MILLISECOND, 999);
        return c.getTimeInMillis();
    }

    /** Same pattern as event date: clamp initial selection to [today, event day] when event is known. */
    private Calendar clampRegistrationInitialDate(Calendar existing) {
        Calendar min = startOfTodayLocal();
        Long maxMs = getRegistrationDatePickerMaxMillis();
        Calendar initial = existing != null ? (Calendar) existing.clone() : (Calendar) min.clone();
        if (initial.before(min)) {
            initial = (Calendar) min.clone();
        }
        if (maxMs != null) {
            Calendar maxBoundary = Calendar.getInstance();
            maxBoundary.setTimeInMillis(maxMs);
            Calendar maxDayStart = startOfDay(maxBoundary);
            if (startOfDay(initial).after(maxDayStart)) {
                initial = (Calendar) maxDayStart.clone();
            }
        }
        return initial;
    }

    private void clearRegistrationOpenFields() {
        registrationOpen = null;
        hasRegOpenTime = false;
        if (btnRegOpenDate != null) {
            btnRegOpenDate.setText("");
        }
        if (btnRegOpenTime != null) {
            btnRegOpenTime.setText("");
        }
    }

    private void clearRegistrationCloseFields() {
        registrationClose = null;
        hasRegCloseTime = false;
        if (btnRegCloseDate != null) {
            btnRegCloseDate.setText("");
        }
        if (btnRegCloseTime != null) {
            btnRegCloseTime.setText("");
        }
    }

    /** If event date moves earlier, drop registration dates that no longer fall on the allowed calendar range. */
    private void clampRegistrationDatesToEvent() {
        if (eventDateTime == null) {
            return;
        }
        Calendar eventDayStart = startOfDay(eventDateTime);
        boolean cleared = false;
        if (registrationOpen != null && startOfDay(registrationOpen).after(eventDayStart)) {
            clearRegistrationOpenFields();
            cleared = true;
        }
        if (registrationClose != null && startOfDay(registrationClose).after(eventDayStart)) {
            clearRegistrationCloseFields();
            cleared = true;
        }
        if (cleared) {
            Toast.makeText(this, R.string.event_create_reg_clamped_to_event, Toast.LENGTH_LONG).show();
        }
    }

    private void pickEventDate() {
        Calendar min = startOfTomorrowLocal();
        Calendar initial = eventDateTime != null ? (Calendar) eventDateTime.clone() : (Calendar) min.clone();
        if (initial.before(min)) {
            initial = (Calendar) min.clone();
        }
        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    if (eventDateTime == null) {
                        eventDateTime = Calendar.getInstance();
                    }
                    eventDateTime.set(Calendar.YEAR, year);
                    eventDateTime.set(Calendar.MONTH, month);
                    eventDateTime.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    // Reset time until user picks it again (avoid stale time from previous selection)
                    eventDateTime.set(Calendar.HOUR_OF_DAY, 0);
                    eventDateTime.set(Calendar.MINUTE, 0);
                    eventDateTime.set(Calendar.SECOND, 0);
                    eventDateTime.set(Calendar.MILLISECOND, 0);
                    hasEventTime = false;
                    if (btnEventTime != null) {
                        btnEventTime.setText("");
                    }
                    btnEventDate.setText(dateFmt.format(eventDateTime.getTime()));
                    setFieldError(tvErrorEventDate, false);
                    clampRegistrationDatesToEvent();
                },
                initial.get(Calendar.YEAR),
                initial.get(Calendar.MONTH),
                initial.get(Calendar.DAY_OF_MONTH));
        dialog.getDatePicker().setMinDate(min.getTimeInMillis());
        dialog.show();
    }

    private void pickEventTime() {
        if (eventDateTime == null) {
            Toast.makeText(this, R.string.event_create_err_event_datetime, Toast.LENGTH_SHORT).show();
            return;
        }
        int hour = eventDateTime.get(Calendar.HOUR_OF_DAY);
        int minute = eventDateTime.get(Calendar.MINUTE);
        new TimePickerDialog(this, (view, hourOfDay, minute1) -> {
            eventDateTime.set(Calendar.HOUR_OF_DAY, hourOfDay);
            eventDateTime.set(Calendar.MINUTE, minute1);
            eventDateTime.set(Calendar.SECOND, 0);
            eventDateTime.set(Calendar.MILLISECOND, 0);
            hasEventTime = true;
            btnEventTime.setText(timeFmt.format(eventDateTime.getTime()));
            setFieldError(tvErrorEventTime, false);
            clampRegistrationDatesToEvent();
        }, hour, minute, false).show();
    }

    private void pickRegistrationOpenDate() {
        Calendar min = null; // no minimum — past dates allowed for registration open
        Calendar initial = clampRegistrationInitialDate(registrationOpen);
        Long maxMs = getRegistrationDatePickerMaxMillis();
        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    if (registrationOpen == null) {
                        registrationOpen = Calendar.getInstance();
                    }
                    registrationOpen.set(Calendar.YEAR, year);
                    registrationOpen.set(Calendar.MONTH, month);
                    registrationOpen.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    registrationOpen.set(Calendar.HOUR_OF_DAY, 0);
                    registrationOpen.set(Calendar.MINUTE, 0);
                    registrationOpen.set(Calendar.SECOND, 0);
                    registrationOpen.set(Calendar.MILLISECOND, 0);
                    hasRegOpenTime = false;
                    if (btnRegOpenTime != null) {
                        btnRegOpenTime.setText("");
                    }
                    btnRegOpenDate.setText(dateFmt.format(registrationOpen.getTime()));
                    setFieldError(tvErrorRegOpenDate, false);
                },
                initial.get(Calendar.YEAR),
                initial.get(Calendar.MONTH),
                initial.get(Calendar.DAY_OF_MONTH));
        if (min != null) {
            dialog.getDatePicker().setMinDate(min.getTimeInMillis());
        }
        if (maxMs != null) {
            dialog.getDatePicker().setMaxDate(maxMs);
        }
        dialog.show();
    }

    private void pickRegistrationOpenTime() {
        if (registrationOpen == null) {
            Toast.makeText(this, R.string.event_create_err_reg_open, Toast.LENGTH_SHORT).show();
            return;
        }
        int hour = registrationOpen.get(Calendar.HOUR_OF_DAY);
        int minute = registrationOpen.get(Calendar.MINUTE);
        new TimePickerDialog(this, (view, hourOfDay, minute1) -> {
            registrationOpen.set(Calendar.HOUR_OF_DAY, hourOfDay);
            registrationOpen.set(Calendar.MINUTE, minute1);
            registrationOpen.set(Calendar.SECOND, 0);
            registrationOpen.set(Calendar.MILLISECOND, 0);
            long minOpen = minRegistrationOpenInstantMillis();
            if (registrationOpen.getTimeInMillis() < minOpen) {
                registrationOpen.setTimeInMillis(minOpen);
                Toast.makeText(this, R.string.event_create_reg_open_clamped_next_minute, Toast.LENGTH_LONG).show();
            }
            hasRegOpenTime = true;
            btnRegOpenTime.setText(timeFmt.format(registrationOpen.getTime()));
            setFieldError(tvErrorRegOpenTime, false);
            adjustRegistrationCloseAfterOpenChanged();
        }, hour, minute, false).show();
    }

    private void pickRegistrationCloseDate() {
        Calendar min = startOfTodayLocal();
        Calendar initial = clampRegistrationInitialDate(registrationClose);
        Long maxMs = getRegistrationDatePickerMaxMillis();
        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    if (registrationClose == null) {
                        registrationClose = Calendar.getInstance();
                    }
                    registrationClose.set(Calendar.YEAR, year);
                    registrationClose.set(Calendar.MONTH, month);
                    registrationClose.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    registrationClose.set(Calendar.HOUR_OF_DAY, 0);
                    registrationClose.set(Calendar.MINUTE, 0);
                    registrationClose.set(Calendar.SECOND, 0);
                    registrationClose.set(Calendar.MILLISECOND, 0);
                    hasRegCloseTime = false;
                    if (btnRegCloseTime != null) {
                        btnRegCloseTime.setText("");
                    }
                    btnRegCloseDate.setText(dateFmt.format(registrationClose.getTime()));
                    setFieldError(tvErrorRegCloseDate, false);
                },
                initial.get(Calendar.YEAR),
                initial.get(Calendar.MONTH),
                initial.get(Calendar.DAY_OF_MONTH));
        dialog.getDatePicker().setMinDate(min.getTimeInMillis());
        if (maxMs != null) {
            dialog.getDatePicker().setMaxDate(maxMs);
        }
        dialog.show();
    }

    private void pickRegistrationCloseTime() {
        if (registrationClose == null) {
            Toast.makeText(this, R.string.event_create_err_reg_close, Toast.LENGTH_SHORT).show();
            return;
        }
        if (registrationOpen == null || !hasRegOpenTime) {
            Toast.makeText(this, R.string.event_create_err_reg_open_time, Toast.LENGTH_SHORT).show();
            return;
        }
        long minCloseMs = registrationOpen.getTimeInMillis() + REGISTRATION_MIN_GAP_MS;
        long proposedMs = registrationClose.getTimeInMillis();
        Calendar initial = Calendar.getInstance();
        initial.setTimeInMillis(Math.max(proposedMs, minCloseMs));
        int hour = initial.get(Calendar.HOUR_OF_DAY);
        int minute = initial.get(Calendar.MINUTE);
        new TimePickerDialog(this, (view, hourOfDay, minute1) -> {
            Calendar trial = (Calendar) registrationClose.clone();
            trial.set(Calendar.HOUR_OF_DAY, hourOfDay);
            trial.set(Calendar.MINUTE, minute1);
            trial.set(Calendar.SECOND, 0);
            trial.set(Calendar.MILLISECOND, 0);
            long pickedMs = trial.getTimeInMillis();
            long minMs = registrationOpen.getTimeInMillis() + REGISTRATION_MIN_GAP_MS;
            if (pickedMs < minMs) {
                registrationClose.setTimeInMillis(minMs);
                Toast.makeText(this, R.string.event_create_reg_close_clamped_to_gap, Toast.LENGTH_SHORT).show();
            } else {
                registrationClose.set(Calendar.HOUR_OF_DAY, hourOfDay);
                registrationClose.set(Calendar.MINUTE, minute1);
                registrationClose.set(Calendar.SECOND, 0);
                registrationClose.set(Calendar.MILLISECOND, 0);
            }
            hasRegCloseTime = true;
            btnRegCloseTime.setText(timeFmt.format(registrationClose.getTime()));
            setFieldError(tvErrorRegCloseTime, false);
        }, hour, minute, false).show();
    }

    private void addCategoryFromInput() {
        if (etCategoryAdd == null) return;
        String raw = etCategoryAdd.getText() != null ? etCategoryAdd.getText().toString().trim() : "";
        if (raw.isEmpty()) return;
        if (!containsCategoryIgnoreCase(raw)) {
            selectedCategories.add(raw);
            renderCategoryChips();
        }
        etCategoryAdd.setText("");
    }

    private boolean containsCategoryIgnoreCase(String candidate) {
        for (String existing : selectedCategories) {
            if (existing != null && existing.equalsIgnoreCase(candidate)) return true;
        }
        return false;
    }

    private void renderCategoryChips() {
        if (layoutCategoryChips == null || scrollCategoryChips == null) return;
        layoutCategoryChips.removeAllViews();
        if (selectedCategories.isEmpty()) {
            scrollCategoryChips.setVisibility(View.GONE);
            return;
        }
        scrollCategoryChips.setVisibility(View.VISIBLE);
        for (String cat : selectedCategories) {
            layoutCategoryChips.addView(createCategoryChip(cat));
        }
    }

    private View createCategoryChip(String label) {
        LinearLayout chip = new LinearLayout(this);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(android.view.Gravity.CENTER_VERTICAL);
        chip.setBackgroundResource(R.drawable.bg_keyword_chip_white);
        int hPad = dpToPx(12);
        int vPad = dpToPx(6);
        chip.setPadding(hPad, vPad, hPad, vPad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(dpToPx(8));
        chip.setLayoutParams(lp);

        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(ContextCompat.getColor(this, R.color.organizer_blue));
        tv.setTextSize(14f);

        TextView remove = new TextView(this);
        remove.setText("×");
        remove.setTextColor(ContextCompat.getColor(this, R.color.organizer_blue));
        remove.setTextSize(14f);
        remove.setPadding(dpToPx(8), 0, 0, 0);
        remove.setBackgroundResource(android.R.color.transparent);
        remove.setOnClickListener(v -> {
            selectedCategories.remove(label);
            renderCategoryChips();
        });
        chip.addView(tv);
        chip.addView(remove);
        return chip;
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void setFieldError(TextView tv, boolean show) {
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

    private void clearRequiredFieldErrors() {
        setFieldError(tvErrorTitle, false);
        setFieldError(tvErrorEventDate, false);
        setFieldError(tvErrorEventTime, false);
        setFieldError(tvErrorLocation, false);
        setFieldError(tvErrorRegOpenDate, false);
        setFieldError(tvErrorRegOpenTime, false);
        setFieldError(tvErrorRegCloseDate, false);
        setFieldError(tvErrorRegCloseTime, false);
    }

    /**
     * @return true if all required fields are present; otherwise shows one toast, red labels, and false.
     */
    private boolean validateRequiredFieldsShowErrors() {
        clearRequiredFieldErrors();
        boolean hasError = false;

        String title = etTitle != null && etTitle.getText() != null
                ? etTitle.getText().toString().trim() : "";
        if (title.isEmpty()) {
            setFieldError(tvErrorTitle, true);
            hasError = true;
        }

        if (eventDateTime == null) {
            setFieldError(tvErrorEventDate, true);
            hasError = true;
        }
        if (!hasEventTime) {
            setFieldError(tvErrorEventTime, true);
            hasError = true;
        }

        String location = etLocation != null && etLocation.getText() != null
                ? etLocation.getText().toString().trim() : "";
        if (location.isEmpty()) {
            setFieldError(tvErrorLocation, true);
            hasError = true;
        }

        if (registrationOpen == null) {
            setFieldError(tvErrorRegOpenDate, true);
            hasError = true;
        }
        if (!hasRegOpenTime) {
            setFieldError(tvErrorRegOpenTime, true);
            hasError = true;
        }
        if (registrationClose == null) {
            setFieldError(tvErrorRegCloseDate, true);
            hasError = true;
        }
        if (!hasRegCloseTime) {
            setFieldError(tvErrorRegCloseTime, true);
            hasError = true;
        }

        if (hasError) {
            Toast.makeText(this, R.string.event_create_err_required_fields, Toast.LENGTH_LONG).show();
            scrollToFirstError();
        }
        return !hasError;
    }

    private void scrollToViewInForm(View view) {
        if (scrollEventCreate == null || view == null) {
            return;
        }
        scrollEventCreate.post(() -> {
            int y = 0;
            View v = view;
            while (v != null && v != scrollEventCreate) {
                y += v.getTop();
                Object p = v.getParent();
                v = p instanceof View ? (View) p : null;
            }
            scrollEventCreate.smoothScrollTo(0, Math.max(0, y - dpToPx(24)));
        });
    }

    private void scrollToFirstError() {
        if (tvErrorTitle != null && tvErrorTitle.getVisibility() == View.VISIBLE) {
            scrollToViewInForm(etTitle);
            return;
        }
        if (tvErrorEventDate != null && tvErrorEventDate.getVisibility() == View.VISIBLE) {
            scrollToViewInForm(btnEventDate);
            return;
        }
        if (tvErrorEventTime != null && tvErrorEventTime.getVisibility() == View.VISIBLE) {
            scrollToViewInForm(btnEventTime);
            return;
        }
        if (tvErrorLocation != null && tvErrorLocation.getVisibility() == View.VISIBLE) {
            scrollToViewInForm(etLocation);
            return;
        }
        if (tvErrorRegOpenDate != null && tvErrorRegOpenDate.getVisibility() == View.VISIBLE) {
            scrollToViewInForm(btnRegOpenDate);
            return;
        }
        if (tvErrorRegOpenTime != null && tvErrorRegOpenTime.getVisibility() == View.VISIBLE) {
            scrollToViewInForm(btnRegOpenTime);
            return;
        }
        if (tvErrorRegCloseDate != null && tvErrorRegCloseDate.getVisibility() == View.VISIBLE) {
            scrollToViewInForm(btnRegCloseDate);
            return;
        }
        if (tvErrorRegCloseTime != null && tvErrorRegCloseTime.getVisibility() == View.VISIBLE) {
            scrollToViewInForm(btnRegCloseTime);
        }
    }

    private void setupRequiredFieldErrorClearing() {
        if (etTitle != null) {
            etTitle.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (s != null && !s.toString().trim().isEmpty()) {
                        setFieldError(tvErrorTitle, false);
                    }
                }
            });
        }
        if (etLocation != null) {
            etLocation.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (locationTextProgrammatic) {
                        return;
                    }
                    if (s != null && !s.toString().trim().isEmpty()) {
                        setFieldError(tvErrorLocation, false);
                    }
                }
            });
        }
    }

    private void attemptCreateEvent() {
        if (!validateRequiredFieldsShowErrors()) {
            return;
        }

        String title = etTitle != null && etTitle.getText() != null
                ? etTitle.getText().toString().trim() : "";
        long eventMs = eventDateTime.getTimeInMillis();
        Calendar minEventDay = startOfTomorrowLocal();
        Calendar eventDay = (Calendar) eventDateTime.clone();
        eventDay.set(Calendar.HOUR_OF_DAY, 0);
        eventDay.set(Calendar.MINUTE, 0);
        eventDay.set(Calendar.SECOND, 0);
        eventDay.set(Calendar.MILLISECOND, 0);
        if (eventDay.before(minEventDay)) {
            Toast.makeText(this, R.string.event_create_err_event_too_soon, Toast.LENGTH_LONG).show();
            return;
        }

        String location = etLocation != null && etLocation.getText() != null
                ? etLocation.getText().toString().trim() : "";

        Calendar todayStart = startOfTodayLocal();
        Calendar regOpenDay = startOfDay(registrationOpen);
        Calendar regCloseDay = startOfDay(registrationClose);
        if (regOpenDay.before(todayStart)) {
            Toast.makeText(this, R.string.event_create_err_reg_day_today, Toast.LENGTH_LONG).show();
            return;
        }
        if (regCloseDay.before(todayStart)) {
            Toast.makeText(this, R.string.event_create_err_reg_day_today, Toast.LENGTH_LONG).show();
            return;
        }
        if (regOpenDay.after(eventDay)) {
            Toast.makeText(this, R.string.event_create_err_reg_day_event, Toast.LENGTH_LONG).show();
            return;
        }
        if (regCloseDay.after(eventDay)) {
            Toast.makeText(this, R.string.event_create_err_reg_day_event, Toast.LENGTH_LONG).show();
            return;
        }

        long now = System.currentTimeMillis();
        long regOpenMs = registrationOpen.getTimeInMillis();
        long regCloseMs = registrationClose.getTimeInMillis();
        long minOpenMs = minRegistrationOpenInstantMillis();

        if (regOpenMs >= regCloseMs) {
            Toast.makeText(this, R.string.event_create_err_reg_order, Toast.LENGTH_LONG).show();
            return;
        }
        if (regCloseMs - regOpenMs < REGISTRATION_MIN_GAP_MS) {
            Toast.makeText(this, R.string.event_create_err_reg_min_gap, Toast.LENGTH_LONG).show();
            return;
        }
        if (regOpenMs < minOpenMs) {
            Toast.makeText(this, R.string.event_create_err_reg_open_next_minute, Toast.LENGTH_LONG).show();
            return;
        }
        if (regCloseMs <= now) {
            Toast.makeText(this, R.string.event_create_err_reg_close_after_now, Toast.LENGTH_LONG).show();
            return;
        }
        if (regCloseMs > eventMs) {
            Toast.makeText(this, R.string.event_create_err_reg_before_event, Toast.LENGTH_LONG).show();
            return;
        }
        if (regOpenMs >= eventMs) {
            Toast.makeText(this, R.string.event_create_err_reg_before_event, Toast.LENGTH_LONG).show();
            return;
        }

        String descriptionRaw = etDescription != null && etDescription.getText() != null
                ? etDescription.getText().toString().trim() : "";
        String description = descriptionRaw.isEmpty() ? null : descriptionRaw;
        String criteriaRaw = etSelectionCriteria != null && etSelectionCriteria.getText() != null
                ? etSelectionCriteria.getText().toString().trim() : "";
        String criteria = criteriaRaw.isEmpty() ? null : criteriaRaw;

        int capacity;
        try {
            capacity = parseCapacityWithDefault(etCapacity != null ? etCapacity.getText().toString() : "");
        } catch (IllegalArgumentException e) {
            Toast.makeText(this, R.string.event_create_err_capacity, Toast.LENGTH_SHORT).show();
            return;
        }

        String priceStr;
        try {
            priceStr = parsePrice(etPrice != null ? etPrice.getText().toString() : "");
        } catch (IllegalArgumentException e) {
            Toast.makeText(this, R.string.event_create_err_price, Toast.LENGTH_SHORT).show();
            return;
        }

        Timestamp eventTs = new Timestamp(new Date(eventMs));
        Timestamp regOpenTs = new Timestamp(new Date(regOpenMs));
        Timestamp regCloseTs = new Timestamp(new Date(regCloseMs));

        if (!NetworkConnectivity.hasValidatedInternet(this)) {
            Toast.makeText(this, R.string.event_create_no_internet, Toast.LENGTH_SHORT).show();
            return;
        }

        Double eventLat = null;
        Double eventLng = null;
        if (lockedLocationText != null && lockedLocationText.trim().equals(location)) {
            eventLat = pendingLocationLat;
            eventLng = pendingLocationLng;
        }

        String locTrim = location != null ? location.trim() : "";
        if (!locTrim.isEmpty() && !"TBD".equalsIgnoreCase(locTrim)) {
            if (eventLat == null || eventLng == null) {
                Toast.makeText(this, R.string.event_select_place_from_suggestions, Toast.LENGTH_LONG).show();
                return;
            }
        }

        finalizeCreateEvent(title, description, location, eventLat, eventLng, eventTs, regOpenTs, regCloseTs,
                priceStr, capacity, criteria, new ArrayList<>(selectedCategories));
    }

    /** Empty or "unlimited" → 0 (unlimited). Invalid non-empty input throws. */
    private int parseCapacityWithDefault(String raw) {
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

    private String parsePrice(String raw) {
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

    private void onPosterPicked(Uri uri) {
        if (uri == null) {
            return;
        }
        if (!validatePosterSize(uri)) {
            return;
        }
        pendingPosterUri = uri;
        if (uploadPlaceholder != null) {
            uploadPlaceholder.setVisibility(View.GONE);
        }
        if (ivEventImage != null) {
            ivEventImage.setVisibility(View.VISIBLE);
            Glide.with(this).load(uri).centerCrop().into(ivEventImage);
        }
    }

    private boolean validatePosterSize(Uri uri) {
        try (ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r")) {
            if (pfd == null) {
                Toast.makeText(this, R.string.event_create_poster_invalid, Toast.LENGTH_SHORT).show();
                return false;
            }
            long len = pfd.getStatSize();
            if (len <= 0) {
                Toast.makeText(this, R.string.event_create_poster_invalid, Toast.LENGTH_SHORT).show();
                return false;
            }
            if (len > MAX_POSTER_BYTES) {
                Toast.makeText(this, R.string.event_create_poster_too_large, Toast.LENGTH_LONG).show();
                return false;
            }
            return true;
        } catch (Exception e) {
            Toast.makeText(this, R.string.event_create_poster_invalid, Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    private void finalizeCreateEvent(String name, String description, String location,
                                     Double locationLat, Double locationLng,
                                     Timestamp eventDate, Timestamp registrationOpen, Timestamp registrationClose,
                                     String price, int capacity, String criteria,
                                     List<String> categories) {

        Event event = new Event(name, description, location, eventDate,
                registrationOpen, registrationClose);
        event.setLocationLatitude(locationLat);
        event.setLocationLongitude(locationLng);
        java.util.ArrayList<String> organizerIds = new java.util.ArrayList<>();
        organizerIds.add(deviceId);
        event.setOrganizers(organizerIds);
        boolean isPrivate = isPrivateVisibilitySelected();
        boolean createdWithoutQrBecausePrivate = isPrivate && generatedQrEventId != null;
        if (!isPrivate && generatedQrEventId != null) {
            event.setEventId(generatedQrEventId);
        }
        event.setPrice(price);
        event.setWaitingListCapacity(capacity);
        event.setCriteria(criteria == null || criteria.isEmpty() ? null : criteria);
        event.setCategory(categories != null ? categories : new ArrayList<>());
        event.setIsPrivate(isPrivate);
        event.setGeolocationRequired(switchGeolocationLock != null && switchGeolocationLock.isChecked());
        applyAgeGroupSelectionToEvent(event);

        setCreateInProgress(true);
        eventController.createEvent(event,
                eventId -> waitingListDB.createWaitlistContainerForEvent(eventId,
                        unused -> runOnUiThread(() -> {
                            event.setWaitingListId(eventId);
                            finishEventCreateWithQrAndOptionalPoster(event, eventId, isPrivate,
                                    createdWithoutQrBecausePrivate);
                        }),
                        err -> runOnUiThread(() -> {
                            Toast.makeText(this,
                                    getString(R.string.event_create_waitlist_container_failed, err.getMessage()),
                                    Toast.LENGTH_LONG).show();
                            finishEventCreateWithQrAndOptionalPoster(event, eventId, isPrivate,
                                    createdWithoutQrBecausePrivate);
                        })),
                e -> runOnUiThread(() -> {
                    setCreateInProgress(false);
                    Toast.makeText(this, getString(R.string.event_create_failed, e.getMessage()),
                            Toast.LENGTH_SHORT).show();
                })
        );
    }

    private void setCreateInProgress(boolean inProgress) {
        if (loadingOverlayCreate != null) {
            loadingOverlayCreate.setVisibility(inProgress ? View.VISIBLE : View.GONE);
        }
        if (btnCreateEvent != null) {
            btnCreateEvent.setEnabled(!inProgress);
            btnCreateEvent.setAlpha(inProgress ? 0.5f : 1f);
        }
        if (blockBackWhileCreating != null) {
            blockBackWhileCreating.setEnabled(inProgress);
        }
    }

    /** After event + waitlists/{eventId} exist: set QR when allowed, upload poster if any, then update event. */
    private void finishEventCreateWithQrAndOptionalPoster(Event event, String eventId,
                                                          boolean isPrivate,
                                                          boolean createdWithoutQrBecausePrivate) {
        event.setQrCodeData(isPrivate ? null : qrCodeController.generateQRCodeData(eventId));
        if (pendingPosterUri != null) {
            imageController.uploadPoster(pendingPosterUri, event,
                    unused -> runOnUiThread(() -> {
                        setCreateInProgress(false);
                        Toast.makeText(this,
                                createdWithoutQrBecausePrivate
                                        ? R.string.event_create_private_no_qr_created
                                        : R.string.event_create_success,
                                Toast.LENGTH_SHORT).show();
                        finish();
                    }),
                    e -> runOnUiThread(() -> {
                        Toast.makeText(this,
                                getString(R.string.event_create_poster_failed, e.getMessage()),
                                Toast.LENGTH_LONG).show();
                        eventController.updateEvent(event,
                                unused2 -> runOnUiThread(() -> {
                                    setCreateInProgress(false);
                                    Toast.makeText(this,
                                            createdWithoutQrBecausePrivate
                                                    ? R.string.event_create_private_no_qr_created
                                                    : R.string.event_create_qr_only,
                                            Toast.LENGTH_SHORT).show();
                                    finish();
                                }),
                                e2 -> runOnUiThread(() -> {
                                    setCreateInProgress(false);
                                    Toast.makeText(this,
                                            getString(R.string.event_create_update_failed, e2.getMessage()),
                                            Toast.LENGTH_SHORT).show();
                                }));
                    }));
        } else {
            eventController.updateEvent(event,
                    unused -> runOnUiThread(() -> {
                        setCreateInProgress(false);
                        Toast.makeText(this,
                                createdWithoutQrBecausePrivate
                                        ? R.string.event_create_private_no_qr_created
                                        : R.string.event_create_success,
                                Toast.LENGTH_SHORT).show();
                        finish();
                    }),
                    e -> runOnUiThread(() -> {
                        setCreateInProgress(false);
                        Toast.makeText(this,
                                getString(R.string.event_create_update_failed, e.getMessage()),
                                Toast.LENGTH_SHORT).show();
                    }));
        }
    }
}
