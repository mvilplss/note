package org.example.algorithm;

import java.util.Arrays;

public class ShellSort extends AbstractArraySort {

    @Override
    public int[] sort(int[] arr) {
        for (int gap = arr.length / 2; gap > 0; gap /= 2) {
            for (int i = gap; i < arr.length; i += gap) {
                int tmp = arr[i];
                int j = i;
                while (j > 0 && arr[j-gap] > tmp) {
                    arr[j] = arr[j-gap];
                    j -= gap;
                }
                if (j != i) {
                    arr[j] = tmp;
                }
            }
        }
        return arr;
    }

    public static void main(String[] args) {
        ShellSort sort = new ShellSort();
        int[] doSort = sort.doSort();
        System.out.println(Arrays.toString(doSort));
    }
}
