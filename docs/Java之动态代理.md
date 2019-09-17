---
title: Java之动态代理
date: 2019-09-17
categories: 
- 开发技术
tags: 
- java
copyright: true
---

## Java之动态代理
动态代理是一种设计模式，通过对原对象进行包装生成代理对象，可以实现对原来对象的方法增强。比如spring的声明式事务就是使用的动态代理模式实现的。下面我们将会了解到：
- 动态代理的实现方式。
- jdk和cglib动态代理类的生成源码分析。
- jdk和cglib动态代理类的执行分析。

## 动态代理类的实现方式
定义一个接口:
```
public interface ComputerIntf {
    int add(int i);
}
```
定义一个实现类（目标类）：
```
public class Computer implements ComputerIntf {
    @Override
    public int add(int i) {
        return i + 1;
    }
}
```
我们下面通过两种方式实现上面add方法在执行计算前输出入参日志。
### jdk动态代理
通过jdk自带的api来实现动态代理：
1. 首先创建一个`InvocationHandler`的实现类，重写`invoke`方法，在方法调用前打印日志，增加带`ComputerIntf`参数构造方法，在创建handler对象时传入代理目标对象，在invoke方法中反射调用时使用。
```
public class JdkInvocationHandler implements InvocationHandler {
    private ComputerIntf computerIntf;

    public JdkInvocationHandler(ComputerIntf computerIntf) {
        this.computerIntf = computerIntf;
    }
    // proxy 生成的代理对象
    // method 目标方法
    // args 目标方法的执行参数
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        System.out.println(args[0]);// 打印入参
        return method.invoke(computerIntf, args);
    }
}
```
2. 通过`Proxy.newProxyInstance`方法创建代理类对象.
```
    @Test
    public void jdkProxy() throws Exception {
    	// 目标对象
    	Computer computer = new Computer();
    	// 创建JdkInvocationHandler对象，并传递目标对象
        JdkInvocationHandler jdkProxy = new JdkInvocationHandler(computer);
        // 通过Proxy创建代理对象，分别传入：类加载器、目标对象接口数组、InvocationHandler对象。
        // *jdk代理对象是作为目标对象的接口的实现类。
        ComputerIntf instance = (ComputerIntf) Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(), Computer.class.getInterfaces(), jdkProxy);
        instance.add(1);
    }
    
    // 执行结果：
    入参：1
```

### cglib动态代理
通过第三方包cglib来实现动态代理：
1. 首先创建一个`MethodInterceptor`的实现类，重写`intercept`方法，同样在方法调用前打印日志。增加一个静态方法`getProxyInstance`用来传递目标class和代理对象的生成。
```
public class CglibMethodInterceptor implements MethodInterceptor {

    @Override
    public Object intercept(Object obj, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
        System.out.println("入参："+args[0]);
        return methodProxy.invokeSuper(obj, args);
    }
}
```
2. 通过cglib的`Enhancer`类设置父类和方法回调来创建出代理对象。
```
    @Test
    public void cglibProxy() throws Exception {
	    Enhancer enhancer = new Enhancer();
	    // *设置父类（cglib代理对象是继承目标对象，作为目标对象的子类）
	    enhancer.setSuperclass(targetClass);
	    // 设置方法拦截器
	    enhancer.setCallback(new CglibMethodInterceptor());
	    // 创建代理对象
	    Computer instance = (Computer)enhancer.create();
	    instance.add(1);
	}

	// 执行结果：
	入参：1    
```

## jdk和cglib动态代理类的生成源码分析
### jdk动态代理类源码分析

### cglib动态代理类源码分析

## jdk和cglib动态代理类的执行分析
### jdk和cglib谁更快
为了测试运行速度，写了一个小的demo，代码如下：
目标类：
```
public interface ComputerIntf {
    int add(int i);
}
public class Computer implements ComputerIntf {
    @Override
    public int add(int i) {
        return i + 1;
    }
}
```
jdk代理：
```
public class JdkInvocationHandler implements InvocationHandler {
    private ComputerIntf computerIntf;
    public JdkInvocationHandler(ComputerIntf computerIntf) {
        this.computerIntf = computerIntf;
    }
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        return method.invoke(computerIntf, args);
    }
}

@Test
public void jdkProxy() throws Exception {
//        System.setProperty("sun.misc.ProxyGenerator.saveGeneratedFiles", "true");// 默认存放当前项目com/sun/proxy下
    JdkInvocationHandler jdkProxy = new JdkInvocationHandler(new Computer());
    long begin = System.currentTimeMillis();
    ComputerIntf instance = (ComputerIntf) Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(), Computer.class.getInterfaces(), jdkProxy);
    for (int i = 0; i < 1000000000; i++) {
        instance.add(i);
    }
    log.info("jdk耗时：{}",System.currentTimeMillis()-begin);
}
```
> 运行5次结果分别为：2611 2747 2739 2728 2660

cglib代理：
```
public class CglibMethodInterceptor implements MethodInterceptor {

    public static Object getProxyInstance(Class targetClass) {
        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(targetClass);
        enhancer.setCallback(new CglibMethodInterceptor());
        return enhancer.create();
    }

    @Override
    public Object intercept(Object obj, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
        return methodProxy.invokeSuper(obj, args);
    }
}
@Test
public void cglibProxy() throws Exception {
//        System.setProperty(DebuggingClassWriter.DEBUG_LOCATION_PROPERTY, "file/proxy");
    Computer instance = (Computer) CglibMethodInterceptor.getProxyInstance(Computer.class);
    long begin = System.currentTimeMillis();
    for (int i = 0; i < 100000000; i++) {
        instance.add(i);
    }
    log.info("cglib耗时：{}",System.currentTimeMillis()-begin);
}
```
> 运行5次结果分别为：2799 2670 2683 2786 2607

**结论**：由上面测试结果可以看出在jdk1.8下，jdk动态代理类的运行速度和cglib动态代理类的运行速度实力相当，那么想进一步了解代理对象的执行过程请看下面内容。

### jdk动态代理类的方法执行过程
获取jdk生成的动态代理类有两种方法：
1. 在动态代理类创建前加上`System.setProperty("sun.misc.ProxyGenerator.saveGeneratedFiles", "true");// 当前项目com/sun/proxy`代码。
2. 设置jvm参数`-Dsun.misc.ProxyGenerator.saveGeneratedFiles=true`。
生成的代理类$Proxy6.class反编译后代码：
```
//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package com.sun.proxy;

import demo.other.proxy.ComputerIntf;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.UndeclaredThrowableException;

public final class $Proxy6 extends Proxy implements ComputerIntf {
    private static Method m1;
    private static Method m3;
    private static Method m2;
    private static Method m0;

    public $Proxy6(InvocationHandler var1) throws  {
        super(var1);
    }

    public final boolean equals(Object var1) throws  {
        try {
            return (Boolean)super.h.invoke(this, m1, new Object[]{var1});
        } catch (RuntimeException | Error var3) {
            throw var3;
        } catch (Throwable var4) {
            throw new UndeclaredThrowableException(var4);
        }
    }
    // 实现add方法
    public final int add(int var1) throws  {
        try {
        	// 将add方法最终传递给我们自定义的InvocationHandler
            return (Integer)super.h.invoke(this, m3, new Object[]{var1});
        } catch (RuntimeException | Error var3) {
            throw var3;
        } catch (Throwable var4) {
            throw new UndeclaredThrowableException(var4);
        }
    }

    public final String toString() throws  {
        try {
            return (String)super.h.invoke(this, m2, (Object[])null);
        } catch (RuntimeException | Error var2) {
            throw var2;
        } catch (Throwable var3) {
            throw new UndeclaredThrowableException(var3);
        }
    }

    public final int hashCode() throws  {
        try {
            return (Integer)super.h.invoke(this, m0, (Object[])null);
        } catch (RuntimeException | Error var2) {
            throw var2;
        } catch (Throwable var3) {
            throw new UndeclaredThrowableException(var3);
        }
    }
    // 获取每个原始是方法
    static {
        try {
            m1 = Class.forName("java.lang.Object").getMethod("equals", Class.forName("java.lang.Object"));
            m3 = Class.forName("demo.other.proxy.ComputerIntf").getMethod("add", Integer.TYPE);
            m2 = Class.forName("java.lang.Object").getMethod("toString");
            m0 = Class.forName("java.lang.Object").getMethod("hashCode");
        } catch (NoSuchMethodException var2) {
            throw new NoSuchMethodError(var2.getMessage());
        } catch (ClassNotFoundException var3) {
            throw new NoClassDefFoundError(var3.getMessage());
        }
    }
}

```
通过上面源码可以看出jdk生成的代理类是实现目标接口`ComputerIntf`的实现类，重写每个方法。在静态代码中获取每个原始方法`m0 m1 m2 m3`，然后在重写的方法中统一包装，最终调用`(Integer)super.h.invoke(this, m3, new Object[]{var1})`。
具体的执行`add`方法过程如下：
1. 当我们调用add方法时内部调用了`super.h.invoke(this, m3, new Object[]{var1});`
2. 其实就是调用我们在创建代理对象时候传入的invocationHandler实现类的对象的`JdkInvocationHandler.invoke`方法，
```
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    	// 增强逻辑...
    	// 反射调用目标对象的方法
        return method.invoke(computerIntf, args);
    }
```
3. 在执行`JdkInvocationHandler.invoke`方法时先执行我们自定义的增强逻辑（打印日志），然后执行通过反射执行目标方法`method.invoke`。

**调用步骤:** 执行代理类的add方法->执行代理类的父类中的invocationhandler对象的invoke方法->执行增强逻辑->通过反射执行目标方法。

### cglib动态代理类的方法执行过程
cglib自带获取动态代理类文件的设置:System.setProperty(DebuggingClassWriter.DEBUG_LOCATION_PROPERTY, "file/proxy");
执行后我们竟然获取到三个类：
```
Computer$$EnhancerByCGLIB$$4ab32890$$FastClassByCGLIB$$fdfcab1d.class
Computer$$EnhancerByCGLIB$$4ab32890.class
Computer$$FastClassByCGLIB$$48de3884.class
```
通过字节码反编译后可以看出Computer$$EnhancerByCGLIB$$4ab32890.class就是我们的代理类（代码比jdk生成的长）：
```
//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package demo.other.proxy;

import java.lang.reflect.Method;
import net.sf.cglib.core.ReflectUtils;
import net.sf.cglib.core.Signature;
import net.sf.cglib.proxy.Callback;
import net.sf.cglib.proxy.Factory;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;

public class Computer$$EnhancerByCGLIB$$4ab32890 extends Computer implements Factory {
    private boolean CGLIB$BOUND;
    public static Object CGLIB$FACTORY_DATA;
    private static final ThreadLocal CGLIB$THREAD_CALLBACKS;
    private static final Callback[] CGLIB$STATIC_CALLBACKS;
    private MethodInterceptor CGLIB$CALLBACK_0;
    private static Object CGLIB$CALLBACK_FILTER;
    private static final Method CGLIB$add$0$Method;
    private static final MethodProxy CGLIB$add$0$Proxy;
    private static final Object[] CGLIB$emptyArgs;
    private static final Method CGLIB$equals$1$Method;
    private static final MethodProxy CGLIB$equals$1$Proxy;
    private static final Method CGLIB$toString$2$Method;
    private static final MethodProxy CGLIB$toString$2$Proxy;
    private static final Method CGLIB$hashCode$3$Method;
    private static final MethodProxy CGLIB$hashCode$3$Proxy;
    private static final Method CGLIB$clone$4$Method;
    private static final MethodProxy CGLIB$clone$4$Proxy;

    static void CGLIB$STATICHOOK1() {
        CGLIB$THREAD_CALLBACKS = new ThreadLocal();
        CGLIB$emptyArgs = new Object[0];
        // 加载动态代理类（也就是本身）
        Class var0 = Class.forName("demo.other.proxy.Computer$$EnhancerByCGLIB$$4ab32890");
        Class var1;
        Method[] var10000 = ReflectUtils.findMethods(new String[]{"equals", "(Ljava/lang/Object;)Z", "toString", "()Ljava/lang/String;", "hashCode", "()I", "clone", "()Ljava/lang/Object;"}, (var1 = Class.forName("java.lang.Object")).getDeclaredMethods());
        CGLIB$equals$1$Method = var10000[0];
        CGLIB$equals$1$Proxy = MethodProxy.create(var1, var0, "(Ljava/lang/Object;)Z", "equals", "CGLIB$equals$1");
        CGLIB$toString$2$Method = var10000[1];
        CGLIB$toString$2$Proxy = MethodProxy.create(var1, var0, "()Ljava/lang/String;", "toString", "CGLIB$toString$2");
        CGLIB$hashCode$3$Method = var10000[2];
        CGLIB$hashCode$3$Proxy = MethodProxy.create(var1, var0, "()I", "hashCode", "CGLIB$hashCode$3");
        CGLIB$clone$4$Method = var10000[3];
        CGLIB$clone$4$Proxy = MethodProxy.create(var1, var0, "()Ljava/lang/Object;", "clone", "CGLIB$clone$4");
        // 获取目标类的原始方法
        CGLIB$add$0$Method = ReflectUtils.findMethods(new String[]{"add", "(I)I"}, (var1 = Class.forName("demo.other.proxy.Computer")).getDeclaredMethods())[0];
        // var1：demo.other.proxy.Computer的class对象
        // var0：demo.other.proxy.Computer$$EnhancerByCGLIB$$4ab32890的class对象
        // (I)I：表示入参和出参类型
        // add：对应var1的方法名
        // CGLIB$add$0：对应var0的方法名
        CGLIB$add$0$Proxy = MethodProxy.create(var1, var0, "(I)I", "add", "CGLIB$add$0");
    }
    // 代理类的CGLIB$add$0
    final int CGLIB$add$0(int var1) {
        return super.add(var1);
    }

    // 代理类重写父类的add方法
    public final int add(int var1) {
        MethodInterceptor var10000 = this.CGLIB$CALLBACK_0;
        // 初始化回调对象var10000（我们创建的CglibMethodInterceptor对象）
        if (this.CGLIB$CALLBACK_0 == null) {
            CGLIB$BIND_CALLBACKS(this);
            var10000 = this.CGLIB$CALLBACK_0;
        }

        if (var10000 != null) {
        	// 执行CglibMethodInterceptor对象的intercept方法
            Object var2 = var10000.intercept(this, CGLIB$add$0$Method, new Object[]{new Integer(var1)}, CGLIB$add$0$Proxy);
            return var2 == null ? 0 : ((Number)var2).intValue();
        } else {
            return super.add(var1);
        }
    }

    final boolean CGLIB$equals$1(Object var1) {
        return super.equals(var1);
    }

    public final boolean equals(Object var1) {
        MethodInterceptor var10000 = this.CGLIB$CALLBACK_0;
        if (this.CGLIB$CALLBACK_0 == null) {
            CGLIB$BIND_CALLBACKS(this);
            var10000 = this.CGLIB$CALLBACK_0;
        }

        if (var10000 != null) {
            Object var2 = var10000.intercept(this, CGLIB$equals$1$Method, new Object[]{var1}, CGLIB$equals$1$Proxy);
            return var2 == null ? false : (Boolean)var2;
        } else {
            return super.equals(var1);
        }
    }

    final String CGLIB$toString$2() {
        return super.toString();
    }

    public final String toString() {
        MethodInterceptor var10000 = this.CGLIB$CALLBACK_0;
        if (this.CGLIB$CALLBACK_0 == null) {
            CGLIB$BIND_CALLBACKS(this);
            var10000 = this.CGLIB$CALLBACK_0;
        }

        return var10000 != null ? (String)var10000.intercept(this, CGLIB$toString$2$Method, CGLIB$emptyArgs, CGLIB$toString$2$Proxy) : super.toString();
    }

    public static MethodProxy CGLIB$findMethodProxy(Signature var0) {
        String var10000 = var0.toString();
        switch(var10000.hashCode()) {
        case -1149925822:
            if (var10000.equals("add(I)I")) {
                return CGLIB$add$0$Proxy;
            }
            break;
        case -508378822:
            if (var10000.equals("clone()Ljava/lang/Object;")) {
                return CGLIB$clone$4$Proxy;
            }
            break;
        case 1826985398:
            if (var10000.equals("equals(Ljava/lang/Object;)Z")) {
                return CGLIB$equals$1$Proxy;
            }
            break;
        case 1913648695:
            if (var10000.equals("toString()Ljava/lang/String;")) {
                return CGLIB$toString$2$Proxy;
            }
            break;
        case 1984935277:
            if (var10000.equals("hashCode()I")) {
                return CGLIB$hashCode$3$Proxy;
            }
        }

        return null;
    }

    public Computer$$EnhancerByCGLIB$$4ab32890() {
        CGLIB$BIND_CALLBACKS(this);
    }

    public static void CGLIB$SET_THREAD_CALLBACKS(Callback[] var0) {
        CGLIB$THREAD_CALLBACKS.set(var0);
    }

    public static void CGLIB$SET_STATIC_CALLBACKS(Callback[] var0) {
        CGLIB$STATIC_CALLBACKS = var0;
    }

    private static final void CGLIB$BIND_CALLBACKS(Object var0) {
        Computer$$EnhancerByCGLIB$$4ab32890 var1 = (Computer$$EnhancerByCGLIB$$4ab32890)var0;
        if (!var1.CGLIB$BOUND) {
            var1.CGLIB$BOUND = true;
            Object var10000 = CGLIB$THREAD_CALLBACKS.get();
            if (var10000 == null) {
                var10000 = CGLIB$STATIC_CALLBACKS;
                if (CGLIB$STATIC_CALLBACKS == null) {
                    return;
                }
            }

            var1.CGLIB$CALLBACK_0 = (MethodInterceptor)((Callback[])var10000)[0];
        }

    }

    public Object newInstance(Callback[] var1) {
        CGLIB$SET_THREAD_CALLBACKS(var1);
        Computer$$EnhancerByCGLIB$$4ab32890 var10000 = new Computer$$EnhancerByCGLIB$$4ab32890();
        CGLIB$SET_THREAD_CALLBACKS((Callback[])null);
        return var10000;
    }

    public Object newInstance(Callback var1) {
        CGLIB$SET_THREAD_CALLBACKS(new Callback[]{var1});
        Computer$$EnhancerByCGLIB$$4ab32890 var10000 = new Computer$$EnhancerByCGLIB$$4ab32890();
        CGLIB$SET_THREAD_CALLBACKS((Callback[])null);
        return var10000;
    }

    public Object newInstance(Class[] var1, Object[] var2, Callback[] var3) {
        CGLIB$SET_THREAD_CALLBACKS(var3);
        Computer$$EnhancerByCGLIB$$4ab32890 var10000 = new Computer$$EnhancerByCGLIB$$4ab32890;
        switch(var1.length) {
        case 0:
            var10000.<init>();
            CGLIB$SET_THREAD_CALLBACKS((Callback[])null);
            return var10000;
        default:
            throw new IllegalArgumentException("Constructor not found");
        }
    }

    public Callback getCallback(int var1) {
        CGLIB$BIND_CALLBACKS(this);
        MethodInterceptor var10000;
        switch(var1) {
        case 0:
            var10000 = this.CGLIB$CALLBACK_0;
            break;
        default:
            var10000 = null;
        }

        return var10000;
    }

    public void setCallback(int var1, Callback var2) {
        switch(var1) {
        case 0:
            this.CGLIB$CALLBACK_0 = (MethodInterceptor)var2;
        default:
        }
    }

    public Callback[] getCallbacks() {
        CGLIB$BIND_CALLBACKS(this);
        return new Callback[]{this.CGLIB$CALLBACK_0};
    }

    public void setCallbacks(Callback[] var1) {
        this.CGLIB$CALLBACK_0 = (MethodInterceptor)var1[0];
    }

    // 初始化
    static {
        CGLIB$STATICHOOK1();
    }
}

```

当我们代理对象调用add方法时候调用过程如下：
1. 首先进行初始化CGLIB$STATICHOOK1()方法，主要目的就是获取目标类和代理类的class对象，获取目标类的方法（通过`ReflectUtils.findMethods`获取）和代理类的方法（通过`MethodProxy.create`获取）。
```
	static {
        CGLIB$STATICHOOK1();
    }
   static void CGLIB$STATICHOOK1() {
        CGLIB$THREAD_CALLBACKS = new ThreadLocal();
        CGLIB$emptyArgs = new Object[0];
        Class var0 = Class.forName("demo.other.proxy.Computer$$EnhancerByCGLIB$$4ab32890");
        Class var1;
        Method[] var10000 = ReflectUtils.findMethods(new String[]{"equals", "(Ljava/lang/Object;)Z", "toString", "()Ljava/lang/String;", "hashCode", "()I", "clone", "()Ljava/lang/Object;"}, (var1 = Class.forName("java.lang.Object")).getDeclaredMethods());
        CGLIB$equals$1$Method = var10000[0];
        CGLIB$equals$1$Proxy = MethodProxy.create(var1, var0, "(Ljava/lang/Object;)Z", "equals", "CGLIB$equals$1");
        CGLIB$toString$2$Method = var10000[1];
        CGLIB$toString$2$Proxy = MethodProxy.create(var1, var0, "()Ljava/lang/String;", "toString", "CGLIB$toString$2");
        CGLIB$hashCode$3$Method = var10000[2];
        CGLIB$hashCode$3$Proxy = MethodProxy.create(var1, var0, "()I", "hashCode", "CGLIB$hashCode$3");
        CGLIB$clone$4$Method = var10000[3];
        CGLIB$clone$4$Proxy = MethodProxy.create(var1, var0, "()Ljava/lang/Object;", "clone", "CGLIB$clone$4");
        CGLIB$add$0$Method = ReflectUtils.findMethods(new String[]{"add", "(I)I"}, (var1 = Class.forName("demo.other.proxy.Computer")).getDeclaredMethods())[0];
        CGLIB$add$0$Proxy = MethodProxy.create(var1, var0, "(I)I", "add", "CGLIB$add$0");
    }
    // 获取目标对象的原始方法
    public static Method[] findMethods(String[] namesAndDescriptors, Method[] methods){
        Map map = new HashMap();
        for (int i = 0; i < methods.length; i++) {
            Method method = methods[i];
            map.put(method.getName() + Type.getMethodDescriptor(method), method);
        }
        Method[] result = new Method[namesAndDescriptors.length / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = (Method)map.get(namesAndDescriptors[i * 2] + namesAndDescriptors[i * 2 + 1]);
            if (result[i] == null) {
                // TODO: error?
            }
        }
        return result;
    }
    // 根据目标类和代理类及目标方法名（add）和代理方法名（CGLIB$add$0）创建代理方法
    public static MethodProxy create(Class c1, Class c2, String desc, String name1, String name2) {
        MethodProxy proxy = new MethodProxy();
        proxy.sig1 = new Signature(name1, desc);
        proxy.sig2 = new Signature(name2, desc);
        proxy.createInfo = new CreateInfo(c1, c2);
        return proxy;
    }

```
2. 执行代理类的add方法，设置代理类的回调对象var10000（也就是我们在enhancer.setCallBack传入的CglibMethodInterceptor对象）。
```
		MethodInterceptor var10000 = this.CGLIB$CALLBACK_0;
        // 初始化回调对象var10000（我们创建的CglibMethodInterceptor对象）
        if (this.CGLIB$CALLBACK_0 == null) {
            CGLIB$BIND_CALLBACKS(this);
            var10000 = this.CGLIB$CALLBACK_0;
        }
```
3. 调用方法拦截器的var10000的intercept方法。
```
Object var2 = var10000.intercept(this, CGLIB$add$0$Method, new Object[]{new Integer(var1)}, CGLIB$add$0$Proxy);
```
4. 执行我们在CglibMethodInterceptor.intercept的增强逻辑。
```
    @Override
    public Object intercept(Object obj, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
        // 增强逻辑
        System.out.println(args[0]);
        // 通过代理方法调用执行目标方法
        return methodProxy.invokeSuper(obj, args);
    }
```
5. 执行methodProxy.invokeSuper方法。
```
	// obj：代理对象
	// args：方法入参
    public Object invokeSuper(Object obj, Object[] args) throws Throwable {
        try {
        	// 初始化fast类
            init();
            FastClassInfo fci = fastClassInfo;
            // 调用代理类的方法
            return fci.f2.invoke(fci.i2, obj, args);
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }
    }
```
MethodProxy执行init方法生成快速类FastClass。
```
    private void init(){
        if (fastClassInfo == null)
        {
            synchronized (initLock)
            {
                if (fastClassInfo == null)
                {
                    CreateInfo ci = createInfo;// 在代理类中CGLIB$STATICHOOK1()方法中创建methodProxy时候初始化，存放目标类和代理类及他们的命名方针和生成策略。
                    FastClassInfo fci = new FastClassInfo();
                    fci.f1 = helper(ci, ci.c1);// 生成目标类的快速类Computer$$FastClassByCGLIB$$48de3884.class
                    fci.f2 = helper(ci, ci.c2);// 生成代理类的快速类Computer$$EnhancerByCGLIB$$4ab32890$$FastClassByCGLIB$$fdfcab1d.class
                    fci.i1 = fci.f1.getIndex(sig1);// 通过快速类和方法签名获取方法索引
                    fci.i2 = fci.f2.getIndex(sig2);// 通过快速类和方法签名获取方法索引
                    fastClassInfo = fci;
                    createInfo = null;
                }
            }
        }
    }
    // 创建信息类
    private static class CreateInfo{
        Class c1;// 目标类
        Class c2;// 代理类
        NamingPolicy namingPolicy;
        GeneratorStrategy strategy;
        boolean attemptLoad;
        public CreateInfo(Class c1, Class c2)
        {
            this.c1 = c1;
            this.c2 = c2;
            AbstractClassGenerator fromEnhancer = AbstractClassGenerator.getCurrent();
            if (fromEnhancer != null) {
                namingPolicy = fromEnhancer.getNamingPolicy();
                strategy = fromEnhancer.getStrategy();
                attemptLoad = fromEnhancer.getAttemptLoad();
            }
        }
    }
    // 根据生成器生成快速类
    private static FastClass helper(CreateInfo ci, Class type) {
        FastClass.Generator g = new FastClass.Generator();
        g.setType(type);
        g.setClassLoader(ci.c2.getClassLoader());
        g.setNamingPolicy(ci.namingPolicy);
        g.setStrategy(ci.strategy);
        g.setAttemptLoad(ci.attemptLoad);
        return g.create();
    }    
```

生成的两个快速类反编译部分源码：
代理类的快速类：Computer$$EnhancerByCGLIB$$4ab32890$$FastClassByCGLIB$$fdfcab1d
```
public class Computer$$EnhancerByCGLIB$$4ab32890$$FastClassByCGLIB$$fdfcab1d extends FastClass {
    public Computer$$EnhancerByCGLIB$$4ab32890$$FastClassByCGLIB$$fdfcab1d(Class var1) {
        super(var1);
    }
    // 通过方法签名获取方法索引
    public int getIndex(Signature var1) {
        String var10000 = var1.toString();
        switch(var10000.hashCode()) {
        case -1832866005:
            if (var10000.equals("CGLIB$add$0(I)I")) {
                return 8;
            }
            break;
        case -1149925822:
            if (var10000.equals("add(I)I")) {
                return 0;
            }
            break;
        }
        return -1;
    }

    // 通过方法名称和类创建方法索引
    public int getIndex(String var1, Class[] var2) {
        switch(var1.hashCode()) {
        case 96417:
            if (var1.equals("add")) {
                switch(var2.length) {
                case 1:
                    if (var2[0].getName().equals("int")) {
                        return 0;
                    }
                }
            }
            break;
        case 1108311562:
            if (var1.equals("CGLIB$add$0")) {
                switch(var2.length) {
                case 1:
                    if (var2[0].getName().equals("int")) {
                        return 8;
                    }
                }
            }
            break;
        }

        return -1;
    }
    // 根据方法索引，调用对象，方法参数直接执行方法
    public Object invoke(int var1, Object var2, Object[] var3) throws InvocationTargetException {
        4ab32890 var10000 = (4ab32890)var2;
        int var10001 = var1;

        try {
            switch(var10001) {
            case 0:
                return new Integer(var10000.add(((Number)var3[0]).intValue()));
            case 8:
                return new Integer(var10000.CGLIB$add$0(((Number)var3[0]).intValue()));
            }
        } catch (Throwable var4) {
            throw new InvocationTargetException(var4);
        }
        throw new IllegalArgumentException("Cannot find matching method/constructor");
    }

}

```

目标类的快速类和上面类似：
```
public class Computer$$FastClassByCGLIB$$48de3884 extends FastClass {
    public Computer$$FastClassByCGLIB$$48de3884(Class var1) {
        super(var1);
    }

    public int getIndex(Signature var1) {
        String var10000 = var1.toString();
        switch(var10000.hashCode()) {
        case -1149925822:
            if (var10000.equals("add(I)I")) {
                return 0;
            }
            break;
        }
        return -1;
    }

    public int getIndex(String var1, Class[] var2) {
        switch(var1.hashCode()) {
        case 96417:
            if (var1.equals("add")) {
                switch(var2.length) {
                case 1:
                    if (var2[0].getName().equals("int")) {
                        return 0;
                    }
                }
            }
            break;
        }
        return -1;
    }

    public Object invoke(int var1, Object var2, Object[] var3) throws InvocationTargetException {
        Computer var10000 = (Computer)var2;
        int var10001 = var1;
        try {
            switch(var10001) {
            case 0:
                return new Integer(var10000.add(((Number)var3[0]).intValue()));
            }
        } catch (Throwable var4) {
            throw new InvocationTargetException(var4);
        }
        throw new IllegalArgumentException("Cannot find matching method/constructor");
    }

}

```

通过上面两个fastclass源码可以知道这个快速类提供类方法的index查询，同时可以通过invoke方法传入index来直接执行目标对象的方法。
我们再回到第5步执行`fci.f2.invoke(fci.i2, obj, args)`，f2就是生成的代理类的快速类Computer$$EnhancerByCGLIB$$4ab32890$$FastClassByCGLIB$$fdfcab1d，其中fci.i2就是在MethodProxy的init方法中调用快速类的getIndex(Signature var1)获取的方法索引，通过代理类的初始化方法可知intercept方法的参数methodProxy对象的i2就是CGLIB$add$0(I)I，索引为8，也就是最终调用的是代理对象的CGLIB$add$0方法而这个方法最终也是调用父类的add方法`super.add(var1);`也就是目标对象的add方法。

**调用步骤:** 调用代理类的add方法->调用方法拦截器的intercept方法->调用代理方法的invokeSuper方法->调用代理类的快速类invoke方法->执行代理类的代理方法`CGLIB$add$0`->调用父类对应的add方法。

> 通过jdk和cglib生成的代理对象的执行过程可知jdk最终是通过反射调用目标方法的，而cglib通过方法索引查询到方法并执行的。





