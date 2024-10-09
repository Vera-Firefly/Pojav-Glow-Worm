package com.firefly.utils;

import android.app.AlertDialog;
import android.content.Context;
import android.widget.SeekBar;
import android.widget.TextView;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.util.Log;

import net.kdt.pojavlaunch.MinecraftGLSurface;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;

public class ResolutionAdjuster {

    private float mScaleFactor;
    private final Context context;
    private MinecraftGLSurface glSurface;

    // 构造函数，传入Context
    public ResolutionAdjuster(Context context) {
        this.context = context;
    }
    public ResolutionAdjuster(Context context, MinecraftGLSurface glSurface) {
        this.context = context;
        this.glSurface = glSurface;
    }

    // 显示滑动条弹窗
    public void showSeekBarDialog() {
        if (glSurface == null) {
            glSurface = new MinecraftGLSurface(context);
        }
        mScaleFactor = glSurface.mScaleFactor;
        int percentage = Math.round(mScaleFactor * 100);
        // 动态创建一个LinearLayout作为容器
        // 什么?为什么不用.xml来构建?
        // 因为麻烦
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 40);
        layout.setGravity(Gravity.CENTER);

        // 动态创建一个TextView,用于显示缩放因子
        final TextView scaleTextView = new TextView(context);
        scaleTextView.setText(percentage + "%");
        scaleTextView.setTextSize(18);
        layout.addView(scaleTextView);

        // 动态创建一个SeekBar,用于调整缩放因子
        final SeekBar scaleSeekBar = new SeekBar(context);
        scaleSeekBar.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 设置滑动条的最大值和初始进度
        int maxScaleFactor = Math.max(LauncherPreferences.PREF_SCALE_FACTOR, 100);
        scaleSeekBar.setMax(maxScaleFactor - 25);
        scaleSeekBar.setProgress((int) (mScaleFactor * 100) - 25);
        layout.addView(scaleSeekBar);

        // 设置滑动条监听器
        scaleSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // 更新缩放因子
                mScaleFactor = (progress + 25) / 100f;
                glSurface.mScaleFactor = mScaleFactor;
                int scaleFactor = Math.round(mScaleFactor * 100);
                // 实时更新显示的缩放因子
                scaleTextView.setText(scaleFactor + "%");

                // 新分辨率
                if (glSurface != null) glSurface.refreshSize();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Nothing to do here
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Nothing to do here
            }
        });

        // 创建并显示弹窗
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(context.getString(R.string.mcl_setting_title_resolution_scaler));
        builder.setView(layout);
        builder.setCancelable(false); // 不允许点击外部关闭弹窗,防止进程错误
        // 设置确认按钮，点击关闭弹窗
        builder.setPositiveButton(android.R.string.ok, (d, i) -> d.dismiss());
        builder.show();
    }

}