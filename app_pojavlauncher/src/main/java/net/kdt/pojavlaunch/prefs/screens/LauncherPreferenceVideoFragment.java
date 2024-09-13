package net.kdt.pojavlaunch.prefs.screens;

import static net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_NOTCH_SIZE;

import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import androidx.preference.ListPreference;
import androidx.preference.SwitchPreference;
import androidx.preference.SwitchPreferenceCompat;

import com.firefly.ui.dialog.CustomDialog;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.CustomSeekBarPreference;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

/**
 * Fragment for any settings video related
 */
public class LauncherPreferenceVideoFragment extends LauncherPreferenceFragment {
    private EditText mSetVideoResolution;

    @Override
    public void onCreatePreferences(Bundle b, String str) {
        addPreferencesFromResource(R.xml.pref_video);
        // Get values
        int scaleFactor = LauncherPreferences.PREF_SCALE_FACTOR;

        //Disable notch checking behavior on android 8.1 and below.
        requirePreference("ignoreNotch").setVisible(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && PREF_NOTCH_SIZE > 0);

        CustomSeekBarPreference seek5 = requirePreference("resolutionRatio",
                CustomSeekBarPreference.class);

        if (scaleFactor > 100) {
            seek5.setRange(25, scaleFactor);
        } else {
            seek5.setRange(25, 100);
        }

        seek5.setValue(scaleFactor);
        seek5.setSuffix(" %");

        // #724 bug fix
        if (scaleFactor < 25) {
            seek5.setValue(100);
        }

        seek5.setOnPreferenceClickListener(preference -> {
            setVideoResolutionDialog(seek5);
            return true;
        });

        // Sustained performance is only available since Nougat
        SwitchPreference sustainedPerfSwitch = requirePreference("sustainedPerformance",
                SwitchPreference.class);
        sustainedPerfSwitch.setVisible(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N);

        final ListPreference rendererListPreference = requirePreference("renderer", ListPreference.class);
        setListPreference(rendererListPreference, "renderer");

        rendererListPreference.setOnPreferenceChangeListener((pre, obj) -> {
            Tools.LOCAL_RENDERER = (String) obj;
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
        requirePreference("force_vsync", SwitchPreferenceCompat.class)
                .setVisible(LauncherPreferences.PREF_USE_ALTERNATE_SURFACE);
    }

    private void setListPreference(ListPreference listPreference, String preferenceKey) {
        Tools.IListAndArry array = null;
        String value = listPreference.getValue();
        if (preferenceKey.equals("renderer")) {
            array = Tools.getCompatibleRenderers(getContext());
            Tools.LOCAL_RENDERER = value;
        }
        listPreference.setEntries(array.getArray());
        listPreference.setEntryValues(array.getList().toArray(new String[0]));
    }

    private void setVideoResolutionDialog(CustomSeekBarPreference seek) {
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_video_resolution, null);
        mSetVideoResolution = view.findViewById(R.id.set_resolution);
        mSetVideoResolution.setText(String.valueOf(seek.getValue()));
        new CustomDialog.Builder(requireContext())
                .setCustomView(view)
                .setConfirmListener(R.string.alertdialog_done, customView -> {
                    String checkValue = mSetVideoResolution.getText().toString();
                    if (checkValue.isEmpty()) {
                        mSetVideoResolution.setError(getString(R.string.global_error_field_empty));
                        return false;
                    }
                    int Value;
                    try {
                        Value = Integer.parseInt(checkValue);
                    } catch (NumberFormatException e) {
                        Log.e("VideoResolution", e.toString());
                        // mSetVideoResolution.setError(e.toString());
                        mSetVideoResolution.setError(requireContext().getString(R.string.setting_set_resolution_outofrange, checkValue));
                        return false;
                    }
                    if (Value < 25 || Value > 1000) {
                        if (Value < 25) {
                            mSetVideoResolution.setError(requireContext().getString(R.string.setting_set_resolution_too_small, 25));
                        }
                        if (Value > 1000) {
                            mSetVideoResolution.setError(requireContext().getString(R.string.setting_set_resolution_too_big, 1000));
                        }
                        return false;
                    }
                    if (Value > 100) {
                        seek.setRange(25, Value);
                    } else {
                        seek.setRange(25, 100);
                    }
                    seek.setValue(Value);
                    return true;
                })
                .setCancelListener(R.string.alertdialog_cancel, customView -> true)
                .build()
                .show();
    }

}
