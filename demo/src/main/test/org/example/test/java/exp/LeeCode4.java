package org.example.test.java.exp;

import org.junit.Test;

public class LeeCode4 {

    @Test
    public void test_() throws Exception {
        Solution solution = new Solution();
        ListNode head = new ListNode(1, new ListNode(2, new ListNode(3, new ListNode(4))));
        ListNode listNode = solution.reverseList(head);
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
        public ListNode reverseList(ListNode head) {
            ListNode reverse = null;
            while (head != null) {
                if (reverse == null) {
                    reverse  = new ListNode(head.val);
                } else {
                    reverse = new ListNode(head.val,reverse);
                }
                head = head.next;
            }
            return reverse;
        }
    }

}
