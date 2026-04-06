package com.example.cobaltevents.ui.admin;

/**
 * US 03.09.01 (and gatekeeping): fixed admin device id for role switch / admin entry.
 *
 * <p>Single place to change the allowed admin Android ID. To discover a device id, run the app and
 * check Logcat for the {@code ADMIN_DEVICE_ID} tag ({@link com.example.cobaltevents.ui.EntrantActivity} logs it).
 */
public final class AdminConfig {

    /** Android {@link android.provider.Settings.Secure#ANDROID_ID} value treated as the admin device. */
    public static final String ADMIN_DEVICE_ID = "a744a227192835f5";

    private AdminConfig() {}
}