package net.kdt.pojavlaunch.firefly.prefs.screens;

import android.os.Bundle;

import net.kdt.pojavlaunch.firefly.R;

public class LauncherPreferenceMiscellaneousFragment extends LauncherPreferenceFragment {
    @Override
    public void onCreatePreferences(Bundle b, String str) {
        addPreferencesFromResource(R.xml.pref_misc);
    }

}
