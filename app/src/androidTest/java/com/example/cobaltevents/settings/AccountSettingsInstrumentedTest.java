package com.example.cobaltevents.settings;

import static org.junit.Assert.*;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.cobaltevents.db.EntrantDB;
import com.example.cobaltevents.db.ProfileDB;
import com.example.cobaltevents.model.Entrant;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Instrumented tests that cover "account settings"-like flows:
 * - Deleting account (ProfileDB.deleteProfile)
 * - Changing account info (EntrantDB.save + get roundtrip)
 *
 * Notes:
 * - Requires Firebase emulator or test project for remote DB operations
 * - Local EntrantDB uses SharedPreferences and runs on device/emulator
 */
@RunWith(AndroidJUnit4.class)
public class AccountSettingsInstrumentedTest {

    private ProfileDB profileDB;
    private EntrantDB entrantDB;
    private static final int TIMEOUT_SECONDS = 10;

    @Before
    public void setUp() {
        profileDB = new ProfileDB();
        Context context = ApplicationProvider.getApplicationContext();
        entrantDB = new EntrantDB(context);
    }

    @Test
    public void deleteAccount_remote_succeeds() throws InterruptedException {
        String deviceId = "del_device_" + UUID.randomUUID();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean ok = new AtomicBoolean(false);

        profileDB.deleteProfile(deviceId,
                v -> { ok.set(true); latch.countDown(); },
                e -> latch.countDown());

        assertTrue(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertTrue(ok.get());
    }

    @Test
    public void changeAccountInfo_localRoundtrip_succeeds() {
        Entrant e = new Entrant(null, "Alice Doe", "alice@example.com", "5551234567", null);
        assertTrue(entrantDB.getEntrant() != null); // ensure instance usable

        entrantDB.saveEntrant(e);
        Entrant loaded = entrantDB.getEntrant();
        assertEquals("Alice Doe", loaded.getName());
        assertEquals("alice@example.com", loaded.getEmail());
        assertEquals("5551234567", loaded.getPhone());

        entrantDB.clearEntrant();
        Entrant cleared = entrantDB.getEntrant();
        // After clear, defaults may be empty strings per EntrantDB.getEntrant()
        assertNotNull(cleared);
    }
}

