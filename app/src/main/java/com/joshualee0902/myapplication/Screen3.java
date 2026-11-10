package com.joshualee0902.myapplication;

import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;

/** Autonomous period. */
public class Screen3 extends FlowActivity {

    private CheckBox crossed;
    private CounterView upper;
    private CounterView lower;

    @Override
    protected int layoutId() {
        return R.layout.activity_screen3;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        crossed = findViewById(R.id.checkCrossedLine);
        upper = findViewById(R.id.AutoUpper);
        lower = findViewById(R.id.AutoLower);
        ((Button) findViewById(R.id.next2)).setOnClickListener(v -> goNext(Screen4.class));
        ((Button) findViewById(R.id.back1)).setOnClickListener(v -> goBack());
        start();
    }

    @Override
    protected void readInputs() {
        record.crossLine = crossed.isChecked();
        record.autoUpper = upper.getValue();
        record.autoLower = lower.getValue();
    }

    @Override
    protected void populate() {
        crossed.setChecked(record.crossLine);
        upper.setValue(record.autoUpper);
        lower.setValue(record.autoLower);
    }
}
