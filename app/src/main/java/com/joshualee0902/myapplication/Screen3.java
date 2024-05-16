package com.joshualee0902.myapplication;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;

import com.cepheuen.elegantnumberbutton.view.ElegantNumberButton;

public class Screen3 extends AppCompatActivity {

    Button next2;
    Button back1;
    CheckBox checkCrossedLine;
    public static String didCrossLine;
    ElegantNumberButton upperScore;
    ElegantNumberButton lowerScore;
    public static String autoUpper;
    public static String autoLower;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_screen3);

        next2 = findViewById(R.id.next2);
        next2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                passData();
                openScreen4();
                finish();
            }
        });

        back1 = findViewById(R.id.back1);
        back1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openScreen2();
            }
        });

    }


    public void passData() {
        checkCrossedLine = findViewById(R.id.checkCrossedLine);
        upperScore = findViewById(R.id.AutoUpper);
        lowerScore = findViewById(R.id.AutoLower);
        if(checkCrossedLine.isChecked()) {
            didCrossLine = "1";
        } else {
            didCrossLine = "0";
        }
        autoUpper = upperScore.getNumber();
        autoLower = lowerScore.getNumber();
    }

    public void openScreen4() {
        Intent intent3 = new Intent(this, Screen4.class);
        startActivity(intent3);
    }

    public void openScreen2() {
        Intent intent4 = new Intent(this, Screen2.class);
        startActivity(intent4);
    }
}
