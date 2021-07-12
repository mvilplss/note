package org.example.algorithm;

import java.util.Arrays;

public abstract class AbstractArraySort implements ArraySort {
    @Override
    public abstract int[] sort(int[] arr);

    public int[] doSort() {
        int[] arr = new int[]{7, 6, 5, 4, 3, 2, 1, 0};
        System.out.println(Arrays.toString(arr));
        int[] sortArr = Arrays.copyOf(arr, arr.length);
        Arrays.sort(sortArr);
        System.out.println(Arrays.toString(sortArr));
        return sort(arr);
    }
}
