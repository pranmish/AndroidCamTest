package com.hscam.androidcamtest.preview;

import android.content.Context;
import android.graphics.Canvas;

public interface ApplicationInterface {

	void layoutUI();
	Context getContext();
	boolean useCamera2();

    void cameraSetup();
	void onFailedStartPreview();
	void hasPausedPreview(boolean paused);
	void cameraInOperation(boolean in_operation);
	void cameraClosed();
	void requestCameraPermission();
	void requestStoragePermission();

	void onDrawPreview(Canvas canvas);

	String getPreviewRotationPref();
	int getCameraIdPref();

	void setCameraIdPref(int cameraId);

	boolean getShowToastsPref();
}
