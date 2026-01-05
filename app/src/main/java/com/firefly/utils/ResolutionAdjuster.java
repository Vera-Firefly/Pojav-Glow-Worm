package com.firefly.utils;

import android.content.Context;
import android.content.res.Configuration;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.LinearLayout;

import com.firefly.ui.dialog.CustomDialog;

import net.kdt.pojavlaunch.firefly.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.firefly.R;
import net.kdt.pojavlaunch.firefly.Tools;

public class ResolutionAdjuster {

    private static float mScaleFactor;
    private final Context context;
    private final OnResolutionChangeListener listener;

    public ResolutionAdjuster(Context context, OnResolutionChangeListener listener) {
        this.context = context;
        this.listener = listener;
    }

    // 显示滑动条弹窗
    public void showSeekBarDialog() {
        if (mScaleFactor == 0.0f) mScaleFactor = LauncherPreferences.PREF_SCALE_FACTOR / 100f;
        int percentage = Math.round(mScaleFactor * 100);

        // 动态创建一个LinearLayout
        // 什么?为什么不用.xml来创建?
        // 因为麻烦
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);  // 设置水平排列
        layout.setPadding(8, 8, 8, 8);
        layout.setGravity(Gravity.CENTER);

        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, typedValue, true);

        // 动态创建 "-" 按钮
        final TextView minusButton = new TextView(context);
        minusButton.setText("-");
        minusButton.setTextSize(18);
        minusButton.setGravity(Gravity.CENTER);
        minusButton.setPadding(16, 16, 16, 16);
        minusButton.setBackgroundResource(typedValue.resourceId);
        layout.addView(minusButton);

        // 动态创建一个 SeekBar 用于调整缩放因子
        final SeekBar scaleSeekBar = new SeekBar(context);
        scaleSeekBar.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); // 设置权重 1, 用于充满剩余空间

        // 获取当前设置的最大缩放因子,并设置为滑动条的最大值
        int maxScaleFactor = Math.max(LauncherPreferences.PREF_SCALE_FACTOR, 100);
        scaleSeekBar.setMax(maxScaleFactor - 25);

        // 根据当前获取的缩放因子,设置滑动条初始值
        scaleSeekBar.setProgress((int) (mScaleFactor * 100) - 25);
        layout.addView(scaleSeekBar);

        // 动态创建 "+" 按钮
        final TextView plusButton = new TextView(context);
        plusButton.setText("+");
        plusButton.setTextSize(18);
        plusButton.setGravity(Gravity.CENTER);
        plusButton.setPadding(16, 16, 16, 16);
        plusButton.setBackgroundResource(typedValue.resourceId);
        layout.addView(plusButton);

        // 动态创建一个TextView用于显示当前分辨率
        final TextView resolutionTextView = new TextView(context);
        changeResolutionRatioPreview(percentage, resolutionTextView);  // 获取当前分辨率
        resolutionTextView.setTextSize(14);
        resolutionTextView.setPadding(10, 0, 0, 0);  // 添加一些左侧间距
        layout.addView(resolutionTextView);

        // 动态创建一个TextView用于显示缩放百分数
        final TextView scaleTextView = new TextView(context);
        scaleTextView.setText(percentage + "%");
        scaleTextView.setTextSize(14);
        scaleTextView.setPadding(10, 0, 0, 0);  // 添加一些左侧间距
        layout.addView(scaleTextView);

        // 设置滑动条监听器, 用于实时处理
        scaleSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int scaleFactor = progress + 25;
                // 更新缩放因子
                mScaleFactor = scaleFactor / 100f;
                listener.onChange(mScaleFactor);
                // 动态更新显示的缩放百分数
                scaleTextView.setText(scaleFactor + "%");
                // 动态更新分辨率TextView,根据缩放因子调整分辨率显示
                changeResolutionRatioPreview(scaleFactor, resolutionTextView);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        minusButton.setOnClickListener(v -> {
            int currentProgress = scaleSeekBar.getProgress();
            if (currentProgress > 0) {
                scaleSeekBar.setProgress(currentProgress - 1); // 微调 -1
            }
        });

        plusButton.setOnClickListener(v -> {
            int currentProgress = scaleSeekBar.getProgress();
            if (currentProgress < scaleSeekBar.getMax()) {
                scaleSeekBar.setProgress(currentProgress + 1); // 微调 +1
            }
        });

        // 创建并显示弹窗
        new CustomDialog.Builder(context)
                .setTitle(context.getString(R.string.mcl_setting_title_resolution_scaler))
                .setCustomView(layout)
                .setCancelable(false)
                .setDraggable(true)
                .setConfirmListener(android.R.string.ok, customView -> true)
                .build()
                .show();
    }

    private void changeResolutionRatioPreview(int progress, TextView resolutionTextView) {
        DisplayMetrics metrics = Tools.currentDisplayMetrics;
        int width = metrics.widthPixels;
        int height = metrics.heightPixels;
        boolean isLandscape = context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE || width > height;

        float progressDouble = progress / 100f;
        // 计算要显示的宽高,用Tools现有的方案getDisplayFriendlyRes()确保是偶数
        int previewWidth = Tools.getDisplayFriendlyRes(isLandscape ? width : height, progressDouble);
        int previewHeight = Tools.getDisplayFriendlyRes(isLandscape ? height : width, progressDouble);

        String preview = previewWidth + " x " + previewHeight;
        resolutionTextView.setText(preview);  // 实时更新TextView中的分辨率
    }

    public interface OnResolutionChangeListener {
        void onChange(float value);
    }
}