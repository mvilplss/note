package org.example.test.java.exp;

import org.junit.Test;

public class LeeCode2 {

    @Test
    public void test_restoreString() throws Exception {
        Solution solution = new Solution();
        System.out.println(solution.restoreString("aiohn", new int[]{3, 1, 4, 2, 0}));
    }

    class Solution {
        public String restoreString(String s, int[] indices) {
            char[] chars = s.toCharArray();
            char[] newChars = new char[chars.length];
            for (int i = 0; i < chars.length; i++) {
                newChars[indices[i]]=chars[i];
            }
            return new String(newChars);
        }
    }

}
