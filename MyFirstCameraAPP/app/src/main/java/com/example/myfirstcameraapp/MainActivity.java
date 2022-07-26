package com.example.myfirstcameraapp;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.BitmapDrawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private String[] permissions = {Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_LOCATION_EXTRA_COMMANDS, Manifest.permission.RECORD_AUDIO};
    private final int REQUEST_CODE = 10001;

    private ImageView imageView;
    private static final String TAG = "FirstCamera";
    private boolean videoState = true;//录屏状态
    private ImageReader mImageReader;
    private HandlerThread handlerThread;
    private Handler backGroundHandler;
    private Handler imageHandler;

    private int sensorOrientation;//相机角度

    private Size previewSize;//预置界面大小
    private AutoTextureView textureView;
    private MySurfaceView surfaceView;

    //相机参数
    private String mCameraId;//相机ID
    private CameraDevice cameraDevice;
    private CaptureRequest.Builder previewRequestBuilder;
    private CameraCaptureSession captureSession;

    //相机状态参数
    private int mState = STATE_PREVIEW;
    private static final int STATE_PREVIEW = 0; //显示Camera预览
    private static final int STATE_WAITING_LOCK = 1;//等待焦点锁定
    private static final int STATE_WAITING_PRECAPTURE = 2;//等待曝光为预拍状态
    private static final int STATE_WAITING_NON_PRECAPTURE = 3;//等待曝光状态不是预捕
    private static final int STATE_PICTURE_TAKEN = 4;//照片已经被拍下

    //拍照类
    private CapturePicture capturePicture ;

    //录像控制类
    private VideoController videoController;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //申请权限
        for (String perission : permissions) {
            if (ContextCompat.checkSelfPermission(this, perission) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE);
            }
        }

        findViewById(R.id.picture).setOnClickListener(this);
        findViewById(R.id.video).setOnClickListener(this);
        //findViewById(R.id.photoAlbum).setOnClickListener(this);
        textureView = (AutoTextureView) findViewById(R.id.texture);
        textureView.setSurfaceTextureListener(mSurfaceTextureListener);
        surfaceView = (MySurfaceView) findViewById(R.id.surfaceView);
        imageView = (ImageView) findViewById(R.id.phonePicture);

        Reference reference = new Reference(this, "test2");
        String localFileName = reference.getString("uriName");
        if (localFileName == null) {
            Log.d(TAG, "sharedPreferencestest : null");
        } else {
            Log.d(TAG, "sharedPreferencestest : " + localFileName);
            Bitmap bitmap = BitmapFactory.decodeFile(localFileName);
            imageView.setImageDrawable(new BitmapDrawable(bitmap));
        }

        imageHandler = new Handler() {
            @Override
            public void handleMessage(@NonNull Message msg) {
                super.handleMessage(msg);
                if (msg.what == 1) {
                    Bitmap bitmap = (Bitmap) msg.obj;
                    if (bitmap != null) {
                        imageView.setImageDrawable(new BitmapDrawable(bitmap));
                    }
                    Log.d("TryMain", "Handle success ");
                }
            }
        };
    }


    private void takePicture() {
        try {
            // This is how to tell the camera to lock focus.
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_START);
            //打开闪光灯
            previewRequestBuilder.set(CaptureRequest.FLASH_MODE,
                    CaptureRequest.FLASH_MODE_SINGLE);
            // Tell #mCaptureCallback to wait for the lock.
            mState = STATE_WAITING_LOCK;
            captureSession.capture(previewRequestBuilder.build(), captureCallback,
                    backGroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void openCamera(int width, int height) {
        try {
            setUpCameraOutputs(width, height);
            configureTransform(width, height);
            CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            manager.openCamera(mCameraId, mStateCallback, backGroundHandler);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //设置与相机相关的成员变量。
    @SuppressWarnings("SuspiciousNameCombination")
    private void setUpCameraOutputs(int width, int height) {

        CameraManager manager = (CameraManager) this.getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : manager.getCameraIdList()) {
                //当前情况下IDList只有0和1这两个
                CameraCharacteristics characteristics
                        = manager.getCameraCharacteristics(cameraId);

                // We don't use a front facing camera in this sample.
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }

                StreamConfigurationMap map = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }
                Log.d(TAG, "cameraID :" + cameraId);
                // For still image captures, we use the largest available size.
                Size largest = Collections.min(
                        Arrays.asList(map.getOutputSizes(ImageFormat.YUV_420_888)),
                        new CompareSizesByArea());
                Log.d(TAG, "setUpCameraOutputs: " + largest.getWidth() + "  " + largest.getHeight());
                mImageReader = ImageReader.newInstance(1440, 1080,
                        ImageFormat.YUV_420_888, /*maxImages*/2);
                mImageReader.setOnImageAvailableListener(
                        imageAvailableListener, backGroundHandler);

                // Find out if we need to swap dimension to get the preview size relative to sensor
                // coordinate.
                int displayRotation = getWindowManager().getDefaultDisplay().getRotation();
                //noinspection ConstantConditions 相机角度
                sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                boolean swappedDimensions = false;
                switch (displayRotation) {
                    case Surface.ROTATION_180:
                        if (sensorOrientation == 90 || sensorOrientation == 270) {
                            swappedDimensions = true;
                        }
                        break;
                    case Surface.ROTATION_270:
                        if (sensorOrientation == 0 || sensorOrientation == 180) {
                            swappedDimensions = true;
                        }
                        break;
                    default:
                        Log.e(TAG, "Display rotation is invalid: " + displayRotation);
                }

                Point displaySize = new Point();
                getWindowManager().getDefaultDisplay().getSize(displaySize);
                int rotatedPreviewWidth = width;
                int rotatedPreviewHeight = height;
                int maxPreviewWidth = displaySize.x;
                int maxPreviewHeight = displaySize.y;

                if (swappedDimensions) {
                    rotatedPreviewWidth = height;
                    rotatedPreviewHeight = width;
                    maxPreviewWidth = displaySize.y;
                    maxPreviewHeight = displaySize.x;
                }
                int MAX_PREVIEW_WIDTH = 2048;
                int MAX_PREVIEW_HEIGHT = 1536;
                if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                    maxPreviewWidth = MAX_PREVIEW_WIDTH;
                }

                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                    maxPreviewHeight = MAX_PREVIEW_HEIGHT;
                }

                // 根据相机支持先选最小，若都小于textureView则选择最大的并将尺寸比例变得符合YUV
                previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                        rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
                        maxPreviewHeight, largest);

                // We fit the aspect ratio of TextureView to the size of preview we picked.
                int orientation = getResources().getConfiguration().orientation;
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    textureView.setAspectRatio(
                            previewSize.getWidth(), previewSize.getHeight());
                    surfaceView.setAspectRatio(
                            previewSize.getWidth(), previewSize.getHeight());
                } else {
                    textureView.setAspectRatio(
                            previewSize.getHeight(), previewSize.getWidth());
                    surfaceView.setAspectRatio(
                            previewSize.getHeight(), previewSize.getWidth());
                }

                // Check if the flash is supported.
                // Boolean available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                //  mFlashSupported = available == null ? false : available;

                mCameraId = cameraId;
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
    }

    private static Size chooseOptimalSize(Size[] choices, int textureViewWidth,
                                          int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio) {

        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                    option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth &&
                        option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    //绘制texture
    private void configureTransform(int viewWidth, int viewHeight) {

        if (null == textureView || null == previewSize) {
            return;
        }
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        Log.d(TAG, "configureTransform: " + rotation);
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            //将前者转化成后者，将textureView大小转化成
            // Scale in X and Y independently, so that src matches dst exactly.

            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.CENTER);

            float scale = Math.max(
                    (float) viewHeight / previewSize.getHeight(),
                    (float) viewWidth / previewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        textureView.setTransform(matrix);
    }

    //创建预置界面的session
    protected void createCameraPreviewSession() {
        Log.d(TAG, "createCameraPreviewSession: ");
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());

            // This is the output Surface we need to start preview.
            Surface surface = new Surface(texture);

            // We set up a CaptureRequest.Builder with the output Surface.
            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(surface);

            // Here, we create a CameraCaptureSession for camera preview.
            cameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            // The camera is already closed
                            if (null == cameraDevice) {
                                return;
                            }
                            // When the session is ready, we start displaying the preview.
                            captureSession = cameraCaptureSession;
                            try {
                                // Auto focus should be continuous for camera preview.
                                previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

                                // Flash is automatically enabled when necessary.
                                previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                                        CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

                                // Finally, we start displaying the camera preview.
                                captureSession.setRepeatingRequest(previewRequestBuilder.build(),
                                        captureCallback, backGroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(
                                @NonNull CameraCaptureSession cameraCaptureSession) {
                            Toast.makeText(MainActivity.this, "Failed", Toast.LENGTH_SHORT).show();

                        }
                    }, null
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    //解除锁定
    public void unlockFocus() {
        Log.d(TAG, "unlockFocus: ");
        try {
            // Reset the auto-focus trigger
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            //关闭闪光灯
            previewRequestBuilder.set(CaptureRequest.FLASH_MODE,
                    CaptureRequest.FLASH_MODE_OFF);
            captureSession.capture(previewRequestBuilder.build(), captureCallback,
                    backGroundHandler);
            // After this, the camera will go back to the normal state of preview.
            mState = STATE_PREVIEW;
            captureSession.setRepeatingRequest(previewRequestBuilder.build(), captureCallback,
                    backGroundHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    //设置监听
    //设置按键监听
    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.picture: {
                takePicture();
                break;
            }
            case R.id.video: {
                if (videoController==null){
                    videoController= new VideoController(MainActivity.this,previewSize);
                }
                if (videoState) {
                    //开始录像
                    videoController.startVideo();
                    videoState = false;
                } else {
                    videoController.stop();
                    createCameraPreviewSession();
                    videoState = true;
                }
            }break;
            case R.id.photoAlbum:{


            }break;

        }
    }


    //设置TextureView
    private final TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int width, int height) {
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surfaceTexture, int width, int height) {
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surfaceTexture) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {

        }
    };

    //设置imageReader监听
    private final ImageReader.OnImageAvailableListener imageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader imageReader) {
            Log.d(TAG, "onImageAvailable");
            backGroundHandler.post(new ImageSaver(imageReader.acquireNextImage(),
                    new File(getExternalFilesDir(null) + "test" + System.currentTimeMillis() + ".jpg"),
                    getBaseContext(), imageHandler,new Exif(MainActivity.this)));
        }
    };


    //设置回调
    //设置stateCallback回调
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice device) {
            // This method is called when the camera is opened.  We start camera preview here.
            Log.d(TAG, "onOpened: ");
            cameraDevice = device;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            cameraDevice.close();
            cameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            cameraDevice.close();
            cameraDevice = null;
            onStop();

        }

    };

    //设置captureCallback回调
    private CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult partialResult) {
            process(partialResult);
        }


        private void process(CaptureResult result) {

            switch (mState) {

                case STATE_PREVIEW: {
                    // We have nothing to do when the camera preview is working normally.
                    break;
                }
                case STATE_WAITING_LOCK: {
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);

                    if (afState == null) {
                        capturePicture = new CapturePicture(MainActivity.this,sensorOrientation,getWindowManager().getDefaultDisplay().getRotation());
                        capturePicture.captureStillPicture();
                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
                        // CONTROL_AE_STATE can be null on some devices
                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                        Log.d(TAG, "aeState: " + aeState);
                        if (aeState == null ||
                                aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            capturePicture = new CapturePicture(MainActivity.this, sensorOrientation,getWindowManager().getDefaultDisplay().getRotation());
                            mState = STATE_PICTURE_TAKEN;
                            capturePicture.captureStillPicture();

                        } else {
                            Log.d(TAG, "runPrecaptureSequence: ");
                            try {
                                // This is how to tell the camera to trigger.
                                previewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                                        CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
                                // Tell #mCaptureCallback to wait for the precapture sequence to be set.
                                mState = STATE_WAITING_PRECAPTURE;
                                captureSession.capture(previewRequestBuilder.build(), captureCallback,
                                        backGroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    break;
                }
                case STATE_WAITING_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null ||
                            aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                            aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        mState = STATE_WAITING_NON_PRECAPTURE;
                    }
                    break;
                }
                case STATE_WAITING_NON_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        mState = STATE_PICTURE_TAKEN;
                        capturePicture = new CapturePicture(MainActivity.this,sensorOrientation,getWindowManager().getDefaultDisplay().getRotation());
                        capturePicture.captureStillPicture();
                    }
                    break;
                }
            }
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        handlerThread = new HandlerThread("Camera");
        handlerThread.start();
        backGroundHandler = new Handler(handlerThread.getLooper());
        if (textureView.isAvailable()) {
            openCamera(textureView.getWidth(), textureView.getHeight());
        } else {
            textureView.setSurfaceTextureListener(mSurfaceTextureListener);
            surfaceView.setZOrderOnTop(true);//将view置于顶层
            surfaceView.getHolder().setFormat(PixelFormat.TRANSPARENT);//设置透明度的格式
        }
    }

    @Override
    protected void onPause() {
        if (captureSession != null) {
            captureSession.close();
            captureSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (mImageReader != null) {
            mImageReader.close();
            mImageReader = null;
        }
        handlerThread.quitSafely();
        try {
            handlerThread.join();
            handlerThread =null;
            backGroundHandler =null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        super.onPause();
    }

    public ImageReader getmImageReader() {
        return mImageReader;
    }

    public CameraDevice getCameraDevice() {
        return cameraDevice;
    }

    public CameraCaptureSession getCaptureSession() {
        return captureSession;
    }

    public AutoTextureView getTextureView() {
        return textureView;
    }

    public CaptureRequest.Builder getPreviewRequestBuilder() {
        return previewRequestBuilder;
    }

    public void setVideoState(boolean videoState) {
        this.videoState = videoState;
    }
}