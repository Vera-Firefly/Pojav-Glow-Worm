package net.kdt.pojavlaunch.firefly.profiles;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ExpandableListAdapter;
import android.widget.TextView;

import com.movtery.ui.subassembly.customprofilepath.ProfilePathHome;

import net.kdt.pojavlaunch.firefly.R;

import java.io.File;
import java.util.Arrays;

public class VersionListAdapter extends BaseExpandableListAdapter implements ExpandableListAdapter {

    private final LayoutInflater mLayoutInflater;

    private final String[] mInstalledVersions;

    public VersionListAdapter(Context ctx) {
        mLayoutInflater = (LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        String[] versions = new File(ProfilePathHome.getVersionsHome()).list();
        mInstalledVersions = versions == null ? new String[0] : versions;
        Arrays.sort(mInstalledVersions);
    }

    @Override
    public int getGroupCount() {
        return 1;
    }

    @Override
    public int getChildrenCount(int groupPosition) {
        return mInstalledVersions.length;
    }

    @Override
    public Object getGroup(int groupPosition) {
        return mInstalledVersions;
    }

    @Override
    public String getChild(int groupPosition, int childPosition) {
        return mInstalledVersions[childPosition];
    }

    @Override
    public long getGroupId(int groupPosition) {
        return groupPosition;
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
        return childPosition;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
        if (convertView == null)
            convertView = mLayoutInflater.inflate(android.R.layout.simple_expandable_list_item_1, parent, false);

        ((TextView) convertView).setText(R.string.mcl_setting_veroption_installed);

        return convertView;
    }

    @Override
    public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
        if (convertView == null)
            convertView = mLayoutInflater.inflate(android.R.layout.simple_expandable_list_item_1, parent, false);
        ((TextView) convertView).setText(getChild(groupPosition, childPosition));
        return convertView;
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return true;
    }

}
