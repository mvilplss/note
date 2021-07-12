package org.example.algorithm;

import java.util.Arrays;

public class InsertSort extends AbstractArraySort {

    @Override
    public int[] sort(int[] arr) {
        for (int i = 1; i < arr.length; i++) {
            int tmp = arr[i];
            int j = i;
            while (j > 0 && tmp < arr[j - 1]) {
                arr[j] = arr[j - 1];
                j--;
            }
            if (j != i) {
                arr[j] = tmp;
            }
            System.out.println("gap="+1+":"+Arrays.toString(arr));
        }
        return arr;
    }

    public static void main(String[] args) {
        InsertSort sort = new InsertSort();
        int[] doSort = sort.doSort();
        System.out.println(Arrays.toString(doSort));
    }
}
