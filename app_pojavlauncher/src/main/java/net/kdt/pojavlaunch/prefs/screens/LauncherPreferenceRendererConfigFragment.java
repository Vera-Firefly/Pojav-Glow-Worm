package net.kdt.pojavlaunch.prefs.screens;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.*;

import java.util.Random;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

// Experimental Settings for Mesa renderer
public class LauncherPreferenceRendererConfigFragment extends LauncherPreferenceFragment {

    private EditText mMesaGLVersion;
    private EditText mMesaGLSLVersion;

    @Override
    public void onCreatePreferences(Bundle b, String str) {
        addPreferencesFromResource(R.xml.pref_renderexp);
        computeVisibility();

        findPreference("SetGLVersion").setOnPreferenceClickListener((preference) -> {
            showSetGLVersionDialog();
            return true;
        });

        // Get RadioGroup Preference for extra
        final PreferenceCategory radioGroupPref = findPreference("radioGroupPref");
        final PreferenceCategory customMesaVersionPref = findPreference("customMesaVersionPref");
        // Adding a Listener for an Option in a RadioGroup
        for (int i = 0; i < radioGroupPref.getPreferenceCount(); i++) {
            final Preference preference = radioGroupPref.getPreference(i);
            preference.setOnPreferenceClickListener(preference1 -> {
                // Set Selected Status
                for (int i12 = 0; i12 < radioGroupPref.getPreferenceCount(); i12++) {
                    ((SwitchPreference) radioGroupPref.getPreference(i12)).setChecked(false);
                }
                ((SwitchPreference) preference1).setChecked(true);
                // Perform the appropriate action
                if (preference1.getKey().equals("ZinkF")) {
                    Toast.makeText(getContext(), R.string.mcl_setting_renderer_default, Toast.LENGTH_SHORT).show();
                } else if (preference1.getKey().equals("ZinkS")) {
                    Toast.makeText(getContext(), R.string.mcl_setting_renderer_zinks, Toast.LENGTH_SHORT).show();
                } else if (preference1.getKey().equals("VulkanLwarlip")) {
                    Toast.makeText(getContext(), R.string.mcl_setting_renderer_zinkt, Toast.LENGTH_SHORT).show();
                } else if (preference1.getKey().equals("Rvirpipe")) {
                    Toast.makeText(getContext(), R.string.mcl_setting_renderer_virgl, Toast.LENGTH_SHORT).show();
                } else if (preference1.getKey().equals("Rpanfrost")) {
                    Toast.makeText(getContext(), R.string.mcl_setting_renderer_pan, Toast.LENGTH_SHORT).show();
                } else if (preference1.getKey().equals("Rfreedreno")) {
                    Toast.makeText(getContext(), R.string.mcl_setting_renderer_fd, Toast.LENGTH_SHORT).show();
                }
                return true;
            });
        }
        for (int i = 0; i < customMesaVersionPref.getPreferenceCount(); i++) {
            final Preference custommvs = customMesaVersionPref.getPreference(i);
            custommvs.setOnPreferenceClickListener(custommvs1 -> {
                for (int i1 = 0; i1 < customMesaVersionPref.getPreferenceCount(); i1++) {
                    ((SwitchPreference) customMesaVersionPref.getPreference(i1)).setChecked(false);
                }
                ((SwitchPreference) custommvs1).setChecked(true);
                if (custommvs1.getKey().equals("ebCustom")) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                    builder.setTitle(getString(R.string.preference_rendererexp_alertdialog_warning));
                    builder.setMessage(getString(R.string.preference_exp_alertdialog_glmessage));
                    builder.setPositiveButton(getString(R.string.preference_rendererexp_alertdialog_done), (dialog, which) -> dialog.dismiss());
                    builder.setNegativeButton(getString(R.string.preference_rendererexp_alertdialog_cancel), (dialog, which) -> ((SwitchPreference) custommvs1).setChecked(false));
//                    AlertDialog dialog = builder.create();
                    builder.show();
                }
                return true;
            });
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String s) {
        super.onSharedPreferenceChanged(p, s);
        computeVisibility();

        // Warning pops up when using experimental settings
        if (s.equals("ExperimentalSetup")) {
            Preference experimentalSetUpPreference = requirePreference("ExperimentalSetup");
            boolean isExperimentalSetUpEnabled = p.getBoolean("ExperimentalSetup", false);

            if (isExperimentalSetUpEnabled) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setTitle(getString(R.string.preference_rendererexp_alertdialog_warning));
                builder.setMessage(getString(R.string.preference_rendererexp_alertdialog_message));
                builder.setPositiveButton(getString(R.string.preference_rendererexp_alertdialog_done), (dialog, which) -> showPopupDialogWithRandomCharacter());
                builder.setNegativeButton(getString(R.string.preference_rendererexp_alertdialog_cancel), (dialog, which) -> {
                    ((SwitchPreference) experimentalSetUpPreference).setChecked(false);
                    SharedPreferences.Editor editor = p.edit();
                    editor.putBoolean("ExperimentalSetup", false);
                    editor.apply();
                });
//                AlertDialog dialog = builder.create();
                builder.show();
            }
        }
    }

    private void computeVisibility(){
        requirePreference("ExpFrameBuffer").setVisible(LauncherPreferences.PREF_EXP_SETUP);
        requirePreference("ebSystem").setVisible(LauncherPreferences.PREF_EXP_SETUP);
        requirePreference("ebSpecific").setVisible(LauncherPreferences.PREF_EXP_SETUP);
        requirePreference("ebCustom").setVisible(LauncherPreferences.PREF_EXP_SETUP);
        requirePreference("ZinkF").setVisible(LauncherPreferences.PREF_EXP_SETUP);
        requirePreference("ZinkS").setVisible(LauncherPreferences.PREF_EXP_SETUP);
        requirePreference("VulkanLwarlip").setVisible(LauncherPreferences.PREF_EXP_SETUP);
        requirePreference("Rvirpipe").setVisible(LauncherPreferences.PREF_EXP_SETUP);
        requirePreference("Rpanfrost").setVisible(LauncherPreferences.PREF_EXP_SETUP);
        requirePreference("Rfreedreno").setVisible(LauncherPreferences.PREF_EXP_SETUP);
        requirePreference("SetGLVersion").setVisible(LauncherPreferences.PREF_EXP_ENABLE_CUSTOM);
    }

    // Extra dialog
    private void showPopupDialogWithRandomCharacter() {
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
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Tip:");
        builder.setMessage(randomCharacter);

        // Set the dialog window button
        builder.setPositiveButton(getString(R.string.preference_alertdialog_know), (dialogInterface, i) -> dialogInterface.dismiss());

        // Create and display dialog
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    // Custom Mesa GL/GLSL Version
    private void showSetGLVersionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        // Specify a layout films
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_mesa_version, null);

        // Define symbol content
        mMesaGLVersion = view.findViewById(R.id.mesa_gl_version);
        mMesaGLSLVersion = view.findViewById(R.id.mesa_glsl_version);

        mMesaGLVersion.setText(LauncherPreferences.PREF_MESA_GL_VERSION);
        mMesaGLSLVersion.setText(LauncherPreferences.PREF_MESA_GLSL_VERSION);

        // Dialog content
        builder.setView(view);
        builder.setTitle(getString(R.string.preference_rendererexp_custom_glversion_title));
        builder.setPositiveButton(getString(R.string.alertdialog_done), (dialog, which) -> {
            LauncherPreferences.PREF_MESA_GL_VERSION = mMesaGLVersion.getText().toString();
            LauncherPreferences.PREF_MESA_GLSL_VERSION = mMesaGLSLVersion.getText().toString();

            LauncherPreferences.DEFAULT_PREF.edit()
                .putString("mesaGLVersion", LauncherPreferences.PREF_MESA_GL_VERSION).apply();
            LauncherPreferences.DEFAULT_PREF.edit()
                .putString("mesaGLSLVersion", LauncherPreferences.PREF_MESA_GLSL_VERSION).apply();
            dialog.dismiss();
        });
        builder.setNegativeButton(getString(R.string.alertdialog_cancel), (dialog, which) -> dialog.cancel());

        AlertDialog dialog = builder.create();
        dialog.show();
    }
}
