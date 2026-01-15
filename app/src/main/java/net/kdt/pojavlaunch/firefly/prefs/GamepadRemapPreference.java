package net.kdt.pojavlaunch.firefly.prefs;

import static com.firefly.utils.ToastUtils.Toast;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;

import net.kdt.pojavlaunch.firefly.R;

import fr.spse.gamepad_remapper.Remapper;

public class GamepadRemapPreference extends Preference {

    public GamepadRemapPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public GamepadRemapPreference(@NonNull Context context) {
        super(context);
        init();
    }

    private void init() {
        setOnPreferenceClickListener(preference -> {
            Remapper.wipePreferences(getContext());
            Toast(getContext(), R.string.preference_controller_map_wiped);
            return true;
        });
    }
}
