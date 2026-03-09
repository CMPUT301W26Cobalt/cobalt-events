package com.example.cobaltevents.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.cobaltevents.R;
import com.example.cobaltevents.controller.ImageController;
import com.example.cobaltevents.model.Event;

/**
 * Allows organizers to manage their events.
 * This activity provides tools for organizers to upload or update
 * event posters and perform other event management actions.
 */
public class EventManageActivity extends AppCompatActivity {

    private static final int PICK_IMAGE = 100;

    private ImageController imageController;
    private Event event;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_manage);

        imageController = new ImageController();

        Button uploadPosterButton = findViewById(R.id.btn_upload_poster);

        uploadPosterButton.setOnClickListener(v -> openImagePicker());
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        startActivityForResult(intent, PICK_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE && resultCode == RESULT_OK && data != null) {

            Uri imageUri = data.getData();

            imageController.uploadPoster(imageUri, event,
                    unused -> Toast.makeText(this, "Poster uploaded!", Toast.LENGTH_SHORT).show(),
                    e -> Toast.makeText(this, "Upload failed", Toast.LENGTH_SHORT).show());
        }
    }
}