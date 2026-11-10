package com.joshualee0902.myapplication;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

/**
 * Base class for the scouting screens. One {@link MatchRecord} travels through the flow:
 * - Next: the screen writes its inputs into the record and launches the next screen with it.
 * - Back (button or system back): the screen writes its inputs and returns the record to the
 *   previous screen, which keeps it. Re-opening a screen repopulates it from the record,
 *   so back-navigation never loses data.
 * - The record is also saved in onSaveInstanceState, so rotation and process death are safe.
 */
public abstract class FlowActivity extends AppCompatActivity {

    static final String EXTRA_RECORD = "record";
    /** Result code meaning the whole flow finished (saved or home); every screen closes. */
    static final int RESULT_FLOW_DONE = RESULT_FIRST_USER;

    protected MatchRecord record;
    private ActivityResultLauncher<Intent> launcher;

    @LayoutRes
    protected abstract int layoutId();

    /** Copy the screen's views into {@link #record}. */
    protected abstract void readInputs();

    /** Copy {@link #record} into the screen's views. */
    protected abstract void populate();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(layoutId());

        MatchRecord restored = null;
        if (savedInstanceState != null) {
            restored = (MatchRecord) savedInstanceState.getSerializable(EXTRA_RECORD);
        }
        if (restored == null) {
            restored = (MatchRecord) getIntent().getSerializableExtra(EXTRA_RECORD);
        }
        record = restored != null ? restored : new MatchRecord();

        launcher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_FLOW_DONE) {
                setResult(RESULT_FLOW_DONE);
                finish();
            } else if (result.getData() != null) {
                MatchRecord back = (MatchRecord) result.getData().getSerializableExtra(EXTRA_RECORD);
                if (back != null) {
                    record = back;
                    populate();
                }
            }
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                goBack();
            }
        });
    }

    /** Subclasses call this at the end of their onCreate, once views are bound. */
    protected void start() {
        populate();
    }

    protected void goNext(Class<?> target) {
        readInputs();
        launcher.launch(new Intent(this, target).putExtra(EXTRA_RECORD, record));
    }

    protected void goBack() {
        readInputs();
        setResult(RESULT_OK, new Intent().putExtra(EXTRA_RECORD, record));
        finish();
    }

    protected void finishFlow() {
        setResult(RESULT_FLOW_DONE);
        finish();
    }

    protected static String joinErrors(List<String> errors) {
        return String.join("\n", errors);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        readInputs();
        outState.putSerializable(EXTRA_RECORD, record);
        super.onSaveInstanceState(outState);
    }
}
