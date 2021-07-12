package org.example.test.java.exp;

import org.junit.Test;

public class LeeCode3 {
    @Test
    public void test_() throws Exception {
        Solution solution = new Solution();
        ListNode l1 = new ListNode(9);
        ListNode l2 = new ListNode(9);
        ListNode listNode = solution.addTwoNumbers(l1, l2);
        while (listNode != null) {
            System.out.print(listNode.val + ",");
            listNode = listNode.next;
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
        public ListNode addTwoNumbers(ListNode l1, ListNode l2) {
            ListNode head = null;
            ListNode current = null;
            int overValue = 0;
            while (l1 != null || l2 != null) {
                int l1Val = l1 == null ? 0 : l1.val;
                int l2Val = l2 == null ? 0 : l2.val;
                int result = l1Val + l2Val + overValue;
                int val = result % 10;
                if (head == null) {
                    head = current = new ListNode(val);
                } else {
                    current.next = new ListNode(val);
                    current = current.next;
                }
                if (l1 != null) {
                    l1 = l1.next;
                }
                if (l2 != null) {
                    l2 = l2.next;
                }
                overValue = result / 10;
            }
            if (overValue != 0) {
                current.next = new ListNode(overValue);
            }
            return head;
        }
    }
}
