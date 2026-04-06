package com.example.cobaltevents.util;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.widget.Toast;

import com.example.cobaltevents.R;
import com.example.cobaltevents.db.WaitingListDB;
import com.google.firebase.firestore.FirebaseFirestoreException;

/**
 * User-facing handling when an event no longer exists (stale list / QR / comments).
 */
public final class EventGoneUi {

    private EventGoneUi() {}

    public static void toast(Context ctx) {
        if (ctx == null) {
            return;
        }
        Toast.makeText(ctx, R.string.event_has_been_deleted, Toast.LENGTH_LONG).show();
    }

    public static Activity findActivity(Context ctx) {
        if (ctx == null) {
            return null;
        }
        if (ctx instanceof Activity) {
            return (Activity) ctx;
        }
        if (ctx instanceof ContextWrapper) {
            return findActivity(((ContextWrapper) ctx).getBaseContext());
        }
        return null;
    }

    public static void runOnUi(Context ctx, Runnable r) {
        if (r == null) {
            return;
        }
        Activity a = findActivity(ctx);
        if (a != null) {
            a.runOnUiThread(r);
        } else {
            r.run();
        }
    }

    public static boolean isFirestoreNotFound(Throwable t) {
        while (t != null) {
            if (t instanceof FirebaseFirestoreException) {
                return ((FirebaseFirestoreException) t).getCode() == FirebaseFirestoreException.Code.NOT_FOUND;
            }
            t = t.getCause();
        }
        return false;
    }

    public static boolean isEventDeletedReason(Throwable t) {
        while (t != null) {
            if (WaitingListDB.REASON_EVENT_DELETED.equals(t.getMessage())) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }
}
