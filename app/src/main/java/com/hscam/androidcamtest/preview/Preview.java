package com.hscam.androidcamtest.preview;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.Pair;
import android.view.Display;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.TextureView;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Toast;

import com.hscam.androidcamtest.MyDebug;
import com.hscam.androidcamtest.cameracontroller.CameraController;
import com.hscam.androidcamtest.cameracontroller.CameraController1;
import com.hscam.androidcamtest.cameracontroller.CameraController2;
import com.hscam.androidcamtest.cameracontroller.CameraControllerException;
import com.hscam.androidcamtest.cameracontroller.CameraControllerManager;
import com.hscam.androidcamtest.cameracontroller.CameraControllerManager1;
import com.hscam.androidcamtest.cameracontroller.CameraControllerManager2;
import com.hscam.androidcamtest.preview.camerasurface.CameraSurface;
import com.hscam.androidcamtest.preview.camerasurface.MySurfaceView;
import com.hscam.androidcamtest.preview.camerasurface.MyTextureView;
import com.hscam.androidcamtest.ui.ToastBoxer;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class Preview implements SurfaceHolder.Callback, TextureView.SurfaceTextureListener {
	private static final String TAG = "Preview";

	private boolean using_android_l = false;
	private boolean using_texture_view = false;

	private ApplicationInterface applicationInterface = null;
	private CameraSurface cameraSurface = null;
	private CanvasView canvasView = null;
	private boolean set_preview_size = false;
	private int preview_w = 0, preview_h = 0;
	private boolean set_textureview_size = false;
	private int textureview_w = 0, textureview_h = 0;

    private Matrix camera_to_preview_matrix = new Matrix();
    private Matrix preview_to_camera_matrix = new Matrix();

    private double preview_targetRatio = 0.0;

	private boolean app_is_paused = true;
	private boolean has_surface = false;
	private boolean has_aspect_ratio = false;
	private double aspect_ratio = 0.0f;
	private CameraControllerManager camera_controller_manager = null;
	private CameraController camera_controller = null;
	private boolean has_permissions = true;
	private static final int PHASE_NORMAL = 0;
	private static final int PHASE_TIMER = 1;
	private static final int PHASE_TAKING_PHOTO = 2;
	private static final int PHASE_PREVIEW_PAUSED = 3;
	private int phase = PHASE_NORMAL;

	private boolean is_preview_started = false;

	private int current_orientation = 0; // orientation received by onOrientationChanged
	private int current_rotation = 0; // orientation relative to camera's orientation (used for parameters.setRotation())

	private boolean has_zoom = false;

	private int max_zoom_factor = 0;
	private List<Integer> zoom_ratios = null;

	private List<CameraController.Size> supported_preview_sizes = null;
	
	private List<CameraController.Size> sizes = null;
	private int current_size_index = -1; // this is an index into the sizes array, or -1 if sizes not yet set

	private Toast last_toast = null;

	private int ui_rotation = 0;

	private boolean supports_face_detection = false;
	private boolean using_face_detection = false;
	private CameraController.Face [] faces_detected = null;

	private final DecimalFormat decimal_format_1dp = new DecimalFormat("#.#");
	private final DecimalFormat decimal_format_2dp = new DecimalFormat("#.##");

	// for testing:
	public int count_cameraStartPreview = 0;
	public boolean test_fail_open_camera = false;

	public Preview(ApplicationInterface applicationInterface, Bundle savedInstanceState, ViewGroup parent) {
		if( MyDebug.LOG ) {
			Log.d(TAG, "new Preview");
		}
		
		this.applicationInterface = applicationInterface;

		this.using_android_l = applicationInterface.useCamera2();
		if( MyDebug.LOG ) {
			Log.d(TAG, "using_android_l?: " + using_android_l);
		}
		
		if( using_android_l ) {
        	// use a TextureView for Android L - had bugs with SurfaceView not resizing properly on Nexus 7; and good to use a TextureView anyway
        	// ideally we'd use a TextureView for older camera API too, but sticking with SurfaceView to avoid risk of breaking behaviour
			this.using_texture_view = true;
		}

        if( using_texture_view ) {
    		this.cameraSurface = new MyTextureView(getContext(), savedInstanceState, this);
    		// a TextureView can't be used both as a camera preview, and used for drawing on, so we use a separate CanvasView
    		this.canvasView = new CanvasView(getContext(), savedInstanceState, this);
    		camera_controller_manager = new CameraControllerManager2(getContext());
        }
        else {
    		this.cameraSurface = new MySurfaceView(getContext(), savedInstanceState, this);
    		camera_controller_manager = new CameraControllerManager1();
        }

		parent.addView(cameraSurface.getView());
		if( canvasView != null ) {
			parent.addView(canvasView);
		}
	}

	private Resources getResources() {
		return cameraSurface.getView().getResources();
	}
	
	public View getView() {
		return cameraSurface.getView();
	}

	// If this code is changed, important to test that face detection and touch to focus still works as expected, for front and back
	// cameras, for old and new API, including with zoom. Also test with SweetCameraActivity.setWindowFlagsForCamera() setting orientation as SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
	// and/or set "Rotate preview" option to 180 degrees.
	private void calculateCameraToPreviewMatrix() {
		if( MyDebug.LOG )
			Log.d(TAG, "calculateCameraToPreviewMatrix");
		if( camera_controller == null )
			return;
		camera_to_preview_matrix.reset();
	    if( !using_android_l ) {
			// from http://developer.android.com/reference/android/hardware/Camera.Face.html#rect
			// Need mirror for front camera
			boolean mirror = camera_controller.isFrontFacing();
			camera_to_preview_matrix.setScale(mirror ? -1 : 1, 1);
			// This is the value for android.hardware.Camera.setDisplayOrientation.
			int display_orientation = camera_controller.getDisplayOrientation();
			if( MyDebug.LOG ) {
				Log.d(TAG, "orientation of display relative to camera orientaton: " + display_orientation);
			}
			camera_to_preview_matrix.postRotate(display_orientation);
	    }
	    else {
			boolean mirror = camera_controller.isFrontFacing();
			camera_to_preview_matrix.setScale(1, mirror ? -1 : 1);
	    	int degrees = getDisplayRotationDegrees();
            int result = (camera_controller.getCameraOrientation() - degrees + 360) % 360;
			if( MyDebug.LOG ) {
				Log.d(TAG, "orientation of display relative to natural orientaton: " + degrees);
				Log.d(TAG, "orientation of display relative to camera orientaton: " + result);
			}
			camera_to_preview_matrix.postRotate(result);
	    }
	    // Camera driver coordinates range from (-1000, -1000) to (1000, 1000).
		// UI coordinates range from (0, 0) to (width, height).

		camera_to_preview_matrix.postScale(cameraSurface.getView().getWidth() / 2000f, cameraSurface.getView().getHeight() / 2000f);
		camera_to_preview_matrix.postTranslate(cameraSurface.getView().getWidth() / 2f, cameraSurface.getView().getHeight() / 2f);
	}
	
	private void calculatePreviewToCameraMatrix() {
		if( camera_controller == null )
			return;
		calculateCameraToPreviewMatrix();
		if( !camera_to_preview_matrix.invert(preview_to_camera_matrix) ) {
    		if( MyDebug.LOG )
    			Log.d(TAG, "calculatePreviewToCameraMatrix failed to invert matrix!?");
		}
	}

	public Matrix getCameraToPreviewMatrix() {
		calculateCameraToPreviewMatrix();
		return camera_to_preview_matrix;
	}

	Matrix getPreviewToCameraMatrix() {
		calculatePreviewToCameraMatrix();
		return preview_to_camera_matrix;
	}

	private ArrayList<CameraController.Area> getAreas(float x, float y) {
		float [] coords = {x, y};
		calculatePreviewToCameraMatrix();
		preview_to_camera_matrix.mapPoints(coords);
		float focus_x = coords[0];
		float focus_y = coords[1];
		
		int focus_size = 50;
		if( MyDebug.LOG ) {
			Log.d(TAG, "x, y: " + x + ", " + y);
			Log.d(TAG, "focus x, y: " + focus_x + ", " + focus_y);
		}
		Rect rect = new Rect();
		rect.left = (int)focus_x - focus_size;
		rect.right = (int)focus_x + focus_size;
		rect.top = (int)focus_y - focus_size;
		rect.bottom = (int)focus_y + focus_size;
		if( rect.left < -1000 ) {
			rect.left = -1000;
			rect.right = rect.left + 2*focus_size;
		}
		else if( rect.right > 1000 ) {
			rect.right = 1000;
			rect.left = rect.right - 2*focus_size;
		}
		if( rect.top < -1000 ) {
			rect.top = -1000;
			rect.bottom = rect.top + 2*focus_size;
		}
		else if( rect.bottom > 1000 ) {
			rect.bottom = 1000;
			rect.top = rect.bottom - 2*focus_size;
		}

	    ArrayList<CameraController.Area> areas = new ArrayList<CameraController.Area>();
	    areas.add(new CameraController.Area(rect, 1000));
	    return areas;
	}
    
    public void getMeasureSpec(int [] spec, int widthSpec, int heightSpec) {
    	if( !this.hasAspectRatio() ) {
    		spec[0] = widthSpec;
    		spec[1] = heightSpec;
    		return;
    	}
    	double aspect_ratio = this.getAspectRatio();

    	int previewWidth = MeasureSpec.getSize(widthSpec);
        int previewHeight = MeasureSpec.getSize(heightSpec);

        // Get the padding of the border background.
        int hPadding = cameraSurface.getView().getPaddingLeft() + cameraSurface.getView().getPaddingRight();
        int vPadding = cameraSurface.getView().getPaddingTop() + cameraSurface.getView().getPaddingBottom();

        // Resize the preview frame with correct aspect ratio.
        previewWidth -= hPadding;
        previewHeight -= vPadding;

        boolean widthLonger = previewWidth > previewHeight;
        int longSide = (widthLonger ? previewWidth : previewHeight);
        int shortSide = (widthLonger ? previewHeight : previewWidth);
        if (longSide > shortSide * aspect_ratio) {
            longSide = (int) ((double) shortSide * aspect_ratio);
        } else {
            shortSide = (int) ((double) longSide / aspect_ratio);
        }
        if (widthLonger) {
            previewWidth = longSide;
            previewHeight = shortSide;
        } else {
            previewWidth = shortSide;
            previewHeight = longSide;
        }

        // Add the padding of the border.
        previewWidth += hPadding;
        previewHeight += vPadding;

        spec[0] = MeasureSpec.makeMeasureSpec(previewWidth, MeasureSpec.EXACTLY);
        spec[1] = MeasureSpec.makeMeasureSpec(previewHeight, MeasureSpec.EXACTLY);
    }
    
    private void mySurfaceCreated() {
		this.has_surface = true;
		this.openCamera();
    }
    
    private void mySurfaceDestroyed() {
		this.has_surface = false;
		this.closeCamera();
    }
    
    private void mySurfaceChanged() {
		// surface size is now changed to match the aspect ratio of camera preview - so we shouldn't change the preview to match the surface size, so no need to restart preview here
        if( camera_controller == null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "camera not opened!");
            return;
        }
        
		// need to force a layoutUI update (e.g., so UI is oriented correctly when app goes idle, device is then rotated, and app is then resumed)
        applicationInterface.layoutUI();
    }
    
	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		if( MyDebug.LOG )
			Log.d(TAG, "surfaceCreated()");
		// The Surface has been created, acquire the camera and tell it where
		// to draw.
		mySurfaceCreated();
		cameraSurface.getView().setWillNotDraw(false); // see http://stackoverflow.com/questions/2687015/extended-surfaceviews-ondraw-method-never-called
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		if( MyDebug.LOG )
			Log.d(TAG, "surfaceDestroyed()");
		// Surface will be destroyed when we return, so stop the preview.
		// Because the CameraDevice object is not a shared resource, it's very
		// important to release it when the activity is paused.
		mySurfaceDestroyed();
	}
	
	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
		if( MyDebug.LOG )
			Log.d(TAG, "surfaceChanged " + w + ", " + h);
        if( holder.getSurface() == null ) {
            // preview surface does not exist
            return;
        }
		mySurfaceChanged();
	}
	
	@Override
	public void onSurfaceTextureAvailable(SurfaceTexture arg0, int width, int height) {
		if( MyDebug.LOG )
			Log.d(TAG, "onSurfaceTextureAvailable()");
		this.set_textureview_size = true;
		this.textureview_w = width;
		this.textureview_h = height;
		mySurfaceCreated();
		configureTransform();
	}

	@Override
	public boolean onSurfaceTextureDestroyed(SurfaceTexture arg0) {
		if( MyDebug.LOG )
			Log.d(TAG, "onSurfaceTextureDestroyed()");
		this.set_textureview_size = false;
		this.textureview_w = 0;
		this.textureview_h = 0;
		mySurfaceDestroyed();
		return true;
	}

	@Override
	public void onSurfaceTextureSizeChanged(SurfaceTexture arg0, int width, int height) {
		if( MyDebug.LOG )
			Log.d(TAG, "onSurfaceTextureSizeChanged " + width + ", " + height);
		this.set_textureview_size = true;
		this.textureview_w = width;
		this.textureview_h = height;
		mySurfaceChanged();
		configureTransform();
	}

	@Override
	public void onSurfaceTextureUpdated(SurfaceTexture arg0) {
	}

    private void configureTransform() { 
		if( MyDebug.LOG )
			Log.d(TAG, "configureTransform");
    	if( camera_controller == null || !this.set_preview_size || !this.set_textureview_size )
    		return;
		if( MyDebug.LOG )
			Log.d(TAG, "textureview size: " + textureview_w + ", " + textureview_h);
    	int rotation = getDisplayRotation();
    	Matrix matrix = new Matrix(); 
		RectF viewRect = new RectF(0, 0, this.textureview_w, this.textureview_h); 
		RectF bufferRect = new RectF(0, 0, this.preview_h, this.preview_w); 
		float centerX = viewRect.centerX(); 
		float centerY = viewRect.centerY(); 
        if( Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation ) { 
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY()); 
	        matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL); 
	        float scale = Math.max(
	        		(float) textureview_h / preview_h, 
                    (float) textureview_w / preview_w); 
            matrix.postScale(scale, scale, centerX, centerY); 
            matrix.postRotate(90 * (rotation - 2), centerX, centerY); 
        } 
        cameraSurface.setTransform(matrix); 
    }
	
	private Context getContext() {
		return applicationInterface.getContext();
	}
	
	private void closeCamera() {
		long debug_time = 0;
		if( MyDebug.LOG ) {
			Log.d(TAG, "closeCamera()");
			debug_time = System.currentTimeMillis();
		}
		preview_targetRatio = 0.0;
		// n.b., don't reset has_set_location, as we can remember the location when switching camera
		applicationInterface.cameraClosed();
		if( camera_controller != null ) {
			// need to check for camera being non-null again - if an error occurred stopping the video, we will have closed the camera, and may not be able to reopen
			if( camera_controller != null ) {
				//camera.setPreviewCallback(null);
				if( MyDebug.LOG ) {
					Log.d(TAG, "closeCamera: about to pause preview: " + (System.currentTimeMillis() - debug_time));
				}
				pausePreview();
				if( MyDebug.LOG ) {
					Log.d(TAG, "closeCamera: about to release camera controller: " + (System.currentTimeMillis() - debug_time));
				}
				camera_controller.release();
				camera_controller = null;
			}
		}
		if( MyDebug.LOG ) {
			Log.d(TAG, "closeCamera: total time: " + (System.currentTimeMillis() - debug_time));
		}
	}
	
	public void pausePreview() {
		long debug_time = 0;
		if( MyDebug.LOG ) {
			Log.d(TAG, "pausePreview()");
			debug_time = System.currentTimeMillis();
		}
		if( camera_controller == null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "camera not opened!");
			return;
		}

		this.setPreviewPaused(false);
		if( MyDebug.LOG ) {
			Log.d(TAG, "pausePreview: about to stop preview: " + (System.currentTimeMillis() - debug_time));
		}
		camera_controller.stopPreview();
		this.phase = PHASE_NORMAL;
		this.is_preview_started = false;
		if( MyDebug.LOG ) {
			Log.d(TAG, "pausePreview: about to call cameraInOperation: " + (System.currentTimeMillis() - debug_time));
		}
		applicationInterface.cameraInOperation(false);
		if( MyDebug.LOG ) {
			Log.d(TAG, "pausePreview: total time: " + (System.currentTimeMillis() - debug_time));
		}
	}
	
	//private int debug_count_opencamera = 0; // see usage below

	/** Try to open the camera. Should only be called if camera_controller==null.
	 */
	private void openCamera() {
		long debug_time = 0;
		if( MyDebug.LOG ) {
			Log.d(TAG, "openCamera()");
			debug_time = System.currentTimeMillis();
		}
		// need to init everything now, in case we don't open the camera (but these may already be initialised from an earlier call - e.g., if we are now switching to another camera)
		// n.b., don't reset has_set_location, as we can remember the location when switching camera
		is_preview_started = false; // theoretically should be false anyway, but I had one RuntimeException from surfaceCreated()->openCamera()->setupCamera()->setPreviewSize() because is_preview_started was true, even though the preview couldn't have been started
    	set_preview_size = false;
    	preview_w = 0;
    	preview_h = 0;
		preview_targetRatio = 0.0;
		has_zoom = false;
		max_zoom_factor = 0;
		zoom_ratios = null;
		faces_detected = null;
		supports_face_detection = false;
		using_face_detection = false;
		sizes = null;
		current_size_index = -1;
		applicationInterface.cameraInOperation(false);
		if( MyDebug.LOG )
			Log.d(TAG, "done showGUI");
		if( !this.has_surface ) {
			if( MyDebug.LOG ) {
				Log.d(TAG, "preview surface not yet available");
			}
			return;
		}
		if( this.app_is_paused ) {
			if( MyDebug.LOG ) {
				Log.d(TAG, "don't open camera as app is paused");
			}
			return;
		}
		
		if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ) {
			if( MyDebug.LOG )
				Log.d(TAG, "check for permissions");
			if( ContextCompat.checkSelfPermission(getContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ) {
				if( MyDebug.LOG )
					Log.d(TAG, "camera permission not available");
				has_permissions = false;
		    	applicationInterface.requestCameraPermission();
		    	// return for now - the application should try to reopen the camera if permission is granted
				return;
			}
			if( ContextCompat.checkSelfPermission(getContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ) {
				if( MyDebug.LOG )
					Log.d(TAG, "storage permission not available");
				has_permissions = false;
		    	applicationInterface.requestStoragePermission();
		    	// return for now - the application should try to reopen the camera if permission is granted
				return;
			}
			if( MyDebug.LOG )
				Log.d(TAG, "permissions available");
		}
		// set in case this was previously set to false
		has_permissions = true;

		try {
			int cameraId = applicationInterface.getCameraIdPref();
			if( cameraId < 0 || cameraId >= camera_controller_manager.getNumberOfCameras() ) {
				if( MyDebug.LOG )
					Log.d(TAG, "invalid cameraId: " + cameraId);
				cameraId = 0;
			}
			if( MyDebug.LOG ) {
				Log.d(TAG, "try to open camera: " + cameraId);
				Log.d(TAG, "openCamera: time before opening camera: " + (System.currentTimeMillis() - debug_time));
			}
			if( test_fail_open_camera ) {
				if( MyDebug.LOG )
					Log.d(TAG, "test failing to open camera");
				throw new CameraControllerException();
			}
	        if( using_android_l ) {
	    		CameraController.ErrorCallback previewErrorCallback = new CameraController.ErrorCallback() {
	    			public void onError() {
	        			if( MyDebug.LOG )
	    					Log.e(TAG, "error from CameraController: preview failed to start");
	        			applicationInterface.onFailedStartPreview();
	        	    }
	    		};
	        	camera_controller = new CameraController2(this.getContext(), cameraId, previewErrorCallback);
	        }
	        else
				camera_controller = new CameraController1(cameraId);
			//throw new CameraControllerException(); // uncomment to test camera not opening
		}
		catch(CameraControllerException e) {
			if( MyDebug.LOG )
				Log.e(TAG, "Failed to open camera: " + e.getMessage());
			e.printStackTrace();
			camera_controller = null;
		}
		if( MyDebug.LOG ) {
			Log.d(TAG, "openCamera: time after opening camera: " + (System.currentTimeMillis() - debug_time));
		}
		boolean take_photo = false;
		if( camera_controller != null ) {
			Activity activity = (Activity)this.getContext();
			if( MyDebug.LOG )
				Log.d(TAG, "intent: " + activity.getIntent());

	        this.setCameraDisplayOrientation();
	        new OrientationEventListener(activity) {
				@Override
				public void onOrientationChanged(int orientation) {
					Preview.this.onOrientationChanged(orientation);
				}
	        }.enable();
			if( MyDebug.LOG ) {
				Log.d(TAG, "openCamera: time after setting orientation: " + (System.currentTimeMillis() - debug_time));
			}

			if( MyDebug.LOG )
				Log.d(TAG, "call setPreviewDisplay");
			cameraSurface.setPreviewDisplay(camera_controller);
			if( MyDebug.LOG ) {
				Log.d(TAG, "openCamera: time after setting preview display: " + (System.currentTimeMillis() - debug_time));
			}

		    setupCamera(take_photo);
		}

		if( MyDebug.LOG ) {
			Log.d(TAG, "openCamera: total time to open camera: " + (System.currentTimeMillis() - debug_time));
		}
	}

	/** Try to reopen the camera, if not currently open (e.g., permission wasn't granted, but now it is).
	 */
	public void retryOpenCamera() {
		if( MyDebug.LOG )
			Log.d(TAG, "retryOpenCamera()");
        if( camera_controller == null ) {
    		if( MyDebug.LOG )
    			Log.d(TAG, "try to reopen camera");
    		this.openCamera();
        }
        else {
    		if( MyDebug.LOG )
    			Log.d(TAG, "camera already open");
        }
	}

	/** Returns false if we failed to open the camera because camera or storage permission wasn't available.
	 */
	public boolean hasPermissions() {
		return has_permissions;
	}

	public void setupCamera(boolean take_photo) {
		if( MyDebug.LOG )
			Log.d(TAG, "setupCamera()");
		if( camera_controller == null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "camera not opened!");
			return;
		}

		setupCameraParameters();

		camera_controller.setRaw(false);

		setPreviewSize();

		startCameraPreview();

		applicationInterface.cameraSetup();

	}

	private void setupCameraParameters() {
		if( MyDebug.LOG )
			Log.d(TAG, "setupCameraParameters()");
		long debug_time = 0;
		if( MyDebug.LOG ) {
			debug_time = System.currentTimeMillis();
		}
		if( MyDebug.LOG ) {
			Log.d(TAG, "setupCameraParameters: time after setting scene mode: " + (System.currentTimeMillis() - debug_time));
		}
		
		{
			// grab all read-only info from parameters
			if( MyDebug.LOG )
				Log.d(TAG, "grab info from parameters");
			CameraController.CameraFeatures camera_features = camera_controller.getCameraFeatures();
			this.has_zoom = camera_features.is_zoom_supported;
			if( this.has_zoom ) {
				this.max_zoom_factor = camera_features.max_zoom;
				this.zoom_ratios = camera_features.zoom_ratios;
			}
			this.supports_face_detection = camera_features.supports_face_detection;
			this.sizes = camera_features.picture_sizes;
	        this.supported_preview_sizes = camera_features.preview_sizes;
		}
		if( MyDebug.LOG ) {
			Log.d(TAG, "setupCameraParameters: time after getting read only info: " + (System.currentTimeMillis() - debug_time));
		}
		
		{
			if( MyDebug.LOG )
				Log.d(TAG, "set up face detection");
			// get face detection supported
			this.faces_detected = null;
			if( this.supports_face_detection ) {
				this.using_face_detection = true;
			}
			else {
				this.using_face_detection = false;
			}

			if( MyDebug.LOG ) {
				Log.d(TAG, "supports_face_detection?: " + supports_face_detection);
				Log.d(TAG, "using_face_detection?: " + using_face_detection);
			}
			if( this.using_face_detection ) {
				class MyFaceDetectionListener implements CameraController.FaceDetectionListener {
				    @Override
				    public void onFaceDetection(CameraController.Face[] faces) {
				    	faces_detected = new CameraController.Face[faces.length];
				    	System.arraycopy(faces, 0, faces_detected, 0, faces.length);				    	
				    }
				}
				camera_controller.setFaceDetectionListener(new MyFaceDetectionListener());
			}
		}

		{
			if( MyDebug.LOG )
				Log.d(TAG, "set up picture sizes");
			if( MyDebug.LOG ) {
				for(int i=0;i<sizes.size();i++) {
					CameraController.Size size = sizes.get(i);
		        	Log.d(TAG, "supported picture size: " + size.width + " , " + size.height);
				}
			}
			current_size_index = -1;
			Pair<Integer, Integer> resolution = new Pair<>(1920, 1080); // Set Preview Size ***Richard***
			if( resolution != null ) {
				int resolution_w = resolution.first;
				int resolution_h = resolution.second;
				// now find size in valid list
				for(int i=0;i<sizes.size() && current_size_index==-1;i++) {
					CameraController.Size size = sizes.get(i);
		        	if( size.width == resolution_w && size.height == resolution_h ) {
		        		current_size_index = i;
						if( MyDebug.LOG )
							Log.d(TAG, "set current_size_index to: " + current_size_index);
		        	}
				}
				if( current_size_index == -1 ) {
					if( MyDebug.LOG )
						Log.e(TAG, "failed to find valid size");
				}
			}

			if( current_size_index == -1 ) {
				// set to largest
				CameraController.Size current_size = null;
				for(int i=0;i<sizes.size();i++) {
					CameraController.Size size = sizes.get(i);
		        	if( current_size == null || size.width*size.height > current_size.width*current_size.height ) {
		        		current_size_index = i;
		        		current_size = size;
		        	}
		        }
			}
			if( current_size_index != -1 ) {
				CameraController.Size current_size = sizes.get(current_size_index);
	    		if( MyDebug.LOG )
	    			Log.d(TAG, "Current size index " + current_size_index + ": " + current_size.width + ", " + current_size.height);

			}
			// size set later in setPreviewSize()
		}
		if( MyDebug.LOG ) {
			Log.d(TAG, "setupCameraParameters: time after picture sizes: " + (System.currentTimeMillis() - debug_time));
		}
	}
	
	private void setPreviewSize() {
		if( MyDebug.LOG )
			Log.d(TAG, "setPreviewSize()");
		// also now sets picture size
		if( camera_controller == null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "camera not opened!");
			return;
		}
		if( is_preview_started ) {
			if( MyDebug.LOG )
				Log.d(TAG, "setPreviewSize() shouldn't be called when preview is running");
			throw new RuntimeException(); // throw as RuntimeException, as this is a programming error
		}

		// first set picture size (for photo mode, must be done now so we can set the picture size from this; for video, doesn't really matter when we set it)
		CameraController.Size new_size = null;

		if( current_size_index != -1 ) {
			new_size = sizes.get(current_size_index);
		}

    	if( new_size != null ) {
    		camera_controller.setPictureSize(new_size.width, new_size.height);
    	}
		// set optimal preview size
        if( supported_preview_sizes != null && supported_preview_sizes.size() > 0 ) {
//        	CameraController.Size best_size = getOptimalPreviewSize(supported_preview_sizes);
			CameraController.Size best_size = new CameraController.Size(1920, 1080);

        	camera_controller.setPreviewSize(best_size.width, best_size.height);

        	this.set_preview_size = true;
        	this.preview_w = best_size.width;
        	this.preview_h = best_size.height;
    		this.setAspectRatio( ((double)best_size.width) / (double)best_size.height );
        }
	}
	
	private static String formatFloatToString(final float f) {
		final int i=(int)f;
		if( f == i )
			return Integer.toString(i);
		return String.format(Locale.getDefault(), "%.2f", f);
	}

	private static int greatestCommonFactor(int a, int b) {
	    while( b > 0 ) {
	        int temp = b;
	        b = a % b;
	        a = temp;
	    }
	    return a;
	}
	
	private static String getAspectRatio(int width, int height) {
		int gcf = greatestCommonFactor(width, height);
		if( gcf > 0 ) {
			// had a Google Play crash due to gcf being 0!? Implies width must be zero
			width /= gcf;
			height /= gcf;
		}
		return width + ":" + height;
	}
	
	public static String getMPString(int width, int height) {
		float mp = (width*height)/1000000.0f;
		return formatFloatToString(mp) + "MP";
	}
	
	public static String getAspectRatioMPString(int width, int height) {
		return "(" + getAspectRatio(width, height) + ", " + getMPString(width, height) + ")";
	}

	public double getTargetRatio() {
		return preview_targetRatio;
	}

	private double calculateTargetRatioForPreview(Point display_size) {
        double targetRatio = 0.0f;

		if( MyDebug.LOG )
			Log.d(TAG, "set preview aspect ratio from display size");
		// base target ratio from display size - means preview will fill the device's display as much as possible
		// but if the preview's aspect ratio differs from the actual photo/video size, the preview will show a cropped version of what is actually taken
		targetRatio = ((double)display_size.x) / (double)display_size.y;
		this.preview_targetRatio = targetRatio;
		if( MyDebug.LOG )
			Log.d(TAG, "targetRatio: " + targetRatio);
		return targetRatio;
	}

	public CameraController.Size getClosestSize(List<CameraController.Size> sizes, double targetRatio) {
		if( MyDebug.LOG )
			Log.d(TAG, "getClosestSize()");
		CameraController.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;
        for(CameraController.Size size : sizes) {
            double ratio = (double)size.width / size.height;
            if( Math.abs(ratio - targetRatio) < minDiff ) {
                optimalSize = size;
                minDiff = Math.abs(ratio - targetRatio);
            }
        }
        return optimalSize;
	}

	public CameraController.Size getOptimalPreviewSize(List<CameraController.Size> sizes) {
		if( MyDebug.LOG )
			Log.d(TAG, "getOptimalPreviewSize()");
		final double ASPECT_TOLERANCE = 0.05;
        if( sizes == null )
        	return null;
        CameraController.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;
        Point display_size = new Point();
		Activity activity = (Activity)this.getContext();
        {
            Display display = activity.getWindowManager().getDefaultDisplay();
            display.getSize(display_size);
    		if( MyDebug.LOG )
    			Log.d(TAG, "display_size: " + display_size.x + " x " + display_size.y);
        }
        double targetRatio = calculateTargetRatioForPreview(display_size);
        int targetHeight = Math.min(display_size.y, display_size.x);
        if( targetHeight <= 0 ) {
            targetHeight = display_size.y;
        }
        // Try to find the size which matches the aspect ratio, and is closest match to display height
        for(CameraController.Size size : sizes) {
    		if( MyDebug.LOG )
    			Log.d(TAG, "    supported preview size: " + size.width + ", " + size.height);
            double ratio = (double)size.width / size.height;
            if( Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE )
            	continue;
            if( Math.abs(size.height - targetHeight) < minDiff ) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }
        if( optimalSize == null ) {
        	// can't find match for aspect ratio, so find closest one
    		if( MyDebug.LOG )
    			Log.d(TAG, "no preview size matches the aspect ratio");
    		optimalSize = getClosestSize(sizes, targetRatio);
        }
		if( MyDebug.LOG ) {
			Log.d(TAG, "chose optimalSize: " + optimalSize.width + " x " + optimalSize.height);
			Log.d(TAG, "optimalSize ratio: " + ((double)optimalSize.width / optimalSize.height));
		}
        return optimalSize;
    }

    private void setAspectRatio(double ratio) {
        if( ratio <= 0.0 )
        	throw new IllegalArgumentException();

        has_aspect_ratio = true;
        if( aspect_ratio != ratio ) {
        	aspect_ratio = ratio;
    		if( MyDebug.LOG )
    			Log.d(TAG, "new aspect ratio: " + aspect_ratio);
    		cameraSurface.getView().requestLayout();
    		if( canvasView != null ) {
    			canvasView.requestLayout();
    		}
        }
    }
    
    private boolean hasAspectRatio() {
    	return has_aspect_ratio;
    }

    private double getAspectRatio() {
    	return aspect_ratio;
    }

    /** Returns the ROTATION_* enum of the display relative to the natural device orientation.
     */
    public int getDisplayRotation() {
    	// gets the display rotation (as a Surface.ROTATION_* constant), taking into account the getRotatePreviewPreferenceKey() setting
		Activity activity = (Activity)this.getContext();
	    int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();

		String rotate_preview = applicationInterface.getPreviewRotationPref();
		if( MyDebug.LOG )
			Log.d(TAG, "    rotate_preview = " + rotate_preview);
		if( rotate_preview.equals("180") ) {
		    switch (rotation) {
		    	case Surface.ROTATION_0: rotation = Surface.ROTATION_180; break;
		    	case Surface.ROTATION_90: rotation = Surface.ROTATION_270; break;
		    	case Surface.ROTATION_180: rotation = Surface.ROTATION_0; break;
		    	case Surface.ROTATION_270: rotation = Surface.ROTATION_90; break;
	    		default:
	    			break;
		    }
		}

		return rotation;
    }
    
    /** Returns the rotation in degrees of the display relative to the natural device orientation.
     */
	private int getDisplayRotationDegrees() {
		if( MyDebug.LOG )
			Log.d(TAG, "getDisplayRotationDegrees");
	    int rotation = getDisplayRotation();
	    int degrees = 0;
	    switch (rotation) {
	    	case Surface.ROTATION_0: degrees = 0; break;
	        case Surface.ROTATION_90: degrees = 90; break;
	        case Surface.ROTATION_180: degrees = 180; break;
	        case Surface.ROTATION_270: degrees = 270; break;
    		default:
    			break;
	    }
		if( MyDebug.LOG )
			Log.d(TAG, "    degrees = " + degrees);
		return degrees;
	}

	public void setCameraDisplayOrientation() {
		if( MyDebug.LOG )
			Log.d(TAG, "setCameraDisplayOrientation()");
		if( camera_controller == null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "camera not opened!");
			return;
		}
	    if( using_android_l ) {
	    	// need to configure the textureview
			configureTransform();
	    }
	    else {
	    	int degrees = getDisplayRotationDegrees();
			if( MyDebug.LOG )
				Log.d(TAG, "    degrees = " + degrees);
			// note the code to make the rotation relative to the camera sensor is done in camera_controller.setDisplayOrientation()
			camera_controller.setDisplayOrientation(degrees);
	    }
	}
	
	private void onOrientationChanged(int orientation) {

		if( orientation == OrientationEventListener.ORIENTATION_UNKNOWN )
			return;
		if( camera_controller == null ) {
			return;
		}
	    orientation = (orientation + 45) / 90 * 90;
	    this.current_orientation = orientation % 360;
	    int new_rotation = 0;
	    int camera_orientation = camera_controller.getCameraOrientation();
	    if( camera_controller.isFrontFacing() ) {
	    	new_rotation = (camera_orientation - orientation + 360) % 360;
	    }
	    else {
	    	new_rotation = (camera_orientation + orientation) % 360;
	    }
	    if( new_rotation != current_rotation ) {
			/*if( MyDebug.LOG ) {
				Log.d(TAG, "    current_orientation is " + current_orientation);
				Log.d(TAG, "    info orientation is " + camera_orientation);
				Log.d(TAG, "    set Camera rotation from " + current_rotation + " to " + new_rotation);
			}*/
	    	this.current_rotation = new_rotation;
	    }
	}

	private int getDeviceDefaultOrientation() {
	    WindowManager windowManager = (WindowManager)this.getContext().getSystemService(Context.WINDOW_SERVICE);
	    Configuration config = getResources().getConfiguration();
	    int rotation = windowManager.getDefaultDisplay().getRotation();
	    if( ( (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) &&
	    		config.orientation == Configuration.ORIENTATION_LANDSCAPE )
	    		|| ( (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) &&    
	            config.orientation == Configuration.ORIENTATION_PORTRAIT ) ) {
	    	return Configuration.ORIENTATION_LANDSCAPE;
	    }
	    else { 
	    	return Configuration.ORIENTATION_PORTRAIT;
	    }
	}

	public void draw(Canvas canvas) {
		/*if( MyDebug.LOG )
			Log.d(TAG, "draw()");*/
		if( this.app_is_paused ) {
    		/*if( MyDebug.LOG )
    			Log.d(TAG, "draw(): app is paused");*/
			return;
		}
		applicationInterface.onDrawPreview(canvas);
	}

	public boolean canSwitchCamera() {
		if( this.phase == PHASE_TAKING_PHOTO ) {
			if( MyDebug.LOG )
				Log.d(TAG, "currently taking a photo");
			return false;
		}
		int n_cameras = camera_controller_manager.getNumberOfCameras();
		if( MyDebug.LOG )
			Log.d(TAG, "found " + n_cameras + " cameras");
		if( n_cameras == 0 )
			return false;
		return true;
	}
	
	public void setCamera(int cameraId) {
		if( MyDebug.LOG )
			Log.d(TAG, "setCamera()");
		if( cameraId < 0 || cameraId >= camera_controller_manager.getNumberOfCameras() ) {
			if( MyDebug.LOG )
				Log.d(TAG, "invalid cameraId: " + cameraId);
			cameraId = 0;
		}
		if( canSwitchCamera() ) {
			closeCamera();
			applicationInterface.setCameraIdPref(cameraId);
			this.openCamera();
		}
	}
	
	public int [] chooseBestPreviewFps(List<int []> fps_ranges) {
		if( MyDebug.LOG )
			Log.d(TAG, "chooseBestPreviewFps()");

		// find value with lowest min that has max >= 30; if more than one of these, pick the one with highest max
		int selected_min_fps = -1, selected_max_fps = -1;
        for(int [] fps_range : fps_ranges) {
	    	if( MyDebug.LOG ) {
    			Log.d(TAG, "    supported fps range: " + fps_range[0] + " to " + fps_range[1]);
	    	}
			int min_fps = fps_range[0];
			int max_fps = fps_range[1];
			if( max_fps >= 30000 ) {
				if( selected_min_fps == -1 || min_fps < selected_min_fps ) {
    				selected_min_fps = min_fps;
    				selected_max_fps = max_fps;
				}
				else if( min_fps == selected_min_fps && max_fps > selected_max_fps ) {
    				selected_min_fps = min_fps;
    				selected_max_fps = max_fps;
				}
			}
        }

        if( selected_min_fps != -1 ) {
	    	if( MyDebug.LOG ) {
    			Log.d(TAG, "    chosen fps range: " + selected_min_fps + " to " + selected_max_fps);
	    	}
        }
        else {
        	// just pick the widest range; if more than one, pick the one with highest max
        	int selected_diff = -1;
            for(int [] fps_range : fps_ranges) {
    			int min_fps = fps_range[0];
    			int max_fps = fps_range[1];
    			int diff = max_fps - min_fps;
    			if( selected_diff == -1 || diff > selected_diff ) {
    				selected_min_fps = min_fps;
    				selected_max_fps = max_fps;
    				selected_diff = diff;
    			}
    			else if( diff == selected_diff && max_fps > selected_max_fps ) {
    				selected_min_fps = min_fps;
    				selected_max_fps = max_fps;
    				selected_diff = diff;
    			}
            }
	    	if( MyDebug.LOG )
	    		Log.d(TAG, "    can't find fps range 30fps or better, so picked widest range: " + selected_min_fps + " to " + selected_max_fps);
        }
    	return new int[]{selected_min_fps, selected_max_fps};
	}

	private void setPreviewFps() {
		if( MyDebug.LOG )
			Log.d(TAG, "setPreviewFps()");
		List<int []> fps_ranges = camera_controller.getSupportedPreviewFpsRange();
		if( fps_ranges == null || fps_ranges.size() == 0 ) {
			if( MyDebug.LOG )
				Log.d(TAG, "fps_ranges not available");
			return;
		}
		int [] selected_fps = null;

		selected_fps = chooseBestPreviewFps(fps_ranges);
        camera_controller.setPreviewFpsRange(selected_fps[0], selected_fps[1]);
	}


    public void startCameraPreview() {
		//if( camera != null && !is_taking_photo && !is_preview_started ) {
		if( camera_controller != null && !is_preview_started ) {
			setPreviewFps();
    		try {
    			camera_controller.startPreview();
		    	count_cameraStartPreview++;
    		}
    		catch(CameraControllerException e) {
    			if( MyDebug.LOG )
    				Log.d(TAG, "CameraControllerException trying to startPreview");
    			e.printStackTrace();
    			applicationInterface.onFailedStartPreview();
    			return;
    		}
			this.is_preview_started = true;
			if( this.using_face_detection ) {
				if( MyDebug.LOG )
					Log.d(TAG, "start face detection");
				camera_controller.startFaceDetection();
				faces_detected = null;
			}
		}
		this.setPreviewPaused(false);
    }

    private void setPreviewPaused(boolean paused) {
		if( MyDebug.LOG )
			Log.d(TAG, "setPreviewPaused: " + paused);
		applicationInterface.hasPausedPreview(paused);
	    if( paused ) {
	    	this.phase = PHASE_PREVIEW_PAUSED;
		}
		else {
	    	this.phase = PHASE_NORMAL;
			applicationInterface.cameraInOperation(false);
		}
    }

    public boolean supportsFaceDetection() {
		if( MyDebug.LOG )
			Log.d(TAG, "supportsFaceDetection");
    	return supports_face_detection;
    }

    public int getCameraId() {
        if( camera_controller == null )
            return 0;
        return camera_controller.getCameraId();
    }

    public String getCameraAPI() {
    	if( camera_controller == null )
    		return "None";
    	return camera_controller.getAPI();
    }
    
    public void onResume() {
		if( MyDebug.LOG )
			Log.d(TAG, "onResume");
		this.app_is_paused = false;
		this.openCamera();
    }

    public void onPause() {
		if( MyDebug.LOG )
			Log.d(TAG, "onPause");
		this.app_is_paused = true;
		this.closeCamera();
    }

	public void onSaveInstanceState(Bundle state) {
		if( MyDebug.LOG )
			Log.d(TAG, "onSaveInstanceState");
	}

    public void showToast(final ToastBoxer clear_toast, final int message_id) {
    	showToast(clear_toast, getResources().getString(message_id));
    }

    public void showToast(final ToastBoxer clear_toast, final String message) {
    	showToast(clear_toast, message, 32);
    }

    public void showToast(final ToastBoxer clear_toast, final String message, final int offset_y_dp) {
		if( !applicationInterface.getShowToastsPref() ) {
			return;
		}
    	
		class RotatedTextView extends View {
			private String [] lines = null;
			private Paint paint = new Paint();
			private Rect bounds = new Rect();
			private Rect sub_bounds = new Rect();
			private RectF rect = new RectF();

			public RotatedTextView(String text, Context context) {
				super(context);

				this.lines = text.split("\n");
			}
			
			void setText(String text) {
				this.lines = text.split("\n");
			}

			@Override 
			protected void onDraw(Canvas canvas) {
				final float scale = Preview.this.getResources().getDisplayMetrics().density;
				paint.setTextSize(14 * scale + 0.5f); // convert dps to pixels
				paint.setShadowLayer(1, 0, 1, Color.BLACK);
				//paint.getTextBounds(text, 0, text.length(), bounds);
				boolean first_line = true;
				for(String line : lines) {
					paint.getTextBounds(line, 0, line.length(), sub_bounds);
					/*if( MyDebug.LOG ) {
						Log.d(TAG, "line: " + line + " sub_bounds: " + sub_bounds);
					}*/
					if( first_line ) {
						bounds.set(sub_bounds);
						first_line = false;
					}
					else {
						bounds.top = Math.min(sub_bounds.top, bounds.top);
						bounds.bottom = Math.max(sub_bounds.bottom, bounds.bottom);
						bounds.left = Math.min(sub_bounds.left, bounds.left);
						bounds.right = Math.max(sub_bounds.right, bounds.right);
					}
				}
				/*if( MyDebug.LOG ) {
					Log.d(TAG, "bounds: " + bounds);
				}*/
				int height = bounds.bottom - bounds.top + 2;
				bounds.bottom += ((lines.length-1) * height)/2;
				bounds.top -= ((lines.length-1) * height)/2;
				final int padding = (int) (14 * scale + 0.5f); // convert dps to pixels
				final int offset_y = (int) (offset_y_dp * scale + 0.5f); // convert dps to pixels
				canvas.save();
				canvas.rotate(ui_rotation, canvas.getWidth()/2.0f, canvas.getHeight()/2.0f);

				rect.left = canvas.getWidth()/2 - bounds.width()/2 + bounds.left - padding;
				rect.top = canvas.getHeight()/2 + bounds.top - padding + offset_y;
				rect.right = canvas.getWidth()/2 - bounds.width()/2 + bounds.right + padding;
				rect.bottom = canvas.getHeight()/2 + bounds.bottom + padding + offset_y;

				paint.setStyle(Paint.Style.FILL);
				paint.setColor(Color.rgb(50, 50, 50));
				//canvas.drawRect(rect, paint);
				final float radius = (24 * scale + 0.5f); // convert dps to pixels
				canvas.drawRoundRect(rect, radius, radius, paint);

				paint.setColor(Color.WHITE);
				int ypos = canvas.getHeight()/2 + offset_y - ((lines.length-1) * height)/2;
				for(String line : lines) {
					canvas.drawText(line, canvas.getWidth()/2 - bounds.width()/2, ypos, paint);
					ypos += height;
				}
				canvas.restore();
			} 
		}

		if( MyDebug.LOG )
			Log.d(TAG, "showToast: " + message);
		final Activity activity = (Activity)this.getContext();
		// We get a crash on emulator at least if Toast constructor isn't run on main thread (e.g., the toast for taking a photo when on timer).
		// Also see http://stackoverflow.com/questions/13267239/toast-from-a-non-ui-thread
		activity.runOnUiThread(new Runnable() {
			public void run() {
				/*if( clear_toast != null && clear_toast.toast != null )
					clear_toast.toast.cancel();

				Toast toast = new Toast(activity);
				if( clear_toast != null )
					clear_toast.toast = toast;*/
				// This method is better, as otherwise a previous toast (with different or no clear_toast) never seems to clear if we repeatedly issue new toasts - this doesn't happen if we reuse existing toasts if possible
				// However should only do this if the previous toast was the most recent toast (to avoid messing up ordering)
				Toast toast = null;
				if( clear_toast != null && clear_toast.toast != null && clear_toast.toast == last_toast ) {
					if( MyDebug.LOG )
						Log.d(TAG, "reuse last toast: " + last_toast);
					toast = clear_toast.toast;
					// for performance, important to reuse the same view, instead of creating a new one (otherwise we get jerky preview update e.g. for changing manual focus slider)
					RotatedTextView view = (RotatedTextView)toast.getView();
					view.setText(message);
					view.invalidate(); // make sure the toast is redrawn
					toast.setView(view);
				}
				else {
					if( clear_toast != null && clear_toast.toast != null ) {
						if( MyDebug.LOG )
							Log.d(TAG, "cancel last toast: " + clear_toast.toast);
						clear_toast.toast.cancel();
					}
					toast = new Toast(activity);
					if( MyDebug.LOG )
						Log.d(TAG, "created new toast: " + toast);
					if( clear_toast != null )
						clear_toast.toast = toast;
					View text = new RotatedTextView(message, activity);
					toast.setView(text);
				}
				toast.setDuration(Toast.LENGTH_SHORT);
				toast.show();
				last_toast = toast;
			}
		});
	}
	
	public void setUIRotation(int ui_rotation) {
		if( MyDebug.LOG )
			Log.d(TAG, "setUIRotation");
		this.ui_rotation = ui_rotation;
	}
	
	public int getUIRotation() {
		return this.ui_rotation;
	}
    
    public boolean usingCamera2API() {
    	return this.using_android_l;
    }

    public CameraController getCameraController() {
    	return this.camera_controller;
    }
    
    public CameraControllerManager getCameraControllerManager() {
    	return this.camera_controller_manager;
    }
    
    public boolean isPreviewPaused() {
    	return this.phase == PHASE_PREVIEW_PAUSED;
    }

    public boolean isPreviewStarted() {
    	return this.is_preview_started;
    }
    
    public CameraController.Face [] getFacesDetected() {
    	// FindBugs warns about returning the array directly, but in fact we need to return direct access rather than copying, so that the on-screen display of faces rectangles updates
    	return this.faces_detected;
    }

}
