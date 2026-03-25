package com.example.cobaltevents.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.cobaltevents.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Full-screen category editor: add field + removable chips (same pattern as {@link EventCreateActivity}).
 */
public class EditCategoriesActivity extends AppCompatActivity {

    private final List<String> categories = new ArrayList<>();

    private EditText etAdd;
    private HorizontalScrollView scrollChips;
    private LinearLayout layoutChips;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_categories);

        ArrayList<String> initial = getIntent().getStringArrayListExtra(EditCategoriesContract.EXTRA_CATEGORIES);
        if (initial != null) {
            for (String s : initial) {
                if (s != null) {
                    String t = s.trim();
                    if (!t.isEmpty()) {
                        categories.add(t);
                    }
                }
            }
        }

        etAdd = findViewById(R.id.edit_cat_et_add);
        scrollChips = findViewById(R.id.edit_cat_scroll_chips);
        layoutChips = findViewById(R.id.edit_cat_layout_chips);
        ImageButton btnClose = findViewById(R.id.edit_cat_btn_close);
        ImageButton btnSaveHeader = findViewById(R.id.edit_cat_btn_save);
        TextView btnAdd = findViewById(R.id.edit_cat_btn_add);

        btnClose.setOnClickListener(v -> finish());
        btnSaveHeader.setOnClickListener(v -> save());
        btnAdd.setOnClickListener(v -> addFromInput());
        etAdd.setOnEditorActionListener((v, actionId, event) -> {
            boolean done = actionId == EditorInfo.IME_ACTION_DONE
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN);
            if (done) {
                addFromInput();
                return true;
            }
            return false;
        });

        renderChips();
    }

    private void addFromInput() {
        if (etAdd == null) {
            return;
        }
        String raw = etAdd.getText() != null ? etAdd.getText().toString().trim() : "";
        if (raw.isEmpty()) {
            return;
        }
        if (!containsIgnoreCase(raw)) {
            categories.add(raw);
            renderChips();
        }
        etAdd.setText("");
    }

    private boolean containsIgnoreCase(String candidate) {
        for (String existing : categories) {
            if (existing != null && existing.equalsIgnoreCase(candidate)) {
                return true;
            }
        }
        return false;
    }

    private void renderChips() {
        if (layoutChips == null || scrollChips == null) {
            return;
        }
        layoutChips.removeAllViews();
        if (categories.isEmpty()) {
            scrollChips.setVisibility(android.view.View.GONE);
            return;
        }
        scrollChips.setVisibility(android.view.View.VISIBLE);
        for (String cat : categories) {
            layoutChips.addView(createChip(cat));
        }
    }

    private LinearLayout createChip(String label) {
        LinearLayout chip = new LinearLayout(this);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(android.view.Gravity.CENTER_VERTICAL);
        chip.setBackgroundResource(R.drawable.bg_keyword_chip_white);
        int hPad = dpToPx(12);
        int vPad = dpToPx(6);
        chip.setPadding(hPad, vPad, hPad, vPad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(dpToPx(8));
        chip.setLayoutParams(lp);

        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(ContextCompat.getColor(this, R.color.organizer_blue));
        tv.setTextSize(14f);

        TextView remove = new TextView(this);
        remove.setText("×");
        remove.setTextColor(ContextCompat.getColor(this, R.color.organizer_blue));
        remove.setTextSize(14f);
        remove.setPadding(dpToPx(8), 0, 0, 0);
        remove.setOnClickListener(v -> {
            categories.remove(label);
            renderChips();
        });
        chip.addView(tv);
        chip.addView(remove);
        return chip;
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void save() {
        Intent data = new Intent();
        data.putExtra(EditResultKinds.EXTRA_KIND, EditResultKinds.KIND_CATEGORIES);
        data.putStringArrayListExtra(EditCategoriesContract.RESULT_CATEGORIES, new ArrayList<>(categories));
        setResult(RESULT_OK, data);
        finish();
    }
}
