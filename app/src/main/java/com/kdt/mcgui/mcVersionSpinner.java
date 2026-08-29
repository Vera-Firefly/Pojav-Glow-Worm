package com.kdt.mcgui;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.transition.Slide;
import android.transition.Transition;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ListView;
import android.widget.PopupWindow;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.FragmentActivity;

import net.kdt.pojavlaunch.firefly.R;
import net.kdt.pojavlaunch.firefly.Tools;
import net.kdt.pojavlaunch.firefly.fragments.VersionCatalogFragment;
import net.kdt.pojavlaunch.firefly.fragments.VersionSettingsFragment;
import net.kdt.pojavlaunch.firefly.profiles.VersionInstanceAdapter;
import net.kdt.pojavlaunch.firefly.version.PgwInstalledVersion;
import net.kdt.pojavlaunch.firefly.version.PgwVersionRepository;

import fr.spse.extended_view.ExtendedTextView;

/**
 * A class implementing custom spinner like behavior, notably:
 * dropdown popup view with a custom direction.
 */
public class mcVersionSpinner extends ExtendedTextView {
    public mcVersionSpinner(@NonNull Context context) {
        super(context);
        init();
    }

    public mcVersionSpinner(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public mcVersionSpinner(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    /* The class is in charge of displaying its own list with adapter content being known in advance */
    private ListView mListView = null;
    private PopupWindow mPopupWindow = null;
    private Object mPopupAnimation;
    private int mSelectedIndex = -1;

    private final VersionInstanceAdapter mVersionAdapter = new VersionInstanceAdapter();


    /**
     * Set the selection AND saves it as a shared preference
     */
    public void setProfileSelection(int position) {
        setSelection(position);
        Object selection = mVersionAdapter.getItem(position);
        if (selection instanceof PgwInstalledVersion) {
            PgwVersionRepository.INSTANCE.select(((PgwInstalledVersion) selection).getId());
        }
    }

    public void setSelection(int position) {
        if (mListView != null) mListView.setSelection(position);
        Object item = mVersionAdapter.getItem(position);
        if (item instanceof PgwInstalledVersion) {
            PgwInstalledVersion version = (PgwInstalledVersion) item;
            setText(version.getConfig().getSummary() == null || version.getConfig().getSummary().trim().isEmpty()
                    ? version.getId() : version.getConfig().getSummary());
            setCompoundDrawablesRelative(
                    net.kdt.pojavlaunch.firefly.version.VersionIconCache.fetch(getResources(), version),
                    null, getCompoundsDrawables()[2], null);
        } else {
            setText(R.string.version_manager_install_new);
            setCompoundDrawablesRelative(ResourcesCompat.getDrawable(getResources(), R.drawable.ic_add, null),
                    null, getCompoundsDrawables()[2], null);
        }
        mSelectedIndex = position;
    }

    public void openProfileEditor(FragmentActivity fragmentActivity) {
        Object currentSelection = mVersionAdapter.getItem(Math.max(0, mSelectedIndex));
        if (currentSelection instanceof PgwInstalledVersion) {
            android.os.Bundle arguments = new android.os.Bundle(1);
            arguments.putString(VersionSettingsFragment.ARG_VERSION_ID, ((PgwInstalledVersion) currentSelection).getId());
            Tools.swapFragment(fragmentActivity, VersionSettingsFragment.class, VersionSettingsFragment.TAG, arguments);
        } else {
            Tools.swapFragment(fragmentActivity, VersionCatalogFragment.class, VersionCatalogFragment.TAG, null);
        }
    }

    /**
     * Reload profiles from the file, forcing the spinner to consider the new data
     */
    public void reloadProfiles() {
        mVersionAdapter.reload();
        PgwInstalledVersion current = PgwVersionRepository.INSTANCE.current();
        setSelection(mVersionAdapter.findVersion(current == null ? "" : current.getId()));
    }

    /**
     * Initialize various behaviors
     */
    private void init() {
        // Setup various attributes
        setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen._12ssp));
        setGravity(Gravity.CENTER_VERTICAL);
        int startPadding = getContext().getResources().getDimensionPixelOffset(R.dimen._17sdp);
        int endPadding = getContext().getResources().getDimensionPixelOffset(R.dimen._5sdp);
        setPaddingRelative(startPadding, 0, endPadding, 0);
        setCompoundDrawablePadding(startPadding);

        reloadProfiles();

        // Popup window behavior
        setOnClickListener(new OnClickListener() {
            final int offset = -getContext().getResources().getDimensionPixelOffset(R.dimen._4sdp);

            @Override
            public void onClick(View v) {
                if (mPopupWindow == null) getPopupWindow();

                if (mPopupWindow.isShowing()) {
                    mPopupWindow.dismiss();
                    return;
                }
                mPopupWindow.showAsDropDown(mcVersionSpinner.this, 0, offset);
                // Post() is required for the layout inflation phase
                post(() -> mListView.setSelection(mSelectedIndex));
            }
        });
    }

    private void openInstallPage() {
        Tools.swapFragment((FragmentActivity) getContext(), VersionCatalogFragment.class,
                VersionCatalogFragment.TAG, null);
    }


    /**
     * Create the listView and popup window for the interface, and set up the click behavior
     */
    @SuppressLint("ClickableViewAccessibility")
    private void getPopupWindow() {
        mListView = (ListView) inflate(getContext(), R.layout.spinner_mc_version, null);
        mListView.setAdapter(mVersionAdapter);
        mListView.setOnItemClickListener((parent, view, position, id) -> {
            Object item = mVersionAdapter.getItem(position);
            if (item instanceof PgwInstalledVersion) {
                hidePopup(true);
                setProfileSelection(position);
            } else if (item == VersionInstanceAdapter.INSTALL_ENTRY) {
                hidePopup(false);
                openInstallPage();
            }
        });

        mPopupWindow = new PopupWindow(mListView, MATCH_PARENT, getContext().getResources().getDimensionPixelOffset(R.dimen._184sdp));
        mPopupWindow.setElevation(5);
        mPopupWindow.setClippingEnabled(false);

        // Block clicking outside of the popup window
        mPopupWindow.setOutsideTouchable(true);
        mPopupWindow.setFocusable(true);
        mPopupWindow.setTouchInterceptor((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
                mPopupWindow.dismiss();
                return true;
            }
            return false;
        });


        // Custom animation, nice slide in
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mPopupAnimation = new Slide(Gravity.BOTTOM);
            mPopupWindow.setEnterTransition((Transition) mPopupAnimation);
            mPopupWindow.setExitTransition((Transition) mPopupAnimation);
        }
    }

    private void hidePopup(boolean animate) {
        if (mPopupWindow == null) return;
        if (!animate && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mPopupWindow.setEnterTransition(null);
            mPopupWindow.setExitTransition(null);
            mPopupWindow.dismiss();
            mPopupWindow.setEnterTransition((Transition) mPopupAnimation);
            mPopupWindow.setExitTransition((Transition) mPopupAnimation);
        } else {
            mPopupWindow.dismiss();
        }
    }

}
