package com.example.cobaltevents.controller;


import com.example.cobaltevents.db.EntrantDB;
import com.example.cobaltevents.db.ProfileDB;
import com.example.cobaltevents.model.Entrant;

public class EntrantController {

    private EntrantDB localDB;
    private ProfileDB remoteDB;

    public EntrantController(EntrantDB localDB) {
        this.localDB = localDB;
        this.remoteDB = new ProfileDB();
    }

    public String validateName(String name) {
        if (name == null || name.trim().isEmpty())
            return "Name is required.";
        return null;
    }

    public String validateEmail(String email) {
        if (email == null || email.trim().isEmpty())
            return "Email is required.";
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches())
            return "Invalid email format.";
        return null;
    }

    public String validatePhone(String phone) {
        if (phone == null || phone.trim().isEmpty())
            return null; // optional
        if (!phone.matches("^[0-9+()\\-\\s]{7,20}$"))
            return "Invalid phone number.";
        return null;
    }

    public boolean saveEntrant(Entrant entrant) {
        if (!entrant.isValid()) return false;
        localDB.saveEntrant(entrant);
        remoteDB.saveProfile(entrant, unused -> {}, e -> {});
            
        return true;
    }
}
