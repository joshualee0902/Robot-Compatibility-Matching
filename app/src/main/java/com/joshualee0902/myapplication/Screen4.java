package com.joshualee0902.myapplication;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;

import com.cepheuen.elegantnumberbutton.view.ElegantNumberButton;

public class Screen4 extends AppCompatActivity {

    Button next3;
    Button back2;
    ElegantNumberButton upperScore;
    ElegantNumberButton lowerScore;
    CheckBox checkWheelSpin;
    CheckBox checkWheelColor;
    CheckBox checkClimbed;
    ElegantNumberButton foul;
    public static String wheel;
    public static String climbed;
    public static String teleUpper;
    public static String teleLower;
    public static String fouls;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_screen4);

        next3 = findViewById(R.id.next3);
        next3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                passData();
                openScreen5();
                finish();
            }
        });

        back2 = findViewById(R.id.back2);
        back2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openScreen3();
            }
        });

    }

    public void passData() {
        upperScore = findViewById(R.id.TeleUpper);
        lowerScore = findViewById(R.id.TeleLower);
        checkWheelSpin = findViewById(R.id.checkWheelSpin);
        checkWheelColor = findViewById(R.id.checkWheelColor);
        checkClimbed = findViewById(R.id.checkClimb);
        foul = findViewById(R.id.Fouls);
        teleUpper = upperScore.getNumber();
        teleLower = lowerScore.getNumber();
        if(checkWheelSpin.isChecked() && checkWheelColor.isChecked()) {
            wheel = "3";
        } else if(checkWheelSpin.isChecked() && checkWheelColor.isChecked() == false) {
            wheel = "2";
        } else if(checkWheelSpin.isChecked() == false && checkWheelColor.isChecked() == false) {
            wheel = "1";
        }
        if(checkClimbed.isChecked()) {
            climbed = "Climbed";
        } else {
            climbed = "Did not Climb";
        }
        fouls = foul.getNumber();
    }

    public void openScreen5() {
        Intent intent5 = new Intent(this, Screen5.class);
        startActivity(intent5);
    }

    public void openScreen3() {
        Intent intent6 = new Intent(this, Screen3.class);
        startActivity(intent6);
    }
}
