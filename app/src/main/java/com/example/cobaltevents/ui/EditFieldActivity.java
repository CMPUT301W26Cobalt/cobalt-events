package com.example.cobaltevents.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.cobaltevents.R;

import java.util.HashMap;
import java.util.Map;

/**
 * Secondary screen: edit a single string field (React {@code /edit-field} analogue).
 */
public class EditFieldActivity extends AppCompatActivity {

    private static final class FieldConfig {
        final int headerTitleRes;
        final int hintRes;
        @Nullable final Integer helperTextRes;
        final boolean multiline;
        final int imeOptions;
        final int inputTypeFlags;

        FieldConfig(int headerTitleRes, int hintRes, @Nullable Integer helperTextRes,
                    boolean multiline, int imeOptions, int inputTypeFlags) {
            this.headerTitleRes = headerTitleRes;
            this.hintRes = hintRes;
            this.helperTextRes = helperTextRes;
            this.multiline = multiline;
            this.imeOptions = imeOptions;
            this.inputTypeFlags = inputTypeFlags;
        }
    }

    private static final Map<String, FieldConfig> CONFIG = new HashMap<>();

    static {
        CONFIG.put(EditFieldContract.FIELD_TITLE, new FieldConfig(
                R.string.edit_event_label_title,
                R.string.edit_event_hint_title,
                null,
                false,
                EditorInfo.IME_ACTION_DONE,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES));
        CONFIG.put(EditFieldContract.FIELD_DESCRIPTION, new FieldConfig(
                R.string.edit_event_label_description,
                R.string.edit_event_hint_description,
                null,
                true,
                EditorInfo.IME_ACTION_NONE,
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                        | InputType.TYPE_TEXT_FLAG_MULTI_LINE));
        CONFIG.put(EditFieldContract.FIELD_CRITERIA, new FieldConfig(
                R.string.edit_event_label_criteria,
                R.string.edit_event_hint_criteria,
                null,
                true,
                EditorInfo.IME_ACTION_NONE,
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                        | InputType.TYPE_TEXT_FLAG_MULTI_LINE));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_field);

        String field = getIntent().getStringExtra(EditFieldContract.EXTRA_FIELD);
        String initial = getIntent().getStringExtra(EditFieldContract.EXTRA_VALUE);
        if (initial == null) {
            initial = "";
        }

        TextView tvHeader = findViewById(R.id.edit_field_tv_header_title);
        EditText et = findViewById(R.id.edit_field_et_value);
        TextView tvHelper = findViewById(R.id.edit_field_tv_helper);
        TextView tvNotFound = findViewById(R.id.edit_field_tv_not_found);
        ScrollView scroll = findViewById(R.id.edit_field_scroll);
        ImageButton btnClose = findViewById(R.id.edit_field_btn_close);
        ImageButton btnSave = findViewById(R.id.edit_field_btn_save);

        FieldConfig cfg = field != null ? CONFIG.get(field) : null;
        if (cfg == null) {
            tvNotFound.setVisibility(View.VISIBLE);
            scroll.setVisibility(View.GONE);
            btnSave.setVisibility(View.GONE);
            tvHeader.setText(R.string.edit_event_modal_title);
            btnClose.setOnClickListener(v -> finish());
            return;
        }

        tvHeader.setText(cfg.headerTitleRes);
        et.setHint(cfg.hintRes);
        et.setText(initial);
        et.setSelection(et.getText() != null ? et.getText().length() : 0);

        if (cfg.multiline) {
            et.setMinLines(5);
            et.setMaxLines(12);
            et.setSingleLine(false);
            et.setHorizontallyScrolling(false);
        } else {
            et.setMinHeight(0);
            et.setMinLines(1);
            et.setSingleLine(true);
        }
        et.setImeOptions(cfg.imeOptions);
        et.setInputType(cfg.inputTypeFlags);

        if (cfg.helperTextRes != null) {
            tvHelper.setText(cfg.helperTextRes);
            tvHelper.setVisibility(View.VISIBLE);
        } else {
            tvHelper.setVisibility(View.GONE);
        }

        btnClose.setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> returnResult(field, et));
        et.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE && !cfg.multiline) {
                returnResult(field, et);
                return true;
            }
            return false;
        });
    }

    private void returnResult(@NonNull String field, @NonNull EditText et) {
        String value = et.getText() != null ? et.getText().toString() : "";
        Intent data = new Intent();
        data.putExtra(EditResultKinds.EXTRA_KIND, EditResultKinds.KIND_TEXT_FIELD);
        data.putExtra(EditFieldContract.RESULT_FIELD, field);
        data.putExtra(EditFieldContract.RESULT_VALUE, value);
        setResult(RESULT_OK, data);
        finish();
    }
}
