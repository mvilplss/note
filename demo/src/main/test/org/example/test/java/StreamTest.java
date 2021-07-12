package org.example.test.java;

import org.junit.Before;
import org.junit.Test;

import java.io.*;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ConcurrentHashMap;

public class StreamTest {
    String source = "/Users/atomic/Downloads/source.txt";
    String target = "/Users/atomic/Downloads/target.txt";

    @Before
    public void init() throws IOException {
        File file = new File(source);
        if (!file.exists()) {
            file.createNewFile();
            FileOutputStream stream = new FileOutputStream(file);
            int g = 1000 * 1000 * 1000;
            stream.write(new byte[g]);
            stream.close();
        }
    }


    @Test// 普通IO
    public void test_io() throws Exception {
        CostTime.begin();
        try (FileInputStream fis = new FileInputStream(source);
             FileOutputStream fos = new FileOutputStream(target)
        ) {
            byte[] buf = new byte[1024*8];
            int len;
            while ((len = fis.read(buf)) != -1) {
                fos.write(buf, 0, len);
            }
            System.out.println("test_io 执行完毕 耗时：" + CostTime.cost());
        }
    }

    @Test// 缓冲区IO
    public void test_buffer_io() throws Exception {
        CostTime.begin();
        try (FileInputStream fis = new FileInputStream(source);
             BufferedInputStream bis = new BufferedInputStream(fis);
             FileOutputStream fos = new FileOutputStream(target);
             BufferedOutputStream bos = new BufferedOutputStream(fos)
        ) {
            byte[] buf = new byte[1024];
            int len;
            while ((len = bis.read(buf)) != -1) {
                bos.write(buf, 0, len);
            }
            System.out.println("test_buffer_io 执行完毕 耗时：" + CostTime.cost());
        }

    }

    @Test// 文件映射零拷贝
    public void test_mmap() throws Exception {
        CostTime.begin();
        try (FileChannel sourceChannel = FileChannel.open(new File(source).toPath(), StandardOpenOption.READ);
             FileChannel targetChannel = FileChannel.open(new File(target).toPath(), StandardOpenOption.WRITE, StandardOpenOption.CREATE)
        ) {
            int g = 1000 * 1000 * 1000;
            long size = sourceChannel.size();
            long step = size / g;
            long rest = size % g;
            for (int i = 0; i < step; i++) {
                MappedByteBuffer map = sourceChannel.map(FileChannel.MapMode.READ_ONLY, 0, g);
                targetChannel.write(map);
            }
            if (rest > 0) {
                MappedByteBuffer map = sourceChannel.map(FileChannel.MapMode.READ_ONLY, step * g, rest);
                targetChannel.write(map);
            }
            System.out.println("test_mmap 执行完毕 耗时：" + CostTime.cost());
        }
    }

    @Test// sendfile模式零拷贝
    public void test_sendfile() throws Exception {
        CostTime.begin();
        try (FileChannel sourceChannel = FileChannel.open(new File(source).toPath(), StandardOpenOption.READ);
             FileChannel targetChannel = FileChannel.open(new File(target).toPath(), StandardOpenOption.WRITE, StandardOpenOption.CREATE)
        ) {
            long size = sourceChannel.size();
            long flag = size;
            // 直接将一个通道的数据传递给另一个数据
            // 最多一次传递Integer.MAX字节，约2.17G
            for (long i = 0L; i <= size; i += flag) {
                sourceChannel.transferTo(i, Math.min(i + flag, size), targetChannel);
            }
            System.out.println("test_sendfile 执行完毕 耗时：" + CostTime.cost());
        }
    }

    public static class CostTime {
        static ThreadLocal<ConcurrentHashMap<String, Long>> threadLocal = new ThreadLocal<>();

        public static void begin() {
            begin("");
        }

        public static Long cost() {
            return cost("");
        }

        public static void begin(String key) {
            long begin = System.currentTimeMillis();
            ConcurrentHashMap<String, Long> hashMap = threadLocal.get();
            if (hashMap == null) {
                hashMap = new ConcurrentHashMap<>();
                threadLocal.set(hashMap);
                hashMap.put(key, begin);
            }
        }

        public static Long cost(String key) {
            long end = System.currentTimeMillis();
            ConcurrentHashMap<String, Long> hashMap = threadLocal.get();
            Long begin;
            if (hashMap != null && (begin = hashMap.get(key)) != null) {
                return end - begin;
            }
            return null;
        }
    }

}
