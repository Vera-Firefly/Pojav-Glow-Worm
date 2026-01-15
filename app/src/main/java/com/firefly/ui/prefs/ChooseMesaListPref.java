package com.firefly.ui.prefs;

import static com.firefly.utils.ToastUtils.Toast;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.ListPreference;

import com.firefly.ui.dialog.CustomDialog;
import com.firefly.utils.ListUtils;
import com.firefly.utils.MesaUtils;

import net.kdt.pojavlaunch.firefly.R;

import java.util.Arrays;
import java.util.List;

public class ChooseMesaListPref extends ListPreference {

    private List<String> defaultLibs;
    private OnPreferenceChangeListener preferenceChangeListener;
    private View.OnClickListener importClickListener;
    private View.OnClickListener downloadClickListener;

    public ChooseMesaListPref(Context context, AttributeSet attrs) {
        super(context, attrs);
        loadDefaultLibs(context);
    }

    private void loadDefaultLibs(Context context) {
        defaultLibs = Arrays.asList(context.getResources().getStringArray(R.array.osmesa_values));
    }

    @Override
    protected void onClick() {
        String initialValue = getValue();
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(getDialogTitle());

        LinearLayout mainLayout = new LinearLayout(getContext());
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(50, 20, 50, 20);

        ListView listView = new ListView(getContext());
        CharSequence[] entriesCharSequence = getEntries();
        String[] entries = new String[entriesCharSequence.length];
        for (int i = 0; i < entriesCharSequence.length; i++) {
            entries[i] = entriesCharSequence[i].toString();
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_1, entries);
        listView.setAdapter(adapter);

        mainLayout.addView(listView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        LinearLayout buttonLayout = new LinearLayout(getContext());
        buttonLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonLayout.setGravity(android.view.Gravity.CENTER);

        Button importButton = new Button(getContext());
        importButton.setText(R.string.pgw_settings_custom_turnip_creat);

        Button downloadButton = new Button(getContext());
        downloadButton.setText(R.string.preference_extra_mesa_download);

        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        buttonLayout.addView(importButton, buttonParams);
        buttonLayout.addView(downloadButton, buttonParams);

        mainLayout.addView(buttonLayout);

        builder.setView(mainLayout);

        AlertDialog dialog = builder.create();
        dialog.show();

        importButton.setOnClickListener(v -> {
            if (importClickListener != null) {
                importClickListener.onClick(v);
            }
            dialog.dismiss();
        });

        downloadButton.setOnClickListener(v -> {
            if (downloadClickListener != null) {
                downloadClickListener.onClick(v);
            }
            dialog.dismiss();
        });

        listView.setOnItemClickListener((parent, view, position, id) -> {
            String newValue = getEntryValues()[position].toString();
            if (!newValue.equals(initialValue)) {
                if (getOnPreferenceChangeListener() != null) {
                    if (getOnPreferenceChangeListener().onPreferenceChange(this, newValue)) {
                        setValue(newValue);
                    }
                } else {
                    setValue(newValue);
                }
            }
            dialog.dismiss();
        });

        listView.setOnItemLongClickListener((adapterView, view, position, id) -> {
            String selectedVersion = getEntryValues()[position].toString();
            if (defaultLibs.contains(selectedVersion)) {
                Toast(getContext(), R.string.preference_rendererexp_mesa_delete_defaultlib);
            } else {
                showDeleteConfirmationDialog(selectedVersion);
            }
            dialog.dismiss();
            return true;
        });
    }

    @Override
    public void setOnPreferenceChangeListener(OnPreferenceChangeListener listener) {
        this.preferenceChangeListener = listener;
        super.setOnPreferenceChangeListener(listener);
    }

    public void setImportButton(String buttonText, View.OnClickListener listener) {
        this.importClickListener = listener;
    }

    public void setDownloadButton(String buttonText, View.OnClickListener listener) {
        this.downloadClickListener = listener;
    }

    private void showDeleteConfirmationDialog(String version) {
        new CustomDialog.Builder(getContext())
                .setTitle(getContext().getString(R.string.preference_rendererexp_mesa_delete_title))
                .setMessage(getContext().getString(R.string.preference_rendererexp_mesa_delete_message, version))
                .setConfirmListener(android.R.string.ok, customView -> {
                    boolean success = MesaUtils.INSTANCE.deleteMesaLib(version);
                    if (success) {
                        Toast(getContext(), R.string.preference_rendererexp_mesa_deleted);
                        setEntriesAndValues();
                    } else {
                        Toast(getContext(), R.string.preference_rendererexp_mesa_delete_fail);
                    }
                    return true;
                })
                .setCancelListener(android.R.string.cancel, customView -> true)
                .build()
                .show();
    }

    private void setEntriesAndValues() {
        ListUtils.ListAndArray array = ListUtils.getCompatibleCMesaLib(getContext());
        setEntries(array.getArray());
        setEntryValues(array.getList().toArray(new String[0]));
        String currentValue = getValue();
        if (!array.getList().contains(currentValue)) {
            setValueIndex(0);
        }
    }
}