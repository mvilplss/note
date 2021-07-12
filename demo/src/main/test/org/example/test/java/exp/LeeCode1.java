package org.example.test.java.exp;

import org.junit.Test;

public class LeeCode1 {
    @Test
    public void test_replaceSpaces() throws Exception {
        String result = replaceSpaces2("rxOpSEXvfIuoRJfjw gwuomevMMBOfeSM vYSPBaovrZBSgmCrSLDirNnILhARNS    "
                , 64);
        System.out.println(result);
    }

    private String replaceSpaces2(String S, int length) {
        char[] chars = S.toCharArray();
        int index = chars.length - 1;
        for (int i = length - 1; i >= 0; i--) {
            if (chars[i] == ' ') {
                chars[index--] = '0';
                chars[index--] = '2';
                chars[index--] = '%';
            } else {
                chars[index--] = chars[i];
            }
        }
        return new String(chars, index + 1, chars.length - index - 1);
    }

    private String replaceSpaces(String S, int length) {
        char[] chars = S.toCharArray();
        System.out.println(chars);
        int len = chars.length;
        if (len == length) {
            return S;
        }
        for (int i = 0; i < len / 2; i++) {
            char tmp = chars[i];
            chars[i] = chars[len - i - 1];
            chars[len - i - 1] = tmp;
        }
        int index = 0;
        for (int i = len - length; i < len; i++) {
            if (chars[i] != ' ') {
                chars[index] = chars[i];
            } else {
                chars[index] = '0';
                chars[index += 1] = '2';
                chars[index += 1] = '%';
            }
            index++;
        }
        int realLen = index;
        for (int i = 0; i < realLen / 2; i++) {
            char tmp = chars[i];
            chars[i] = chars[realLen - 1 - i];
            chars[realLen - 1 - i] = tmp;
        }
        return new String(chars, 0, realLen);
    }
}
