package com.example.teststripscaner.activities;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.teststripscaner.R;
import com.example.teststripscaner.fragments.ResultFragment;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new ResultFragment())
                    .addToBackStack(null)
                    .commit();
        }
    }

}