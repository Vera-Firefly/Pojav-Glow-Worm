package net.kdt.pojavlaunch.firefly.prefs.screens;

import static com.firefly.utils.ToastUtils.Toast;

import android.app.Activity;
import android.content.Intent;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.preference.PreferenceCategory;

import net.kdt.pojavlaunch.firefly.R;
import net.kdt.pojavlaunch.firefly.prefs.CustomSeekBarPreference;
import net.kdt.pojavlaunch.firefly.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.firefly.Tools;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class LauncherPreferenceControlFragment extends LauncherPreferenceFragment {
    private boolean mGyroAvailable = false;
    private ActivityResultLauncher<Intent> mouseSettingLauncher;

    @Override
    public void onCreatePreferences(Bundle b, String str) {
        // Initialize the ActivityResultLauncher for picking an image
        mouseSettingLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri currentUri = result.getData().getData();
                    try {
                        File file = new File(Tools.DIR_GAME_HOME, "mouse");
                        if (file.exists()) {
                            file.delete();
                        }

                        InputStream stream1 = getContext().getContentResolver().openInputStream(currentUri);
                        FileOutputStream stream = new FileOutputStream(file);

                        IOUtils.copy(stream1, stream);
                        stream.close();
                        stream1.close();
                        Toast(getContext(), R.string.notif_mouse);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        );

        // Get values
        int longPressTrigger = LauncherPreferences.PREF_LONGPRESS_TRIGGER;
        int prefButtonSize = (int) LauncherPreferences.PREF_BUTTONSIZE;
        int mouseScale = (int) LauncherPreferences.PREF_MOUSESCALE;
        int gyroSampleRate = LauncherPreferences.PREF_GYRO_SAMPLE_RATE;
        int touchControllerVibrateLength = LauncherPreferences.PREF_TOUCHCONTROLLER_VIBRATE_LENGTH;
        float mouseSpeed = LauncherPreferences.PREF_MOUSESPEED;
        float gyroSpeed = LauncherPreferences.PREF_GYRO_SENSITIVITY;
        float joystickDeadzone = LauncherPreferences.PREF_DEADZONE_SCALE;

        // Triggers a write for some reason which resets the value
        addPreferencesFromResource(R.xml.pref_control);

        CustomSeekBarPreference seek2 = requirePreference("timeLongPressTrigger",
                CustomSeekBarPreference.class);
        seek2.setRange(100, 1000);
        seek2.setValue(longPressTrigger);
        seek2.setSuffix(" ms");

        CustomSeekBarPreference seek3 = requirePreference("buttonscale",
                CustomSeekBarPreference.class);
        seek3.setRange(80, 250);
        seek3.setValue(prefButtonSize);
        seek3.setSuffix(" %");

        CustomSeekBarPreference seek4 = requirePreference("mousescale",
                CustomSeekBarPreference.class);
        seek4.setRange(25, 300);
        seek4.setValue(mouseScale);
        seek4.setSuffix(" %");

        CustomSeekBarPreference seek6 = requirePreference("mousespeed",
                CustomSeekBarPreference.class);
        seek6.setRange(25, 300);
        seek6.setValue((int) (mouseSpeed * 100f));
        seek6.setSuffix(" %");

        CustomSeekBarPreference deadzoneSeek = requirePreference("gamepad_deadzone_scale",
                CustomSeekBarPreference.class);
        deadzoneSeek.setRange(50, 200);
        deadzoneSeek.setValue((int) (joystickDeadzone * 100f));
        deadzoneSeek.setSuffix(" %");

        CustomSeekBarPreference touchControllerVibrateLengthSeek = requirePreference("touchControllerVibrateLength",
                CustomSeekBarPreference.class);
        touchControllerVibrateLengthSeek.setRange(10, 1000);
        touchControllerVibrateLengthSeek.setValue(touchControllerVibrateLength);
        touchControllerVibrateLengthSeek.setSuffix(" ms");

        Context context = getContext();
        if (context != null) {
            mGyroAvailable = ((SensorManager) context.getSystemService(Context.SENSOR_SERVICE)).getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null;
        }
        PreferenceCategory gyroCategory = requirePreference("gyroCategory",
                PreferenceCategory.class);
        gyroCategory.setVisible(mGyroAvailable);

        CustomSeekBarPreference gyroSensitivitySeek = requirePreference("gyroSensitivity",
                CustomSeekBarPreference.class);
        gyroSensitivitySeek.setRange(25, 300);
        gyroSensitivitySeek.setValue((int) (gyroSpeed * 100f));
        gyroSensitivitySeek.setSuffix(" %");

        CustomSeekBarPreference gyroSampleRateSeek = requirePreference("gyroSampleRate",
                CustomSeekBarPreference.class);
        gyroSampleRateSeek.setRange(5, 50);
        gyroSampleRateSeek.setValue(gyroSampleRate);
        gyroSampleRateSeek.setSuffix(" ms");

        // Custom Mouse
        findPreference("control_mouse_setting").setOnPreferenceClickListener(preference -> {
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            mouseSettingLauncher.launch(intent);
            return true;
        });

        findPreference("control_mouse_remove").setOnPreferenceClickListener(preference -> {
            File file = new File(Tools.DIR_GAME_HOME, "mouse");
            if (file.exists()) {
                file.delete();
            }
            Toast(getContext(), R.string.notif_mouse1);
            return true;
        });

        computeVisibility();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String s) {
        super.onSharedPreferenceChanged(p, s);
        computeVisibility();
    }

    private void computeVisibility() {
        requirePreference("timeLongPressTrigger").setVisible(!LauncherPreferences.PREF_DISABLE_GESTURES);
        requirePreference("gyroSensitivity").setVisible(LauncherPreferences.PREF_ENABLE_GYRO);
        requirePreference("gyroSampleRate").setVisible(LauncherPreferences.PREF_ENABLE_GYRO);
        requirePreference("gyroInvertX").setVisible(LauncherPreferences.PREF_ENABLE_GYRO);
        requirePreference("gyroInvertY").setVisible(LauncherPreferences.PREF_ENABLE_GYRO);
        requirePreference("gyroSmoothing").setVisible(LauncherPreferences.PREF_ENABLE_GYRO);
    }
}