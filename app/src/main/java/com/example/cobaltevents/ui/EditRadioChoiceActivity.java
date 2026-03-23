package com.example.cobaltevents.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.cobaltevents.R;

/**
 * Single-choice screen for visibility or age group (radio list).
 */
public class EditRadioChoiceActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_radio_choice);

        String field = getIntent().getStringExtra(EditRadioChoiceContract.EXTRA_FIELD);
        String current = getIntent().getStringExtra(EditRadioChoiceContract.EXTRA_CURRENT_VALUE);
        if (current == null) {
            current = "";
        }

        TextView tvTitle = findViewById(R.id.edit_radio_tv_title);
        TextView tvHelper = findViewById(R.id.edit_radio_tv_helper);
        RadioGroup group = findViewById(R.id.edit_radio_group);
        ImageButton btnClose = findViewById(R.id.edit_radio_btn_close);
        ImageButton btnSave = findViewById(R.id.edit_radio_btn_save);

        if (EditRadioChoiceContract.FIELD_VISIBILITY.equals(field)) {
            tvTitle.setText(R.string.event_create_visibility);
            tvHelper.setText(R.string.edit_radio_visibility_helper);
            tvHelper.setVisibility(View.VISIBLE);
            populateGroup(group, R.array.event_visibility_options, current);
        } else if (EditRadioChoiceContract.FIELD_AGE.equals(field)) {
            tvTitle.setText(R.string.event_create_age_group);
            tvHelper.setText(R.string.edit_radio_age_helper);
            tvHelper.setVisibility(View.VISIBLE);
            populateGroup(group, R.array.event_age_group_options, current);
        } else {
            Toast.makeText(this, R.string.edit_field_not_found, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        btnClose.setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> save(field, group));
    }

    private void populateGroup(RadioGroup group, int arrayRes, String current) {
        String[] opts = getResources().getStringArray(arrayRes);
        int padH = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16f, getResources().getDisplayMetrics());
        int padV = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 14f, getResources().getDisplayMetrics());
        int checkedId = View.NO_ID;
        String cur = current.trim();
        for (int i = 0; i < opts.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(opts[i]);
            rb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
            rb.setTextColor(ContextCompat.getColor(this, R.color.edit_event_modal_title));
            rb.setPadding(padH, padV, padH, padV);
            rb.setMinHeight((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48f, getResources().getDisplayMetrics()));
            int id = View.generateViewId();
            rb.setId(id);
            group.addView(rb);
            if (opts[i].equalsIgnoreCase(cur)) {
                checkedId = id;
            }
        }
        if (checkedId != View.NO_ID) {
            group.check(checkedId);
        } else if (group.getChildCount() > 0) {
            group.check(group.getChildAt(0).getId());
        }
    }

    private void save(String field, RadioGroup group) {
        int id = group.getCheckedRadioButtonId();
        if (id == View.NO_ID) {
            Toast.makeText(this, R.string.event_create_err_required_fields, Toast.LENGTH_SHORT).show();
            return;
        }
        RadioButton rb = group.findViewById(id);
        if (rb == null) {
            return;
        }
        Intent data = new Intent();
        data.putExtra(EditResultKinds.EXTRA_KIND, EditResultKinds.KIND_RADIO);
        data.putExtra(EditRadioChoiceContract.RESULT_FIELD, field);
        data.putExtra(EditRadioChoiceContract.RESULT_VALUE, rb.getText().toString());
        setResult(RESULT_OK, data);
        finish();
    }
}
