package com.example.myfirstcameraapp;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;

public class ImageSaver implements Runnable {

    private static final String TAG = "ImageSaver";
    private final Image mImage;
    /**
     * The file we save the image into.
     */
    private final File mFile;

    private final Context mContext;
    private Handler mHandler;
    private Exif exif ;
    ImageSaver(Image image, File file, Context context, Handler handler,Exif exif) {
        mImage = image;
        mFile = file;
        mContext = context;
        mHandler = handler;
        this.exif =exif;
    }


    @Override
    public void run() {
        Log.d(TAG, "run ");

        byte[] bytes = getYuvFromImage(mImage);

        YuvImage yuvImage = new YuvImage(bytes, ImageFormat.NV21, mImage.getWidth(), mImage.getHeight(), null);

        ByteArrayOutputStream output = null;
       // FileOutputStream fileOutputStream = null;
        try {
            if (yuvImage != null) {
                //存储至本APP的路径内
                output = new ByteArrayOutputStream();
                yuvImage.compressToJpeg(new Rect(0, 0, mImage.getWidth(), mImage.getHeight()), 100, output);
                Bitmap bitmap = BitmapFactory.decodeByteArray(output.toByteArray(), 0, output.size());
                bitmap = changeOrientation(bitmap);

                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, new FileOutputStream(mFile.getPath()));
                Log.d(TAG, "output success:" + mFile.toString());


                ContentResolver contentResolver = mContext.getContentResolver();
                ContentValues contentValues = getImageContentValues(mFile, System.currentTimeMillis());
                //将图片的相关信息插入到相册的数据库中
                Uri localUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
                Reference reference = new Reference(mContext, "test2");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                        mContext.getApplicationInfo().targetSdkVersion >= Build.VERSION_CODES.Q) {
                    //拷贝文件到相册的uri,android11及以上得这么干，否则不会显示。可以参考ScreenMediaRecorder的save方法
                    OutputStream os = contentResolver.openOutputStream(localUri, "w");
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, os);
                    os.close();
                }
                //mContext.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, localUri));
                reference.saveString("uriName", mFile.getPath());

                Message msg = new Message();
                msg.what = 1;
                msg.obj = bitmap;
                mHandler.sendMessage(msg);
                exif.setExif(contentResolver.openFileDescriptor(localUri, "rw").getFileDescriptor());
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            mImage.close();
            if (null != output) {
                try {
                    output.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    //改变输出图片方向问题
    private Bitmap changeOrientation(Bitmap bitmap) {
        Matrix matrix = new Matrix();
        //这里的orientation 可以通过方法传入来实现，根据任意角度变换
        int orientationDegree = 90;
        matrix.setRotate(orientationDegree, (float) bitmap.getWidth() / 2,
                (float) bitmap.getHeight() / 2);
        float targetX, targetY;

        if (orientationDegree == 90) {
            targetX = bitmap.getHeight();
            targetY = 0;
        } else {
            targetX = bitmap.getHeight();
            targetY = bitmap.getWidth();
        }

        final float[] values = new float[9];
        matrix.getValues(values);


        float x1 = values[Matrix.MTRANS_X];
        float y1 = values[Matrix.MTRANS_Y];

        matrix.postTranslate(targetX - x1, targetY - y1);
        Bitmap canvasBitmap = Bitmap.createBitmap(bitmap.getHeight(), bitmap.getWidth(),
                Bitmap.Config.ARGB_8888);

        Paint paint = new Paint();
        Canvas canvas = new Canvas(canvasBitmap);
        canvas.drawBitmap(bitmap, matrix, paint);
        return canvasBitmap;
    }

    //设置输入相册数据库所需的字段
    public ContentValues getImageContentValues(File paramFile, long timestamp) {
        ContentValues localContentValues = new ContentValues();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            localContentValues.put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Camera");
        }
        localContentValues.put(MediaStore.Images.Media.TITLE, paramFile.getName());
        localContentValues.put(MediaStore.Images.Media.DISPLAY_NAME, paramFile.getName());
        localContentValues.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        localContentValues.put(MediaStore.Images.Media.DATE_TAKEN, timestamp);
        localContentValues.put(MediaStore.Images.Media.DATE_MODIFIED, timestamp);
        localContentValues.put(MediaStore.Images.Media.DATE_ADDED, timestamp);
        localContentValues.put(MediaStore.Images.Media.ORIENTATION, 0);
        localContentValues.put(MediaStore.Images.Media.DATA, paramFile.getAbsolutePath());
        localContentValues.put(MediaStore.Images.Media.SIZE, paramFile.length());
        return localContentValues;
    }

    public static byte[] getYuvFromImage(Image image) {

        long time1 = System.currentTimeMillis();
        int w = image.getWidth(), h = image.getHeight();
        int i420Size = w * h * 3 / 2;
        int picel1 = ImageFormat.getBitsPerPixel(ImageFormat.NV21);
        int picel2 = ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888);

        Image.Plane[] planes = image.getPlanes();
        //remaining0 = rowStride*(h-1)+w => 27632= 192*143+176
        int remaining0 = planes[0].getBuffer().remaining();
        int remaining1 = planes[1].getBuffer().remaining();
        //remaining2 = rowStride*(h/2-1)+w-1 =>  13807=  192*71+176-1
        int remaining2 = planes[2].getBuffer().remaining();
        //获取pixelStride，可能跟width相等，可能不相等
        int pixelStride = planes[2].getPixelStride();
        int rowOffest = planes[2].getRowStride();
        byte[] nv21 = new byte[i420Size];
        byte[] yRawSrcBytes = new byte[remaining0];
        byte[] uRawSrcBytes = new byte[remaining1];
        byte[] vRawSrcBytes = new byte[remaining2];
        planes[0].getBuffer().get(yRawSrcBytes);
        planes[1].getBuffer().get(uRawSrcBytes);
        planes[2].getBuffer().get(vRawSrcBytes);
        if (pixelStride == w) {
            //两者相等，说明每个YUV块紧密相连，可以直接拷贝
            System.arraycopy(yRawSrcBytes, 0, nv21, 0, rowOffest * h);
            System.arraycopy(vRawSrcBytes, 0, nv21, rowOffest * h, rowOffest * h / 2 - 1);
        } else {
            byte[] ySrcBytes = new byte[w * h];
            byte[] uSrcBytes = new byte[w * h / 2 - 1];
            byte[] vSrcBytes = new byte[w * h / 2 - 1];
            for (int row = 0; row < h; row++) {
                //源数组每隔 rowOffest 个bytes 拷贝 w 个bytes到目标数组
                System.arraycopy(yRawSrcBytes, rowOffest * row, ySrcBytes, w * row, w);

                //y执行两次，uv执行一次
                if (row % 2 == 0) {
                    //最后一行需要减一
                    if (row == h - 2) {
                        System.arraycopy(vRawSrcBytes, rowOffest * row / 2, vSrcBytes, w * row / 2, w - 1);
                    } else {
                        System.arraycopy(vRawSrcBytes, rowOffest * row / 2, vSrcBytes, w * row / 2, w);
                    }
                }
            }
            System.arraycopy(ySrcBytes, 0, nv21, 0, w * h);
            System.arraycopy(vSrcBytes, 0, nv21, w * h, w * h / 2 - 1);
        }

        return nv21;
    }


}
