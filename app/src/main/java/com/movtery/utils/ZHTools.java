package com.movtery.utils;

import java.util.Locale;

public class ZHTools {
    private ZHTools() {
    }

    public static String getSystemLanguage() {
        Locale locale = Locale.getDefault();
        return locale.getLanguage() + "_" + locale.getCountry().toLowerCase();
    }
}
