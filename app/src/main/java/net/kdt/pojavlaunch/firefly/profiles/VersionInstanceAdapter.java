package net.kdt.pojavlaunch.firefly.profiles;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;

import net.kdt.pojavlaunch.firefly.R;
import net.kdt.pojavlaunch.firefly.version.PgwInstalledVersion;
import net.kdt.pojavlaunch.firefly.version.PgwVersionRepository;
import net.kdt.pojavlaunch.firefly.version.VersionIconCache;

import java.util.Collections;
import java.util.List;

import fr.spse.extended_view.ExtendedTextView;

/** Displays installed version directories together with a single installation entry. */
public final class VersionInstanceAdapter extends BaseAdapter {
    public static final Object INSTALL_ENTRY = new Object();
    private List<PgwInstalledVersion> versions = Collections.emptyList();
    private String currentVersionId;

    public void reload() {
        versions = PgwVersionRepository.INSTANCE.scan();
        PgwInstalledVersion current = PgwVersionRepository.INSTANCE.current();
        currentVersionId = current == null ? null : current.getId();
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return versions.size() + 1;
    }

    @Override
    public Object getItem(int position) {
        return position < versions.size() ? versions.get(position) : INSTALL_ENTRY;
    }

    public int findVersion(String id) {
        for (int i = 0; i < versions.size(); i++) {
            if (versions.get(i).getId().equals(id)) return i;
        }
        return versions.size();
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView == null
                ? LayoutInflater.from(parent.getContext()).inflate(R.layout.item_version_profile_layout, parent, false)
                : convertView;
        ExtendedTextView text = (ExtendedTextView) view;
        Object item = getItem(position);
        Drawable icon;
        if (item instanceof PgwInstalledVersion) {
            PgwInstalledVersion version = (PgwInstalledVersion) item;
            icon = VersionIconCache.fetch(parent.getResources(), version);
            String summary = version.getConfig().getSummary();
            text.setText(summary == null || summary.trim().isEmpty() ? version.getId() : summary);
            boolean selected = version.getId().equals(currentVersionId);
            text.setBackgroundColor(selected ? 0x3CFFFFFF : Color.TRANSPARENT);
        } else {
            icon = ResourcesCompat.getDrawable(parent.getResources(), R.drawable.ic_add, null);
            text.setText(R.string.version_manager_install_new);
            text.setBackgroundColor(Color.TRANSPARENT);
        }
        text.setCompoundDrawablesRelative(icon, null, text.getCompoundsDrawables()[2], null);
        return text;
    }
}
