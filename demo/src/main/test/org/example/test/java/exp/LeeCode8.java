package org.example.test.java.exp;

import org.apache.commons.collections.list.SynchronizedList;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class LeeCode8 {

    @Test
    public void test_() throws Exception {
        List<Integer> A = new ArrayList<>();
        A.add(2);
        A.add(1);
        A.add(0);
        List<Integer> B = new ArrayList<>();
        List<Integer> C = new ArrayList<>();
        new Solution().hanota(A, B, C);
        System.out.println(A);
        System.out.println(B);
        System.out.println(C);
    }

    class Solution {
        public void hanota(List<Integer> A, List<Integer> B, List<Integer> C) {
            move(A,B,C,A.size());
        }

        private void move(List<Integer> a, List<Integer> b, List<Integer> c, int size) {
            if (size==1){

                return;
            }
            move(a,b,c,size-1);
            move(a,c,b,1);
            move(b,c,c,size-1);
        }

    }

    @Test
    public void test_count() throws Exception{
        List<Integer> list = new ArrayList<>();
        list.add(1);
        list.add(1);
        list.add(1);
        list.add(1);
        list.add(1);
        list.add(1);
        System.out.println(count(list));
    }
    public int count(List<Integer> list){
       return iamnum(0,list);
    }

    private int iamnum(int index, List<Integer> list) {
        try {
            list.get(index);
        }catch (Exception e){
            return index;
        }
        return iamnum(++index,list);
    }
}
