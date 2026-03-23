package com.example.cobaltevents.ui;

/**
 * Dispatches {@link android.app.Activity#setResult} payloads from edit sub-screens back to
 * {@link EditEventDialog}.
 */
public final class EditResultKinds {
    public static final String EXTRA_KIND = "result_kind";

    public static final String KIND_TEXT_FIELD = "text_field";
    public static final String KIND_DATETIME = "datetime";
    public static final String KIND_CAPACITY = "capacity";
    public static final String KIND_PRICE = "price";
    public static final String KIND_RADIO = "radio";
    public static final String KIND_LOCATION = "location";
    public static final String KIND_CATEGORIES = "categories";

    private EditResultKinds() {}
}
