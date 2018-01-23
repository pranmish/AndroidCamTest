package com.hscam.androidcamtest.ui;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.RelativeLayout;

import com.hscam.androidcamtest.MyDebug;
import com.hscam.androidcamtest.R;
import com.hscam.androidcamtest.ui.activities.NewCameraActivity;

public class MainUI {
	private static final String TAG = "MainUI";

	private NewCameraActivity main_activity = null;

    private int current_orientation = 0;
	private boolean ui_placement_right = true;

	private boolean immersive_mode = false;
    private boolean show_gui = true; // result of call to showGUI() - false means a "reduced" GUI is displayed, whilst taking photo or video

	public MainUI(NewCameraActivity main_activity) {
		if( MyDebug.LOG )
			Log.d(TAG, "MainUI");
		this.main_activity = main_activity;
	}

	private void setViewRotation(View view, float ui_rotation) {
		//view.setRotation(ui_rotation);
		float rotate_by = ui_rotation - view.getRotation();
		if( rotate_by > 181.0f )
			rotate_by -= 360.0f;
		else if( rotate_by < -181.0f )
			rotate_by += 360.0f;
		// view.animate() modifies the view's rotation attribute, so it ends up equivalent to view.setRotation()
		// we use rotationBy() instead of rotation(), so we get the minimal rotation for clockwise vs anti-clockwise
		view.animate().rotationBy(rotate_by).setDuration(100).setInterpolator(new AccelerateDecelerateInterpolator()).start();
	}

    public void layoutUI() {
		long debug_time = 0;
		if( MyDebug.LOG ) {
			Log.d(TAG, "layoutUI");
			debug_time = System.currentTimeMillis();
		}
		//this.preview.updateUIPlacement();
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
		String ui_placement = "ui_right";
    	// we cache the preference_ui_placement to save having to check it in the draw() method
		this.ui_placement_right = ui_placement.equals("ui_right");
		if( MyDebug.LOG )
			Log.d(TAG, "ui_placement: " + ui_placement);
		// new code for orientation fixed to landscape	
		// the display orientation should be locked to landscape, but how many degrees is that?
	    int rotation = main_activity.getWindowManager().getDefaultDisplay().getRotation();
	    int degrees = 0;
	    switch (rotation) {
	    	case Surface.ROTATION_0: degrees = 0; break;
	        case Surface.ROTATION_90: degrees = 90; break;
	        case Surface.ROTATION_180: degrees = 180; break;
	        case Surface.ROTATION_270: degrees = 270; break;
    		default:
    			break;
	    }
	    // getRotation is anti-clockwise, but current_orientation is clockwise, so we add rather than subtract
	    // relative_orientation is clockwise from landscape-left
    	//int relative_orientation = (current_orientation + 360 - degrees) % 360;
    	int relative_orientation = (current_orientation + degrees) % 360;
		if( MyDebug.LOG ) {
			Log.d(TAG, "    current_orientation = " + current_orientation);
			Log.d(TAG, "    degrees = " + degrees);
			Log.d(TAG, "    relative_orientation = " + relative_orientation);
		}
		int ui_rotation = (360 - relative_orientation) % 360;
		main_activity.getPreview().setUIRotation(ui_rotation);
		int align_left = RelativeLayout.ALIGN_LEFT;
		int align_right = RelativeLayout.ALIGN_RIGHT;
		//int align_top = RelativeLayout.ALIGN_TOP;
		//int align_bottom = RelativeLayout.ALIGN_BOTTOM;
		int left_of = RelativeLayout.LEFT_OF;
		int right_of = RelativeLayout.RIGHT_OF;
		int above = RelativeLayout.ABOVE;
		int below = RelativeLayout.BELOW;
		int align_parent_left = RelativeLayout.ALIGN_PARENT_LEFT;
		int align_parent_right = RelativeLayout.ALIGN_PARENT_RIGHT;
		int align_parent_top = RelativeLayout.ALIGN_PARENT_TOP;
		int align_parent_bottom = RelativeLayout.ALIGN_PARENT_BOTTOM;
		if( !ui_placement_right ) {
			//align_top = RelativeLayout.ALIGN_BOTTOM;
			//align_bottom = RelativeLayout.ALIGN_TOP;
			above = RelativeLayout.BELOW;
			below = RelativeLayout.ABOVE;
			align_parent_top = RelativeLayout.ALIGN_PARENT_BOTTOM;
			align_parent_bottom = RelativeLayout.ALIGN_PARENT_TOP;
		}
		{

			View view = main_activity.findViewById(R.id.btn_process);
			RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_parent_left, 0);
			layoutParams.addRule(align_parent_right, RelativeLayout.TRUE);
			view.setLayoutParams(layoutParams);
			setViewRotation(view, ui_rotation);
		}

		if( MyDebug.LOG ) {
			Log.d(TAG, "layoutUI: total time: " + (System.currentTimeMillis() - debug_time));
		}
    }

    public boolean getUIPlacementRight() {
    	return this.ui_placement_right;
    }

    public void onOrientationChanged(int orientation) {
		/*if( MyDebug.LOG ) {
			Log.d(TAG, "onOrientationChanged()");
			Log.d(TAG, "orientation: " + orientation);
			Log.d(TAG, "current_orientation: " + current_orientation);
		}*/
		if( orientation == OrientationEventListener.ORIENTATION_UNKNOWN )
			return;
		int diff = Math.abs(orientation - current_orientation);
		if( diff > 180 )
			diff = 360 - diff;
		// only change orientation when sufficiently changed
		if( diff > 60 ) {
		    orientation = (orientation + 45) / 90 * 90;
		    orientation = orientation % 360;
		    if( orientation != current_orientation ) {
			    this.current_orientation = orientation;
				if( MyDebug.LOG ) {
					Log.d(TAG, "current_orientation is now: " + current_orientation);
				}
			    layoutUI();
			}
		}
	}

    public boolean inImmersiveMode() {
    	return immersive_mode;
    }

    public void showGUI(final boolean show) {
		if( MyDebug.LOG )
			Log.d(TAG, "showGUI: " + show);
		this.show_gui = show;
		if( inImmersiveMode() )
			return;
    }

}
