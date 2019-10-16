---
title: Java之类加载器
date: 2018-01-02
categories: 
- 开发技术
tags: 
- java
copyright: true
---

## 什么是类加载器

我们都知道Java程序运行到jvm上，我们把Java源码文件编译为jvm运行的指令，这些指令按照一定格式存在一个文件中-Java class文件，这些class文件如果要运行需要类加载器先加载到虚拟机内存中,然后获取一个java.lang.Class对象。
在Java中有三种类加载器：
- 引导类加载器：用来加载jdk的自带类（rt.jar等,可以通过`Launcher.getBootstrapClassPath()`获取路径.）
- 扩展类加载器Launcher.ExtClassLoader：用来加载%JAVA_HOME%/jre/lib/ext下的类。
- 应用类加载器Launcher.AppClassLoader：用来加载classpath下的类。

获取不同的类加载器:
``` 
	@Test
    public void javaClassLoader() throws Exception {
        log.info(s(Integer.class.getClassLoader()));
        log.info(s(NashornGuards.class.getClassLoader()));// javascript引擎，位于%JAVA_HOME%/jre/lib/ext下
        log.info(s(ClassLoaderDemo.class.getClassLoader()));
    }
```
运行结果：
```
19:28:20:228|INFO |main|27|null
19:28:20:236|INFO |main|28|sun.misc.Launcher$ExtClassLoader@1b40d5f0
19:28:20:236|INFO |main|29|sun.misc.Launcher$AppClassLoader@18b4aac2
```

## 如何保证类唯一性

Classloader为了保证加载到jvm中的类的唯一性，通过`双亲委派模式`和`同步锁`实现。
- `双亲委派模式`保证了一个类只被一个加载器加载。
- `同步锁`保证了在并发情况下一个类只被加载一次（jvm类加载默认是并行的）。

我们看下ClassLoader类的源码：
``` 
    protected Class<?> loadClass(String name, boolean resolve)
        throws ClassNotFoundException
    {
    	// 同步锁
        synchronized (getClassLoadingLock(name)) {
            // First, check if the class has already been loaded 检测是否加载过
            Class<?> c = findLoadedClass(name);
            if (c == null) {
                long t0 = System.nanoTime();
                try {
                    if (parent != null) {// 父类不为空则递归继续让父类加载
                        c = parent.loadClass(name, false);
                    } else {// 如果父类为空则由引导类加载器加载
                        c = findBootstrapClassOrNull(name);
                    }
                } catch (ClassNotFoundException e) {
                    // 这个catch很关键，执行findClass后位找到class则会抛出异常，所以捕获后可以保证继续父类的子类执行。
                    // ClassNotFoundException thrown if class not found
                    // from the non-null parent class loader
                }

                if (c == null) {
                    // If still not found, then invoke findClass in order
                    // to find the class.
                    long t1 = System.nanoTime();
                    // 如果通过递归向上检测父类及父类的父类等都没有加载,则当前加载器进行加载.
                    // 下面自定义类加载器可以看到重写这个方法
                    c = findClass(name);

                    // this is the defining class loader; record the stats
                    sun.misc.PerfCounter.getParentDelegationTime().addTime(t1 - t0);
                    sun.misc.PerfCounter.getFindClassTime().addElapsedTimeFrom(t1);
                    sun.misc.PerfCounter.getFindClasses().increment();
                }
            }

            if (resolve) {
                resolveClass(c);
            }
            return c;
        }
    }
```
上面源码执行过程:
1.当前类加载器获取此类名的锁
2.从jvm内存中查找类
3.如果没有查找到,则判断是否有父类
4.有父类则进行调用父类的loadClass()进行类,重复1步骤.
5.没有父类则从引导加载器加载的类中查找findBootstrapClassOrNull()
6.如果仍然未获取到类则调用当前类加载器的findClass()进行加载.

双亲委派的不仅实现了类的不同优先级,保证了高级别类不被低级别类覆盖.比如系统级别的Integer.class不被自定义的覆盖.

## 类加载核心
看完上面的步骤,你可能会问自己:如果所有父类都没有加载这个类,那么调用当前的类加器的`findClass()`方法是怎么找到这个类的.
看下URLClassLoader类的findClass()源码:
```
    /* The search path for classes and resources */
    URLClassPath ucp;
    protected Class<?> findClass(final String name)
        throws ClassNotFoundException
    {
        final Class<?> result;
        try {
            result = AccessController.doPrivileged(
                new PrivilegedExceptionAction<Class<?>>() {
                    public Class<?> run() throws ClassNotFoundException {
                        String path = name.replace('.', '/').concat(".class");
                        // 从当前加载器的类路径中获取类资源
                        Resource res = ucp.getResource(path, false);
                        if (res != null) {
                            try {
                                return defineClass(name, res);
                            } catch (IOException e) {
                                throw new ClassNotFoundException(name, e);
                            }
                        } else {
                            return null;
                        }
                    }
                }, acc);
        } catch (java.security.PrivilegedActionException pae) {
            throw (ClassNotFoundException) pae.getException();
        }
        if (result == null) {
            throw new ClassNotFoundException(name);
        }
        return result;
    }
```
通过上面源码可以看出类的最终查找是通过`ucp.getResource(path, false)`获取的,而ucp是存储每个加载器的加载空间.
我们看下AppClassLoader的部分源码就明白了:
```java
   static class AppClassLoader extends URLClassLoader {
        final URLClassPath ucp = SharedSecrets.getJavaNetAccess().getURLClassPath(this);

        public static ClassLoader getAppClassLoader(final ClassLoader var0) throws IOException {
            final String var1 = System.getProperty("java.class.path");
            final File[] var2 = var1 == null ? new File[0] : Launcher.getClassPath(var1);
            return (ClassLoader)AccessController.doPrivileged(new PrivilegedAction<Launcher.AppClassLoader>() {
                public Launcher.AppClassLoader run() {
                    URL[] var1x = var1 == null ? new URL[0] : Launcher.pathToURLs(var2);
                    return new Launcher.AppClassLoader(var1x, var0);
                }
            });
        }

        AppClassLoader(URL[] var1, ClassLoader var2) {
            super(var1, var2, Launcher.factory);
            this.ucp.initLookupCache(this);
        }
        static {
            ClassLoader.registerAsParallelCapable();
        }
    }
```
AppClassLoader继承URLClassLoader,通过调用静态方法`getAppClassLoader`创建AppClassLoader对象,具体步骤如下:
- 获取java.class.path的属性,也就是我们classpath的路径.
- 把文件路径转化为URL[]数组.
- 调用AppClassLoader带参数构造器,然后调用super父类的构造把url[]传给父类,初始化父类的ucp路径.
- 初始化自己的ucp查找路径缓存.

URLClassLoader的构造器源码:
```
    public URLClassLoader(URL[] urls, ClassLoader parent,
                          URLStreamHandlerFactory factory) {
        super(parent);
        // this is to make the stack depth consistent with 1.1
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkCreateClassLoader();
        }
        acc = AccessController.getContext();
        // 根据子类的urls创建ucp.
        ucp = new URLClassPath(urls, factory, acc);
    }
```
这样当调用当前对象的`findClass`方法时候,使用的`ucp`对象就是当前对象创建时候创建的`ucp`.
简单调用流程如下:
![类加载的简单流程,丑见谅!](https://github.com/mvilplss/note/blob/master/image/类加载器加载流程.png?raw=true)

## 写一个自定义加载器

我们大多数使用使用默认应用类加载器都可以完成几乎所有工作,但是如果我们想加载远程的class到本地虚拟机,那么就需要自定义一个加载器.
实现自定义类加载器只需要继承抽象类`ClassLoader`,然后重写`findClass`方法,然后实现远程类的字节下载即可.
代码如下:
``` java

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

    // 自定义加载器
    class NetworkClassLoader extends ClassLoader{
    	// 自定义类字节码获取
        private byte[] loadClassData(){
            try {
                log.info("network load ...");
                // 为了方便，使用本地代替了远程下载
                return Files.readAllBytes(Paths.get("lib/ClassLoaderDemo$RemoteClass.class"));
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        public Class<?> findClass(String name){
            byte[] loadClassData = loadClassData();
            if (loadClassData==null){
                throw new Error("class data is null");
            }
            // 调用父类的方法进行加载
            return defineClass(name, loadClassData, 0, loadClassData.length);
        }
    }

    // 远程的类代码
    public static class RemoteClass{
        public String myName(){
            return "I am remote class !";
        }
    }
```

执行结果:
```
20:09:20:199|INFO |main|71|network load ...
20:09:20:226|INFO |main|59|I am remote class !
20:09:20:226|INFO |main|60|ClassLoaderDemo$NetworkClassLoader@402bba4f
20:09:20:226|INFO |main|61|sun.misc.Launcher$AppClassLoader@18b4aac2
20:09:20:227|INFO |main|62|sun.misc.Launcher$ExtClassLoader@1b40d5f0
20:09:20:227|INFO |main|63|null
```

## 相关疑问

1.binary name是什么?
就是我们的类的全名如:com.lang.Integer
2.什么是破坏双亲委派机制？
通过TCCL(ThreadContextClassLoader)来实现上级加载器加载的类中通过非当前加载器加载子类时候破坏了双亲委派机制，如jdk的spi机制，典型的就是jdbc的Driver实现类的自动加载。Driver和DriverManager接口是BootStrap加载器加载的，但是实现类是通过DriverManager通过TCCL方式加载的。




