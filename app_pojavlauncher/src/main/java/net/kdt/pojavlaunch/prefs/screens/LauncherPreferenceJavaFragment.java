package net.kdt.pojavlaunch.prefs.screens;

import static net.kdt.pojavlaunch.Architecture.is32BitsDevice;
import static net.kdt.pojavlaunch.Tools.getTotalDeviceMemory;
import static net.kdt.pojavlaunch.Tools.runOnUiThread;

import android.content.Context;

import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.preference.EditTextPreference;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.contracts.OpenDocumentWithExtension;
import net.kdt.pojavlaunch.multirt.MultiRTConfigDialog;
import net.kdt.pojavlaunch.prefs.CustomSeekBarPreference;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import com.movtery.utils.MemoryUtils;

import java.util.Timer;
import java.util.TimerTask;

public class LauncherPreferenceJavaFragment extends LauncherPreferenceFragment {
    private final Timer timer = new Timer();
    private MultiRTConfigDialog mDialogScreen;
    private final ActivityResultLauncher<Object> mVmInstallLauncher =
            registerForActivityResult(new OpenDocumentWithExtension("xz"), (data)->{
                if(data != null) Tools.installRuntimeFromUri(getContext(), data);
            });

    @Override
    public void onCreatePreferences(Bundle b, String str) {
        int ramAllocation = LauncherPreferences.PREF_RAM_ALLOCATION;
        // Triggers a write for some reason
        addPreferencesFromResource(R.xml.pref_java);

        CustomSeekBarPreference seek7 = requirePreference("allocation",
                CustomSeekBarPreference.class);

        int maxRAM;
        int deviceRam = getTotalDeviceMemory(seek7.getContext());

        if(is32BitsDevice() || deviceRam < 2048) maxRAM = Math.min(1024, deviceRam);
        else maxRAM = deviceRam - (deviceRam < 3064 ? 800 : 1024); //To have a minimum for the device to breathe

        seek7.setMin(256);
        seek7.setMax(maxRAM);
        seek7.setValue(ramAllocation);
        seek7.setSuffix(" MB");

        updateMemoryInfo(requireContext(), seek7);
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(() -> updateMemoryInfo(requireContext(), seek7));
            }
        }, 0, 1000);

        EditTextPreference editJVMArgs = findPreference("javaArgs");
        if (editJVMArgs != null) {
            editJVMArgs.setOnBindEditTextListener(TextView::setSingleLine);
        }

        requirePreference("install_jre").setOnPreferenceClickListener(preference->{
            openMultiRTDialog();
            return true;
        });
    }

    @Override
    public void onDestroy() {
        timer.cancel();
        super.onDestroy();
    }

    private void updateMemoryInfo(Context context, CustomSeekBarPreference seek) {
        String summary = getString(
                R.string.zh_setting_java_memory_info,
                Tools.formatFileSize(MemoryUtils.getUsedDeviceMemory(context)),
                Tools.formatFileSize(MemoryUtils.getTotalDeviceMemory(context)),
                Tools.formatFileSize(MemoryUtils.getFreeDeviceMemory(context)));
        runOnUiThread(() -> seek.setSummary(summary));
    }

    private void openMultiRTDialog() {
        if (mDialogScreen == null) {
            mDialogScreen = new MultiRTConfigDialog();
            mDialogScreen.prepare(getContext(), mVmInstallLauncher);
        }
        mDialogScreen.show();
    }
}
