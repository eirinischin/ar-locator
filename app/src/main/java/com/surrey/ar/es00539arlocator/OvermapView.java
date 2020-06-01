package com.surrey.ar.es00539arlocator;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import com.google.ar.sceneform.math.Vector3;

public class OvermapView extends View {
    Bitmap overmapImage;
    Bitmap keysImage;
    Bitmap oculosImage;
    Bitmap stickmanImage;
    int originX;
    int originY;
    int width;
    int height;
    private final int paintColor = Color.BLACK;
    private Paint drawPaint;

    private Vector3 keys;
    private Vector3 oculos;
    private Vector3 camera;

    // Setup paint with color and stroke styles
    private void setupPaint() {
        drawPaint = new Paint();
        drawPaint.setColor(paintColor);
        drawPaint.setAntiAlias(true);
        drawPaint.setStrokeWidth(5);
        drawPaint.setStyle(Paint.Style.STROKE);
        drawPaint.setStrokeJoin(Paint.Join.ROUND);
        drawPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    public OvermapView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setFocusable(true);
        setFocusableInTouchMode(true);
        overmapImage = BitmapFactory.decodeResource(getResources(), R.drawable.overmap);
        keysImage = BitmapFactory.decodeResource(getResources(), R.drawable.little_keys);
        oculosImage = BitmapFactory.decodeResource(getResources(), R.drawable.little_glasses);
        stickmanImage = BitmapFactory.decodeResource(getResources(), R.drawable.stickman);
        width = overmapImage.getWidth();
        height = overmapImage.getHeight();
        this.setMinimumWidth(width);
        this.setMinimumHeight(height);
        originX = 0;
        originY = 0;
        keys = null;
        oculos = null;
        camera = null;
        setupPaint();
    }

    public void setOrigin(int x, int y) {
        this.originX = x;
        this.originY = y;
        this.invalidate();
    }

    public void setCamera(Vector3 pos) {
        this.camera = pos;
        this.invalidate();
    }

    public void setKeys(Vector3 pos) {
        this.keys = pos;
        this.invalidate();
    }

    public void setOculos(Vector3 pos) {
        this.oculos = pos;
        this.invalidate();
    }

    // Transform and draw relative vector
    private void drawTransform(Canvas canvas, Vector3 pos, Bitmap bitmap) {
        int newY = (int) (originY + (100f * pos.x));
        if (newY < 0) { newY = 0; }
        if (newY >= height) { newY = height - 1; }
        int newX = (int) (originX + (100f * pos.z));
        if (newX < 0) { newX = 0; }
        if (newX >= width) { newX = width - 1; }
        canvas.drawBitmap(bitmap, newX, newY, null);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawBitmap(overmapImage, 0, 0, null);
        drawPaint.setColor(Color.BLUE);
        canvas.drawCircle(originX, originY, 20, drawPaint);

        if (keys != null) {
            drawTransform(canvas, keys, keysImage);
        }
        if (oculos != null) {
            drawTransform(canvas, oculos, oculosImage);
        }
        if (camera != null) {
            drawTransform(canvas, camera, stickmanImage);
        }
    }
}