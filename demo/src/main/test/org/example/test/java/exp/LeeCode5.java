package org.example.test.java.exp;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.ThreadPoolExecutor;

public class LeeCode5 {
    @Test
    public void test_() throws Exception{
        System.out.println(Arrays.toString(new Solution().dailyTemperatures(new int[]{89,62,70,58,47,47,46,76,100,70})));
    }


    class Solution {
        public int[] dailyTemperatures(int[] temperatures) {
            int[] result = new int[temperatures.length];
            for (int i = 0; i < temperatures.length-1; i++) {
                int cur = temperatures[i];
                int x = 0;
                boolean has = false;
                for (int j = i+1; j < temperatures.length; j++) {
                    if (cur>=temperatures[j]){
                        x++;
                    }else {
                        has = true;
                        break;
                    }
                }
                if (has){
                    result[i]=x+1;
                }else {
                    result[i]=0;
                }
            }
            return result;
        }
    }
}
