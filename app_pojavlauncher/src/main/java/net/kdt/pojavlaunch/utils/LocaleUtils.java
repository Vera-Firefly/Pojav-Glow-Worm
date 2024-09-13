package net.kdt.pojavlaunch.utils;


import static net.kdt.pojavlaunch.prefs.LauncherPreferences.DEFAULT_PREF;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.LocaleList;

import androidx.preference.PreferenceManager;

import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.util.Locale;

public class LocaleUtils extends ContextWrapper {

    public LocaleUtils(Context base) {
        super(base);
    }

    public static ContextWrapper setLocale(Context context) {
        if (DEFAULT_PREF == null) {
            DEFAULT_PREF = PreferenceManager.getDefaultSharedPreferences(context);
            LauncherPreferences.loadPreferences(context);
        }

        if (DEFAULT_PREF.getBoolean("force_english", false)) {
            Resources resources = context.getResources();
            Configuration configuration = resources.getConfiguration();

            /**
             * en-XA is a pseudo-language (usually used for UI testing)
             * that has no corresponding resource directory
             * Therefore, when the application is set to this language
             * the system falls back to the default resource in the values directory
             */
            Locale defaultLocale = new Locale("en", "XA");
            configuration.setLocale(defaultLocale);
            Locale.setDefault(defaultLocale);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                LocaleList localeList = new LocaleList(defaultLocale);
                LocaleList.setDefault(localeList);
                configuration.setLocales(localeList);
            }

            resources.updateConfiguration(configuration, resources.getDisplayMetrics());

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                context = context.createConfigurationContext(configuration);
            }
        }

        return new LocaleUtils(context);
    }
}
