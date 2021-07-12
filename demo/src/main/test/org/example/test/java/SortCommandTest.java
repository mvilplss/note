package org.example.test.java;

import org.junit.Test;

public class SortCommandTest {
    @Test
    public void test_() throws Exception{
        long start = System.currentTimeMillis();
        long sum=0;
        for (int i = 0; i < 1000; i++) {
            for (int j = 0; j <10000; j ++) {
                for (int k = 0; k < 100000; k++) {
                    sum=k+j+i;
                }
            }
        }
        long end = System.currentTimeMillis();
        System.out.println("Time spent is " + (end - start));

        start = System.currentTimeMillis();
        for (int i = 0; i < 100000; i++) {
            for (int j = 0; j <10000; j ++) {
                for (int k = 0; k < 1000; k++) {
                    sum=k+j+i;
                }
            }
        }
        System.out.println(sum);
        end = System.currentTimeMillis();
        System.out.println("Time spent is " + (end - start) + "ms");
    }
}
