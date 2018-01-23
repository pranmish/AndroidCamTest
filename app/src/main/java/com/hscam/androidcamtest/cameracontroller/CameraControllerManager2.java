package com.hscam.androidcamtest.cameracontroller;

import android.annotation.TargetApi;
import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.os.Build;
import android.util.Log;

import com.hscam.androidcamtest.MyDebug;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class CameraControllerManager2 extends CameraControllerManager {
	private static final String TAG = "CameraControllerManager2";

	private Context context = null;

	public CameraControllerManager2(Context context) {
		this.context = context;
	}

	@Override
	public int getNumberOfCameras() {
		CameraManager manager = (CameraManager)context.getSystemService(Context.CAMERA_SERVICE);
		try {
			return manager.getCameraIdList().length;
		}
		catch(CameraAccessException e) {
			if( MyDebug.LOG )
				Log.e(TAG, "exception trying to get camera ids");
			e.printStackTrace();
		}
		catch(AssertionError e) {
			if( MyDebug.LOG )
				Log.e(TAG, "assertion error trying to get camera ids");
			e.printStackTrace();
		}
		return 0;
	}

	@Override
	public boolean isFrontFacing(int cameraId) {
		CameraManager manager = (CameraManager)context.getSystemService(Context.CAMERA_SERVICE);
		try {
			String cameraIdS = manager.getCameraIdList()[cameraId];
			CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraIdS);
			return characteristics.get(CameraCharacteristics.LENS_FACING) == CameraMetadata.LENS_FACING_FRONT;
		}
		catch(CameraAccessException e) {
			if( MyDebug.LOG )
				Log.e(TAG, "exception trying to get camera characteristics");
			e.printStackTrace();
		}
		return false;
	}

	private boolean isHardwareLevelSupported(CameraCharacteristics c, int requiredLevel) {
		int deviceLevel = c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
		if( MyDebug.LOG ) {
			if( deviceLevel == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY )
				Log.d(TAG, "Camera has LEGACY Camera2 support");
			else if( deviceLevel == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED )
				Log.d(TAG, "Camera has LIMITED Camera2 support");
			else if( deviceLevel == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL )
				Log.d(TAG, "Camera has FULL Camera2 support");
			else
				Log.d(TAG, "Camera has unknown Camera2 support: " + deviceLevel);
		}
		if (deviceLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
			return requiredLevel == deviceLevel;
		}
		// deviceLevel is not LEGACY, can use numerical sort
		return requiredLevel <= deviceLevel;
	}

	public boolean allowCamera2Support(int cameraId) {
		CameraManager manager = (CameraManager)context.getSystemService(Context.CAMERA_SERVICE);
		try {
			String cameraIdS = manager.getCameraIdList()[cameraId];
			CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraIdS);
			boolean supported = isHardwareLevelSupported(characteristics, CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED);
			return supported;
		}
		catch(CameraAccessException e) {
			if( MyDebug.LOG )
				Log.e(TAG, "exception trying to get camera characteristics");
			e.printStackTrace();
		}
		catch(NumberFormatException e) {
			if( MyDebug.LOG )
				Log.e(TAG, "exception trying to get camera characteristics");
			e.printStackTrace();
		}
		return false;
	}
}
