package demo;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.util.Arrays;

/**
 * All rights Reserved, Designed By www.maihaoche.com
 *
 * @author: 三行（sanxing@maihaoche.com）
 * @date: 2019/10/15
 * @Copyright: 2017-2020 www.maihaoche.com Inc. All rights reserved.
 * 注意：本内容仅限于卖好车内部传阅，禁止外泄以及用于其他的商业目
 */
@Slf4j
public class MathDemo extends BaseDemo {

    @Test
    public void xxx() throws Exception{
        int[] A = {1,2,3,4,11,12,13,14,15,16};
        int m=4,n=6;
        int i= 0 ;
        while (i<m&&i<n){
            int t = A[i];
            A[i]=A[i+m];
            A[i+m]=t;
            i++;
        }

        if (m<n){
           int rest = n-m;
            for (int j = rest; j < rest; j--) {
                int end = m+n-j;
                int t = A[end];

            }
        }
        System.out.println(Arrays.toString(A));


    }
}
