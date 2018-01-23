package com.hscam.androidcamtest;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

import com.hscam.androidcamtest.preview.ApplicationInterface;
import com.hscam.androidcamtest.ui.DrawPreview;
import com.hscam.androidcamtest.ui.activities.NewCameraActivity;

public class MyApplicationInterface implements ApplicationInterface {
	private static final String TAG = "MyApplicationInterface";
    
	private Rect text_bounds = new Rect();

	private DrawPreview drawPreview = null;
	private NewCameraActivity main_activity = null;

	private int cameraId = 1;
	private int zoom_factor = 0;
	private float focus_distance = 0.0f;

	public MyApplicationInterface(NewCameraActivity main_activity, Bundle savedInstanceState) {
		long debug_time = 0;
		if( MyDebug.LOG ) {
			Log.d(TAG, "MyApplicationInterface");
			debug_time = System.currentTimeMillis();
		}
		this.main_activity = main_activity;
		if( MyDebug.LOG )
			Log.d(TAG, "MyApplicationInterface: time after creating location supplier: " + (System.currentTimeMillis() - debug_time));
		this.drawPreview = new DrawPreview(main_activity, this);
		
        if( savedInstanceState != null ) {
    		cameraId = savedInstanceState.getInt("cameraId", 0);
			if( MyDebug.LOG )
				Log.d(TAG, "found cameraId: " + cameraId);
    		zoom_factor = savedInstanceState.getInt("zoom_factor", 0);
			if( MyDebug.LOG )
				Log.d(TAG, "found zoom_factor: " + zoom_factor);
			focus_distance = savedInstanceState.getFloat("focus_distance", 0.0f);
			if( MyDebug.LOG )
				Log.d(TAG, "found focus_distance: " + focus_distance);
        }

		if( MyDebug.LOG )
			Log.d(TAG, "MyApplicationInterface: total time to create MyApplicationInterface: " + (System.currentTimeMillis() - debug_time));
	}
	
	void onSaveInstanceState(Bundle state) {
		if( MyDebug.LOG )
			Log.d(TAG, "onSaveInstanceState");
		if( MyDebug.LOG )
			Log.d(TAG, "save cameraId: " + cameraId);
    	state.putInt("cameraId", cameraId);
		if( MyDebug.LOG )
			Log.d(TAG, "save zoom_factor: " + zoom_factor);
    	state.putInt("zoom_factor", zoom_factor);
		if( MyDebug.LOG )
			Log.d(TAG, "save focus_distance: " + focus_distance);
    	state.putFloat("focus_distance", focus_distance);
	}
	
	protected void onDestroy() {
		if( MyDebug.LOG )
			Log.d(TAG, "onDestroy");
		if( drawPreview != null ) {
			drawPreview.onDestroy();
		}
	}

    @Override
	public Context getContext() {
    	return main_activity;
    }
    
    @Override
	public boolean useCamera2() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        return main_activity.supportsCamera2();
    }

	@Override
	public void cameraSetup() {
	}

	@Override
	public void onFailedStartPreview() {
		main_activity.getPreview().showToast(null, R.string.failed_to_start_camera_preview);
	}

    @Override
	public void hasPausedPreview(boolean paused) {

	}
    
    @Override
    public void cameraInOperation(boolean in_operation) {
		if( MyDebug.LOG )
			Log.d(TAG, "cameraInOperation: " + in_operation);
    	drawPreview.cameraInOperation(in_operation);
    }

	@Override
	public void cameraClosed() {
	}

    @Override
	public void requestCameraPermission() {
		if( MyDebug.LOG )
			Log.d(TAG, "requestCameraPermission");
		main_activity.requestCameraPermission();
    }
    
    @Override
	public void requestStoragePermission() {
		if( MyDebug.LOG )
			Log.d(TAG, "requestStoragePermission");
		main_activity.requestStoragePermission();
    }

    @Override
    public void onDrawPreview(Canvas canvas) {
    	drawPreview.onDrawPreview(canvas);
    }

	@Override
	public void layoutUI() {
		main_activity.getMainUI().layoutUI();
	}

	@Override
	public String getPreviewRotationPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getString(PreferenceKeys.getRotatePreviewPreferenceKey(), "0");
	}

	@Override
	public int getCameraIdPref() {
		return cameraId;
	}

	@Override
	public void setCameraIdPref(int cameraId) {
		this.cameraId = cameraId;
	}

	@Override
	public boolean getShowToastsPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getBoolean(PreferenceKeys.getShowToastsPreferenceKey(), false);
	}
}
