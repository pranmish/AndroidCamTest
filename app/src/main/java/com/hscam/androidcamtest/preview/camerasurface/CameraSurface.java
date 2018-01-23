package com.hscam.androidcamtest.preview.camerasurface;

import android.graphics.Matrix;
import android.view.View;

import com.hscam.androidcamtest.cameracontroller.CameraController;

public interface CameraSurface {
    abstract View getView();
	abstract void setPreviewDisplay(CameraController camera_controller); // n.b., uses double-dispatch similar to Visitor pattern - behaviour depends on type of CameraSurface and CameraController
	abstract void setTransform(Matrix matrix);
}