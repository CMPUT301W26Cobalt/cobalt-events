package com.example.cobaltevents.model;

/**
 * Combines Event and WaitingList data for history display
 */
public class EventHistory {
    
    private Event event;
    private WaitingList registration;
    
    public EventHistory(Event event, WaitingList registration) {
        this.event = event;
        this.registration = registration;
    }
    
    public Event getEvent() { return event; }
    public WaitingList getRegistration() { return registration; }
    
    public String getStatus() {
        return registration != null ? registration.getStatus() : "unknown";
    }
}
