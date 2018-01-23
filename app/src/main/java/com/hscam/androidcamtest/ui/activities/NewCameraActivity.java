package com.hscam.androidcamtest.ui.activities;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;

import com.hscam.androidcamtest.MyApplicationInterface;
import com.hscam.androidcamtest.MyDebug;
import com.hscam.androidcamtest.R;
import com.hscam.androidcamtest.cameracontroller.CameraControllerManager2;
import com.hscam.androidcamtest.preview.Preview;
import com.hscam.androidcamtest.ui.MainUI;

public class NewCameraActivity extends AppCompatActivity {

    private static final String TAG = "NewCameraActivity";

    private boolean supports_camera2 = false;
    private Preview preview = null;
    private MyApplicationInterface applicationInterface;

    final private int MY_PERMISSIONS_REQUEST_CAMERA = 0;
    final private int MY_PERMISSIONS_REQUEST_STORAGE = 1;

    private MainUI mainUI = null;

    private boolean detectingFaces = false;

    private Button btnProcess;

    private OrientationEventListener orientationEventListener = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_camera);

        btnProcess = (Button) findViewById(R.id.btn_process);
        btnProcess.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isDetectingFaces()){
                    detectingFaces = false;
                    btnProcess.setText(R.string.start);
                }else{
                    detectingFaces = true;
                    btnProcess.setText(R.string.stop);
                }
            }
        });

        mainUI = new MainUI(this);
        applicationInterface = new MyApplicationInterface(this, savedInstanceState);

        initCamera2Support();
        setWindowFlagsForCamera();

        preview = new Preview(applicationInterface, savedInstanceState, ((ViewGroup) this.findViewById(R.id.preview)));

        orientationEventListener = new OrientationEventListener(this) {

            @Override
            public void onOrientationChanged(int orientation) {
                if( orientation == OrientationEventListener.ORIENTATION_UNKNOWN )
                    return;
                NewCameraActivity.this.mainUI.onOrientationChanged(orientation);
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        getWindow().getDecorView().getRootView().setBackgroundColor(Color.BLACK);
        orientationEventListener.enable();
        mainUI.layoutUI();
        preview.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        orientationEventListener.disable();
        preview.onPause();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void initCamera2Support() {
        if( MyDebug.LOG )
            Log.d(TAG, "initCamera2Support");
        supports_camera2 = false;
        if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ) {
            CameraControllerManager2 manager2 = new CameraControllerManager2(this);
            supports_camera2 = true;
            if( manager2.getNumberOfCameras() == 0 ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "Camera2 reports 0 cameras");
                supports_camera2 = false;
            }
            for(int i=0;i<manager2.getNumberOfCameras() && supports_camera2;i++) {
                if( !manager2.allowCamera2Support(i) ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "camera " + i + " doesn't have limited or full support for Camera2 API");
                    supports_camera2 = false;
                }
            }
        }
        if( MyDebug.LOG )
            Log.d(TAG, "supports_camera2? " + supports_camera2);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        if( MyDebug.LOG )
            Log.d(TAG, "onConfigurationChanged()");
        // configuration change can include screen orientation (landscape/portrait) when not locked (when settings is open)
        // needed if app is paused/resumed when settings is open and device is in portrait mode
        preview.setCameraDisplayOrientation();
        super.onConfigurationChanged(newConfig);
    }

    public void setWindowFlagsForCamera() {
        if( MyDebug.LOG )
            Log.d(TAG, "setWindowFlagsForCamera");

        // force to landscape mode
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    public boolean supportsCamera2() {
        return this.supports_camera2;
    }

    public Preview getPreview() {
        return this.preview;
    }

    public void requestCameraPermission() {
        if( MyDebug.LOG )
            Log.d(TAG, "requestCameraPermission");
        if( Build.VERSION.SDK_INT < Build.VERSION_CODES.M ) {
            if( MyDebug.LOG )
                Log.e(TAG, "shouldn't be requesting permissions for pre-Android M!");
            return;
        }

        if( ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA) ) {
            // Show an explanation to the user *asynchronously* -- don't block
            // this thread waiting for the user's response! After the user
            // sees the explanation, try again to request the permission.
            showRequestPermissionRationale(MY_PERMISSIONS_REQUEST_CAMERA);
        }
        else {
            // Can go ahead and request the permission
            if( MyDebug.LOG )
                Log.d(TAG, "requesting camera permission...");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, MY_PERMISSIONS_REQUEST_CAMERA);
        }
    }

    public void requestStoragePermission() {
        if( MyDebug.LOG )
            Log.d(TAG, "requestStoragePermission");
        if( Build.VERSION.SDK_INT < Build.VERSION_CODES.M ) {
            if( MyDebug.LOG )
                Log.e(TAG, "shouldn't be requesting permissions for pre-Android M!");
            return;
        }

        if( ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ) {
            // Show an explanation to the user *asynchronously* -- don't block
            // this thread waiting for the user's response! After the user
            // sees the explanation, try again to request the permission.
            showRequestPermissionRationale(MY_PERMISSIONS_REQUEST_STORAGE);
        }
        else {
            // Can go ahead and request the permission
            if( MyDebug.LOG )
                Log.d(TAG, "requesting storage permission...");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, MY_PERMISSIONS_REQUEST_STORAGE);
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private void showRequestPermissionRationale(final int permission_code) {
        if( MyDebug.LOG )
            Log.d(TAG, "showRequestPermissionRational: " + permission_code);
        if( Build.VERSION.SDK_INT < Build.VERSION_CODES.M ) {
            if( MyDebug.LOG )
                Log.e(TAG, "shouldn't be requesting permissions for pre-Android M!");
            return;
        }

        boolean ok = true;
        String [] permissions = null;
        int message_id = 0;
        if( permission_code == MY_PERMISSIONS_REQUEST_CAMERA ) {
            if( MyDebug.LOG )
                Log.d(TAG, "display rationale for camera permission");
            permissions = new String[]{Manifest.permission.CAMERA};
            message_id = R.string.permission_rationale_camera;
        }
        else {
            if( MyDebug.LOG )
                Log.e(TAG, "showRequestPermissionRational unknown permission_code: " + permission_code);
            ok = false;
        }

        if( ok ) {
            final String [] permissions_f = permissions;
            new AlertDialog.Builder(this)
                    .setTitle(R.string.permission_rationale_title)
                    .setMessage(message_id)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setPositiveButton(android.R.string.ok, null)
                    .setOnDismissListener(new DialogInterface.OnDismissListener() {
                        public void onDismiss(DialogInterface dialog) {
                            if( MyDebug.LOG )
                                Log.d(TAG, "requesting permission...");
                            ActivityCompat.requestPermissions(NewCameraActivity.this, permissions_f, permission_code);
                        }
                    }).show();
        }
    }

    public MainUI getMainUI() {
        return this.mainUI;
    }

    public boolean isDetectingFaces(){
        return detectingFaces;
    }
}
