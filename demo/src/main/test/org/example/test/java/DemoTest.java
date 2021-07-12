package org.example.test.java;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class DemoTest {

    @Test
    public void test_merge() throws Exception{
       File db = new File("/Users/atomic/Desktop/db");
        File[] files = db.listFiles();
        for(File sql:files){
            try(InputStreamReader isr = new InputStreamReader(new FileInputStream(sql));BufferedReader br = new BufferedReader(isr)) {
                String line = null;
                while ((line=br.readLine())!=null){
                    System.out.println(line);
                }
            }
        }
    }
}
