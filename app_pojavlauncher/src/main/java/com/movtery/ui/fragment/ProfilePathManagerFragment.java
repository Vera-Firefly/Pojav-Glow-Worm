package com.movtery.ui.fragment;

import android.content.Context;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.TooltipCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.movtery.ui.subassembly.customprofilepath.ProfileItem;
import com.movtery.ui.subassembly.customprofilepath.ProfilePathAdapter;
import com.movtery.ui.subassembly.customprofilepath.ProfilePathJsonObject;
import com.movtery.ui.subassembly.customprofilepath.ProfilePathManager;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.fragments.FileSelectorFragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class ProfilePathManagerFragment extends Fragment {
    public static final String TAG = "ProfilePathManagerFragment";
    private final List<ProfileItem> mData = new ArrayList<>();
    private ProfilePathAdapter adapter;

    public ProfilePathManagerFragment() {
        super(R.layout.fragment_profile_path_manager);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        String value = (String) ExtraCore.consumeValue(ExtraConstants.FILE_SELECTOR);

        if (value != null && !value.isEmpty() && !isAddedPath(value)) {
            Context context = requireContext();
            final EditText edit = new EditText(context);
            edit.setSingleLine();

            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setView(edit).setTitle(R.string.profiles_path_create_new_title);
            builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
                String string = edit.getText().toString();
                if (string.isEmpty()) {
                    edit.setError(getString(R.string.global_error_field_empty));
                    return;
                }

                mData.add(new ProfileItem(UUID.randomUUID().toString(), string, value));
                ProfilePathManager.save(mData);
                refresh();
            });
            builder.setNegativeButton(android.R.string.cancel, null);

            builder.show();
        }

        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        refreshData();

        RecyclerView pathList = view.findViewById(R.id.profile_path);
        ImageButton refreshButton = view.findViewById(R.id.profile_path_refresh_button);
        ImageButton createNewButton = view.findViewById(R.id.profile_path_create_new_button);
        ImageButton returnButton = view.findViewById(R.id.profile_path_return_button);

        TooltipCompat.setTooltipText(refreshButton, refreshButton.getContentDescription());
        TooltipCompat.setTooltipText(createNewButton, createNewButton.getContentDescription());
        TooltipCompat.setTooltipText(returnButton, returnButton.getContentDescription());

        adapter = new ProfilePathAdapter(pathList, this.mData);
        pathList.setLayoutManager(new LinearLayoutManager(requireContext()));
        pathList.setAdapter(adapter);

        refreshButton.setOnClickListener(v -> refresh());
        createNewButton.setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            bundle.putBoolean(FileSelectorFragment.BUNDLE_SELECT_FOLDER, true);
            bundle.putBoolean(FileSelectorFragment.BUNDLE_SHOW_FILE, false);
            bundle.putBoolean(FileSelectorFragment.BUNDLE_REMOVE_LOCK_PATH, false);
            bundle.putString(FileSelectorFragment.BUNDLE_ROOT_PATH, Environment.getExternalStorageDirectory().getAbsolutePath());

            Tools.swapFragment(requireActivity(),
                    FileSelectorFragment.class, FileSelectorFragment.TAG, bundle);
        });
        returnButton.setOnClickListener(v -> requireActivity().onBackPressed());
    }

    private void refresh() {
        refreshData();
        adapter.updateData(this.mData);
    }

    private void refreshData() {
        this.mData.clear();
        this.mData.add(new ProfileItem("default", getString(R.string.profiles_path_default), Tools.DIR_GAME_HOME));

        try {
            String json;
            if (Tools.FILE_PROFILE_PATH.exists()) {
                json = Tools.read(Tools.FILE_PROFILE_PATH);
                if (json.isEmpty()) {
                    return;
                }
            } else {
                return;
            }

            JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();

            for (String key : jsonObject.keySet()) {
                ProfilePathJsonObject profilePathId = new Gson().fromJson(jsonObject.get(key), ProfilePathJsonObject.class);
                ProfileItem item = new ProfileItem(key, profilePathId.title, profilePathId.path);
                this.mData.add(item);
            }
        } catch (Exception ignored) {
        }
    }

    private boolean isAddedPath(String path) {
        for (ProfileItem mDatum : this.mData) {
            if (Objects.equals(mDatum.path, path)) {
                return true;
            }
        }
        return false;
    }
}
