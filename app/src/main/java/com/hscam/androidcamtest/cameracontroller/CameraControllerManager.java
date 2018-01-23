package com.hscam.androidcamtest.cameracontroller;

public abstract class CameraControllerManager {
    public abstract int getNumberOfCameras();
	public abstract boolean isFrontFacing(int cameraId);
}