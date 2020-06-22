package com.example.teststripscaner.utils;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Size;
import android.view.View;

import com.example.teststripscaner.R;

public class ViewfinderView extends View {
    private Point mFrameStartPoint;
    private Size mFrameSize;
    private int mFrameCornerRadius;
    private int mFrameStrokeWidth;

    public ViewfinderView(Context context) {
        super(context);
    }

    public ViewfinderView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ViewfinderView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public int getFrameWidth() {
        return mFrameSize.getWidth();
    }

    public int getFrameHeight() {
        return mFrameSize.getHeight();
    }

    public int getFrameLeft() {
        return mFrameStartPoint.x;
    }

    public int getFrameTop() {
        return mFrameStartPoint.y;
    }


    private void init() {
        float frameWidthCoef = (float) (0.01 * getResources().getInteger(R.integer.frame_width_percent));
        float frameHeightCoef = (float) (0.01 * getResources().getInteger(R.integer.frame_height_percent));


        int frameWidth = Math.round(getWidth() * frameWidthCoef);
        int frameHeight = Math.round(getHeight() * frameHeightCoef);

        mFrameStartPoint = new Point(frameWidth, frameHeight);
        mFrameSize = new Size(frameWidth, frameHeight);
        mFrameCornerRadius = getResources().getInteger(R.integer.frame_corner_radius);
        mFrameStrokeWidth = getResources().getInteger(R.integer.frame_stroke_width);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        init();

        int frameLeft = mFrameStartPoint.x;
        int frameTop = mFrameStartPoint.y;
        int frameRight = mFrameStartPoint.x + mFrameSize.getWidth();
        int frameBottom = mFrameStartPoint.y + mFrameSize.getHeight();
        RectF frame = new RectF(frameLeft, frameTop, frameRight, frameBottom);

        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        Paint eraser = new Paint();
        eraser.setAntiAlias(true);
        eraser.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        Path path = new Path();
        Paint stroke = new Paint();
        stroke.setAntiAlias(true);
        stroke.setStrokeWidth(mFrameStrokeWidth);
        stroke.setColor(Color.WHITE);
        stroke.setStyle(Paint.Style.STROKE);

        path.addRoundRect(frame, (float) mFrameCornerRadius, (float) mFrameCornerRadius, Path.Direction.CW);
        canvas.drawPath(path, stroke);
        canvas.drawRoundRect(frame, (float) mFrameCornerRadius, (float) mFrameCornerRadius, eraser);
    }
}
