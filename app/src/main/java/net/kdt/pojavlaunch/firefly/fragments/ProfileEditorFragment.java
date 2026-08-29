package net.kdt.pojavlaunch.firefly.fragments;

import static com.firefly.utils.ToastUtils.Toast;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.util.Base64OutputStream;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.firefly.utils.ListUtils;
import com.movtery.ui.subassembly.customprofilepath.ProfilePathHome;
import com.movtery.ui.subassembly.customprofilepath.ProfilePathManager;

import net.kdt.pojavlaunch.firefly.R;
import net.kdt.pojavlaunch.firefly.Tools;
import net.kdt.pojavlaunch.firefly.extra.ExtraConstants;
import net.kdt.pojavlaunch.firefly.extra.ExtraCore;
import net.kdt.pojavlaunch.firefly.multirt.MultiRTUtils;
import net.kdt.pojavlaunch.firefly.multirt.RTSpinnerAdapter;
import net.kdt.pojavlaunch.firefly.multirt.Runtime;
import net.kdt.pojavlaunch.firefly.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.firefly.profiles.ProfileIconCache;
import net.kdt.pojavlaunch.firefly.profiles.VersionSelectorDialog;
import net.kdt.pojavlaunch.firefly.utils.CropperUtils;
import net.kdt.pojavlaunch.firefly.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.firefly.value.launcherprofiles.MinecraftProfile;
import net.kdt.pojavlaunch.firefly.version.LocalVersion;
import net.kdt.pojavlaunch.firefly.version.LocalVersionManager;
import net.kdt.pojavlaunch.firefly.version.VersionRemovalResult;
import net.kdt.pojavlaunch.firefly.version.VersionIsolation;

import kotlin.Pair;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ProfileEditorFragment extends Fragment implements CropperUtils.CropperListener {
    public static final String TAG = "ProfileEditorFragment";
    private static final float DISABLED_PATH_ALPHA = 0.65f;

    private String mProfileKey;
    private MinecraftProfile mTempProfile = null;
    private String mValueToConsume = "";
    private Button mSaveButton, mDeleteButton, mControlSelectButton, mGameDirButton, mVersionSelectButton;
    private CheckBox mEnableModsCheck;
    private Spinner mDefaultRuntime, mDefaultRenderer;
    private EditText mDefaultName, mDefaultJvmArgument;
    private TextView mDefaultPath, mDefaultVersion, mDefaultControl;
    private ImageView mProfileIcon;
    private final ActivityResultLauncher<?> mCropperLauncher = CropperUtils.registerCropper(this, this);

    private List<String> mRenderNames;
    private boolean mVersionDeleteInProgress;

    public ProfileEditorFragment() {
        super(R.layout.fragment_profile_editor);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Paths, which can be changed
        String value = (String) ExtraCore.consumeValue(ExtraConstants.FILE_SELECTOR);
        if (value != null) {
            if (mValueToConsume.equals(FileSelectorFragment.BUNDLE_SELECT_FOLDER)) {
                if (!LauncherPreferences.PREF_VERSION_ISOLATION) mTempProfile.gameDir = value;
            } else {
                mTempProfile.controlFile = value;
            }
        }
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        bindViews(view);

        ListUtils.RenderersList renderersList = ListUtils.getCompatibleRenderers(view.getContext());
        mRenderNames = renderersList.rendererIds;
        List<String> renderList = new ArrayList<>(renderersList.rendererDisplayNames.length + 1);
        renderList.addAll(Arrays.asList(renderersList.rendererDisplayNames));
        renderList.add(view.getContext().getString(R.string.global_default));
        mDefaultRenderer.setAdapter(new ArrayAdapter<>(getContext(), R.layout.item_simple_list_1, renderList));

        // Set up behaviors
        mSaveButton.setOnClickListener(v -> {
            ProfileIconCache.dropIcon(mProfileKey);
            save();

            Tools.backToMainMenu(requireActivity());
        });

        mDeleteButton.setOnClickListener(v -> confirmVersionDeletion());

        View.OnClickListener gameDirListener = getGameDirListener();
        mGameDirButton.setOnClickListener(gameDirListener);
        mDefaultPath.setOnClickListener(gameDirListener);

        View.OnClickListener controlSelectListener = getControlSelectListener();
        mControlSelectButton.setOnClickListener(controlSelectListener);
        mDefaultControl.setOnClickListener(controlSelectListener);

        // Setup the expendable list behavior
        View.OnClickListener versionSelectListener = getVersionSelectListener();
        mVersionSelectButton.setOnClickListener(versionSelectListener);
        mDefaultVersion.setOnClickListener(versionSelectListener);

        // Set up the icon change click listener
        mProfileIcon.setOnClickListener(v -> CropperUtils.startCropper(mCropperLauncher));

        loadValues(LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, ""), view.getContext());
    }

    private View.OnClickListener getGameDirListener() {
        return v -> {
            if (LauncherPreferences.PREF_VERSION_ISOLATION) return;
            Bundle bundle = new Bundle(2);
            bundle.putBoolean(FileSelectorFragment.BUNDLE_SELECT_FOLDER, true);
            bundle.putString(FileSelectorFragment.BUNDLE_ROOT_PATH, ProfilePathManager.getCurrentPath());
            bundle.putBoolean(FileSelectorFragment.BUNDLE_SHOW_FILE, false);
            mValueToConsume = FileSelectorFragment.BUNDLE_SELECT_FOLDER;

            Tools.swapFragment(requireActivity(),
                    FileSelectorFragment.class, FileSelectorFragment.TAG, bundle);
        };
    }

    private View.OnClickListener getControlSelectListener() {
        return v -> {
            Bundle bundle = new Bundle(3);
            bundle.putBoolean(FileSelectorFragment.BUNDLE_SELECT_FOLDER, false);
            bundle.putString(FileSelectorFragment.BUNDLE_ROOT_PATH, Tools.CTRLMAP_PATH);
            mValueToConsume = FileSelectorFragment.BUNDLE_SELECT_FILE;
            mValueToConsume = "select_file";

            Tools.swapFragment(requireActivity(),
                    FileSelectorFragment.class, FileSelectorFragment.TAG, bundle);
        };
    }

    private View.OnClickListener getVersionSelectListener() {
        return v -> VersionSelectorDialog.open(v.getContext(), (id, snapshot)-> {
            mTempProfile.lastVersionId = id;
            mDefaultVersion.setText(id);
            updateGameDirectoryPresentation();
            updateDeleteButtonState();
        });
    }

    private void loadValues(@NonNull String profile, @NonNull Context context) {
        if (mTempProfile == null) {
            mTempProfile = getProfile(profile);
        }
        mProfileIcon.setImageDrawable(
                ProfileIconCache.fetchIcon(getResources(), mProfileKey, mTempProfile.icon)
        );

        // Runtime spinner
        List<Runtime> runtimes = MultiRTUtils.getRuntimes();
        int jvmIndex = runtimes.indexOf(new Runtime("<Default>"));
        if (mTempProfile.javaDir != null) {
            String selectedRuntime = mTempProfile.javaDir.substring(Tools.LAUNCHERPROFILES_RTPREFIX.length());
            int nindex = runtimes.indexOf(new Runtime(selectedRuntime));
            if (nindex != -1) jvmIndex = nindex;
        }
        mDefaultRuntime.setAdapter(new RTSpinnerAdapter(context, runtimes));
        if (jvmIndex == -1) jvmIndex = runtimes.size() - 1;
        mDefaultRuntime.setSelection(jvmIndex);

        // Renderer spinner
        int rendererIndex = mDefaultRenderer.getAdapter().getCount() - 1;
        if (mTempProfile.pojavRendererName != null) {
            int nindex = mRenderNames.indexOf(mTempProfile.pojavRendererName);
            if (nindex != -1) rendererIndex = nindex;
        }
        mDefaultRenderer.setSelection(rendererIndex);

        mEnableModsCheck.setChecked(mTempProfile.enableModsCheck);

        mDefaultVersion.setText(mTempProfile.lastVersionId);
        mDefaultJvmArgument.setText(mTempProfile.javaArgs == null ? "" : mTempProfile.javaArgs);
        mDefaultName.setText(mTempProfile.name);
        mDefaultControl.setText(mTempProfile.controlFile == null ? "" : mTempProfile.controlFile);
        updateGameDirectoryPresentation();
        updateDeleteButtonState();
    }

    private MinecraftProfile getProfile(@NonNull String profile) {
        MinecraftProfile minecraftProfile;
        if (getArguments() == null) {
            LauncherProfiles.load(ProfilePathManager.getCurrentProfile());
            MinecraftProfile originalProfile = LauncherProfiles.mainProfileJson.profiles.get(profile);
            if (originalProfile != null) minecraftProfile = new MinecraftProfile(originalProfile);
            else minecraftProfile = MinecraftProfile.createTemplate();
            mProfileKey = profile;
        } else {
            minecraftProfile = MinecraftProfile.createTemplate();
            mProfileKey = LauncherProfiles.getFreeProfileKey();
        }
        return minecraftProfile;
    }

    private void bindViews(@NonNull View view) {
        mDefaultControl = view.findViewById(R.id.vprof_editor_ctrl_spinner);
        mDefaultRuntime = view.findViewById(R.id.vprof_editor_spinner_runtime);
        mDefaultRenderer = view.findViewById(R.id.vprof_editor_profile_renderer);
        mDefaultVersion = view.findViewById(R.id.vprof_editor_version_spinner);

        mDefaultPath = view.findViewById(R.id.vprof_editor_path);
        mDefaultName = view.findViewById(R.id.vprof_editor_profile_name);
        mDefaultJvmArgument = view.findViewById(R.id.vprof_editor_jre_args);

        mSaveButton = view.findViewById(R.id.vprof_editor_save_button);
        mDeleteButton = view.findViewById(R.id.vprof_editor_delete_button);
        mControlSelectButton = view.findViewById(R.id.vprof_editor_ctrl_button);
        mVersionSelectButton = view.findViewById(R.id.vprof_editor_version_button);
        mGameDirButton = view.findViewById(R.id.vprof_editor_path_button);
        mProfileIcon = view.findViewById(R.id.vprof_editor_profile_icon);
        mEnableModsCheck = view.findViewById(R.id.vprof_settings_enable_mods_check);
    }

    private void save() {
        //First, check for potential issues in the inputs
        mTempProfile.lastVersionId = mDefaultVersion.getText().toString();
        mTempProfile.controlFile = mDefaultControl.getText().toString();
        mTempProfile.name = mDefaultName.getText().toString();
        mTempProfile.javaArgs = mDefaultJvmArgument.getText().toString();
        mTempProfile.enableModsCheck = mEnableModsCheck.isChecked();

        if (mTempProfile.controlFile.isEmpty()) mTempProfile.controlFile = null;
        if (mTempProfile.javaArgs.isEmpty()) mTempProfile.javaArgs = null;
        if (!LauncherPreferences.PREF_VERSION_ISOLATION) {
            mTempProfile.gameDir = mDefaultPath.getText().toString();
            if (mTempProfile.gameDir.isEmpty()) mTempProfile.gameDir = null;
        }

        Runtime selectedRuntime = (Runtime) mDefaultRuntime.getSelectedItem();
        mTempProfile.javaDir = (selectedRuntime.name.equals("<Default>") || selectedRuntime.versionString == null)
                ? null : Tools.LAUNCHERPROFILES_RTPREFIX + selectedRuntime.name;

        if (mDefaultRenderer.getSelectedItemPosition() == mRenderNames.size())
            mTempProfile.pojavRendererName = null;
        else
            mTempProfile.pojavRendererName = mRenderNames.get(mDefaultRenderer.getSelectedItemPosition());

        LauncherProfiles.mainProfileJson.profiles.put(mProfileKey, mTempProfile);
        LauncherProfiles.write(ProfilePathManager.getCurrentProfile());
        ExtraCore.setValue(ExtraConstants.REFRESH_VERSION_SPINNER, mProfileKey);
    }

    private void updateGameDirectoryPresentation() {
        boolean isolated = LauncherPreferences.PREF_VERSION_ISOLATION;
        mDefaultPath.setEnabled(!isolated);
        mGameDirButton.setEnabled(!isolated);
        mDefaultPath.setAlpha(isolated ? DISABLED_PATH_ALPHA : 1f);
        mGameDirButton.setAlpha(isolated ? DISABLED_PATH_ALPHA : 1f);
        if (isolated) {
            mDefaultPath.setText(VersionIsolation.displayRelativeGameDirectory(
                    new File(ProfilePathHome.getGameHome()),
                    mTempProfile.lastVersionId
            ));
        } else {
            mDefaultPath.setText(mTempProfile.gameDir == null ? "" : mTempProfile.gameDir);
        }
    }

    private void updateDeleteButtonState() {
        String versionId = mTempProfile == null ? null : mTempProfile.lastVersionId;
        mDeleteButton.setEnabled(versionId != null && !versionId.trim().isEmpty() && !"Unknown".equals(versionId));
    }

    private void confirmVersionDeletion() {
        if (mVersionDeleteInProgress || mTempProfile == null) return;
        String versionId = mTempProfile.lastVersionId;
        if (versionId == null || versionId.trim().isEmpty() || "Unknown".equals(versionId)) {
            Toast(requireContext(), R.string.version_manager_delete_unavailable);
            return;
        }

        mVersionDeleteInProgress = true;
        new Thread(() -> {
            try {
                LocalVersion version = LocalVersionManager.INSTANCE.get(versionId);
                List<Pair<String, MinecraftProfile>> profiles = version == null
                        ? null
                        : LocalVersionManager.INSTANCE.profilesUsing(versionId);
                Tools.runOnUiThread(() -> showVersionDeleteConfirmation(versionId, version, profiles));
            } catch (Exception exception) {
                Tools.runOnUiThread(() -> {
                    mVersionDeleteInProgress = false;
                    if (isAdded()) Tools.showError(requireContext(), exception);
                });
            }
        }, "pgw-version-delete-check").start();
    }

    private void showVersionDeleteConfirmation(
            @NonNull String versionId,
            @Nullable LocalVersion version,
            @Nullable List<Pair<String, MinecraftProfile>> profiles
    ) {
        if (!isAdded()) return;
        if (version == null) {
            mVersionDeleteInProgress = false;
            Toast(requireContext(), R.string.version_manager_delete_unavailable);
            updateDeleteButtonState();
            return;
        }

        StringBuilder message = new StringBuilder(getString(
                LauncherPreferences.PREF_VERSION_ISOLATION
                        ? R.string.version_manager_delete_message_isolated
                        : R.string.version_manager_delete_message
        ));
        if (profiles != null && !profiles.isEmpty()) {
            message.append(getString(R.string.version_manager_profiles, profileNames(profiles)));
        }

        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.version_manager_delete_title, versionId))
                .setMessage(message)
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> mVersionDeleteInProgress = false)
                .setOnCancelListener(dialog -> mVersionDeleteInProgress = false)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> deleteVersion(versionId))
                .show();
    }

    @NonNull
    private String profileNames(@NonNull List<Pair<String, MinecraftProfile>> profiles) {
        StringBuilder names = new StringBuilder();
        for (Pair<String, MinecraftProfile> profile : profiles) {
            if (names.length() > 0) names.append(", ");
            String name = profile.getSecond().name;
            names.append(name == null || name.trim().isEmpty() ? profile.getFirst() : name);
        }
        return names.toString();
    }

    private void deleteVersion(@NonNull String versionId) {
        mDeleteButton.setEnabled(false);
        new Thread(() -> {
            try {
                VersionRemovalResult result = LocalVersionManager.INSTANCE.delete(versionId);
                Tools.runOnUiThread(() -> {
                    if (!isAdded()) return;
                    ExtraCore.setValue(ExtraConstants.REFRESH_VERSION_SPINNER, result.getSelectedProfileKey());
                    Tools.backToMainMenu(requireActivity());
                });
            } catch (Exception exception) {
                Tools.runOnUiThread(() -> {
                    mVersionDeleteInProgress = false;
                    if (!isAdded()) return;
                    mDeleteButton.setEnabled(true);
                    Tools.showError(requireContext(), exception);
                });
            }
        }, "pgw-version-delete").start();
    }

    @Override
    public void onCropped(Bitmap contentBitmap) {
        mProfileIcon.setImageBitmap(contentBitmap);
        Log.i("bitmap", "w=" + contentBitmap.getWidth() + " h=" + contentBitmap.getHeight());
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (Base64OutputStream base64OutputStream = new Base64OutputStream(byteArrayOutputStream, Base64.NO_WRAP)) {
            contentBitmap.compress(
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.R ?
                            // On Android < 30, there was no distinction between "lossy" and "lossless",
                            // and the type is picked by the quality parameter. We set the quality to 60.
                            // so it should be lossy,
                            Bitmap.CompressFormat.WEBP :
                            // On Android >= 30, we can explicitly specify that we want lossy compression
                            // with the visual quality of 60.
                            Bitmap.CompressFormat.WEBP_LOSSY,
                    60,
                    base64OutputStream
            );
            base64OutputStream.flush();
            byteArrayOutputStream.flush();
        } catch (IOException e) {
            Tools.showErrorRemote(e);
            return;
        }
        String iconLine = new String(byteArrayOutputStream.toByteArray(), StandardCharsets.UTF_8);
        mTempProfile.icon = "data:image/webp;base64," + iconLine;
    }

    @Override
    public void onFailed(Exception exception) {
        Tools.showErrorRemote(exception);
    }
}
