package com.example.cobaltevents.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.cobaltevents.R;

import java.math.BigDecimal;

/**
 * Edit event price (empty = free / zero).
 */
public class EditPriceActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_price);

        String initial = getIntent().getStringExtra(EditCapacityPriceContract.EXTRA_PRICE_RAW);
        if (initial == null) {
            initial = "";
        }

        EditText et = findViewById(R.id.edit_price_et);
        et.setText(initial);
        et.setSelection(et.getText() != null ? et.getText().length() : 0);

        ImageButton btnClose = findViewById(R.id.edit_price_btn_close);
        ImageButton btnSave = findViewById(R.id.edit_price_btn_save);
        btnClose.setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> save(et));
    }

    private void save(EditText et) {
        String raw = et.getText() != null ? et.getText().toString().trim() : "";
        if (!raw.isEmpty()) {
            try {
                BigDecimal bd = new BigDecimal(raw);
                if (bd.compareTo(BigDecimal.ZERO) < 0) {
                    Toast.makeText(this, R.string.event_create_err_price, Toast.LENGTH_SHORT).show();
                    return;
                }
            } catch (Exception e) {
                Toast.makeText(this, R.string.event_create_err_price, Toast.LENGTH_SHORT).show();
                return;
            }
        }
        Intent data = new Intent();
        data.putExtra(EditResultKinds.EXTRA_KIND, EditResultKinds.KIND_PRICE);
        data.putExtra(EditCapacityPriceContract.RESULT_PRICE_RAW, raw);
        setResult(RESULT_OK, data);
        finish();
    }
}
