package net.kdt.pojavlaunch.profiles;

import net.kdt.pojavlaunch.Logger;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.kdt.pojavlaunch.Tools.getGameDirPath;

public class ProfileLanguageSelector {
    private ProfileLanguageSelector() {
    }

    public static String getMatchingLanguage(int index) {
            switch(index) {
                case 0: return "default";
                case 1: return "none";

                case 2: return "af_za";
                case 3: return "ar_sa";
                case 4: return "ast_es";
                case 5: return "az_az";
                case 6: return "ba_ru";
                case 7: return "bar";
                case 8: return "be_by";
                case 9: return "bg_bg";
                case 10: return "br_fr";
                case 11: return "brb";
                case 12: return "bs_ba";
                case 13: return "ca_es";
                case 14: return "cs_cz";
                case 15: return "cy_gb";
                case 16: return "da_dk";
                case 17: return "de_at";
                case 18: return "de_ch";
                case 19: return "de_de";
                case 20: return "el_gr";
                case 21: return "en_au";
                case 22: return "en_ca";
                case 23: return "en_gb";
                case 24: return "en_nz";
                case 25: return "en_pt";
                case 26: return "en_ud";
                case 27: return "en_us";
                case 28: return "enp";
                case 29: return "enws";
                case 30: return "eo_uy";
                case 31: return "es_ar";
                case 32: return "es_cl";
                case 33: return "es_ec";
                case 34: return "es_es";
                case 35: return "es_mx";
                case 36: return "es_uy";
                case 37: return "es_ve";
                case 38: return "esan";
                case 39: return "et_ee";
                case 40: return "eu_es";
                case 41: return "fa_ir";
                case 42: return "fi_fi";
                case 43: return "fil_ph";
                case 44: return "fo_fo";
                case 45: return "fr_ca";
                case 46: return "fr_fr";
                case 47: return "fra_de";
                case 48: return "fur_it";
                case 49: return "fy_nl";
                case 50: return "ga_ie";
                case 51: return "gd_gb";
                case 52: return "gl_es";
                case 53: return "haw_us";
                case 54: return "he_il";
                case 55: return "hi_in";
                case 56: return "hr_hr";
                case 57: return "hu_hu";
                case 58: return "hy_am";
                case 59: return "id_id";
                case 60: return "ig_ng";
                case 61: return "io_en";
                case 62: return "is_is";
                case 63: return "isv";
                case 64: return "it_it";
                case 65: return "ja_jp";
                case 66: return "jbo_en";
                case 67: return "ka_ge";
                case 68: return "kk_kz";
                case 69: return "kn_in";
                case 70: return "ko_kr";
                case 71: return "ksh";
                case 72: return "kw_gb";
                case 73: return "la_la";
                case 74: return "lb_lu";
                case 75: return "li_li";
                case 76: return "lmo";
                case 77: return "lo_la";
                case 78: return "lol_us";
                case 79: return "lt_lt";
                case 80: return "lv_lv";
                case 81: return "lzh";
                case 82: return "mk_mk";
                case 83: return "mn_mn";
                case 84: return "ms_my";
                case 85: return "mt_mt";
                case 86: return "nah";
                case 87: return "nds_de";
                case 88: return "nl_be";
                case 89: return "nl_nl";
                case 90: return "nn_no";
                case 91: return "no_no";
                case 92: return "oc_fr";
                case 93: return "ovd";
                case 94: return "pl_pl";
                case 95: return "pt_br";
                case 96: return "pt_pt";
                case 97: return "qya_aa";
                case 98: return "ro_ro";
                case 99: return "rpr";
                case 100: return "ru_ru";
                case 101: return "ry_ua";
                case 102: return "sah_sah";
                case 103: return "se_no";
                case 104: return "sk_sk";
                case 105: return "sl_si";
                case 106: return "so_so";
                case 107: return "sq_al";
                case 108: return "sr_cs";
                case 109: return "sr_sp";
                case 110: return "sv_se";
                case 111: return "sxu";
                case 112: return "szl";
                case 113: return "ta_in";
                case 114: return "th_th";
                case 115: return "tl_ph";
                case 116: return "tlh_aa";
                case 117: return "tok";
                case 118: return "tr_tr";
                case 119: return "tt_ru";
                case 120: return "uk_ua";
                case 121: return "val_es";
                case 122: return "vec_it";
                case 123: return "vi_vn";
                case 124: return "yi_de";
                case 125: return "yo_ng";
                case 126: return "zh_cn";
                case 127: return "zh_hk";
                case 128: return "zh_tw";
                case 129: return "zlm_arab";

                default: return "en_us";
            }
    }

    public static String getOlderMatchingLanguage(int index) {
        String temp = getMatchingLanguage(index);
        StringBuilder builder = new StringBuilder(temp);
        int underscoreIndex = temp.indexOf('_');

        if (underscoreIndex != -1) {
            for (int i = underscoreIndex; i < temp.length(); i++) {
                builder.setCharAt(i, Character.toUpperCase(temp.charAt(i)));
            } // Convert to uppercase only the characters after the underscore
        }

        return builder.toString();
    }

    public static String getVersion(String versionId) {
        int firstDotIndex = versionId.indexOf('.');
        int secondDotIndex = versionId.indexOf('.', firstDotIndex + 1);

        if (firstDotIndex != -1) { // It's the official version
            if (secondDotIndex == -1) return versionId.substring(firstDotIndex + 1);
                else return versionId.substring(firstDotIndex + 1, secondDotIndex);
        } else return versionId;
    }

    public static String getDigitsBeforeFirstLetter(String input) {
        // Regular expressions match numeric characters
        Pattern pattern = Pattern.compile("^\\d*");
        Matcher matcher = pattern.matcher(input);

        return matcher.find() ? matcher.group() : "";
    }

    public static String getDigitsBetweenFirstAndSecondLetter(String input) {
        Pattern pattern = Pattern.compile("([a-zA-Z])(\\d*)([a-zA-Z])");
        Matcher matcher = pattern.matcher(input);

        if (matcher.find()) {
            return matcher.group(2);
        }
        return "";
    }

    public static boolean containsLetter(String input) {
        return input.matches(".*[a-zA-Z].*");
    }

    public static String getLanguage(String versionId, int index) {
        int version = 1;

        String optifineSuffix = "OptiFine"; // "1.20.4-OptiFine_HD_U_I7_pre3"
        String forgeSuffix = "forge"; // "1.20.2-forge-48.1.0"
        String fabricSuffix = "fabric-loader"; // "fabric-loader-0.15.7-1.20.4"
        String quiltSuffix = "quilt-loader"; // "quilt-loader-0.23.1-1.20.4"
        String regex = "^\\d+[a-zA-Z]\\d+[a-zA-Z]$";

        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(versionId);

        if (containsLetter(versionId)) {
            if (versionId.contains(optifineSuffix) || versionId.contains(forgeSuffix)) { // OptiFine & Forge
                int lastIndex = versionId.indexOf('-');
                if (lastIndex != -1) {
                    version = Integer.parseInt(getVersion(versionId.substring(0, lastIndex)));
                }
            } else if (versionId.contains(fabricSuffix) || versionId.contains(quiltSuffix)) { // Fabric & Quilt
                int lastIndex = versionId.lastIndexOf('-');

                if (lastIndex != -1) {
                    version = Integer.parseInt(getVersion(versionId.substring(lastIndex + 1)));
                }
            } else if (matcher.matches()) { // Development versions "24w09a" "16w20a"
                int result1 = Integer.parseInt(getDigitsBeforeFirstLetter(versionId));
                int result2 = Integer.parseInt(getDigitsBetweenFirstAndSecondLetter(versionId));

                if(result1 < 16) {
                    return getOlderMatchingLanguage(index);
                } else if (result1 == 16 & result2 <= 32) {
                    return getOlderMatchingLanguage(index);
                }

                return getMatchingLanguage(index);
            }
        }
        version = Integer.parseInt(getVersion(versionId));

        // 1.10 -
        if (version < 11) {
            return getOlderMatchingLanguage(index);
        }

        return getMatchingLanguage(index); // ? & 1.0
    }

    public static void languageChangers(MinecraftProfile minecraftProfile) throws IOException {
        File optionFile = new File((getGameDirPath(minecraftProfile.gameDir)) + File.separator + "options.txt");
        if (!optionFile.exists()) { // Create an options.txt file in the game path
            optionFile.createNewFile();
            Logger.appendToLog("Language Selector -> Created a new options.txt file.");
        }

        ArrayList<String> options = new ArrayList<>();
        boolean foundMatch = false;
        String language;
        if (minecraftProfile.language == 0) {
            language = getLanguage(minecraftProfile.lastVersionId, Integer.parseInt(LauncherPreferences.DEFAULT_PREF.getString("gameLanguage", "-1")) + 2);
        } else {
            language = getLanguage(minecraftProfile.lastVersionId, minecraftProfile.language);
        }

        try (BufferedReader optionFileReader = new BufferedReader(new InputStreamReader(new FileInputStream(optionFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = optionFileReader.readLine()) != null) {
                // Match the "lang: xxx" format with a regular expression
                Pattern pattern = Pattern.compile("lang:(\\S+)");
                Matcher matcher = pattern.matcher(line);

                if (matcher.find()) {
                    line = matcher.replaceAll("lang:" + language);
                    foundMatch = true;
                }

                options.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        // If the file is empty, or no matching field is found, the "lang" field is added by default
        if (!foundMatch) {
            options.add("lang:" + language);
            Logger.appendToLog("Language Selector -> The \"lang:" + language + "\" field has been added to the options.txt file.");
        }

        try (BufferedWriter optionFileWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(optionFile), StandardCharsets.UTF_8))) {
            for (String option : options) {
                optionFileWriter.write(option);
                optionFileWriter.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
