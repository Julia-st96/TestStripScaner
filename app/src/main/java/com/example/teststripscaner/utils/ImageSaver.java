package com.example.teststripscaner.utils;

import android.media.Image;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class ImageSaver implements Runnable {

    private final Image mImage;
    private final String mImageFileName;

    public ImageSaver(Image image, String imageFileName) {
        mImage = image;
        mImageFileName = imageFileName;
    }

    @Override
    public void run() {
        ByteBuffer byteBuffer = mImage.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[byteBuffer.remaining()];
        byteBuffer.get(bytes);

        FileOutputStream fileOutputStream = null;
        try {
            fileOutputStream = new FileOutputStream(mImageFileName);
            fileOutputStream.write(bytes);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            mImage.close();

            if (fileOutputStream != null) {
                try {
                    fileOutputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }
}
