package com.hscam.androidcamtest.preview.camerasurface;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import com.hscam.androidcamtest.MyDebug;
import com.hscam.androidcamtest.cameracontroller.CameraController;
import com.hscam.androidcamtest.cameracontroller.CameraControllerException;
import com.hscam.androidcamtest.preview.Preview;

/** Provides support for the surface used for the preview, using a SurfaceView.
 */
public class MySurfaceView extends SurfaceView implements CameraSurface {
	private static final String TAG = "MySurfaceView";

	private Preview preview = null;
	private int [] measure_spec = new int[2];
	
	@SuppressWarnings("deprecation")
	public
	MySurfaceView(Context context, Bundle savedInstanceState, Preview preview) {
		super(context);
		this.preview = preview;
		if( MyDebug.LOG ) {
			Log.d(TAG, "new MySurfaceView");
		}

		// Install a SurfaceHolder.Callback so we get notified when the
		// underlying surface is created and destroyed.
		getHolder().addCallback(preview);
        // deprecated setting, but required on Android versions prior to 3.0
		getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS); // deprecated
	}
	
	@Override
	public View getView() {
		return this;
	}
	
	@Override
	public void setPreviewDisplay(CameraController camera_controller) {
		if( MyDebug.LOG )
			Log.d(TAG, "setPreviewDisplay");
		try {
			camera_controller.setPreviewDisplay(this.getHolder());
		}
		catch(CameraControllerException e) {
			if( MyDebug.LOG )
				Log.e(TAG, "Failed to set preview display: " + e.getMessage());
			e.printStackTrace();
		}
	}

	@Override
	public void onDraw(Canvas canvas) {
		preview.draw(canvas);
	}

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
    	preview.getMeasureSpec(measure_spec, widthSpec, heightSpec);
    	super.onMeasure(measure_spec[0], measure_spec[1]);
    }

	@Override
	public void setTransform(Matrix matrix) {
		if( MyDebug.LOG )
			Log.d(TAG, "setting transforms not supported for MySurfaceView");
		throw new RuntimeException();
	}
}
