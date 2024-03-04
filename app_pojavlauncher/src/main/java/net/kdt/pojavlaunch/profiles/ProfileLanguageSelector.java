package net.kdt.pojavlaunch.profiles;

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
                case 1: return "af_za";
                case 2: return "ar_sa";
                case 3: return "ast_es";
                case 4: return "az_az";
                case 5: return "ba_ru";
                case 6: return "bar";
                case 7: return "be_by";
                case 8: return "bg_bg";
                case 9: return "br_fr";
                case 10: return "brb";
                case 11: return "bs_ba";
                case 12: return "ca_es";
                case 13: return "cs_cz";
                case 14: return "cy_gb";
                case 15: return "da_dk";
                case 16: return "de_at";
                case 17: return "de_ch";
                case 18: return "de_de";
                case 19: return "el_gr";
                case 20: return "en_au";
                case 21: return "en_ca";
                case 22: return "en_gb";
                case 23: return "en_nz";
                case 24: return "en_pt";
                case 25: return "en_ud";
                case 26: return "en_us";
                case 27: return "enp";
                case 28: return "enws";
                case 29: return "eo_uy";
                case 30: return "es_ar";
                case 31: return "es_cl";
                case 32: return "es_ec";
                case 33: return "es_es";
                case 34: return "es_mx";
                case 35: return "es_uy";
                case 36: return "es_ve";
                case 37: return "esan";
                case 38: return "et_ee";
                case 39: return "eu_es";
                case 40: return "fa_ir";
                case 41: return "fi_fi";
                case 42: return "fil_ph";
                case 43: return "fo_fo";
                case 44: return "fr_ca";
                case 45: return "fr_fr";
                case 46: return "fra_de";
                case 47: return "fur_it";
                case 48: return "fy_nl";
                case 49: return "ga_ie";
                case 50: return "gd_gb";
                case 51: return "gl_es";
                case 52: return "haw_us";
                case 53: return "he_il";
                case 54: return "hi_in";
                case 55: return "hr_hr";
                case 56: return "hu_hu";
                case 57: return "hy_am";
                case 58: return "id_id";
                case 59: return "ig_ng";
                case 60: return "io_en";
                case 61: return "is_is";
                case 62: return "isv";
                case 63: return "it_it";
                case 64: return "ja_jp";
                case 65: return "jbo_en";
                case 66: return "ka_ge";
                case 67: return "kk_kz";
                case 68: return "kn_in";
                case 69: return "ko_kr";
                case 70: return "ksh";
                case 71: return "kw_gb";
                case 72: return "la_la";
                case 73: return "lb_lu";
                case 74: return "li_li";
                case 75: return "lmo";
                case 76: return "lo_la";
                case 77: return "lol_us";
                case 78: return "lt_lt";
                case 79: return "lv_lv";
                case 80: return "lzh";
                case 81: return "mk_mk";
                case 82: return "mn_mn";
                case 83: return "ms_my";
                case 84: return "mt_mt";
                case 85: return "nah";
                case 86: return "nds_de";
                case 87: return "nl_be";
                case 88: return "nl_nl";
                case 89: return "nn_no";
                case 90: return "no_no";
                case 91: return "oc_fr";
                case 92: return "ovd";
                case 93: return "pl_pl";
                case 94: return "pt_br";
                case 95: return "pt_pt";
                case 96: return "qya_aa";
                case 97: return "ro_ro";
                case 98: return "rpr";
                case 99: return "ru_ru";
                case 100: return "ry_ua";
                case 101: return "sah_sah";
                case 102: return "se_no";
                case 103: return "sk_sk";
                case 104: return "sl_si";
                case 105: return "so_so";
                case 106: return "sq_al";
                case 107: return "sr_cs";
                case 108: return "sr_sp";
                case 109: return "sv_se";
                case 110: return "sxu";
                case 111: return "szl";
                case 112: return "ta_in";
                case 113: return "th_th";
                case 114: return "tl_ph";
                case 115: return "tlh_aa";
                case 116: return "tok";
                case 117: return "tr_tr";
                case 118: return "tt_ru";
                case 119: return "uk_ua";
                case 120: return "val_es";
                case 121: return "vec_it";
                case 122: return "vi_vn";
                case 123: return "yi_de";
                case 124: return "yo_ng";
                case 125: return "zh_cn";
                case 126: return "zh_hk";
                case 127: return "zh_tw";
                case 128: return "zlm_arab";

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

    public static String getLanguage(String versionId, MinecraftProfile minecraftProfile) {
        if (containsLetter(versionId)) {
            String result1 = getDigitsBeforeFirstLetter(versionId);
            String result2 = getDigitsBetweenFirstAndSecondLetter(versionId);

            if(Integer.parseInt(result1) <= 16 & Integer.parseInt(result2) <= 20) return getOlderMatchingLanguage(minecraftProfile.language);
            else return getMatchingLanguage(minecraftProfile.language);
        }

        int version = Integer.parseInt(getVersion(versionId));

        // 1.1 - 1.10
        if (1 <= version && version < 11) {
            return getOlderMatchingLanguage(minecraftProfile.language);
        }
        else {
            return getMatchingLanguage(minecraftProfile.language); // ? & 1.0
        }
    }

    public static void languageChangers(MinecraftProfile minecraftProfile) throws IOException {
        File optionFile = new File((getGameDirPath(minecraftProfile.gameDir)) + File.separator + "options.txt");
        if (!optionFile.exists()) { // Create an options.txt file in the game path
            optionFile.createNewFile();
        }

        ArrayList<String> options = new ArrayList<>();
        boolean foundMatch = false;
        String language = getLanguage(minecraftProfile.lastVersionId, minecraftProfile);

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
