package com.example.cobaltevents.ui;

/**
 * Extras for {@link EditCategoriesActivity} (chip list, same UX as create event).
 */
public final class EditCategoriesContract {

    public static final String EXTRA_CATEGORIES = "categories";
    /** Distinct from {@link EditResultKinds#KIND_CATEGORIES} string value to avoid Intent key clash. */
    public static final String RESULT_CATEGORIES = "result_categories";

    private EditCategoriesContract() {}
}
