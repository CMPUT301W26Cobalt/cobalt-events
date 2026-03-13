package com.example.cobaltevents.model;

/**
 * Model class combining Event and WaitingList data for history display.
 * This class is used to associate an event with an entrant's registration status.
 */
public class EventHistory {
    
    private Event event;
    private WaitingList registration;
    
    /**
     * Constructs an EventHistory object.
     * @param event The event object.
     * @param registration The registration details from the waiting list.
     */
    public EventHistory(Event event, WaitingList registration) {
        this.event = event;
        this.registration = registration;
    }
    
    /** @return The associated event. */
    public Event getEvent() { return event; }
    /** @return The associated registration details. */
    public WaitingList getRegistration() { return registration; }
    
    /** @return The registration status as a string (e.g., "invited", "accepted"). */
    public String getStatus() {
        return registration != null ? registration.getStatus() : "unknown";
    }
}
