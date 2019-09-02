package demo;

import org.junit.Test;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * All rights Reserved, Designed By www.maihaoche.com
 *
 * @author: 三行（sanxing@maihaoche.com）
 * @date: 2019/8/29
 * @Copyright: 2017-2020 www.maihaoche.com Inc. All rights reserved.
 * 注意：本内容仅限于卖好车内部传阅，禁止外泄以及用于其他的商业目
 */
public class FileDemo {

    @Test
    public void genBigFile() throws IOException {
        FileOutputStream fileOutputStream = new FileOutputStream("file/bigfile.txt");
        String content = "专注、坚持、无奇、专注、坚持、无奇、专注、坚持、无奇、专注、坚持、无奇、专注、坚持、无奇、专注、坚持、无奇、专注、坚持、无奇、专注、坚持、无奇、专注、坚持、无奇、";
        for (int i = 0; i < 10000000; i++) {
            fileOutputStream.write(content.getBytes());
        }
        fileOutputStream.close();
    }

    @Test
    public void readAllBytes() throws URISyntaxException, IOException {
        byte[] bytes = Files.readAllBytes(Paths.get("file/bigfile.txt"));
        System.out.println(new String(bytes));

    }

    @Test
    public void xxx() throws URISyntaxException, IOException {
        FileInputStream fileInputStream = new FileInputStream("file/bigfile.txt");
        byte[] buf = new byte[4];
        int i =0;
        while (fileInputStream.read(buf)!=-1&&i++<10){
            System.out.println(new String(buf));
        }
    }

    @Test
    public void xsss() throws IOException {
        FileChannel fileChannel = FileChannel.open(Paths.get("file/bigfile.txt"), StandardOpenOption.READ);
        fileChannel.position(3);
        ByteBuffer dst = ByteBuffer.allocate(3);
        int read = fileChannel.read(dst);
        System.out.println(new String(dst.array()));
    }
    }
