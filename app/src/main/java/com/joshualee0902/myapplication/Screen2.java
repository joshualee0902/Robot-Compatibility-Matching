package com.joshualee0902.myapplication;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class Screen2 extends AppCompatActivity {

    public static String teamNumber;
    public static String matchNumber;
    EditText teamNum;
    EditText matchNum;
    Button next1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_screen2);

        next1 = findViewById(R.id.next1);
        next1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                passData();
                openScreen3();
                finish();
            }
        });

    }


    public void passData() {
        teamNum = findViewById(R.id.teamNum);
        matchNum = findViewById(R.id.matchNum);
        teamNumber = teamNum.getText().toString();
        matchNumber = matchNum.getText().toString();
    }

    public void openScreen3() {
        Intent intent2 = new Intent(this, Screen3.class);
        startActivity(intent2);
    }

}
