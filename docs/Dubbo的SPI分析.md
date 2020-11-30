---
title: Dubbo的SPI分析
date: 2020-11-29
categories: 
- 开发技术
tags: 
- java
- dubbo
copyright: true
cover: https://gitee.com/mvilplss/note/raw/master/image/dubbo1.png.png
---
## 简介
SPI 全称为 Service Provider Interface，是一种服务发现机制。SPI 的本质是将接口实现类的全限定名配置在文件中，并由服务加载器读取配置文件，加载实现类。这样可以在运行时，动态为接口替换实现类。正因此特性，我们可以很容易的通过 SPI 机制为我们的程序提供拓展功能。SPI 机制在第三方框架中也有所应用，比如 Dubbo 就是通过 SPI 机制加载所有的组件。不过，Dubbo 并未使用 Java 原生的 SPI 机制，而是对其进行了增强，使其能够更好的满足需求。
## 普通SPI
### Java SPI 示例
#### 定义接口和实现类
```java
public interface AnimalHoo {
    void hoo();
}
```
```java
public class CatHoo implements AnimalHoo{
    @Override
    public void hoo() {
        System.out.println("喵喵...");
    }
}
public class DogHoo implements AnimalHoo{
    @Override
    public void hoo() {
        System.out.println("汪汪...");
    }
}
```
#### 定义spi配置
在resources/META-INF/services目录下增加spi配置文件，文件名称为接口的全限定名：`org.apache.dubbo.demo.spi.AnimalHoo`，文件内容如下：
```
org.apache.dubbo.demo.spi.CatHoo
org.apache.dubbo.demo.spi.DogHoo
```
#### 执行测试代码
```java
public class DubboSpiTest {
    @Test
    public void test_java_spi() throws Exception {
        ServiceLoader<AnimalHoo> animalHoos = ServiceLoader.load(AnimalHoo.class);
        animalHoos.forEach(animalHoo -> {
            animalHoo.hoo();
        });
    }
}
```
运行结果
```
喵喵...
汪汪...
```
### dubbo spi 示例
#### 接口和实现类
接口和Java的spi实现方式多了注解`@SPI`，value为默认的扩展实现类名称
```java
@SPI("cat")
public interface AnimalHoo {
    void hoo();
}

public class CatHoo implements AnimalHoo{
    @Override
    public void hoo() {
        System.out.println("喵喵...");
    }
}

public class DogHoo implements AnimalHoo{
    @Override
    public void hoo() {
        System.out.println("汪汪...");
    }
}
```
#### 配置文件
和Java Spi配置类似在resources/META-INF/dubbo目录下增加spi配置文件，文件名称为接口的全限定名：`org.apache.dubbo.demo.spi.AnimalHoo`，文件内容如下：
```
cat=org.apache.dubbo.demo.spi.CatHoo
dog=org.apache.dubbo.demo.spi.DogHoo
```
#### 执行测试代码
```java
public class DubboSpiTest {
    @Test
    public void test_dubbo_spi() throws Exception {
        ExtensionLoader<AnimalHoo> extensionLoader = ExtensionLoader.getExtensionLoader(AnimalHoo.class);
        AnimalHoo defaultExtension = extensionLoader.getDefaultExtension();
        defaultExtension.hoo();
        AnimalHoo cat = extensionLoader.getExtension("cat");
        cat.hoo();
        AnimalHoo dog = extensionLoader.getExtension("dog");
        dog.hoo();
    }
}
```
运行结果
```
喵喵...
喵喵...
汪汪...
```
## dubbo SPI 源码分析
#### 获取扩展加载器
```java
    public static <T> ExtensionLoader<T> getExtensionLoader(Class<T> type) {
        // 试着从缓存中获取扩展加载器
        ExtensionLoader<T> loader = (ExtensionLoader<T>) EXTENSION_LOADERS.get(type);
        if (loader == null) {
            // 获取不到时候，初始化一个扩展加载器
            EXTENSION_LOADERS.putIfAbsent(type, new ExtensionLoader<T>(type));
            loader = (ExtensionLoader<T>) EXTENSION_LOADERS.get(type);
        }
        return loader;
    }
 // 其中objectFactory的类型是在new ExtensionLoader时候通过自适应扩展获取的，下面会详细介绍自适应扩展
    private ExtensionLoader(Class<?> type) {
        this.type = type;
        objectFactory = (type == ExtensionFactory.class ? null : ExtensionLoader.getExtensionLoader(ExtensionFactory.class).getAdaptiveExtension());
    }
```
#### 通过扩展加载器获取扩展对象
```java
    // 获取扩展对象
    public T getExtension(String name) {
        return getExtension(name, true);
    }
    public T getExtension(String name, boolean wrap) {
        // 试着从缓存中获取扩展对象的持有者，如果缓存中没有则创建一个
        final Holder<Object> holder = getOrCreateHolder(name);
        Object instance = holder.get();
        if (instance == null) {
            synchronized (holder) {
                instance = holder.get();
                if (instance == null) {
                    // 创建扩展对象
                    instance = createExtension(name, wrap);
                    holder.set(instance);
                }
            }
        }
        return (T) instance;
    }
    private T createExtension(String name, boolean wrap) {
        // 根据扩展对象名称获取类
        Class<?> clazz = getExtensionClasses().get(name);
        try {
            T instance = (T) EXTENSION_INSTANCES.get(clazz);
            if (instance == null) {
                // 通过放射进行初始化，然后放入到缓存中
                EXTENSION_INSTANCES.putIfAbsent(clazz, clazz.newInstance());
                instance = (T) EXTENSION_INSTANCES.get(clazz);
            }
            // 扩展对象属性注入
            injectExtension(instance);
            // 获取包装类
            if (wrap) {
                List<Class<?>> wrapperClassesList = new ArrayList<>();
                if (cachedWrapperClasses != null) {
                    wrapperClassesList.addAll(cachedWrapperClasses);
                    wrapperClassesList.sort(WrapperComparator.COMPARATOR);
                    Collections.reverse(wrapperClassesList);
                }
                if (CollectionUtils.isNotEmpty(wrapperClassesList)) {
                    for (Class<?> wrapperClass : wrapperClassesList) {
                        Wrapper wrapper = wrapperClass.getAnnotation(Wrapper.class);
                        if (wrapper == null
                                || (ArrayUtils.contains(wrapper.matches(), name) && !ArrayUtils.contains(wrapper.mismatches(), name))) {
                            // 如果是包装类，则对包装类进行注入此扩展对象，然后返回包装类对象
                            instance = injectExtension((T) wrapperClass.getConstructor(type).newInstance(instance));
                        }
                    }
                }
            }
            //  如果扩展类实现类Lifecycle接口，则会调用initialize方法进行初始化
            initExtension(instance);
            return instance;
        } catch (Throwable t) {
        }
    }
    // 获取扩展类
    private Map<String, Class<?>> getExtensionClasses() {
        Map<String, Class<?>> classes = cachedClasses.get();
        if (classes == null) {
            synchronized (cachedClasses) {
                classes = cachedClasses.get();
                if (classes == null) {
                    // 加载所有扩展类放置缓存中
                    classes = loadExtensionClasses();
                    cachedClasses.set(classes);
                }
            }
        }
        return classes;
    }
    // 加载所有扩展类
    private Map<String, Class<?>> loadExtensionClasses() {
        Map<String, Class<?>> extensionClasses = new HashMap<>();
        for (LoadingStrategy strategy : strategies) {
            loadDirectory(extensionClasses, strategy.directory(), type.getName(), strategy.preferExtensionClassLoader(), strategy.overridden(), strategy.excludedPackages());
            // 向后兼容，更改路径再加载一次
            loadDirectory(extensionClasses, strategy.directory(), type.getName().replace("org.apache", "com.alibaba"), strategy.preferExtensionClassLoader(), strategy.overridden(), strategy.excludedPackages());
        }
        return extensionClasses;
    }
    // 加载策略的获取
    private static LoadingStrategy[] loadLoadingStrategies() {
        //  这里用到到的是Java spi的加载方法，会加载到三个策略对象，每个策略对象对应一个配置目录
        //org.apache.dubbo.common.extension.DubboInternalLoadingStrategy
        //org.apache.dubbo.common.extension.DubboLoadingStrategy
        //org.apache.dubbo.common.extension.ServicesLoadingStrategy
        return stream(ServiceLoader.load(LoadingStrategy.class).spliterator(), false)
                .sorted()
                .toArray(LoadingStrategy[]::new);
    }

    // 加载扩展的文件目录
    private void loadDirectory(Map<String, Class<?>> extensionClasses, String dir, String type,
                               boolean extensionLoaderClassLoaderFirst, boolean overridden, String... excludedPackages) {
        // 约定的地址 /META-INFO/dubbo/+type
        String fileName = dir + type;
        try {
            Enumeration<java.net.URL> urls = null;
            ClassLoader classLoader = findClassLoader();
            urls = classLoader.getResources(fileName);
            if (urls != null) {
                while (urls.hasMoreElements()) {
                    java.net.URL resourceURL = urls.nextElement();
                    // 根据文件路径获取加载资源
                    loadResource(extensionClasses, classLoader, resourceURL, overridden, excludedPackages);
                }
            }
        } catch (Throwable t) {
        }
    }
    // 加载扩展类资源
    private void loadResource(Map<String, Class<?>> extensionClasses, ClassLoader classLoader,
                              java.net.URL resourceURL, boolean overridden, String... excludedPackages) {
        try {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resourceURL.openStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    final int ci = line.indexOf('#');
                    if (ci >= 0) {
                        line = line.substring(0, ci);
                    }
                    line = line.trim();
                    if (line.length() > 0) {
                        try {
                            String name = null;
                            int i = line.indexOf('=');
                            if (i > 0) {
                                name = line.substring(0, i).trim();
                                line = line.substring(i + 1).trim();
                            }
                            if (line.length() > 0 && !isExcluded(line, excludedPackages)) {
                                // 获取class对象
                                loadClass(extensionClasses, resourceURL, Class.forName(line, true, classLoader), name, overridden);
                            }
                        } catch (Throwable t) {
                            IllegalStateException e = new IllegalStateException("Failed to load extension class (interface: " + type + ", class line: " + line + ") in " + resourceURL + ", cause: " + t.getMessage(), t);
                            exceptions.put(line, e);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            logger.error("Exception occurred when loading extension class (interface: " +
                    type + ", class file: " + resourceURL + ") in " + resourceURL, t);
        }
    }
    // 将扩展class根据注解进行分类缓存
    private void loadClass(Map<String, Class<?>> extensionClasses, java.net.URL resourceURL, Class<?> clazz, String name,
                               boolean overridden) throws NoSuchMethodException {
            if (clazz.isAnnotationPresent(Adaptive.class)) {
                // 缓存自适应扩展类
                cacheAdaptiveClass(clazz, overridden);
            } else if (isWrapperClass(clazz)) {
                // 缓存包装类
                cacheWrapperClass(clazz);
            } else {
                clazz.getConstructor();
                if (StringUtils.isEmpty(name)) {
                    name = findAnnotationName(clazz);
                    if (name.length() == 0) {
                        throw new IllegalStateException("No such extension name for the class " + clazz.getName() + " in the config " + resourceURL);
                    }
                }
                String[] names = NAME_SEPARATOR.split(name);
                if (ArrayUtils.isNotEmpty(names)) {
                    // 缓存激活类
                    cacheActivateClass(clazz, names[0]);
                    for (String n : names) {
                        // 缓存普通扩展点
                        cacheName(clazz, n);
                        saveInExtensionClass(extensionClasses, clazz, n, overridden);
                    }
                }
            }
        }

    // 扩展对象的注入
    private T injectExtension(T instance) {
        if (objectFactory == null) {
            return instance;
        }
        try {
            for (Method method : instance.getClass().getMethods()) {
                if (!isSetter(method)) {
                    continue;
                }
                Class<?> pt = method.getParameterTypes()[0];
                try {
                    String property = getSetterProperty(method);
                    Object object = objectFactory.getExtension(pt, property);
                    if (object != null) {
                        method.invoke(instance, object);
                    }
                } catch (Exception e) {
                }
            }
        } catch (Exception e) {
        }
        return instance;
    }
```
#### 扩展类执行流程总结
上面通过代码执行过程介绍了一个完整的基本的获取扩展对象的，基本流程如下：
1. ExtensionLoader.getExtensionLoader获取扩展加载器，先尝试在缓存中获取，否则进行new一个对象并缓存起来
2. 调用getExtension通过名称获取扩展对象，先尝试从缓存获取，否则创建一个扩展对象
3. 先从缓存中获取中获取扩展对象类，如果类缓存不存在则进行加载类缓存
    - 加载扩展类前先获取加载策略集合，然后循环策略执行loadDirectory方法
    - 最终调用loadResource方法，通过类资源加载器获取spi扩展配置文件，然后解析文件的实现类全限定名称，通过Class.forName加载到jvm中
    - 然后在对每个class根据注解和构造方法进行分别缓存到自适应类缓存（类上@Adaptive）、自动激活类缓存、普通扩展类缓存，包装类（根据构造函数判断）
4. 通过class类型尝试从内存中获取扩展对象，否则进行通过反射进行初始化对象
5. 然后根据扩展类是否有setter方法尝试注入扩展对象
6. 判断此类是否有包装类，如果有则对通过包装类的构造器注入此对象，然后返回此包装类对象
7. 再根据扩展类是否实现了Lifecycle接口来调用initialize方法进行自定义初始化
8. 最后设置对象到holder缓存并返回扩展对象

## dubbo自适应SPI扩展
在 Dubbo 中，很多拓展都是通过 SPI 机制进行加载的，比如 Protocol、Cluster、LoadBalance 等。有时，有些拓展并不想在框架启动阶段被加载，而是希望在拓展方法被调用时，根据运行时参数进行加载。Dubbo 通过自适应拓展机制很好的解决了。自适应拓展机制的实现逻辑比较复杂，首先 Dubbo 会为拓展接口生成具有代理功能的代码。然后通过 javassist 或 jdk 编译这段代码，得到 Class 类。最后再通过反射创建代理类，当调用自适应类的方法时候，代理类会根据传入的参数自动获取一个对象。
### 自适应spi示例
#### 定义接口和实现类
模拟dubbo的负载均衡自适应实现方式
```java
@SPI
public interface Balance {
    @Adaptive("loadbalance")
    void select(URL url);
}
public class ConsistentHashBalance implements Balance{
    @Override
    public void select(URL url) {
        System.out.println("我是一致性负载均衡");
    }
}
public class RandomBalance implements Balance{
    @Override
    public void select(URL url) {
        System.out.println("我是随机负载均衡");
    }
}
```
#### 配置文件
和Java Spi配置类似在resources/META-INF/dubbo目录下增加spi配置文件，文件名称为接口的全限定名：`org.apache.dubbo.demo.spi.Balance`，文件内容如下：
```
consistent=org.apache.dubbo.demo.spi.ConsistentHashBalance
random=org.apache.dubbo.demo.spi.RandomBalance
```
#### 执行测试代码
```java
    @Test
    public void test_dubbo_spi_adaptive() throws Exception {
        ExtensionLoader<Balance> extensionLoader = ExtensionLoader.getExtensionLoader(Balance.class);
        Balance balance = extensionLoader.getAdaptiveExtension();
        balance.select(URL.valueOf("dubbo://xxx.xxx.Xx?loadbalance=consistent"));
    }
```
运行结果
```
我是一致性负载均衡
```
### 源码分析
第一步同样先获取类扩展加载器，这里不在重复介绍。
然后获取自适应扩展对象
```java
    // 获取自适应对象
    public T getAdaptiveExtension() {
        Object instance = cachedAdaptiveInstance.get();
        if (instance == null) {
            synchronized (cachedAdaptiveInstance) {
                instance = cachedAdaptiveInstance.get();
                if (instance == null) {
                    try {
                        // 创建自适应对象
                        instance = createAdaptiveExtension();
                        cachedAdaptiveInstance.set(instance);
                    } catch (Throwable t) {}
                }
            }
        }
        return (T) instance;
    }
    // 创建自适应对象
    private T createAdaptiveExtension() {
        try {
            Class<?> adaptiveExtensionClass = getAdaptiveExtensionClass();
            Object o = adaptiveExtensionClass.newInstance();
            // 注入扩展对象
            return injectExtension((T) o);
        } catch (Exception e) {}
    }
    // 获取扩展对象类
    private Class<?> getAdaptiveExtensionClass() {
        // 上面已经介绍过，加载所有SPI类，然后分门别类的进行缓存
        getExtensionClasses();
        // 判断是否有缓存
        if (cachedAdaptiveClass != null) {
            return cachedAdaptiveClass;
        }
        // 创建一个自适应类
        return cachedAdaptiveClass = createAdaptiveExtensionClass();
    }
    // 创建自适应类
    private Class<?> createAdaptiveExtensionClass() {
        // 调用自适应代码生成器生成自适器java代码
        String code = new AdaptiveClassCodeGenerator(type, cachedDefaultName).generate();
        ClassLoader classLoader = findClassLoader();
        // 获取自适应编译器
        org.apache.dubbo.common.compiler.Compiler compiler = ExtensionLoader.getExtensionLoader(org.apache.dubbo.common.compiler.Compiler.class).getAdaptiveExtension();
        // 对生成的自适应java代码进行编译和加载
        return compiler.compile(code, classLoader);
    }
```
生成自适应器的源码如下：
```java
public class Balance$Adaptive
implements Balance {
    public void select(URL uRL) {
        if (uRL == null) {
            throw new IllegalArgumentException("url == null");
        }
        URL uRL2 = uRL;
        String string = uRL2.getParameter("loadbalance");
        if (string == null) {
            throw new IllegalStateException(new StringBuffer().append("Failed to get extension (org.apache.dubbo.demo.spi.Balance) name from url (").append(uRL2.toString()).append(") use keys([loadbalance])").toString());
        }
        Balance balance = (Balance)ExtensionLoader.getExtensionLoader(Balance.class).getExtension(string);
        balance.select(uRL);
    }
}
```
由源码可以看出，自适应扩展其实就是在普通扩展基础上对接口生成了一个代理类，由代理类根据入参进行判断执行某个实现类的同名方法。
#### 自适应扩展执行流程总结
1. 同样先调用ExtensionLoader.getExtensionLoader获取扩展加载器，先尝试在缓存中获取，否则进行new一个对象并缓存起来
2. 调用getAdaptiveExtension获取自适应扩展对象，先尝试从缓存获取，否则创建一个扩展对象
3. 先执行getExtensionClasses加载和缓存所有类
    - 加载扩展类前先获取加载策略集合，然后循环策略执行loadDirectory方法
    - 最终调用loadResource方法，通过类资源加载器获取spi扩展配置文件，然后解析文件的实现类全限定名称，通过Class.forName加载到jvm中
    - 然后在对每个class根据注解和构造方法进行分别缓存到自适应类缓存（类上@Adaptive）、自动激活类缓存、普通扩展类缓存，包装类（根据构造函数判断）
4. 判断是否已经缓存或自适应代理类，否则生成一个自适应代理类
    - new一个自适应代码生成器，然后生成一个自适应代理类Java模版代码
    - 然后根据SPI获取自适应编译器，然后进行Java代码的编译和加载到jvm中
5. 然后根据setter方法尝试注入扩展对象
6. 最后设置对象到holder缓存并返回自适应扩展代理对象
#### 自适应类的其他用法
如果某个实现类加上@Adaptive，那么这个实现类就是这个接口的自适应扩展类，无需再生成代理类，当默认生成的自适应代理类不满足我们的需求时候可以自行实现。
示例如下：
```java
@Adaptive
public class BalanceAdaptive implements Balance{
    @Override
    public void select(URL url) {
        // 如果为null则默认使用random，而非抛异常
        if (url==null){
            Balance random = ExtensionLoader.getExtensionLoader(Balance.class).getExtension("random");
            random.select(url);
        }else {
            String loadbalance = url.getParameter("loadbalance");
            Balance random = ExtensionLoader.getExtensionLoader(Balance.class).getExtension(loadbalance);
            random.select(url);
        }
    }
}
```
### 包装类型的扩展
#### 扩展包装类示例
假如我们要对某个接口下的扩展类进行包装，则可以对接口实现一个扩展类，示例如下：
```java
public class CatHooWrapper implements AnimalHoo{
    private AnimalHoo animalHoo;
    // 构造方法必须固定这么写
    public CatHooWrapper(AnimalHoo animalHoo) {
        this.animalHoo = animalHoo;
    }
    // 当执行此接口下的扩展类，则会执行此方法
    @Override
    public void hoo() {
        System.out.println("包装类执行前");
        animalHoo.hoo();
        System.out.println("包装类执行前");
    }
}
```
#### 原理解析
```java
    private T createExtension(String name, boolean wrap) {
        Class<?> clazz = getExtensionClasses().get(name);
        if (clazz == null) {
            throw findException(name);
        }
        try {
            T instance = (T) EXTENSION_INSTANCES.get(clazz);
            if (instance == null) {
                EXTENSION_INSTANCES.putIfAbsent(clazz, clazz.newInstance());
                instance = (T) EXTENSION_INSTANCES.get(clazz);
            }
            injectExtension(instance);
            // 判断是否进行返回包装类
            if (wrap) {
                List<Class<?>> wrapperClassesList = new ArrayList<>();
                // cachedWrapperClasses是在getExtensionClasses时候，根据构造函数类型已经做了缓存
                // 对包装类进行排序，先排序，然后倒叙，然后在下面循环的时候，保证返回正序的第一个
                if (cachedWrapperClasses != null) {
                    wrapperClassesList.addAll(cachedWrapperClasses);
                    wrapperClassesList.sort(WrapperComparator.COMPARATOR);
                    Collections.reverse(wrapperClassesList);
                }
                if (CollectionUtils.isNotEmpty(wrapperClassesList)) {
                    for (Class<?> wrapperClass : wrapperClassesList) {
                        Wrapper wrapper = wrapperClass.getAnnotation(Wrapper.class);
                        if (wrapper == null
                                || (ArrayUtils.contains(wrapper.matches(), name) && !ArrayUtils.contains(wrapper.mismatches(), name))) {
                            // 通过构造器注入扩展对象和实例化包装类对象
                            instance = injectExtension((T) wrapperClass.getConstructor(type).newInstance(instance));
                        }
                    }
                }
            }
            initExtension(instance);
            return instance;
        } catch (Throwable t) {}
    }
```

#### 包装类的调用过程
1. 同样先调用ExtensionLoader.getExtensionLoader获取扩展加载器，先尝试在缓存中获取，否则进行new一个对象并缓存起来
2. 调用getExtension获取扩展对象，先尝试从缓存获取，否则创建一个扩展对象
3. 先执行getExtensionClasses加载和缓存所有类
    - 加载扩展类前先获取加载策略集合，然后循环策略执行loadDirectory方法
    - 最终调用loadResource方法，通过类资源加载器获取spi扩展配置文件，然后解析文件的实现类全限定名称，通过Class.forName加载到jvm中
    - 然后在对每个class根据注解和构造方法进行分别缓存到自适应类缓存（类上@Adaptive）、自动激活类缓存、普通扩展类缓存，包装类（根据构造函数判断）缓存
4. 判断是加载包装类wrap=true
    - 判断缓存中（cachedWrapperClasses）是否有包装类
    - 对包装类进行倒叙排序，然后循环通过构造函数注入扩展对象并实例化包装类
5. 再根据扩展类是否实现了Lifecycle接口来调用initialize方法进行自定义初始化
7. 最后设置对象到holder缓存并返回自适应扩展代理对象
> dubbo使用包装类的有ProtocolFilterWrapper（生成过滤器调用链）和ProtocolListenerWrapper。

### 自动激活扩展点
- 当某些扩展点不想使用自适应同时又想通过参数获取时候可以考虑使用自动扩展方式：可以通过url中的参数名称获取。
- 当某写扩展点无论是否指定都要执行的时候可以考虑使用扩展点：无论是否指定，当获取当前接口下的扩展点时候自动加入带@Active的扩展类。
- 当通过某个参数获取一组扩展点，可以使用group来指定要获取扩展点的一组名称
#### 示例如下
```java
@SPI
public interface Filter {
    void invoke(URL ull);
}
// 服务提供者分组
@Activate(group = "provider",value = "filterName")
public class ProviderAccLogFilter implements Filter{
    @Override
    public void invoke(URL ull) {
        System.out.println("ProviderAccLogFilter... ");
    }
}
@Activate(group = "provider",value = "filterName")
public class ProviderLimitFilter implements Filter{
    @Override
    public void invoke(URL ull) {
        System.out.println("ProviderLimitFilter... ");
    }
}
// 消费者分组
@Activate(group = "consumer",value = "filterName")
public class ConsumerAccLogFilter implements Filter{

    @Override
    public void invoke(URL ull) {
        System.out.println("ConsumerAccLogFilter... ");
    }
}

@Activate(group = "consumer",value = "filterName")
public class ConsumerLimitFilter implements Filter{

    @Override
    public void invoke(URL ull) {
        System.out.println("ConsumerLimitFilter... ");
    }
}
// 无名者
@Activate
public class AllFilter implements Filter{
    @Override
    public void invoke(URL ull) {
        System.out.println("AllFilter... ");
    }
}
```
#### 执行测试代码
```java
    @Test
    public void test_dubbo_spi_active() throws Exception {
        ExtensionLoader<Filter> extensionLoader = ExtensionLoader.getExtensionLoader(Filter.class);
        System.out.println("=======不指定组========");
        List<Filter> filters = extensionLoader.getActivateExtension(URL.valueOf("dubbo://xxx.xxx.Xx?filterName=allFilter,"), "filterName");
        filters.forEach(filter -> {
            filter.invoke(null);
        });
        System.out.println("=======不指定组，用自定义key========");
        filters = extensionLoader.getActivateExtension(URL.valueOf("dubbo://xxx.xxx.Xx?myFilterName=providerAccLogFilter,"), "myFilterName");
        filters.forEach(filter -> {
            filter.invoke(null);
        });
        System.out.println("========指定组=======");
        filters = extensionLoader.getActivateExtension(URL.valueOf("dubbo://xxx.xxx.Xx?filterName=consumerAccLogFilter"), "filterName","consumer");
        filters.forEach(filter -> {
            filter.invoke(null);
        });
        System.out.println("========指定组，用组外扩展名=======");
        filters = extensionLoader.getActivateExtension(URL.valueOf("dubbo://xxx.xxx.Xx?filterName=providerAccLogFilter"), "filterName","consumer");
        filters.forEach(filter -> {
            filter.invoke(null);
        });
        System.out.println("========指定组，用自定义key=======");
        filters = extensionLoader.getActivateExtension(URL.valueOf("dubbo://xxx.xxx.Xx?myFilterName=providerAccLogFilter,"), "myFilterName","provider");
        filters.forEach(filter -> {
            filter.invoke(null);
        });
    }
```
#### 执行结果
```
=======不指定组========
ProviderAccLogFilter... 
ConsumerLimitFilter... 
ConsumerAccLogFilter... 
ProviderLimitFilter... 
AllFilter... 
=======不指定组，用自定义key========
AllFilter... 
ProviderAccLogFilter... 
========指定组=======
ConsumerLimitFilter... 
ConsumerAccLogFilter... 
========指定组，用组外扩展名=======
ConsumerLimitFilter... 
ConsumerAccLogFilter... 
ProviderAccLogFilter... 
========指定组，用自定义key=======
ProviderAccLogFilter... 
```
由结果可以得出结论：
- 不指定组，使用默认key，加载所有扩展点
- 不指定组，使用自定义key的，只有名称匹配和不指定key的被加载
- 指定组，只加载组中的扩展点
- 指定组，指定组外的扩展点名称，则加载指定扩展点和组内扩展点
- 指定组，用自定义key则只加载组中名称匹配的扩展点
#### 源码分析
```java
    public List<T> getActivateExtension(URL url, String[] values, String group) {
        List<T> activateExtensions = new ArrayList<>();
        List<String> names = values == null ? new ArrayList<>(0) : asList(values);
        // 如果名称不包含-default的则进行加载
        if (!names.contains(REMOVE_VALUE_PREFIX + DEFAULT_KEY)) {
            getExtensionClasses();
            // 获取所有自动激活扩展类并循环
            for (Map.Entry<String, Object> entry : cachedActivates.entrySet()) {
                String name = entry.getKey();
                Object activate = entry.getValue();
                String[] activateGroup, activateValue;
                // 获取Activate注解中的配置
                if (activate instanceof Activate) {
                    activateGroup = ((Activate) activate).group();
                    activateValue = ((Activate) activate).value();
                } else if (activate instanceof com.alibaba.dubbo.common.extension.Activate) {
                    activateGroup = ((com.alibaba.dubbo.common.extension.Activate) activate).group();
                    activateValue = ((com.alibaba.dubbo.common.extension.Activate) activate).value();
                } else {
                    continue;
                }
                //  
                if (isMatchGroup(group, activateGroup)// 指定组并且相同组，或则不指定组
                        && !names.contains(name) // 指定名称不包含此扩展点名称
                        && !names.contains(REMOVE_VALUE_PREFIX + name)// 不包含"-"+name
                        && isActive(activateValue, url)) // url中包括此key
                    {
                    activateExtensions.add(getExtension(name));// 加载扩展点
                }
            }
            activateExtensions.sort(ActivateComparator.COMPARATOR);
        }
        List<T> loadedExtensions = new ArrayList<>();
        // 加载指定名称的扩展点
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            if (!name.startsWith(REMOVE_VALUE_PREFIX)
                    && !names.contains(REMOVE_VALUE_PREFIX + name)) {
                if (DEFAULT_KEY.equals(name)) {
                    if (!loadedExtensions.isEmpty()) {
                        activateExtensions.addAll(0, loadedExtensions);
                        loadedExtensions.clear();
                    }
                } else {
                    loadedExtensions.add(getExtension(name));
                }
            }
        }
        if (!loadedExtensions.isEmpty()) {
            activateExtensions.addAll(loadedExtensions);
        }
        return activateExtensions;
    }
```
#### 自动激活扩展点的调用过程
1. 同样先调用ExtensionLoader.getExtensionLoader获取扩展加载器，先尝试在缓存中获取，否则进行new一个对象并缓存起来
2. 调用getActivateExtension获取自动激活对象集合
3. 先执行getExtensionClasses加载和缓存所有类
    - 加载扩展类前先获取加载策略集合，然后循环策略执行loadDirectory方法
    - 最终调用loadResource方法，通过类资源加载器获取spi扩展配置文件，然后解析文件的实现类全限定名称，通过Class.forName加载到jvm中
    - 然后在对每个class根据注解和构造方法进行分别缓存到自适应类缓存（类上@Adaptive）、自动激活类缓存、普通扩展类缓存，包装类（根据构造函数判断）缓存
4. 循环当前接口的所有自动激活的类缓存
    - 获取Activate注解中的配置
    - 判断是否配置组或组相同&&未指定此扩展名称&&不是开头"-"&&url中包含此扩展指定的key名称
    - 然后加载服务条件的扩展点
5. 然后在加载指定名称的扩展点
6. 最后合并所有扩展点，然后返回扩展点集合

## 参考文献
http://dubbo.apache.org/zh/docs/v2.7/dev/source/adaptive-extension/
