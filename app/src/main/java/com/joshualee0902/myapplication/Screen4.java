package com.joshualee0902.myapplication;

import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.RadioGroup;

/** TeleOp period, wheel, climb, fouls and defense. */
public class Screen4 extends FlowActivity {

    private static final int[] DEFENSE_IDS = {R.id.defense0, R.id.defense1, R.id.defense2, R.id.defense3};

    private CounterView upper;
    private CounterView lower;
    private CounterView fouls;
    private CheckBox spin;
    private CheckBox color;
    private CheckBox climb;
    private CheckBox defended;
    private RadioGroup defenseGroup;

    @Override
    protected int layoutId() {
        return R.layout.activity_screen4;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        upper = findViewById(R.id.TeleUpper);
        lower = findViewById(R.id.TeleLower);
        fouls = findViewById(R.id.Fouls);
        spin = findViewById(R.id.checkWheelSpin);
        color = findViewById(R.id.checkWheelColor);
        climb = findViewById(R.id.checkClimb);
        defended = findViewById(R.id.checkDefended);
        defenseGroup = findViewById(R.id.defenseGroup);
        ((Button) findViewById(R.id.next3)).setOnClickListener(v -> goNext(Screen5.class));
        ((Button) findViewById(R.id.back2)).setOnClickListener(v -> goBack());
        start();
    }

    @Override
    protected void readInputs() {
        record.teleUpper = upper.getValue();
        record.teleLower = lower.getValue();
        record.fouls = fouls.getValue();
        record.wheel = MatchRecord.wheelFromChecks(spin.isChecked(), color.isChecked());
        record.climbed = climb.isChecked();
        record.defendedAgainst = defended.isChecked();
        int rating = 0;
        for (int i = 0; i < DEFENSE_IDS.length; i++) {
            if (defenseGroup.getCheckedRadioButtonId() == DEFENSE_IDS[i]) rating = i;
        }
        record.defenseRating = rating;
    }

    @Override
    protected void populate() {
        upper.setValue(record.teleUpper);
        lower.setValue(record.teleLower);
        fouls.setValue(record.fouls);
        spin.setChecked((record.wheel & MatchRecord.WHEEL_SPIN) != 0);
        color.setChecked((record.wheel & MatchRecord.WHEEL_COLOR) != 0);
        climb.setChecked(record.climbed);
        defended.setChecked(record.defendedAgainst);
        int idx = Math.max(0, Math.min(MatchRecord.MAX_DEFENSE, record.defenseRating));
        defenseGroup.check(DEFENSE_IDS[idx]);
    }
}
