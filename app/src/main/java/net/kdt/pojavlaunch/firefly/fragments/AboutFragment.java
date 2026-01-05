package net.kdt.pojavlaunch.firefly.fragments;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.firefly.R;
import net.kdt.pojavlaunch.firefly.Tools;

public class AboutFragment extends Fragment {
    public static final String TAG = "ABOUT_FRAGMENT";
    private static final String GITHUB_URL_PLT = "https://github.com/PojavLauncherTeam";
    private static final String GITHUB_URL_VF = "https://github.com/Vera-Firefly";
    private static final String GITHUB_URL_MOV = "https://github.com/MovTery";
    private static final String GITHUB_URL_EURYA = "https://github.com/Eurya2233369";
    private static final String GITHUB_URL_COLORYR = "https://github.com/Coloryr";
    private static final String GITHUB_URL_MIO = "https://github.com/ShirosakiMio";
    private static final String GITHUB_URL_T = "https://github.com/Tungstend";
    private static final String GITHUB_URL_APAI = "https://github.com/aaaapai";
    private static final String GITHUB_URL_WVN = "https://github.com/WesleyVanNeck";
    public AboutFragment() {
        super(R.layout.fragment_about);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Button mContributorButton1 = view.findViewById(R.id.contributor_pojavteam);
        Button mContributorButton2 = view.findViewById(R.id.contributor_vera_firefly);
        Button mContributorButton3 = view.findViewById(R.id.contributor_movtery);
        Button mContributorButton4 = view.findViewById(R.id.contributor_eurya2233369);
        Button mContributorButton5 = view.findViewById(R.id.contributor_coloryr);
        Button mContributorButton6 = view.findViewById(R.id.contributor_mio);
        Button mContributorButton7 = view.findViewById(R.id.contributor_tungstend);
        Button mContributorButton8 = view.findViewById(R.id.contributor_aaaapai);
        Button mContributorButton9 = view.findViewById(R.id.contributor_wvn);

        mContributorButton1.setOnClickListener(v -> Tools.openURL(requireActivity(), GITHUB_URL_PLT));
        mContributorButton2.setOnClickListener(v -> Tools.openURL(requireActivity(), GITHUB_URL_VF));
        mContributorButton3.setOnClickListener(v -> Tools.openURL(requireActivity(), GITHUB_URL_MOV));
        mContributorButton4.setOnClickListener(v -> Tools.openURL(requireActivity(), GITHUB_URL_EURYA));
        mContributorButton5.setOnClickListener(v -> Tools.openURL(requireActivity(), GITHUB_URL_COLORYR));
        mContributorButton6.setOnClickListener(v -> Tools.openURL(requireActivity(), GITHUB_URL_MIO));
        mContributorButton7.setOnClickListener(v -> Tools.openURL(requireActivity(), GITHUB_URL_T));
        mContributorButton8.setOnClickListener(v -> Tools.openURL(requireActivity(), GITHUB_URL_APAI));
        mContributorButton9.setOnClickListener(v -> Tools.openURL(requireActivity(), GITHUB_URL_WVN));

        mContributorButton2.setOnLongClickListener((v) -> {
            Tools.openURL(requireActivity(), Tools.URL_HOME);
            return true;
        });

    }

}
