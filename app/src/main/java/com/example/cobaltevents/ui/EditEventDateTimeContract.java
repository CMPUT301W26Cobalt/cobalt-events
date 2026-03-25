package com.example.cobaltevents.ui;

/**
 * Intent extras for {@link EditEventDateTimeActivity}.
 */
public final class EditEventDateTimeContract {

    public static final String EXTRA_MODE = "mode";
    /** Edit event date & time */
    public static final int MODE_EVENT = 1;
    /** Registration opens */
    public static final int MODE_REG_OPEN = 2;
    /** Registration closes */
    public static final int MODE_REG_CLOSE = 3;

    /** -1 = no value yet */
    public static final String EXTRA_INITIAL_MS = "initial_ms";
    public static final String EXTRA_HAS_TIME = "has_time";

    /** End of event day (ms), or -1 if event not set — caps registration date pickers */
    public static final String EXTRA_EVENT_DAY_END_MS = "event_day_end_ms";
    /** For {@link #MODE_REG_CLOSE}: registration open instant, or -1 */
    public static final String EXTRA_REG_OPEN_MS = "reg_open_ms";
    public static final String EXTRA_HAS_REG_OPEN_TIME = "has_reg_open_time";

    public static final String RESULT_MODE = "result_mode";
    public static final String RESULT_TIME_MS = "result_time_ms";
    public static final String RESULT_HAS_TIME = "result_has_time";

    private EditEventDateTimeContract() {}
}
