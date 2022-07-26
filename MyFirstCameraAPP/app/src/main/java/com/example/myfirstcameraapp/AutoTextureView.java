package com.example.myfirstcameraapp;

import android.content.Context;
import android.util.AttributeSet;
import android.view.TextureView;

import androidx.annotation.NonNull;

public class AutoTextureView extends TextureView {
    private int ratioWidth,ratioHeight ;
    private float proportion=0;
    public AutoTextureView(@NonNull Context context) {
        this(context, null, 0);
    }
    public AutoTextureView(@NonNull Context context, AttributeSet attributeSet){
        this(context, attributeSet, 0);
    }
    public AutoTextureView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }
    public void setAspectRatio(int width, int height) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Size cannot be negative.");
        }
        ratioWidth = width;
        ratioHeight = height;
        requestLayout();
    }
    public void setProportion(float proportion){this.proportion =proportion;requestLayout();}
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width=MeasureSpec.getSize(widthMeasureSpec);
        int height=MeasureSpec.getSize(heightMeasureSpec);
        if (proportion!=0){
            if (width < height * proportion) {
                setMeasuredDimension(width, (int) (width *proportion));
            } else {
                setMeasuredDimension((int) (height * proportion), height);
            }
            return;
        }
        if (ratioHeight ==0||ratioWidth==0){
            setMeasuredDimension(width,height);
        }else {
            if (width < height * ratioWidth / ratioHeight) {
                setMeasuredDimension(width, width * ratioHeight / ratioWidth);
            } else {
                setMeasuredDimension(height * ratioWidth / ratioHeight, height);
            }
        }
    }
}
