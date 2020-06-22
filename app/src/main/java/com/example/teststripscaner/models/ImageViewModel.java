package com.example.teststripscaner.models;

import android.graphics.Bitmap;

import androidx.lifecycle.ViewModel;

public class ImageViewModel extends ViewModel {
    public Bitmap bitmap;
    public double result;
    public boolean isNotCalculated = true;
}
