package com.movtery.utils.stringutils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringUtils {
    public static boolean containsDot(String input) {
        int dotIndex = input.indexOf('.');
        return dotIndex != -1;
    }

    /**
     * 在一段字符串中提取数字
     */
    public static int[] extractNumbers(String str, int quantity) {
        Pattern pattern = Pattern.compile("\\d+");
        Matcher matcher = pattern.matcher(str);

        int[] numbers = new int[quantity];

        int count = 0;
        while (matcher.find() && count < quantity) {
            numbers[count] = Integer.parseInt(matcher.group());
            count++;
        }

        return numbers;
    }
}
