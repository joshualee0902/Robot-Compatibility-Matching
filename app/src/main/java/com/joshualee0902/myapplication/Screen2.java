package com.joshualee0902.myapplication;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.util.List;

/** Pre-match: team and match number (both required). */
public class Screen2 extends FlowActivity {

    private EditText teamNum;
    private EditText matchNum;

    @Override
    protected int layoutId() {
        return R.layout.activity_screen2;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        teamNum = findViewById(R.id.teamNum);
        matchNum = findViewById(R.id.matchNum);

        Button next = findViewById(R.id.next1);
        next.setOnClickListener(v -> {
            readInputs();
            List<String> errors = record.validatePreMatch();
            if (errors.isEmpty()) {
                goNext(Screen3.class);
            } else {
                Toast.makeText(this, joinErrors(errors), Toast.LENGTH_LONG).show();
            }
        });
        ((Button) findViewById(R.id.back)).setOnClickListener(v -> goBack());
        start();
    }

    @Override
    protected void readInputs() {
        record.teamNumber = parse(teamNum);
        record.matchNumber = parse(matchNum);
    }

    @Override
    protected void populate() {
        teamNum.setText(record.teamNumber > 0 ? String.valueOf(record.teamNumber) : "");
        matchNum.setText(record.matchNumber > 0 ? String.valueOf(record.matchNumber) : "");
    }

    static int parse(EditText e) {
        try {
            return Integer.parseInt(e.getText().toString().trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
