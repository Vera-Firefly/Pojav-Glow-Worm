package net.kdt.pojavlaunch.firefly.customcontrols.mouse;

import static net.kdt.pojavlaunch.firefly.Tools.currentDisplayMetrics;
import static net.kdt.pojavlaunch.firefly.prefs.LauncherPreferences.DEFAULT_PREF;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

import net.kdt.pojavlaunch.firefly.GrabListener;
import net.kdt.pojavlaunch.firefly.R;
import net.kdt.pojavlaunch.firefly.Tools;
import net.kdt.pojavlaunch.firefly.prefs.LauncherPreferences;

import org.lwjgl.glfw.CallbackBridge;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * Class dealing with the virtual mouse
 */
public class Touchpad extends View implements GrabListener, AbstractTouchpad {
    /* Whether the Touchpad should be displayed */
    private boolean mDisplayState;
    /* Mouse pointer icon used by the touchpad */
    private Drawable mMousePointerDrawable;
    private float mMouseX, mMouseY;
    /* Resolution scaler option, allow downsizing a window */
    private float mScaleFactor = DEFAULT_PREF.getInt("resolutionRatio", 100) / 100f;

    public Touchpad(@NonNull Context context) {
        this(context, null);
    }

    public Touchpad(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    @Override
    public void refreshScaleFactor(float scaleFactor) {
        this.mScaleFactor = scaleFactor;
    }

    /**
     * Enable the touchpad
     */
    private void _enable() {
        setVisibility(VISIBLE);
        placeMouseAt(currentDisplayMetrics.widthPixels / 2f, currentDisplayMetrics.heightPixels / 2f);
    }

    /**
     * Disable the touchpad and hides the mouse
     */
    private void _disable() {
        setVisibility(GONE);
    }

    /**
     * @return The new state, enabled or disabled
     */
    public boolean switchState() {
        mDisplayState = !mDisplayState;
        if (!CallbackBridge.isGrabbing()) {
            if (mDisplayState) _enable();
            else _disable();
        }
        return mDisplayState;
    }

    public void placeMouseAt(float x, float y) {
        mMouseX = x;
        mMouseY = y;
        updateMousePosition();
    }

    private void sendMousePosition() {
        CallbackBridge.sendCursorPos((mMouseX * mScaleFactor), (mMouseY * mScaleFactor));
    }

    private void updateMousePosition() {
        sendMousePosition();
        // I wanted to implement a dirty rect for this, but it is ignored since API level 21
        // (which is our min API)
        // Let's hope the "internally calculated area" is good enough.
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.translate(mMouseX, mMouseY);
        mMousePointerDrawable.draw(canvas);
    }

    private void init() {
        loadMousePointer();
        setFocusable(false);
        setDefaultFocusHighlightEnabled(false);
        disable();
        mDisplayState = false;
    }
    private void loadMousePointer() {
        mMousePointerDrawable = loadCustomMousePointer()
                ? mMousePointerDrawable
                : loadDefaultMousePointerDrawable();
    }

    private boolean loadCustomMousePointer() {
        File mouseFile = new File(Tools.DIR_GAME_HOME, "mouse");

        if (!mouseFile.exists() || !mouseFile.isFile()) {
            return false;
        }

        try (InputStream stream = new FileInputStream(mouseFile)) {
            Bitmap bitmap = BitmapFactory.decodeStream(stream);
            if (bitmap == null) {
                return false;
            }

            mMousePointerDrawable = createScaledMouseDrawable(bitmap);
            return mMousePointerDrawable != null;

        } catch (Exception e) {
            return false;
        }
    }

    private BitmapDrawable createScaledMouseDrawable(Bitmap original) {
        int originalWidth = original.getWidth();
        int originalHeight = original.getHeight();

        if (originalWidth <= 0 || originalHeight <= 0) {
            return null;
        }

        float scale = LauncherPreferences.PREF_MOUSESCALE / 100f;
        int[] dimensions = calculateScaledDimensions(originalWidth, originalHeight, scale);

        Bitmap scaled = Bitmap.createScaledBitmap(original, dimensions[0], dimensions[1], true);

        BitmapDrawable drawable = new BitmapDrawable(getResources(), scaled);
        drawable.setBounds(0, 0, dimensions[0], dimensions[1]);
        return drawable;
    }

    private int[] calculateScaledDimensions(int width, int height, float scale) {
        float aspectRatio = (float) width / height;
        int targetWidth = (int) (36 * scale);
        int targetHeight = (int) (targetWidth / aspectRatio);
        int maxSize = (int) (200 * scale);

        if (targetWidth > maxSize || targetHeight > maxSize) {
            if (width > height) {
                targetWidth = maxSize;
                targetHeight = (int) (maxSize / aspectRatio);
            } else {
                targetHeight = maxSize;
                targetWidth = (int) (maxSize * aspectRatio);
            }
        }

        return new int[]{Math.max(1, targetWidth), Math.max(1, targetHeight)};
    }

    private Drawable loadDefaultMousePointerDrawable() {
        Drawable drawable = ResourcesCompat.getDrawable(
                getResources(),
                R.drawable.ic_mouse_pointer,
                getContext().getTheme()
        );

        if (drawable != null) {
            float scale = LauncherPreferences.PREF_MOUSESCALE / 100f;
            int width = Math.max(1, (int) (36 * scale));
            int height = Math.max(1, (int) (54 * scale));
            drawable.setBounds(0, 0, width, height);
        }

        return drawable;
    }

    @Override
    public void onGrabState(boolean isGrabbing) {
        post(() -> updateGrabState(isGrabbing));
    }

    private void updateGrabState(boolean isGrabbing) {
        if (!isGrabbing) {
            if (mDisplayState && getVisibility() != VISIBLE) _enable();
            if (!mDisplayState && getVisibility() == VISIBLE) _disable();
        } else {
            if (getVisibility() != View.GONE) _disable();
        }
    }

    @Override
    public boolean getDisplayState() {
        return mDisplayState;
    }

    @Override
    public void applyMotionVector(float x, float y) {
        mMouseX = Math.max(0, Math.min(currentDisplayMetrics.widthPixels, mMouseX + x * LauncherPreferences.PREF_MOUSESPEED));
        mMouseY = Math.max(0, Math.min(currentDisplayMetrics.heightPixels, mMouseY + y * LauncherPreferences.PREF_MOUSESPEED));
        updateMousePosition();
    }

    @Override
    public void enable(boolean supposed) {
        if (mDisplayState) return;
        mDisplayState = true;
        if (supposed && CallbackBridge.isGrabbing()) return;
        _enable();
    }

    @Override
    public void disable() {
        if (!mDisplayState) return;
        mDisplayState = false;
        _disable();
    }
}
