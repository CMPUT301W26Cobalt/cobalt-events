package com.example.cobaltevents.model;

import java.util.Date;

/**
 * Data model for an event stored in the Firestore "events" collection.
 * Notifications reference events so users can tap a notification to see event details (US 01.04.01 criteria 4).
 */
public class Event {

    private String eventId;
    private String title;
    private String description;
    private Date date;
    private String organizerId;
    private int maxEntrants;
    private boolean lotteryDrawn;

    /** No-arg constructor required by Firestore deserialization. */
    public Event() {}

    public Event(String title, String description, Date date, String organizerId, int maxEntrants) {
        this.title = title;
        this.description = description;
        this.date = date;
        this.organizerId = organizerId;
        this.maxEntrants = maxEntrants;
        this.lotteryDrawn = false;
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Date getDate() { return date; }
    public void setDate(Date date) { this.date = date; }

    public String getOrganizerId() { return organizerId; }
    public void setOrganizerId(String organizerId) { this.organizerId = organizerId; }

    public int getMaxEntrants() { return maxEntrants; }
    public void setMaxEntrants(int maxEntrants) { this.maxEntrants = maxEntrants; }

    public boolean isLotteryDrawn() { return lotteryDrawn; }
    public void setLotteryDrawn(boolean lotteryDrawn) { this.lotteryDrawn = lotteryDrawn; }
}
