package net.kdt.pojavlaunch.prefs.screens;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.MediaStore;
import android.os.Bundle;

import android.widget.Toast;
import androidx.preference.Preference;

import com.kdt.pickafile.FileListView;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;


public class LauncherPreferenceMiscellaneousFragment extends LauncherPreferenceFragment {
    @Override
    public void onCreatePreferences(Bundle b, String str) {
        addPreferencesFromResource(R.xml.pref_misc);
        Preference driverPreference = requirePreference("zinkPreferSystemDriver");
        if(!Tools.checkVulkanSupport(driverPreference.getContext().getPackageManager())) {
            driverPreference.setVisible(false);
        }
        findPreference("control_mouse_setting").setOnPreferenceClickListener((preference) -> {
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            startActivityForResult(intent, 1);
            return true;
        });

        findPreference("control_mouse_remove").setOnPreferenceClickListener((preference) -> {
            File file = new File(Tools.DIR_GAME_HOME, "mouse");
            if (file.exists()) {
                file.delete();
            }
            Toast.makeText(getContext(), R.string.notif_mouse1, Toast.LENGTH_SHORT).show();
            return true;
        });
    }
    @Override
    public void onActivityResult(
            int requestCode, int resultCode, final Intent data) {
        if (resultCode != Activity.RESULT_OK) {
            // Handle error
            return;
        }

        if (requestCode == 1) {// Get photo picker response for single select.
            Uri currentUri = data.getData();
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
                Toast.makeText(getContext(), R.string.notif_mouse, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
