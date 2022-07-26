package com.example.myfirstcameraapp;

import android.content.Context;
import android.content.SharedPreferences;

public class Reference {
    private Context context;
    private String filename;
    public Reference(){}
    public Reference(Context context){
        this.context = context;
    }
    public Reference(Context context, String filename){
        this.context = context;
        this.filename=filename;
    }

    public void saveString(String key,String value){
        if (filename==null) filename="test1";
        SharedPreferences preferences =
                context.getSharedPreferences(filename, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(key, value);
        editor.apply();
    }
    public String getString(String key){
        if (filename==null) filename="test1";
        SharedPreferences preferences =
                context.getSharedPreferences(filename, Context.MODE_PRIVATE);
        return preferences.getString(key, null);
    }
}
