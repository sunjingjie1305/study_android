package com.example.myfirstcameraapp;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.media.ExifInterface;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Exif {
    //设置exif
    private ExifInterface exif = null;
    private MainActivity activity;

    public Exif(MainActivity activity) {
        this.activity = activity;
    }

    public void setExif(FileDescriptor filepath) {
        try {
            exif = new ExifInterface(filepath); //根据图片的路径获取图片的Exif
            Map<String, String> map = getLocal();
            String longitude = map.get("longitude");
            String latitude = map.get("latitude");

            exif.setAttribute(ExifInterface.TAG_DATETIME, String.valueOf(System.currentTimeMillis())); //把时间
            exif.setAttribute(ExifInterface.TAG_MAKE, "tttt"); //MAKE设备
            exif.setAttribute(ExifInterface.TAG_MODEL, "test"); //MODEL
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, longitude); //把经度写进exif
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, latitude); //把纬度写进exif
            exif.saveAttributes(); //最后保存起来

        } catch (IOException e) {
            Log.e("Mine", "cannot save exif", e);
        }

    }

    //获取经纬度
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

}
