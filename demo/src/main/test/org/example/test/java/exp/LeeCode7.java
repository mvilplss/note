package org.example.test.java.exp;

import org.junit.Test;

import java.util.Arrays;

public class LeeCode7 {
    @Test
    public void test_() throws Exception{
        System.out.println(Arrays.toString(new Solution().maxSlidingWindow(new int[]{1,-1},1)));
    }

    class Solution {
        // 当前窗口是否包含上个窗口最大值，如果有则最后一位和最大值比较
        public int[] maxSlidingWindow(int[] nums, int k) {
            if (nums == null || nums.length == 0) {
                return new int[]{};
            }
            int[] maxs = new int[nums.length - k + 1];
            int maxIndex = 0;
            for (int i = 0; i < nums.length - k + 1; i++) {
                if (maxIndex != 0 && maxIndex >= i) {
                    if (nums[i + k - 1] >= nums[maxIndex]) {
                        maxIndex = i + k - 1;
                    }
                } else {
                    maxIndex = i;
                    for (int j = 1; j < k; j++) {
                        if (nums[i + j] >= nums[maxIndex]) {
                            maxIndex = i + j;
                        }
                    }
                }
                maxs[i] = nums[maxIndex];
            }
            return maxs;
        }

        // 直接实现
        public int[] maxSlidingWindowV1(int[] nums, int k) {
            if (nums==null || nums.length==0){
                return new int[]{};
            }
            int[] maxs = new int[nums.length-k+1];
            for (int i = 0; i < nums.length-k+1; i++) {
                int maxIndex = i;
                for (int j = 1; j < k; j++) {
                    if (nums[i + j] > nums[maxIndex]) {
                        maxIndex=i + j;
                    }
                }
                maxs[i]=nums[maxIndex];
            }
            return maxs;
        }
    }


}
