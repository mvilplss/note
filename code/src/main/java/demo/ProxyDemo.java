package demo;

import demo.other.proxy.*;
import lombok.extern.slf4j.Slf4j;
import net.sf.cglib.core.DebuggingClassWriter;
import net.sf.cglib.core.ReflectUtils;
import net.sf.cglib.proxy.MethodProxy;
import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;


// 保存class
// -Dsun.misc.ProxyGenerator.saveGeneratedFiles=true
@Slf4j
public class ProxyDemo extends BaseDemo {


    // 2611 2747 2739 2728
    // 2660 2539 2992 2498
    @Test
    public void jdkProxy() throws Exception {
        System.setProperty("sun.misc.ProxyGenerator.saveGeneratedFiles", "true");// 当前项目com/sun/proxy
        JdkInvocationHandler jdkProxy = new JdkInvocationHandler(new Computer());
        long begin = System.currentTimeMillis();
        ComputerIntf instance = (ComputerIntf) Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(), Computer.class.getInterfaces(), jdkProxy);
        for (int i = 0; i < 1; i++) {
            instance.add(i);
        }
        log.info("jdk耗时：{}",System.currentTimeMillis()-begin);
    }

    // 2799 2670 2683 2786
    // 2607 2616 2637 2654
    @Test
    public void cglibProxy() throws Exception {
        System.setProperty(DebuggingClassWriter.DEBUG_LOCATION_PROPERTY, "file/proxy");
        Computer instance = (Computer) CglibMethodInterceptor.getProxyInstance(Computer.class);
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 1; i++) {
            instance.add(i);
        }
        log.info("cglib耗时：{}",System.currentTimeMillis()-begin);
    }

    @Test
    public void originalMethod() throws Throwable {
        Class var1;
        Method method = ReflectUtils.findMethods(new String[]{"add", "(I)I"}, (var1 = Class.forName("demo.other.proxy.Computer")).getDeclaredMethods())[0];
        System.out.println(method);
        Class var0 = Class.forName("demo.other.proxy.Computer$$EnhancerByCGLIB$$4ab32890");
        System.out.println(var0);
        MethodProxy CGLIB$add$0$Proxy = MethodProxy.create(var1, var0, "(I)I", "add", "CGLIB$add$0");
        System.out.println(CGLIB$add$0$Proxy);
        Object o1 = var0.newInstance();
        System.out.println(o1);
        Object invoke = CGLIB$add$0$Proxy.invoke(o1, new Object[]{1});
        System.out.println(invoke);
        Object invokeSuper = CGLIB$add$0$Proxy.invokeSuper(o1, new Object[]{1});
        System.out.println(invokeSuper);

    }
}
