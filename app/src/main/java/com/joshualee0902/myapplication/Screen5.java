package com.joshualee0902.myapplication;

import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** Post-match: outcome, ranking points, bricked, comment. Total points are calculated, not typed. */
public class Screen5 extends FlowActivity {

    private RadioGroup outcomeGroup;
    private CounterView rankingPoints;
    private CheckBox bricked;
    private EditText comment;
    private TextView totalView;

    @Override
    protected int layoutId() {
        return R.layout.activity_screen5;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        outcomeGroup = findViewById(R.id.outcomeGroup);
        rankingPoints = findViewById(R.id.RankingPoints);
        bricked = findViewById(R.id.checkBricked);
        comment = findViewById(R.id.Comment);
        totalView = findViewById(R.id.totalPointsAuto);

        ((Button) findViewById(R.id.next4)).setOnClickListener(v -> {
            readInputs();
            record.recomputeTotal();
            List<String> errors = record.validate();
            if (errors.isEmpty()) {
                goNext(Screen6.class);
            } else {
                Toast.makeText(this, joinErrors(errors), Toast.LENGTH_LONG).show();
            }
        });
        ((Button) findViewById(R.id.back3)).setOnClickListener(v -> goBack());
        start();
    }

    @Override
    protected void readInputs() {
        int id = outcomeGroup.getCheckedRadioButtonId();
        record.outcome = id == R.id.outcomeW ? "W" : id == R.id.outcomeD ? "D" : id == R.id.outcomeL ? "L" : "";
        record.rankingPoints = rankingPoints.getValue();
        record.bricked = bricked.isChecked();
        record.comment = comment.getText().toString();
        record.recomputeTotal();
    }

    @Override
    protected void populate() {
        switch (record.outcome) {
            case "W": outcomeGroup.check(R.id.outcomeW); break;
            case "D": outcomeGroup.check(R.id.outcomeD); break;
            case "L": outcomeGroup.check(R.id.outcomeL); break;
            default: outcomeGroup.clearCheck();
        }
        rankingPoints.setValue(record.rankingPoints);
        bricked.setChecked(record.bricked);
        comment.setText(record.comment);
        record.recomputeTotal();
        totalView.setText(getString(R.string.total_points_auto, record.totalPoints));
    }
}
