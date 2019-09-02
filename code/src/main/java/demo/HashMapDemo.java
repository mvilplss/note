package demo;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

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
    public void xxx() throws Exception {
        Map<Integer, String> map = new HashMap<>(1, 1f);
        for (int i = 0; i < 9; i++) {
            map.put((int) Math.pow(2, i), i + "");
        }

        Class<HashMap> mapClass = HashMap.class;
        Field[] fields = mapClass.getDeclaredFields();
        System.out.println(fields.length);
        for (Field field : fields) {
            field.setAccessible(true);

            if ("table".equals(field.getName())) {
                HashMap.Entry[] table = (HashMap.Entry[]) field.get(map);
                System.out.println(table.length);
                for (int i = 0; i < table.length; i++) {
                    HashMap.Entry entry = table[i];
                    if (entry != null) {
                        Class<? extends Map.Entry> aClass = entry.getClass();
                        Field[] nodeFields = aClass.getDeclaredFields();
                        for (int j = 0; j < nodeFields.length; j++) {
                            System.out.println(nodeFields.length);
                            Field nodeField = nodeFields[j];
                            nodeField.setAccessible(true);
                            Object o = nodeField.get(entry);
                            log.error(nodeField.getName() + "=" + o);
                        }
                    }
                    log.info(s(entry != null ? entry.getClass() : null));
                }
                log.info(field.getName() + "=" + Arrays.toString((table)));
            } else {
                log.info(field.getName() + "=" + field.get(map));

            }

        }
    }

    @Test
    public void hashMapRefectionStudy() throws Exception {
        Map<Integer, String> map = new HashMap<>();
        for (int i = 0; i < 15; i++) {
            map.put((int) Math.pow(2, i), String.valueOf(i));
        }
        // 相关class准备
        Class<?> nodeClass = Class.forName("java.util.HashMap$Node");
        Class<?> treeNodeClass = Class.forName("java.util.HashMap$TreeNode");
        // 获取table大小和相关属性
        Object[] table = (Object[]) getFieldValue("table", map);
        log.info("table:"+table.length);
        Object size = getFieldValue("size", map);
        log.info("size:"+size);
        Object modCount = getFieldValue("modCount", map);
        log.info("modCount:"+modCount);
        Object threshold = getFieldValue("threshold", map);
        log.info("threshold:"+threshold);
        Object loadFactor = getFieldValue("loadFactor", map);
        log.info("loadFactor:"+loadFactor);
        // 格式化打印数据
        for (int i = 0; i < table.length; i++) {
            Object o = table[i];
            System.out.print("[" + i + "\t] ");
            if (o != null) {
                if (o.getClass() == nodeClass) {// 默认为链表
                    System.out.print("link ");
                    while (o != null) {
                        System.out.print(o + " ");
                        o = getFieldValue("next",o);
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
                            Object leftNode = getFieldValue("left",firstNode);
                            queue.addLast(leftNode);
                            Object rightNode = getFieldValue("right",firstNode);
                            queue.addLast(rightNode);
                        }
                        if (isNextTree(treeCnt)) {
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

    // 判断是否是下一层树叶
    public boolean isNextTree(int num) {
        double n = (Math.log(num + 1) / Math.log(2));
        return n == (int) n;
    }

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

}
