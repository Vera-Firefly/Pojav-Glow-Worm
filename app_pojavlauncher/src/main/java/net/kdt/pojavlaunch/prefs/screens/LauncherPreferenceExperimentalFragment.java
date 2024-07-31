package net.kdt.pojavlaunch.prefs.screens;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.preference.*;

import java.util.Random;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

// Experimental Settings for Mesa renderer
public class LauncherPreferenceExperimentalFragment extends LauncherPreferenceFragment {

    private EditText mMesaGLVersion;
    private EditText mMesaGLSLVersion;
    private String expRenderer;

    @Override
    public void onCreatePreferences(Bundle b, String str) {
        addPreferencesFromResource(R.xml.pref_experimental);
        computeVisibility();

        findPreference("SetGLVersion").setOnPreferenceClickListener((preference) -> {
            showSetGLVersionDialog();
            return true;
        });

        final ListPreference CMesaLibP = requirePreference("CMesaLibrary", ListPreference.class);
        final ListPreference CDriverModelP = requirePreference("CDriverModels", ListPreference.class);
        
        setListPreference(CMesaLibP, "CMesaLibrary");
        setListPreference(CDriverModelP, "CDriverModels");
        
        CMesaLibP.setOnPreferenceChangeListener((pre, obj) -> {
                Tools.MESA_LIBS = (String)obj;
                setListPreference(CDriverModelP, "CDriverModels");
                CDriverModelP.setValueIndex(0);
                return true;
        });
        
        CDriverModelP.setOnPreferenceChangeListener((pre, obj) -> {
                Tools.DRIVER_MODEL = (String)obj;
                return true;
        });

        SwitchPreference expRendererPref = requirePreference("ExperimentalSetup", SwitchPreference.class);
        expRendererPref.setOnPreferenceChangeListener((p, v) -> {
            onChangeRenderer();
            boolean isExpRenderer = (boolean) v;
            if (isExpRenderer) {
                onExpRendererDialog(p);
            }
            return true;
        });

        // Custom GL/GLSL
        final PreferenceCategory customMesaVersionPref = requirePreference("customMesaVersionPref", PreferenceCategory.class);
        SwitchPreference setSystemVersion = requirePreference("ebSystem", SwitchPreference.class);
        setSystemVersion.setOnPreferenceChangeListener((p, v) -> {
            closeOtherCustomMesaPref(customMesaVersionPref);
            LauncherPreferences.PREF_EXP_ENABLE_SYSTEM = (boolean) v;
            return true;
        });

        SwitchPreference setSpecificVersion = requirePreference("ebSpecific", SwitchPreference.class);
        setSpecificVersion.setOnPreferenceChangeListener((p, v) -> {
            closeOtherCustomMesaPref(customMesaVersionPref);
            LauncherPreferences.PREF_EXP_ENABLE_SPECIFIC = (boolean) v;
            return true;
        });

        SwitchPreference setGLVersion = requirePreference("SetGLVersion", SwitchPreference.class);
        setGLVersion.setOnPreferenceChangeListener((preference, value) -> {
            boolean value1 = (boolean) value;
            if (value1) {
                closeOtherCustomMesaPref(customMesaVersionPref);
            }
            LauncherPreferences.PREF_EXP_ENABLE_CUSTOM = value1;
            LauncherPreferences.DEFAULT_PREF.edit().putBoolean("ebCustom", value1).apply();
            return value1;
        });
        setGLVersion.setOnPreferenceClickListener(preference -> {
            showSetGLVersionDialog();
            return true;
        });

    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String s) {
        super.onSharedPreferenceChanged(p, s);
        computeVisibility();
    }

    private void computeVisibility(){
        requirePreference("SpareFrameBuffer").setVisible(LauncherPreferences.PREF_EXP_SETUP);
        requirePreference("MesaRendererChoose").setVisible(LauncherPreferences.PREF_EXP_SETUP);
        requirePreference("customMesaVersionPref").setVisible(LauncherPreferences.PREF_EXP_SETUP);
    }

    private void setListPreference(ListPreference listPreference, String preferenceKey) {
        Tools.IListAndArry array = null;
        String value = listPreference.getValue();
        if (preferenceKey.equals("CMesaLibrary")) {
            array = Tools.getCompatibleCMesaLib(getContext());
            Tools.MESA_LIBS = value;
        } else if (preferenceKey.equals("CDriverModels")) {
            array = Tools.getCompatibleCDriverModel(getContext());
            Tools.DRIVER_MODEL = value;
        }
        listPreference.setEntries(array.getArray());
        listPreference.setEntryValues(array.getList().toArray(new String[0]));
    }

    //Try Open Extra Tip
    private void expTip() {
        //Generate any of there characters
        String[] characters = {
        getString(R.string.alertdialog_tipa),
        getString(R.string.alertdialog_tipb),
        getString(R.string.alertdialog_tipc)
        };
        Random random = new Random();
        int index = random.nextInt(characters.length);
        String randomCharacter = characters[index];

        // Create AlertDialog. Builder and set dialog content
        AlertDialog dialog = new AlertDialog.Builder(getContext())
            .setTitle("Tip:")
            .setMessage(randomCharacter)
            .setPositiveButton(R.string.preference_alertdialog_know, null)
            .create();
        dialog.show();
    }

    private void closeOtherCustomMesaPref(PreferenceCategory customMesaVersionPref) {
        for (int i = 0; i < customMesaVersionPref.getPreferenceCount(); i++) {
            Preference closepref = customMesaVersionPref.getPreference(i);
            if (closepref instanceof SwitchPreference) {
                ((SwitchPreference) closepref).setChecked(false);
            }
        }
    }

    private void onExpRendererDialog(Preference pre) {
        AlertDialog dialog = new AlertDialog.Builder(getContext())
            .setTitle(R.string.preference_rendererexp_alertdialog_warning)
            .setMessage(R.string.preference_rendererexp_alertdialog_message)
            .setPositiveButton(R.string.preference_rendererexp_alertdialog_done, (dia, which) -> {
                expTip();
            })
            .setNegativeButton(R.string.preference_rendererexp_alertdialog_cancel, (dia, which) -> {
                onChangeRenderer();
                ((SwitchPreference) pre).setChecked(false);
            })
            .create();
        dialog.show();
    }

    // Custom Mesa GL/GLSL Version
    private void showSetGLVersionDialog() {
        // Specify a layout films
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_mesa_version, null);

        // Define symbol content
        mMesaGLVersion = view.findViewById(R.id.mesa_gl_version);
        mMesaGLSLVersion = view.findViewById(R.id.mesa_glsl_version);

        // Set text for GL/GLSL values
        mMesaGLVersion.setText(LauncherPreferences.PREF_MESA_GL_VERSION);
        mMesaGLSLVersion.setText(LauncherPreferences.PREF_MESA_GLSL_VERSION);

        AlertDialog dialog = new AlertDialog.Builder(getContext())
            // Dialog content
            .setTitle(R.string.preference_rendererexp_custom_glversion_title)
            .setView(view)
            .setPositiveButton(R.string.alertdialog_done, (dia, i) -> {
                // Gets the GL and GLSL version of the user input
                String glVersion = mMesaGLVersion.getText().toString();
                String glslVersion = mMesaGLSLVersion.getText().toString();

                // Verify that the GL version is within the allowed range
                if (!isValidVersion(glVersion, "2.8", "4.6") && !isValidVersion(glslVersion, "280", "460")) {
                    showSetGLVersionDialog();
                    mMesaGLVersion.setError(getString(R.string.customglglsl_alertdialog_error_gl));
                    mMesaGLVersion.requestFocus();
                    mMesaGLSLVersion.setError(getString(R.string.customglglsl_alertdialog_error_glsl));
                    mMesaGLSLVersion.requestFocus();
                    return;
                } else if (!isValidVersion(glVersion, "2.8", "4.6")) {
                    showSetGLVersionDialog();
                    mMesaGLVersion.setError(getString(R.string.customglglsl_alertdialog_error_gl));
                    mMesaGLVersion.requestFocus();
                    return;
                } else if (!isValidVersion(glslVersion, "280", "460")) {
                    showSetGLVersionDialog();
                    mMesaGLSLVersion.setError(getString(R.string.customglglsl_alertdialog_error_glsl));
                    mMesaGLSLVersion.requestFocus();
                    return;
                }

                // Update preferences
                LauncherPreferences.PREF_MESA_GL_VERSION = glVersion;
                LauncherPreferences.PREF_MESA_GLSL_VERSION = glslVersion;
                LauncherPreferences.PREF_EXP_ENABLE_CUSTOM = true;

                // Modify the value of GL/GLSL according to the text content
                LauncherPreferences.DEFAULT_PREF.edit()
                    .putString("mesaGLVersion", LauncherPreferences.PREF_MESA_GL_VERSION)
                    .putString("mesaGLSLVersion", LauncherPreferences.PREF_MESA_GLSL_VERSION)
                    .putBoolean("ebCustom", true)
                    .apply();
            })
            .setNegativeButton(R.string.alertdialog_cancel, null)
            .create();
        dialog.show();
    }

    // Check whether the GL/GLSL version is within the acceptable range
    private boolean isValidVersion(String version, String minVersion, String maxVersion) {
        try {
            float versionNumber = Float.parseFloat(version);
            float minVersionNumber = Float.parseFloat(minVersion);
            float maxVersionNumber = Float.parseFloat(maxVersion);

        return versionNumber >= minVersionNumber && versionNumber <= maxVersionNumber;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void onChangeRenderer() {
        String rendererValue = LauncherPreferences.DEFAULT_PREF.getString("renderer", null);
        if ("mesa_3d".equals(rendererValue)) {
            LauncherPreferences.DEFAULT_PREF.edit().putString("renderer", expRenderer).apply();
        } else if ("vulkan_zink".equals(rendererValue)
        || "opengles3_virgl".equals(rendererValue)
        || "freedreno".equals(rendererValue)
        || "panfrost".equals(rendererValue)) {
            expRenderer = LauncherPreferences.DEFAULT_PREF.getString("renderer", null);
            LauncherPreferences.DEFAULT_PREF.edit().putString("renderer", "mesa_3d").apply();
        }
    }
}
