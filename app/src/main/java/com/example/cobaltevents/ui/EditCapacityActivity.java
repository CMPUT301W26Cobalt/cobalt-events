package com.example.cobaltevents.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.cobaltevents.R;

/**
 * Edit waiting list capacity (blank or "Unlimited" = unlimited; otherwise a positive integer).
 */
public class EditCapacityActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_capacity);

        String initial = getIntent().getStringExtra(EditCapacityPriceContract.EXTRA_CAPACITY_RAW);
        if (initial == null) {
            initial = "";
        }

        EditText et = findViewById(R.id.edit_cap_et);
        et.setText(initial);
        et.setSelection(et.getText() != null ? et.getText().length() : 0);

        ImageButton btnClose = findViewById(R.id.edit_cap_btn_close);
        ImageButton btnSave = findViewById(R.id.edit_cap_btn_save);
        btnClose.setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> save(et));
    }

    private void save(EditText et) {
        String raw = et.getText() != null ? et.getText().toString().trim() : "";
        if (!raw.isEmpty() && !raw.equalsIgnoreCase("unlimited")) {
            try {
                int n = Integer.parseInt(raw);
                if (n < 1) {
                    Toast.makeText(this, R.string.event_create_err_capacity, Toast.LENGTH_SHORT).show();
                    return;
                }
            } catch (NumberFormatException e) {
                Toast.makeText(this, R.string.event_create_err_capacity, Toast.LENGTH_SHORT).show();
                return;
            }
        }
        Intent data = new Intent();
        data.putExtra(EditResultKinds.EXTRA_KIND, EditResultKinds.KIND_CAPACITY);
        data.putExtra(EditCapacityPriceContract.RESULT_CAPACITY_RAW, raw);
        setResult(RESULT_OK, data);
        finish();
    }
}
