package com.example.cobaltevents.controller;

import com.example.cobaltevents.db.EventDB;
import com.example.cobaltevents.model.Event;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import java.util.List;

/**
 * Controller that mediates between Event UI activities and the database layer.
 */
public class EventController {

    private final EventDB eventDB;

    public EventController() {
        this.eventDB = new EventDB();
    }

    /**
     * US 2.01.01: Creates a new event in the database
     */
    public void createEvent(Event event,
                            OnSuccessListener<String> onSuccess,
                            OnFailureListener onFailure) {
        eventDB.createEvent(event, onSuccess, onFailure);
    }

    public void getAllEvents(OnSuccessListener<List<Event>> onSuccess,
                             OnFailureListener onFailure) {
        eventDB.getAllEvents(onSuccess, onFailure);
    }

    public void getEvent(String eventId,
                         OnSuccessListener<Event> onSuccess,
                         OnFailureListener onFailure) {
        eventDB.getEvent(eventId, onSuccess, onFailure);
    }

    public void getEventsByOrganizer(String organizerDeviceId,
                                     OnSuccessListener<List<Event>> onSuccess,
                                     OnFailureListener onFailure) {
        eventDB.getEventsByOrganizer(organizerDeviceId, onSuccess, onFailure);
    }

    public void updateEvent(Event event,
                            OnSuccessListener<Void> onSuccess,
                            OnFailureListener onFailure) {
        eventDB.updateEvent(event, onSuccess, onFailure);
    }

    public void deleteEvent(String eventId,
                            OnSuccessListener<Void> onSuccess,
                            OnFailureListener onFailure) {
        eventDB.deleteEvent(eventId, onSuccess, onFailure);
    }
}
