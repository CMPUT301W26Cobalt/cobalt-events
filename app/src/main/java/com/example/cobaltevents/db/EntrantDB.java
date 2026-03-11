package com.example.cobaltevents.db;
import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import com.example.cobaltevents.model.Entrant;

public class EntrantDB {

    private SharedPreferences prefs;
    private String deviceId;

    public EntrantDB(Context context) {
        prefs = context.getSharedPreferences("EntrantProfile", Context.MODE_PRIVATE);
        deviceId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
    }

    public void saveEntrant(Entrant entrant) {
        entrant.setDeviceId(deviceId);
        prefs.edit()
                .putString("name", entrant.getName())
                .putString("email", entrant.getEmail())
                .putString("phone", entrant.getPhone())
                .putString("profilePictureUrl", entrant.getProfilePictureUrl())
                .apply();
    }

    public Entrant getEntrant() {
        String name = prefs.getString("name", "");
        String email = prefs.getString("email", "");
        String phone = prefs.getString("phone", "");
        String profilePictureUrl = prefs.getString("profilePictureUrl", null);
        return new Entrant(deviceId, name, email, phone, profilePictureUrl);
    }
}