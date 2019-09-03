package demo;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import util.TestUtil;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.*;

/**
 * All rights Reserved, Designed By www.maihaoche.com
 *
 * @author: 三行（sanxing@maihaoche.com）
 * @date: 2019/9/2
 * @Copyright: 2017-2020 www.maihaoche.com Inc. All rights reserved.
 * 注意：本内容仅限于卖好车内部传阅，禁止外泄以及用于其他的商业目
 */
// hashmap每个阶段，结合源码展开。
@Slf4j
public class HashMapDemo extends BaseDemo {

    @Test
    public void hashMapRefectionStudy() throws Exception {
        HashMap<Integer, String> map = new HashMap<>();
        for (int i = 0; i < 14; i++) {
            map.put((int) Math.pow( 2, i)+3, String.valueOf(i));
        }
        // 相关class准备
        Class<?> nodeClass = Class.forName("java.util.HashMap$Node");
        Class<?> treeNodeClass = Class.forName("java.util.HashMap$TreeNode");
        // 获取table大小和相关属性
        Object[] table = (Object[]) getFieldValue("table", map);
        log.info("table:" + table.length);
        Object size = getFieldValue("size", map);
        log.info("size:" + size);
        Object modCount = getFieldValue("modCount", map);
        log.info("modCount:" + modCount);
        Object threshold = getFieldValue("threshold", map);
        log.info("threshold:" + threshold);
        Object loadFactor = getFieldValue("loadFactor", map);
        log.info("loadFactor:" + loadFactor);
        // 格式化打印数据
        for (int i = 0; i < table.length; i++) {
            Object o = table[i];
            System.out.print("[" + i + "\t] ");
            if (o != null) {
                if (o.getClass() == nodeClass) {// 默认为链表
                    System.out.print("link ");
                    while (o != null) {
                        System.out.print(o + " ");
                        o = getFieldValue("next", o);
                    }
                    System.out.println();
                } else if (o.getClass() == treeNodeClass) {// 如果是红黑树
                    System.out.println("tree ");
                    // 获取root
                    Object root = o;
                    // 通过队列上线广度搜索打印
                    LinkedList<Object> queue = new LinkedList();
                    queue.add(root);
                    int treeCnt = 1;
                    while (!queue.isEmpty()) {
                        Object firstNode = queue.removeFirst();
                        System.out.print(firstNode + "\t\t");
                        if (firstNode != null) {
                            Object leftNode = getFieldValue("left", firstNode);
                            queue.addLast(leftNode);
                            Object rightNode = getFieldValue("right", firstNode);
                            queue.addLast(rightNode);
                        }
                        if (isLayerLastTreeNode(treeCnt)) {
                            System.out.println();
                        }
                        treeCnt++;
                    }
                    System.out.println();
                }
            } else {
                System.out.println("link " + o);
            }
        }
    }

    // 判断是否是一层树叶的最后一个
    // 1 3 7 15 31 ...
    public boolean isLayerLastTreeNode(int num) {
        double n = (Math.log(num + 1) / Math.log(2));
        return n == (int) n;
    }

    // 通过反射获取对象的任意属性
    public Object getFieldValue(String field, Object obj) throws NoSuchFieldException, IllegalAccessException {
        Class<?> aClass = obj.getClass();
        Field declaredField = aClass.getDeclaredField(field);
        if (declaredField == null) {
            return null;
        }
        declaredField.setAccessible(true);
        return declaredField.get(obj);
    }


    @Test
    public void formatEach() throws Exception {
        // 出现8此碰撞,容量<64则进行扩容
        Map<Integer, String> map = new HashMap<>();
        for (int i = 0; i < 15; i++) {
            map.put((int) Math.pow(2, i), String.valueOf(i));
        }
        Class<HashMap> mapClass = HashMap.class;
        Class<?> nodeClass = Class.forName("java.util.HashMap$Node");
        Class<?> treeNodeClass = Class.forName("java.util.HashMap$TreeNode");
        Field[] fields = mapClass.getDeclaredFields();
        for (Field field : fields) {
            field.setAccessible(true);
            if ("table".equals(field.getName())) {
                Object[] table = (Object[]) field.get(map);
                for (int i = 0; i < table.length; i++) {
                    Object o = table[i];
                    System.out.print("[" + i + "\t] ");
                    if (o != null) {
                        if (o.getClass() == nodeClass) {
                            System.out.print("link ");
                            while (o != null) {
                                System.out.print(o + " ");
                                Field next = nodeClass.getDeclaredField("next");
                                next.setAccessible(true);
                                o = next.get(o);
                            }
                            System.out.println();
                        } else if (o.getClass() == treeNodeClass) {
                            System.out.println("tree ");
                            // 通过两个集合交替
                            LinkedList list1 = new LinkedList();
                            list1.add(o);
                            LinkedList list2 = new LinkedList();
                            while (!list1.isEmpty() || !list2.isEmpty()) {
                                if (!list1.isEmpty()) {
                                    list1.forEach(node -> {
                                        System.out.print(node + "\t\t");
                                        try {
                                            if (node != null) {
                                                Field left = treeNodeClass.getDeclaredField("left");
                                                left.setAccessible(true);
                                                Object leftNode = left.get(node);
                                                list2.addLast(leftNode);
                                                Field right = treeNodeClass.getDeclaredField("right");
                                                right.setAccessible(true);
                                                Object rightNode = right.get(node);
                                                list2.addLast(rightNode);
                                            }
                                        } catch (Exception e) {
                                            // ignore
                                        }
                                    });
                                    System.out.println();
                                    list1.clear();
                                } else {
                                    list2.forEach(node -> {
                                        System.out.print(node + "\t\t");
                                        try {
                                            if (node != null) {
                                                Field left = treeNodeClass.getDeclaredField("left");
                                                left.setAccessible(true);
                                                Object leftNode = left.get(node);
                                                list1.addLast(leftNode);
                                                Field right = treeNodeClass.getDeclaredField("right");
                                                right.setAccessible(true);
                                                Object rightNode = right.get(node);
                                                list1.addLast(rightNode);
                                            }
                                        } catch (Exception e) {
                                            // ignore
                                        }
                                    });
                                    System.out.println();
                                    list2.clear();
                                }
                            }

                            System.out.println();
                        } else {
                            System.err.println("not match");
                        }
                    } else {
                        System.out.println("link " + o);
                    }
                }
                System.out.println(Arrays.toString(table));
            }

        }
    }

    // 树的各种遍历
    @Test
    public void treeForeach() throws Exception {
        Map<Integer, String> map = new HashMap<>();
        for (int i = 0; i < 15; i++) {
            map.put((int) Math.pow(2, i), String.valueOf(i));
        }
        // 相关class准备
        Class<?> treeNodeClass = Class.forName("java.util.HashMap$TreeNode");
        // 获取table大小和相关属性
        Object[] table = (Object[]) getFieldValue("table", map);
        // 格式化打印数据
        for (int i = 0; i < table.length; i++) {
            Object o = table[i];
            if (o != null && o.getClass() == treeNodeClass) {// 如果是红黑树
                Object root = o;
                log.info("广度优先：层次遍历");
                breadthFirstSearch(root);
                log.info("深度优先：前序遍历");
                depthFirstSearch(root);
            }
        }
    }

    // 广度优先：层次遍历
    private void breadthFirstSearch(Object root) throws NoSuchFieldException, IllegalAccessException {
        LinkedList<Object> queue = new LinkedList();
        queue.add(root);
        int treeCnt = 1;
        while (!queue.isEmpty()) {
            Object firstNode = queue.removeFirst();
            System.out.print(firstNode + "\t\t");
            if (firstNode != null) {
                Object leftNode = getFieldValue("left", firstNode);
                if (leftNode != null)
                    queue.addLast(leftNode);
                Object rightNode = getFieldValue("right", firstNode);
                if (rightNode != null)
                    queue.addLast(rightNode);
            }
            if (isLayerLastTreeNode(treeCnt)) {
                System.out.println();
            }
            treeCnt++;
        }
        System.out.println();
    }

    // 深度优先遍历：根据root的出现位置分为前序（root在第一位），中序（root出现在中间），后序（root最后出现）。
    // 通过调整左右还分为左前序和右前序。
    private void depthFirstSearch(Object root) throws NoSuchFieldException, IllegalAccessException {
        if (root!=null){
            System.out.println("前序："+root);
            Object left = getFieldValue("left", root);
            depthFirstSearch(left);
//            System.out.println("中序："+root);
            Object right = getFieldValue("right", root);
            depthFirstSearch(right);
//            System.out.println("后序："+root);

        }
    }

    // 设为size
    @Test
    public void maxSize() throws Exception{
        Map map = new HashMap();
        Class<? extends Map> mapClass = map.getClass();
        Field sizeField = mapClass.getDeclaredField("size");
        sizeField.setAccessible(true);
        sizeField.set(map,Integer.MAX_VALUE);
        log.info(s(map.size()));
        map.put("last","");
        log.info(s(map.size()));
        map.put("really_last","");
        log.info(s(map.size()));
    }

    // fail-fast
    @Test
    public void failFast() throws Exception{
        // 初始化map
        Map map = new HashMap();
        for (int i = 0; i < 10; i++) {
            map.put(i,i);
        }
        // 创建一个线程来循环map的元素
        Thread thread = new Thread(() -> {
            Set set = map.entrySet();
            for (Object s : set) {
                System.out.println(s);
                TestUtil.sleep(100);
            }
        });
        thread.start();
        // 操作map
        map.put("100",'1');
        thread.join();
    }

    @Test
    public void hashDemo() throws Exception{
        int hash = 88888888^88888888>>>16;
        log.info(s(hash));
        log.info(s(Integer.toBinaryString(hash)));
    }
    // oldCap = 32
    // [3	] link 35=5 67=6 131=7 259=8 515=9 1027=10 2051=11 4099=12
    @Test
    public void o() throws Exception{
        System.out.println(35&31);
        System.out.println(67&31);
        System.out.println(131&31);

        System.out.println(35&32);

        System.out.println(35&63);
        System.out.println(67&32);
        System.out.println(131&32);
        System.out.println(Integer.toBinaryString(32));
        System.out.println(Integer.toBinaryString(67));
        System.out.println(Integer.toBinaryString(131));
    }

}
