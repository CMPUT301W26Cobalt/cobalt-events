package com.example.cobaltevents.ui;

/**
 * Intent extras and field keys for {@link EditFieldActivity}, mirroring the web
 * {@code /edit-field?field=&value=} flow.
 */
public final class EditFieldContract {

    public static final String EXTRA_FIELD = "field";
    public static final String EXTRA_VALUE = "value";

    public static final String RESULT_FIELD = "field";
    public static final String RESULT_VALUE = "value";

    public static final String FIELD_TITLE = "title";
    public static final String FIELD_DESCRIPTION = "description";
    public static final String FIELD_LOCATION = "location";
    public static final String FIELD_CRITERIA = "criteria";
    public static final String FIELD_CATEGORIES = "categories";

    private EditFieldContract() {}
}
