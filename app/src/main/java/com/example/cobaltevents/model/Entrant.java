package com.example.cobaltevents.model;

import android.util.Patterns;

public class Entrant {

    private String name;
    private String email;
    private String phone; // this is optional so hint feature in XML is utilized

    public Entrant(String name, String email, String phone) {
        this.name = name;
        this.email = email;
        this.phone = phone;
    }

    public boolean isValidName() {
        return name != null && !name.trim().isEmpty();
    }

    public boolean isValidEmail() {
        return email != null &&
                !email.trim().isEmpty() &&
                Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    public boolean isValidPhone() {
        if (phone == null || phone.trim().isEmpty()) return true; // optional
        return phone.matches("^[0-9+()\\-\\s]{7,20}$");
    }

    public boolean isValid() {
        return isValidName() && isValidEmail() && isValidPhone();
    }

    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
}