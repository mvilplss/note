package org.example.algorithm;

import java.util.Arrays;

public class SelectionSort extends AbstractArraySort {

    @Override
    public int[] sort(int[] arr) {
        for (int i = 0; i < arr.length - 1; i++) {
            int min = i;
            for (int j = i + 1; j < arr.length; j++) {
                if (arr[min] > arr[j]) {
                    min = j;
                }
            }
            if (min != i) {
                int temp = arr[min];
                arr[min] = arr[i];
                arr[i] = temp;
            }
        }
        return arr;
    }

    public static void main(String[] args) {
        SelectionSort sort = new SelectionSort();
        int[] doSort = sort.doSort();
        System.out.println(Arrays.toString(doSort));
    }
}
