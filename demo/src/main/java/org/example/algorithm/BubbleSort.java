package org.example.algorithm;

import java.util.Arrays;

public class BubbleSort extends AbstractArraySort {

    @Override
    public int[] sort(int[] arr) {
        boolean isOk;
        for (int i = 0; i < arr.length - 1; i++) {
            isOk = true;
            for (int j = 0; j < arr.length - 1 - i; j++) {
                if (arr[j] > arr[j + 1]) {
                    int temp = arr[j];
                    arr[j] = arr[j + 1];
                    arr[j + 1] = temp;
                    isOk = false;
                }
            }
            // *如果已经有序了，则停止冒泡
            if (isOk){
                break;
            }
        }
        return arr;
    }

    public static void main(String[] args) {
        BubbleSort sort = new BubbleSort();
        int[] doSort = sort.doSort();
        System.out.println(Arrays.toString(doSort));
    }
}
