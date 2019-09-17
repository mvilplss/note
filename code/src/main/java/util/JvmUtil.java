package util;

import sun.misc.ProxyGenerator;
import sun.misc.Unsafe;

import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.util.Arrays;

/**
 * All rights Reserved, Designed By www.maihaoche.com
 *
 * @author: 三行（sanxing@maihaoche.com）
 * @date: 2019/8/19
 * @Copyright: 2017-2020 www.maihaoche.com Inc. All rights reserved.
 * 注意：本内容仅限于卖好车内部传阅，禁止外泄以及用于其他的商业目
 */
public class JvmUtil {
    static Unsafe unsafe = null;
    static {
        Field theUnsafe = null;
        try {
            theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            unsafe=(Unsafe) theUnsafe.get(null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public static String getPid(){
        return ManagementFactory.getRuntimeMXBean().getName();
    }

    public static String getJvmAddress(Object... objects){
        long offset = unsafe.arrayBaseOffset(objects.getClass());
        long scale = unsafe.arrayIndexScale(objects.getClass());
        if (scale!=4){
            throw new AssertionError("不支持");
        }
        final long i1 = (unsafe.getLong(objects, offset) & 0xFFFFFFFFL) * 8;// 64bit=8
       return "0x0000000"+Long.toHexString(i1);
    }

    public static String saveClass(Object obj){
        Class<?> clazz = obj.getClass();
        Class<?>[] interfaces = clazz.getInterfaces();
        byte[] classFile = ProxyGenerator.generateProxyClass(clazz.getName(), interfaces);
        String path = JvmUtil.class.getResource(".").getPath();
        FileOutputStream fos = null;
        String f = path + clazz.getName() + ".class";
        try {
            fos = new FileOutputStream(f);
            fos.write(classFile);//保存到磁盘
            fos.flush();
        }catch(Exception e) {
            e.printStackTrace();
        }finally {
            try {
                fos.close();
            }catch(IOException e) {
                e.printStackTrace();
            }
        }
        return f;

    }
}
