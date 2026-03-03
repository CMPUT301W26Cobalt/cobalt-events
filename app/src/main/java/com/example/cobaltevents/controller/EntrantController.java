package com.example.cobaltevents.controller;


import com.example.cobaltevents.db.EntrantDB;
import com.example.cobaltevents.model.Entrant;

public class EntrantController {

    private EntrantDB entrantDB;

    public EntrantController(EntrantDB entrantDB) {
        this.entrantDB = entrantDB;
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

    public boolean saveEntrant(String name, String email, String phone) {

        Entrant entrant = new Entrant(name.trim(), email.trim(), phone.trim());

        if (!entrant.isValid()) return false;

        entrantDB.saveEntrant(entrant);
        return true;
    }
}