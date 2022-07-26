package com.example.myfirstcameraapp;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.SurfaceView;

/**
 * Created by txs on 2018/5/25.
 */

public class MySurfaceView extends SurfaceView {
    private int lineX = 2;
    private int lineY = 2;
    private Paint mPaint = null;
    private int width;
    private int height;
    private int mRatioWidth = 0;
    private int mRatioHeight = 0;


    public MySurfaceView(Context context) {
        this(context, null);
    }

    public MySurfaceView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MySurfaceView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.GridAutoTextureView);
        lineX = a.getInteger(R.styleable.GridAutoTextureView_linesX, lineX);
        lineY = a.getInteger(R.styleable.GridAutoTextureView_linesY, lineY);
        a.recycle();
        init();
        setWillNotDraw(false);//这个方法是保证回调ondraw方法用的，可以考虑用监听Surface状态的时候使用
    }

    /**
     * 设置长宽比
     */
    public void setAspectRatio(int width, int heigth) {
        if (width < 0 || heigth < 0) {
            throw new IllegalArgumentException("长宽参数不能为负");
        }
        mRatioHeight = heigth;
        mRatioWidth = width;
        requestLayout();//宽高比之后重新绘制
    }

    private void init() {//关于paint类
        mPaint = new Paint();
        mPaint.setColor(Color.RED);
        mPaint.setStyle(Paint.Style.FILL_AND_STROKE);//设置画笔的类型是填充，还是描边，还是描边且填充
        mPaint.setStrokeWidth(1);//设置笔刷的粗细
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        width = MeasureSpec.getSize(widthMeasureSpec);
        height = MeasureSpec.getSize(heightMeasureSpec);
        if(0==mRatioWidth||0==mRatioHeight){//初次绘制的情况
            setMeasuredDimension(width,height);
        }else {
            if (width < height * mRatioWidth / mRatioHeight)//哪边占比小就用它为绘制参考便，实际上是在选择同比例最大绘制范围
            {
                setMeasuredDimension(width, width * mRatioHeight / mRatioWidth);//设置SurfaceView的大小适应于预览流的大小

            } else {
                setMeasuredDimension(height * mRatioWidth / mRatioHeight, height);

            }

        }
    }


}
