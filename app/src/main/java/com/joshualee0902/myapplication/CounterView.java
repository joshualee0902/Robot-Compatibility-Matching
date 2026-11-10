package com.joshualee0902.myapplication;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Minimal -/value/+ counter (replaces the discontinued ElegantNumberButton library). Never goes below 0. */
public class CounterView extends LinearLayout {
    private int value;
    private final TextView valueView;

    public CounterView(Context context) {
        this(context, null);
    }

    public CounterView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);

        Button minus = new Button(context);
        minus.setText("−");
        minus.setContentDescription(context.getString(R.string.decrease));
        valueView = new TextView(context);
        valueView.setTextSize(24);
        valueView.setGravity(Gravity.CENTER);
        valueView.setMinWidth((int) (56 * context.getResources().getDisplayMetrics().density));
        Button plus = new Button(context);
        plus.setText("+");
        plus.setContentDescription(context.getString(R.string.increase));

        addView(minus);
        addView(valueView);
        addView(plus);

        minus.setOnClickListener(v -> setValue(value - 1));
        plus.setOnClickListener(v -> setValue(value + 1));
        setValue(0);
    }

    public int getValue() {
        return value;
    }

    public void setValue(int v) {
        value = Math.max(0, v);
        valueView.setText(String.valueOf(value));
    }
}
