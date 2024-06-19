package net.kdt.pojavlaunch.prefs.screens;

import static net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_NOTCH_SIZE;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;

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
    private SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener;

    @Override
    public void onCreatePreferences(Bundle b, String str) {
        addPreferencesFromResource(R.xml.pref_video);
        // Get values
        int scaleFactor = LauncherPreferences.PREF_SCALE_FACTOR;

        //Disable notch checking behavior on android 8.1 and below.
        requirePreference("ignoreNotch").setVisible(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && PREF_NOTCH_SIZE > 0);

        CustomSeekBarPreference seek5 = requirePreference("resolutionRatio",
                CustomSeekBarPreference.class);
        seek5.setRange(25, 300);
        seek5.setValue(scaleFactor);
        seek5.setSuffix(" %");

        // #724 bug fix
        if (seek5.getValue() < 25) {
            seek5.setValue(100);
        }

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

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        rendererListPreference = requirePreference("renderer", ListPreference.class);
        setListPreference(rendererListPreference, "renderer");

        preferenceChangeListener = (sharedPreferences, key) -> {
            if (LauncherPreferences.PREF_EXP_SETUP.equals("ExperimentalSetup")) {
                updateRendererList();
            }
        };
        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(preferenceChangeListener);
    }

    private void updateRendererList() {
        if (rendererListPreference != null) {
            setListPreference(rendererListPreference, "renderer");
            rendererListPreference.setValueIndex(0);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(preferenceChangeListener);
    }
}
