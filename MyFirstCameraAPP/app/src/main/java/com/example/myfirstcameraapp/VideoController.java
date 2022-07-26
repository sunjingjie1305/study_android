package com.example.myfirstcameraapp;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.location.Location;
import android.location.LocationManager;
import android.media.MediaRecorder;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VideoController {
    private static final String TAG = "VideoController";
    private MediaRecorder mediaRecorder;
    private MainActivity activity;
    private CameraCaptureSession videoSession = null;
    private CaptureRequest.Builder videoCaptureRequest = null;
    private CameraCaptureSession mPreviewSession;
    private CameraDevice mCameraDevice;
    private AutoTextureView mTextureView;
    private Size mPreviewSize;
    private CaptureRequest.Builder mPreviewRequestBuilder;

    public VideoController(MainActivity mainActivity,Size size){
        activity=mainActivity;
        mPreviewSize=size;
        mCameraDevice=activity.getCameraDevice();
        mTextureView=activity.getTextureView();
        mPreviewRequestBuilder=activity.getPreviewRequestBuilder();
    }
    //开始录像
    public void startVideo() {
        if (mCameraDevice == null  || mPreviewSize == null) {
            return;
        }
        if (mPreviewSession != null) {
            mPreviewSession.close();
            mPreviewSession = null;
        }
        //改变textView大小
        mTextureView.setProportion(1);

        try {
            setUpMediaRecorder();
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            List<Surface> surfaces = new ArrayList<>();

            //为相机预览设置曲面
            Surface previewSurface = new Surface(texture);
            surfaces.add(previewSurface);
            mPreviewRequestBuilder.addTarget(previewSurface);

            //设置MediaRecorder的表面
            Surface recorderSurface = mediaRecorder.getSurface();
            surfaces.add(recorderSurface);
            mPreviewRequestBuilder.addTarget(recorderSurface);

            // 启动捕获会话
            // 一旦会话开始，我们就可以更新UI并开始录制
            mCameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    try {
                        mPreviewSession = session;
                        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
                        mPreviewSession.setRepeatingRequest(mPreviewRequestBuilder.build(), ss, null);
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    mediaRecorder.start();
                                    Log.d(TAG, "Exception");
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }).start();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {

                }
            }, null);


        } catch (CameraAccessException e) {
            Log.d(TAG, "Exception");
            e.printStackTrace();
        }
    }

    //ss暂时仅做录像时查看抓取情况
    private CameraCaptureSession.CaptureCallback ss = new CameraCaptureSession.CaptureCallback() {
        private void process(CaptureResult result) {
            Log.d(TAG, "Capture a picture");
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult) {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {

            process(result);
        }
    };


    private void setUpMediaRecorder() {
        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC); //设置用于录制的音源
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);//开始捕捉和编码数据到setOutputFile（指定的文件）
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4); //设置在录制过程中产生的输出文件的格式
        try {

            try {
                mediaRecorder.setMaxFileSize(270000);//约263kb
            } catch (RuntimeException exception) {
                exception.printStackTrace();
            }
            Log.d(TAG, "Exception");
            mediaRecorder.setOutputFile(broadcastVideoFileQ(activity, getLocal()));//设置输出文件的路径
            mediaRecorder.setVideoEncodingBitRate(10000000);//设置录制的视频编码比特率
            mediaRecorder.setVideoFrameRate(25);//设置要捕获的视频帧速率
            mediaRecorder.setVideoSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());//设置要捕获的视频的宽度和高度
            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);//设置视频编码器，用于录制
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);//设置audio的编码格式
            int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
            Log.d(TAG, "setUpMediaRecorder: " + rotation);

            mediaRecorder.setOnInfoListener(new MediaRecorder.OnInfoListener() {
                private String TAG = "MediaRecorder info listener";
                @SuppressLint("NewApi")
                @Override
                public void onInfo(MediaRecorder mr, int what, int extra) {
                    switch (what) {
                        case MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_APPROACHING:
                            try {
                                mediaRecorder.stop();
                                mediaRecorder.release();
                                activity.setVideoState(true);
                                activity.createCameraPreviewSession();
                                Toast.makeText(activity,"超过存储，已停止录像",Toast.LENGTH_LONG).show();
                                Log.i(TAG, "Max file size approaching");
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            break;
                    }
                }
            });
            mediaRecorder.prepare();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public FileDescriptor broadcastVideoFileQ(Context context, Map<String, String> location) throws FileNotFoundException {
        Uri uri = null;
        try {
            long dateTaken = System.currentTimeMillis();
            ContentValues values = new ContentValues();
            values.put(MediaStore.Video.Media.TITLE, "test" + System.currentTimeMillis() + ".mp4");
            values.put(MediaStore.Video.Media.DISPLAY_NAME, "/storage/0/DCIM/Camera/" + "test" + System.currentTimeMillis() + ".mp4");
            values.put(MediaStore.Video.Media.DATE_TAKEN, dateTaken);
            values.put(MediaStore.Video.Media.DATE_MODIFIED, dateTaken / 1000);
            values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
            values.put(MediaStore.Video.Media.RELATIVE_PATH, "DCIM/Camera/");
            if (location != null) {
                values.put(MediaStore.Video.Media.LATITUDE, location.get("latitude"));
                values.put(MediaStore.Video.Media.LONGITUDE, location.get("longitude"));
            }
            uri = context.getContentResolver()
                    .insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);

        } catch (Throwable tr) {
            tr.printStackTrace();
        }
        return context.getContentResolver().openFileDescriptor(uri, "rw").getFileDescriptor();

    }
    public Map<String, String> getLocal() {
        // 获取位置服务
        String serviceName = Context.LOCATION_SERVICE;
        // 调用getSystemService()方法来获取LocationManager对象
        LocationManager locationManager = (LocationManager) activity.getSystemService(serviceName);
        // 指定LocationManager的定位方法
        List<String> providers = locationManager.getProviders(true);
        Location bestLocation = null;
        for (String provider : providers) {
            //屏蔽报错
            // 调用getLastKnownLocation()方法获取当前的位置信息
            @SuppressLint("MissingPermission") Location l = locationManager.getLastKnownLocation(provider);
            if (l == null) {
                continue;
            }
            if (bestLocation == null || l.getAccuracy() < bestLocation.getAccuracy()) {
                // Found best last known location: %s", l);
                bestLocation = l;
                break;
            }
        }
        //获取纬度
        double lat = bestLocation.getLatitude();
        //获取经度
        double lng = bestLocation.getLongitude();
        Map<String, String> map = new HashMap<>();
        map.put("longitude", decimalToDMS(lng));
        map.put("latitude", decimalToDMS(lat));
        return map;
    }

    //将经纬度变为可识别的格式
    public String decimalToDMS(double coord) {
        String output, degrees, minutes, seconds;
        double mod = coord % 1;
        int intPart = (int) coord;
        degrees = String.valueOf(intPart);
        coord = mod * 60;
        mod = coord % 1;
        intPart = (int) coord;
        if (intPart < 0) {
            // Convert number to positive if it's negative.
            intPart *= -1;
        }
        minutes = String.valueOf(intPart);
        coord = mod * 60;
        intPart = (int) coord;
        if (intPart < 0) {
            // Convert number to positive if it's negative.
            intPart *= -1;
        }
        seconds = String.valueOf(intPart);
        output = degrees + "/1," + minutes + "/1," + seconds + "/1";
        return output;
    }


    public void stop(){
        mediaRecorder.stop();
        mediaRecorder.release();
    }
}
