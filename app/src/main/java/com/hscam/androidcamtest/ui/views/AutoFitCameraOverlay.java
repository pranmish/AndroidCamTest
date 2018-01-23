package com.hscam.androidcamtest.ui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.camera2.params.Face;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;

import com.hscam.androidcamtest.R;

/**
 * Created by Harry on 1/21/2018.
 */

public class AutoFitCameraOverlay extends View {

    private int mRatioWidth = 16;
    private int mRatioHeight = 9;

    private Face[] mFaces = null;
    private Rect mSensorRect = null;

    public AutoFitCameraOverlay(Context context) {
        super(context);
    }

    public AutoFitCameraOverlay(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public AutoFitCameraOverlay(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setAspectRatio(int width, int height) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Size cannot be negative.");
        }
        mRatioWidth = width;
        mRatioHeight = height;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        if (0 == mRatioWidth || 0 == mRatioHeight) {
            setMeasuredDimension(width, height);
        } else {
            if (width < height * mRatioWidth / mRatioHeight) {
                setMeasuredDimension(width, width * mRatioHeight / mRatioWidth);
            } else {
                setMeasuredDimension(height * mRatioWidth / mRatioHeight, height);
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Paint faceRectPaint = new Paint();
        faceRectPaint.setStyle(Paint.Style.STROKE);
        faceRectPaint.setStrokeWidth(getResources().getDimension(R.dimen.face_rect_stroke_width));
        faceRectPaint.setColor(Color.GREEN);

        Paint eyeRectPaint = new Paint();
        eyeRectPaint.setStyle(Paint.Style.STROKE);
        eyeRectPaint.setStrokeWidth(getResources().getDimension(R.dimen.face_rect_stroke_width));
        eyeRectPaint.setColor(Color.RED);

        Paint mouthRectPaint = new Paint();
        mouthRectPaint.setStyle(Paint.Style.STROKE);
        mouthRectPaint.setStrokeWidth(getResources().getDimension(R.dimen.face_rect_stroke_width));
        mouthRectPaint.setColor(Color.BLUE);

        if (mFaces != null && mFaces.length > 0){
            for (int i = 0; i < mFaces.length; i++){
                if (mFaces[i].getScore() < 50) continue;
                Face detectedFace = mFaces[i];
                Rect faceRect = convertRectFromCamera2(mSensorRect, detectedFace.getBounds());
                canvas.drawRect(faceRect, faceRectPaint);

                Point leftEyePoint = detectedFace.getLeftEyePosition();
                Point rightEyePoint = detectedFace.getRightEyePosition();
                Point mouthPoint = detectedFace.getMouthPosition();


                if (leftEyePoint != null){
                    Rect leftEyeRect = new Rect(leftEyePoint.x - 10, leftEyePoint.y - 10, rightEyePoint.x + 10, rightEyePoint.y + 10);
                    canvas.drawRect(leftEyeRect, eyeRectPaint);
                }

                if (rightEyePoint != null){
                    Rect rightEyeRect = new Rect(rightEyePoint.x - 10, rightEyePoint.y - 10, rightEyePoint.x + 10, rightEyePoint.y + 10);
                    canvas.drawRect(rightEyeRect, eyeRectPaint);
                }

                if (mouthPoint != null){
                    Rect mouthRect = new Rect(mouthPoint.x - 10, mouthPoint.y - 10, mouthPoint.x + 10, mouthPoint.y + 10);
                    canvas.drawRect(mouthRect, mouthRectPaint);
                }
            }
        }

        super.onDraw(canvas);
    }

    public void setFaces(Rect sensorRect, Face[] newFaces){
        mFaces = newFaces;
        mSensorRect = sensorRect;
        invalidate();
    }

    private Rect convertRectFromCamera2(Rect crop_rect, Rect camera2_rect) {
        // inverse of convertRectToCamera2()
        double left_f = (camera2_rect.left-crop_rect.left)/(double)(crop_rect.width()-1);
        double top_f = (camera2_rect.top-crop_rect.top)/(double)(crop_rect.height()-1);
        double right_f = (camera2_rect.right-crop_rect.left)/(double)(crop_rect.width()-1);
        double bottom_f = (camera2_rect.bottom-crop_rect.top)/(double)(crop_rect.height()-1);
        int left = (int)(left_f * 2000);
        int right = (int)(right_f * 2000);
        int top = (int)(top_f * 2000);
        int bottom = (int)(bottom_f * 2000);

        Rect rect = new Rect(left, top, right, bottom);
        return rect;
    }
}
