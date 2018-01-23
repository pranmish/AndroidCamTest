package com.hscam.androidcamtest.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Button;

import com.hscam.androidcamtest.MyApplicationInterface;
import com.hscam.androidcamtest.MyDebug;
import com.hscam.androidcamtest.R;
import com.hscam.androidcamtest.cameracontroller.CameraController;
import com.hscam.androidcamtest.preview.Preview;
import com.hscam.androidcamtest.ui.activities.NewCameraActivity;

import java.text.DecimalFormat;

public class DrawPreview {
	private static final String TAG = "DrawPreview";

	private NewCameraActivity main_activity = null;
	private MyApplicationInterface applicationInterface = null;

	private Paint p = new Paint();
	private RectF face_rect = new RectF();
	private RectF draw_rect = new RectF();
	private int [] gui_location = new int[2];
	private DecimalFormat decimalFormat = new DecimalFormat("#0.0");
	private float stroke_width = 0.0f;

	private boolean taking_picture = false;
	private boolean front_screen_flash = false;
    
	public DrawPreview(NewCameraActivity main_activity, MyApplicationInterface applicationInterface) {
		if( MyDebug.LOG )
			Log.d(TAG, "DrawPreview");
		this.main_activity = main_activity;
		this.applicationInterface = applicationInterface;

		p.setAntiAlias(true);
        p.setStrokeCap(Paint.Cap.ROUND);
		final float scale = getContext().getResources().getDisplayMetrics().density;
		this.stroke_width = (float) (1.0f * scale + 0.5f); // convert dps to pixels
		p.setStrokeWidth(stroke_width);
	}
	
	public void onDestroy() {
		if( MyDebug.LOG )
			Log.d(TAG, "onDestroy");
		// clean up just in case
	}

	private Context getContext() {
    	return main_activity;
    }
	
	public void cameraInOperation(boolean in_operation) {
    	if( in_operation ) {
    		taking_picture = true;
    	}
    	else {
    		taking_picture = false;
    		front_screen_flash = false;
    	}
    }
	
	public void turnFrontScreenFlashOn() {
		front_screen_flash = true;
	}

    private String getTimeStringFromSeconds(long time) {
    	int secs = (int)(time % 60);
    	time /= 60;
    	int mins = (int)(time % 60);
    	time /= 60;
    	long hours = time;
    	//String time_s = hours + ":" + String.format("%02d", mins) + ":" + String.format("%02d", secs) + ":" + String.format("%03d", ms);
    	String time_s = hours + ":" + String.format("%02d", mins) + ":" + String.format("%02d", secs);
    	return time_s;
    }

	public void onDrawPreview(Canvas canvas) {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getContext());
		Preview preview  = main_activity.getPreview();
		CameraController camera_controller = preview.getCameraController();
		int ui_rotation = preview.getUIRotation();
		boolean ui_placement_right = false;

		final float scale = getContext().getResources().getDisplayMetrics().density;
		if( camera_controller!= null && front_screen_flash ) {
			p.setColor(Color.WHITE);
			canvas.drawRect(0.0f, 0.0f, canvas.getWidth(), canvas.getHeight(), p);
		}

		canvas.save();
		canvas.rotate(ui_rotation, canvas.getWidth()/2.0f, canvas.getHeight()/2.0f);

		int text_y = (int) (20 * scale + 0.5f); // convert dps to pixels
		// fine tuning to adjust placement of text with respect to the GUI, depending on orientation
		int text_base_y = 0;
		if( ui_rotation == ( ui_placement_right ? 0 : 180 ) ) {
			text_base_y = canvas.getHeight() - (int)(0.5*text_y);
		}
		else if( ui_rotation == ( ui_placement_right ? 180 : 0 ) ) {
			text_base_y = canvas.getHeight() - (int)(2.5*text_y); // leave room for GUI icons
		}
		else if( ui_rotation == 90 || ui_rotation == 270 ) {
			//text_base_y = canvas.getHeight() + (int)(0.5*text_y);
			Button view = (Button) main_activity.findViewById(R.id.btn_process);
			// align with "top" of the take_photo button, but remember to take the rotation into account!
			view.getLocationOnScreen(gui_location);
			int view_left = gui_location[0];
			preview.getView().getLocationOnScreen(gui_location);
			int this_left = gui_location[0];
			int diff_x = view_left - ( this_left + canvas.getWidth()/2 );
    		/*if( MyDebug.LOG ) {
    			Log.d(TAG, "view left: " + view_left);
    			Log.d(TAG, "this left: " + this_left);
    			Log.d(TAG, "canvas is " + canvas.getWidth() + " x " + canvas.getHeight());
    		}*/
			int max_x = canvas.getWidth();
			if( ui_rotation == 90 ) {
				// so we don't interfere with the top bar info (datetime, free memory, ISO)
				max_x -= (int)(2.5*text_y);
			}
			if( canvas.getWidth()/2 + diff_x > max_x ) {
				// in case goes off the size of the canvas, for "black bar" cases (when preview aspect ratio != screen aspect ratio)
				diff_x = max_x - canvas.getWidth()/2;
			}
			text_base_y = canvas.getHeight()/2 + diff_x - (int)(0.5*text_y);
		}
		final int top_y = (int) (5 * scale + 0.5f); // convert dps to pixels
		final int location_size = (int) (20 * scale + 0.5f); // convert dps to pixels

		final double close_angle = 1.0f;
		if( camera_controller == null ) {
			/*if( MyDebug.LOG ) {
				Log.d(TAG, "no camera!");
				Log.d(TAG, "width " + canvas.getWidth() + " height " + canvas.getHeight());
			}*/
			p.setColor(Color.WHITE);
			p.setTextSize(14 * scale + 0.5f); // convert dps to pixels
			p.setTextAlign(Paint.Align.CENTER);
			int pixels_offset = (int) (20 * scale + 0.5f); // convert dps to pixels
			if( preview.hasPermissions() ) {
				canvas.drawText(getContext().getResources().getString(R.string.failed_to_open_camera_1), canvas.getWidth() / 2.0f, canvas.getHeight() / 2.0f, p);
				canvas.drawText(getContext().getResources().getString(R.string.failed_to_open_camera_2), canvas.getWidth() / 2.0f, canvas.getHeight() / 2.0f + pixels_offset, p);
				canvas.drawText(getContext().getResources().getString(R.string.failed_to_open_camera_3), canvas.getWidth() / 2.0f, canvas.getHeight() / 2.0f + 2*pixels_offset, p);
			}
			else {
				canvas.drawText(getContext().getResources().getString(R.string.no_permission), canvas.getWidth() / 2.0f, canvas.getHeight() / 2.0f, p);
			}
		}

		canvas.restore();
		
		CameraController.Face [] faces_detected = preview.getFacesDetected();
		if( faces_detected != null && main_activity.isDetectingFaces() ) {
			p.setColor(Color.rgb(0, 235, 0));
			p.setStyle(Paint.Style.STROKE);
			for(CameraController.Face face : faces_detected) {
				// Android doc recommends filtering out faces with score less than 50 (same for both Camera and Camera2 APIs)
				if( face.score >= 50 ) {
					face_rect.set(face.rect);
					preview.getCameraToPreviewMatrix().mapRect(face_rect);

					int eye_radius = (int) (5 * scale + 0.5f); // convert dps to pixels
					int mouth_radius = (int) (10 * scale + 0.5f); // convert dps to pixels
//					float [] top_left = {face.rect.left, face.rect.top};
//					float [] bottom_right = {face.rect.right, face.rect.bottom};
//					canvas.drawRect(top_left[0], top_left[1], bottom_right[0], bottom_right[1], p);
					canvas.drawRect(face_rect, p);

					int width = canvas.getWidth();
					int height = canvas.getHeight();

					if( face.leftEye != null ) {
						float [] left_point = {face.leftEye.x, face.leftEye.y};
                        preview.getCameraToPreviewMatrix().mapPoints(left_point);
						canvas.drawCircle(left_point[0], left_point[1], eye_radius, p);
					}
					if( face.rightEye != null ) {
						float [] right_point = {face.rightEye.x, face.rightEye.y};
                        preview.getCameraToPreviewMatrix().mapPoints(right_point);
						canvas.drawCircle(right_point[0], right_point[1], eye_radius, p);
					}
					if( face.mouth != null ) {
						float [] mouth_point = {face.mouth.x, face.mouth.y};
                        preview.getCameraToPreviewMatrix().mapPoints(mouth_point);
						canvas.drawCircle(mouth_point[0], mouth_point[1], mouth_radius, p);
					}
				}
			}
			p.setStyle(Paint.Style.FILL); // reset
		}
    }

    private void cameraToPreview(float [] coords, int width, int height) {
		float alpha = (coords[0]) / 2000.0f;
		float beta = (coords[1]) / 2000.0f;
		coords[0] = alpha * (float)width;
		coords[1] = beta * (float)height;
	}
}
