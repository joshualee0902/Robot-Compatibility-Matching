package com.joshualee0902.myapplication;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.cepheuen.elegantnumberbutton.view.ElegantNumberButton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Screen5 extends AppCompatActivity {

    Button back3;
    Button next4;
    EditText totalPoints;
    EditText outcome;
    ElegantNumberButton rankingPoint;
    CheckBox checkBricked;
    EditText comment;
    public static String totPoints;
    public static String windrawlose;
    public static String rankingPoints;
    public static String bricked;
    public static String say;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_screen5);

        next4 = findViewById(R.id.next4);
        next4.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                passData();
                openScreen6();
                finish();
            }
        });

        back3 = findViewById(R.id.back3);
        back3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openScreen4();
            }
        });

    }

    public void passData() {
        totalPoints = findViewById(R.id.totalPoints);
        outcome = findViewById(R.id.outcome);
        rankingPoint = findViewById(R.id.RankingPoints);
        checkBricked = findViewById(R.id.checkBricked);
        comment = findViewById(R.id.Comment);
        totPoints = totalPoints.getText().toString();
        windrawlose = outcome.getText().toString();
        rankingPoints = rankingPoint.getNumber();
        if(checkBricked.isChecked()) {
            bricked = "1";
        } else {
            bricked = "0";
        }
        say = comment.getText().toString();
    }

    public void openScreen6() {
        Intent intent7 = new Intent(this, Screen6.class);
        startActivity(intent7);
    }

    public void openScreen4() {
        Intent intent8 = new Intent(this, Screen4.class);
        startActivity(intent8);
    }


}

