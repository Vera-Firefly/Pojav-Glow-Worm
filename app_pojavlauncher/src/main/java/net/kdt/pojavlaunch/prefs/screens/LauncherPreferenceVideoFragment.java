package net.kdt.pojavlaunch.prefs.screens;

import static net.kdt.pojavlaunch.prefs.LauncherPreferences.DEFAULT_PREF;
import static net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_NOTCH_SIZE;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.SwitchPreference;
import androidx.preference.SwitchPreferenceCompat;

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
        seek5.setMin(25);
        seek5.setValue(scaleFactor);
        seek5.setSuffix(" %");

        // #724 bug fix
        if (seek5.getValue() < 25) {
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
            Tools.LOCAL_RENDERER = (String)obj;
            return true;
        });

        computeVisibility();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String s) {
        super.onSharedPreferenceChanged(p, s);
        computeVisibility();
    }

    private void computeVisibility(){
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
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
            .setView(view)
            .setPositiveButton(R.string.alertdialog_done, (dia, i) -> {
                String checkValue = mSetVideoResolution.getText().toString();
                if (checkValue.isEmpty()) {
                    setVideoResolutionDialog(seek);
                    mSetVideoResolution.setError(getString(R.string.global_error_field_empty));
                    return;
                }
                int Value;
                try {
                    Value = Integer.parseInt(checkValue);
                } catch (NumberFormatException e) {
                    Log.e("VideoResolution", e.toString());
                    // mSetVideoResolution.setError(e.toString());
                    setVideoResolutionDialog(seek);
                    mSetVideoResolution.setError(requireContext().getString(R.string.setting_set_resolution_outofrange, checkValue));
                    return;
                }
                if (Value < 25) {
                    setVideoResolutionDialog(seek);
                    mSetVideoResolution.setError(requireContext().getString(R.string.setting_set_resolution_too_small, 25));
                    return;
                }
                if (Value > 1000) {
                    setVideoResolutionDialog(seek);
                    mSetVideoResolution.setError(requireContext().getString(R.string.setting_set_resolution_too_big, 1000));
                    return;
                    }
                seek.setValue(Value);
                })
            .setNegativeButton(R.string.alertdialog_cancel, null)
            .create();
        dialog.show();
    }

}
