package com.example.cobaltevents.db;
import android.content.Context;
import android.content.SharedPreferences;
import com.example.cobaltevents.model.Entrant;

public class EntrantDB {

    private SharedPreferences prefs;

    public EntrantDB(Context context) {
        prefs = context.getSharedPreferences("EntrantProfile", Context.MODE_PRIVATE);
    }

    public void saveEntrant(Entrant entrant) {
        prefs.edit()
                .putString("name", entrant.getName())
                .putString("email", entrant.getEmail())
                .putString("phone", entrant.getPhone())
                .apply();
    }

    public Entrant getEntrant() {
        String name = prefs.getString("name", "");
        String email = prefs.getString("email", "");
        String phone = prefs.getString("phone", "");
        return new Entrant(name, email, phone);
    }
}
