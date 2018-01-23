package com.hscam.androidcamtest.preview.camerasurface;

import android.content.Context;
import android.graphics.Matrix;
import android.os.Bundle;
import android.util.Log;
import android.view.TextureView;
import android.view.View;

import com.hscam.androidcamtest.MyDebug;
import com.hscam.androidcamtest.cameracontroller.CameraController;
import com.hscam.androidcamtest.cameracontroller.CameraControllerException;
import com.hscam.androidcamtest.preview.Preview;

/** Provides support for the surface used for the preview, using a TextureView.
 */
public class MyTextureView extends TextureView implements CameraSurface {
	private static final String TAG = "MyTextureView";

	private Preview preview = null;
	private int [] measure_spec = new int[2];
	
	public MyTextureView(Context context, Bundle savedInstanceState, Preview preview) {
		super(context);
		this.preview = preview;
		if( MyDebug.LOG ) {
			Log.d(TAG, "new MyTextureView");
		}

		this.setSurfaceTextureListener(preview);
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
			camera_controller.setPreviewTexture(this.getSurfaceTexture());
		}
		catch(CameraControllerException e) {
			if( MyDebug.LOG )
				Log.e(TAG, "Failed to set preview display: " + e.getMessage());
			e.printStackTrace();
		}
	}

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
    	preview.getMeasureSpec(measure_spec, widthSpec, heightSpec);
    	super.onMeasure(measure_spec[0], measure_spec[1]);
    }

	@Override
	public void setTransform(Matrix matrix) {
		super.setTransform(matrix);
	}
}
