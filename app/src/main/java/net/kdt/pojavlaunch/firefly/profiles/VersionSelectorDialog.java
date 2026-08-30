package net.kdt.pojavlaunch.firefly.profiles;

import android.content.Context;
import android.view.LayoutInflater;
import android.widget.ExpandableListView;

import androidx.appcompat.app.AlertDialog;

import net.kdt.pojavlaunch.firefly.R;

public class VersionSelectorDialog {
    public static void open(Context context, VersionSelectorListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        ExpandableListView expandableListView = (ExpandableListView) LayoutInflater.from(context)
                .inflate(R.layout.dialog_expendable_list_view, null);
        VersionListAdapter adapter = new VersionListAdapter(context);

        expandableListView.setAdapter(adapter);
        builder.setView(expandableListView);
        AlertDialog dialog = builder.show();

        expandableListView.setOnChildClickListener((parent, v1, groupPosition, childPosition, id) -> {
            String version = adapter.getChild(groupPosition, childPosition);
            listener.onVersionSelected(version, false);
            dialog.dismiss();
            return true;
        });
    }
}
