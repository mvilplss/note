package org.example.test.java.exp;

import org.junit.Test;

public class LeeCode9 {
    @Test
    public void test_() throws Exception {
        Solution solution = new Solution();
        ListNode listNode = new ListNode(3, new ListNode(5, new ListNode(8, new ListNode(2, new ListNode(1)))));
        ListNode node = solution.sortList(listNode);
        while (node != null) {
            System.out.println(node.val);
            node = node.next;
        }
    }


    public class ListNode {
        int val;
        ListNode next;

        ListNode() {
        }

        ListNode(int val) {
            this.val = val;
        }

        ListNode(int val, ListNode next) {
            this.val = val;
            this.next = next;
        }
    }

    class Solution {
        public ListNode sortList(ListNode head) {
            ListNode sl = new ListNode(head.val);
            head = head.next;
            while (head != null) {
                ListNode sc = sl;
                while (true) {
                    if (head.val < sc.val) {
                        break;
                    }
                    if (sc.next != null) {
                        sc = sc.next;
                    } else {
                        break;
                    }
                }
                if (sc.next==null){
                    sc.next=new ListNode(head.val);
                }else {
                    int tmp = sc.val;
                    sc.val = head.val;
                    ListNode nt = sc.next;
                    sc.next = new ListNode(tmp, nt);
                }
                head = head.next;
            }
            return sl;
        }
    }

    @Test
    public void test_xxx() throws Exception{
        double num = 65;
        System.out.println("计划："+num*1/3);
        System.out.println("编码："+num*1/6);
        System.out.println("调试自测："+num*1/4);
        System.out.println("系统测试："+num*1/4);
    }
}
