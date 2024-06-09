package net.kdt.pojavlaunch.fragments;

import com.movtery.ui.fragment.ProfilePathManagerFragment;
import com.qz.terminal2.ConsoleActivity;

import static net.kdt.pojavlaunch.Tools.runOnUiThread;
import static net.kdt.pojavlaunch.Tools.shareLog;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.kdt.mcgui.mcVersionSpinner;

import net.kdt.pojavlaunch.CustomControlsActivity;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.progresskeeper.TaskCountListener;

public class MainMenuFragment extends Fragment implements TaskCountListener {
    public static final String TAG = "MainMenuFragment";
    private static final int REQUEST_CODE_PERMISSIONS = 0;
    private mcVersionSpinner mVersionSpinner;
    private boolean mTasksRunning;

    public MainMenuFragment(){
        super(R.layout.fragment_launcher);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Button mNewsButton = view.findViewById(R.id.news_button);
        Button mCustomControlButton = view.findViewById(R.id.custom_control_button);
        Button mInstallJarButton = view.findViewById(R.id.install_jar_button);
        Button mStartTerminalButton = view.findViewById(R.id.start_terminal_button);
        Button mShareLogsButton = view.findViewById(R.id.share_logs_button);

        ImageButton mPathManagerButton = view.findViewById(R.id.path_manager_button);
        ImageButton mEditProfileButton = view.findViewById(R.id.edit_profile_button);
        Button mPlayButton = view.findViewById(R.id.play_button);
        mVersionSpinner = view.findViewById(R.id.mc_version_spinner);

        mNewsButton.setOnClickListener(v -> Tools.openURL(requireActivity(), Tools.URL_HOME));
        mCustomControlButton.setOnClickListener(v -> startActivity(new Intent(requireContext(), CustomControlsActivity.class)));
        mInstallJarButton.setOnClickListener(v -> runInstallerWithConfirmation(false));
        mInstallJarButton.setOnLongClickListener(v->{
            runInstallerWithConfirmation(true);
            return true;
        });
        mPathManagerButton.setOnClickListener(v -> {
            if (!mTasksRunning) {
                checkPermissions(() -> Tools.swapFragment(requireActivity(), ProfilePathManagerFragment.class, ProfilePathManagerFragment.TAG, null));
            } else {
                runOnUiThread(() -> Toast.makeText(requireContext(), R.string.profiles_path_task_in_progress, Toast.LENGTH_SHORT).show());
            }
        });
        mStartTerminalButton.setOnClickListener(v -> startActivity(new Intent(requireContext(), ConsoleActivity.class)));
        mEditProfileButton.setOnClickListener(v -> mVersionSpinner.openProfileEditor(requireActivity()));

        mPlayButton.setOnClickListener(v -> ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true));

        mShareLogsButton.setOnClickListener((v) -> shareLog(requireContext()));

        mNewsButton.setOnLongClickListener((v)->{
            Tools.swapFragment(requireActivity(), SearchModFragment.class, SearchModFragment.TAG, null);
            return true;
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        mVersionSpinner.reloadProfiles();
    }

    private void runInstallerWithConfirmation(boolean isCustomArgs) {
        if (ProgressKeeper.getTaskCount() == 0)
            Tools.installMod(requireActivity(), isCustomArgs);
        else
            Toast.makeText(requireContext(), R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onUpdateTaskCount(int taskCount) {
        mTasksRunning = taskCount != 0;
    }

    private void checkPermissions(PermissionGranted permissionGranted) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            handlePermissionsForAndroid11AndAbove(permissionGranted);
        } else {
            handlePermissionsForAndroid10AndBelow(permissionGranted);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    private void handlePermissionsForAndroid11AndAbove(PermissionGranted permissionGranted) {
        if (!Environment.isExternalStorageManager()) {
            showPermissionRequestDialog(() -> {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + requireActivity().getPackageName()));
                startActivityForResult(intent, REQUEST_CODE_PERMISSIONS);
            });
        } else {
            permissionGranted.granted();
        }
    }

    private void handlePermissionsForAndroid10AndBelow(PermissionGranted permissionGranted) {
        if (!hasStoragePermissions()) {
            showPermissionRequestDialog(() -> ActivityCompat.requestPermissions(requireActivity(), new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, REQUEST_CODE_PERMISSIONS));
        } else {
            permissionGranted.granted();
        }
    }

    private boolean hasStoragePermissions() {
        return ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void showPermissionRequestDialog(RequestPermissions requestPermissions) {
        new AlertDialog.Builder(requireContext())
                .setMessage(R.string.permissions_manage_external_storage)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> requestPermissions.onRequest())
                .setNegativeButton(android.R.string.cancel, null)
                .setCancelable(false)
                .show();
    }

    private interface RequestPermissions {
        void onRequest();
    }

    private interface PermissionGranted {
        void granted();
    }
}
