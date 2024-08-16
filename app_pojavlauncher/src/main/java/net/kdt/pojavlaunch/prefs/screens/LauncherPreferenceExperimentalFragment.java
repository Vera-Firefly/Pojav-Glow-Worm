package net.kdt.pojavlaunch.prefs.screens;

import android.app.ProgressDialog;
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

import java.util.Arrays;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.MesaUtils;

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

        final Preference downloadMesa = requirePreference("DownloadMesa", Preference.class);
        downloadMesa.setOnPreferenceClickListener((a)-> {
            loadMesaList();
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
            boolean set = (boolean) v;
            if (!set) return false;
            closeOtherCustomMesaPref(customMesaVersionPref);
            return true;
        });

        SwitchPreference setSpecificVersion = requirePreference("ebSpecific", SwitchPreference.class);
        setSpecificVersion.setOnPreferenceChangeListener((p, v) -> {
            boolean set = (boolean) v;
            if (!set) return false;
            closeOtherCustomMesaPref(customMesaVersionPref);
            return true;
        });

        SwitchPreference setGLVersion = requirePreference("SetGLVersion", SwitchPreference.class);
        setGLVersion.setOnPreferenceChangeListener((p, v) -> {
            boolean set = (boolean) v;
            if (!set) return false;
            closeOtherCustomMesaPref(customMesaVersionPref);
            LauncherPreferences.DEFAULT_PREF.edit().putBoolean("ebCustom", true).apply();
            return true;
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
            boolean have = false;
            for (int a = 0; a < array.getList().size(); a++) {
                if (array.getList().get(a).equalsIgnoreCase(value)) {
                    have = true;
                    break;
                }
            }
            if (!have) {
                value = array.getList().get(0);
                listPreference.setValue(value);
            }
            Tools.MESA_LIBS = value;
        } else if (preferenceKey.equals("CDriverModels")) {
            array = Tools.getCompatibleCDriverModel(getContext());
            Tools.DRIVER_MODEL = value;
        }
        listPreference.setEntries(array.getArray());
        listPreference.setEntryValues(array.getList().toArray(new String[0]));
    }

    private void expTip() {
        String[] characters = {
        getString(R.string.alertdialog_tipa),
        getString(R.string.alertdialog_tipb),
        getString(R.string.alertdialog_tipc)
        };
        Random random = new Random();
        int index = random.nextInt(characters.length);
        String randomCharacter = characters[index];

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
            .setPositiveButton(R.string.preference_rendererexp_alertdialog_done, null)
            .setNegativeButton(R.string.preference_rendererexp_alertdialog_cancel, (dia, which) -> {
                onChangeRenderer();
                ((SwitchPreference) pre).setChecked(false);
            })
            .create();
        dialog.show();
    }

    // Custom Mesa GL/GLSL Version
    private void showSetGLVersionDialog() {
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_mesa_version, null);

        mMesaGLVersion = view.findViewById(R.id.mesa_gl_version);
        mMesaGLSLVersion = view.findViewById(R.id.mesa_glsl_version);

        mMesaGLVersion.setText(LauncherPreferences.PREF_MESA_GL_VERSION);
        mMesaGLSLVersion.setText(LauncherPreferences.PREF_MESA_GLSL_VERSION);

        AlertDialog dialog = new AlertDialog.Builder(getContext())
            .setTitle(R.string.preference_rendererexp_custom_glversion_title)
            .setView(view)
            .setPositiveButton(R.string.alertdialog_done, (dia, i) -> {
                String glVersion = mMesaGLVersion.getText().toString();
                String glslVersion = mMesaGLSLVersion.getText().toString();

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

                LauncherPreferences.PREF_MESA_GL_VERSION = glVersion;
                LauncherPreferences.PREF_MESA_GLSL_VERSION = glslVersion;

                LauncherPreferences.DEFAULT_PREF.edit()
                    .putString("mesaGLVersion", LauncherPreferences.PREF_MESA_GL_VERSION)
                    .putString("mesaGLSLVersion", LauncherPreferences.PREF_MESA_GLSL_VERSION)
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

    private void loadMesaList() {
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setMessage(R.string.preference_rendererexp_mesa_download_load)
                .show();
        PojavApplication.sExecutorService.execute(() -> {
            Set<String> list = MesaUtils.INSTANCE.getMesaList();
            requireActivity().runOnUiThread(() -> {
                dialog.dismiss();

                if (list == null) {
                    AlertDialog alertDialog1 = new AlertDialog.Builder(requireActivity())
                            .setMessage(R.string.preference_rendererexp_mesa_get_fail)
                            .create();
                    alertDialog1.show();
                } else {
                    final String[] items3 = new String[list.size()];
                    list.toArray(items3);
                    // Add List
                    AlertDialog alertDialog3 = new AlertDialog.Builder(requireActivity())
                            .setTitle(R.string.preference_rendererexp_mesa_select_download)
                            .setItems(items3, (dialogInterface, i) -> {
                                if (i < 0 || i > items3.length)
                                    return;
                                dialogInterface.dismiss();
                                downloadMesa(items3[i]);
                            })
                            .create();
                    alertDialog3.show();
                }
            });
        });
    }

    private void downloadMesa(String version) {
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setMessage(R.string.preference_rendererexp_mesa_downloading)
                .show();
        PojavApplication.sExecutorService.execute(() -> {
            boolean data = MesaUtils.INSTANCE.downloadMesa(version);
            requireActivity().runOnUiThread(() -> {
                dialog.dismiss();
                if (data) {
                    Toast.makeText(requireContext(), R.string.preference_rendererexp_mesa_downloaded, Toast.LENGTH_SHORT)
                            .show();
                } else {
                    AlertDialog alertDialog1 = new AlertDialog.Builder(requireActivity())
                            .setMessage(R.string.preference_rendererexp_mesa_download_fail)
                            .create();
                    alertDialog1.show();
                }
            });
        });
    }
}
