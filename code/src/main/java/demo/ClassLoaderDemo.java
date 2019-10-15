package demo;

import jdk.nashorn.internal.runtime.linker.NashornGuards;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import sun.misc.Launcher;
import sun.misc.URLClassPath;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * All rights Reserved, Designed By www.maihaoche.com
 *
 * @author: 三行（sanxing@maihaoche.com）
 * @date: 2019/8/31
 * @Copyright: 2017-2020 www.maihaoche.com Inc. All rights reserved.
 * 注意：本内容仅限于卖好车内部传阅，禁止外泄以及用于其他的商业目
 */
@Slf4j
public class ClassLoaderDemo extends BaseDemo {
    // 破坏双亲委派场景
    @Test
    public void breakDelegates() throws Exception{
        // 双亲委派是什么
        // spi机制加载jdbc驱动利用tccl实现加载类，破坏双亲委派。
    }

    @Test
    public void javaClassLoader() throws Exception {
        log.info(s(Integer.class.getClassLoader()));
        log.info(s(NashornGuards.class.getClassLoader()));// javascript引擎，位于%JAVA_HOME%/jre/lib/ext下
        log.info(s(ClassLoaderDemo.class.getClassLoader()));
    }

    @Test
    public void delegates() throws Exception {
        log.info(s(Thread.currentThread().getContextClassLoader()));
        log.info(s(Thread.currentThread().getContextClassLoader().getParent()));
        log.info(s(Thread.currentThread().getContextClassLoader().getParent().getParent()));
    }

    @Test
    public void bootstrapClassPath() throws Exception {
        URLClassPath classPath = Launcher.getBootstrapClassPath();
        URL[] urLs = classPath.getURLs();
        for (int i = 0; i < urLs.length; i++) {
            log.info(s(urLs[i]));
        }
    }

    @Test
    public void getSystemClassLoader() throws Exception {
        ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();
        log.info(s(systemClassLoader));
    }

    @Test
    public void loadExtClass() throws Exception {
        Class<?> aClass = ClassLoaderDemo.class.getClassLoader().loadClass("jdk.nashorn.internal.runtime.linker.NashornGuards");
        log.info(s(aClass.getClassLoader()));
    }

    @Test
    public void loadClassInJar() throws Exception {
        URLClassLoader urlClassLoader = new URLClassLoader(new URL[]{new URL("file://lib/nashorn.jar")});
        Class<?> aClass = urlClassLoader.loadClass("jdk.nashorn.internal.runtime.linker.NashornGuards");
        System.out.println(aClass + "");
        System.out.println(aClass.getClassLoader() + "");
    }

    @Test
    public void arrClassLoader() throws Exception {
        NashornGuards[] arr = new NashornGuards[1];
        log.info(s(arr.getClass().getClassLoader()));
    }

    @Test
    public void remoteClassLoader() throws Exception {
        NetworkClassLoader networkClassLoader = new NetworkClassLoader();
        Class<?> aClass = networkClassLoader.loadClass("ClassLoaderDemo$RemoteClass");
        Object o = aClass.newInstance();
        Object myName = aClass.getMethod("myName").invoke(o);
        log.info(s(myName));
        log.info(s(aClass.getClassLoader()));
        log.info(s(aClass.getClassLoader().getParent()));
        log.info(s(aClass.getClassLoader().getParent().getParent()));
        log.info(s(aClass.getClassLoader().getParent().getParent().getParent()));

    }

    class NetworkClassLoader extends ClassLoader {

        private byte[] loadClassData() {
            try {
                log.info("network load ...");
                // 为了方便，使用本地代替了远程下载
                return Files.readAllBytes(Paths.get("lib/ClassLoaderDemo$RemoteClass.class"));
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        public Class<?> findClass(String name) {
            byte[] loadClassData = loadClassData();
            if (loadClassData == null) {
                throw new Error("class data is null");
            }
            // name必须正确，jdk为了防止加载错类。
            return defineClass("ClassLoaderDemo$RemoteClass", loadClassData, 0, loadClassData.length);
        }
    }

    public static class RemoteClassx {
        public String myName() {
            return "I am remote class !";
        }
    }

}
