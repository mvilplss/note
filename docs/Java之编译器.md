---
title: Java之编译器
date: 2018-01-20
categories: 
- 开发技术
tags: 
- java
copyright: true
---

写过规则引擎的同学都知道drools语言，我们都通过一个drools容器来加载并执行drools写的各种规则,也玩过通过Java的脚本引擎执行过Javascript代码.这些动态加载并运行代码主要是用于编写不同规则,而非在代码中写满各种ifelse判断.
有的开发同学可能会想,Java语言可以作为想动态语言一样使用吗?答案是可以的,下面我们就开始!

## 简单实现
sun公司在jdk1.6后就正式发布了关于Java编译器的API,下面我们直接看一个简单的例子:
```
    @Test
    public void simpleCode() throws Exception {
        JavaCompiler javaCompiler = ToolProvider.getSystemJavaCompiler();
        int run = javaCompiler.run(null, null, null, "file/SimpleBean.java");
        if (run == 0) {
            log.info("编译成功");
        } else {
            log.error("编译失败");
        }
    }
```
上面代码是直接对Java源码文件进行编译,编译后的class文件会存在和源码的同一个目录下.

## 带有诊断器的实现
```
    // 带有诊断器，编译本地磁盘上源码
    @Test
    public void templateCode() throws Exception {
        JavaCompiler javaCompiler = ToolProvider.getSystemJavaCompiler();
        // 诊断收集器
        DiagnosticCollector<JavaFileObject> diagnosticCollector = new DiagnosticCollector<JavaFileObject>();
        StandardJavaFileManager standardFileManager = javaCompiler.getStandardFileManager(diagnosticCollector, null, null);
        Iterable<? extends JavaFileObject> javaFileObjects = standardFileManager.getJavaFileObjects("/Users/sanxing/blog/note/code/file/SimpleBean.java");
        JavaCompiler.CompilationTask compilerTask = javaCompiler.getTask(null, standardFileManager, diagnosticCollector, null, null, javaFileObjects);
        Boolean call = compilerTask.call();
        if (call) {
            log.info("编译成功");
        } else {
            log.error("编译失败");
            diagnosticCollector.getDiagnostics().forEach(diagnostic -> {
                log.error(diagnostic.getKind().name());
                log.error(diagnostic.getCode());
                log.error(diagnostic.getSource().getName() + ">" + diagnostic.getLineNumber() + ":" + diagnostic.getColumnNumber()
                        + ":" + diagnostic.getMessage(Locale.CHINA));
            });
        }
    }
```

当编译失败后,诊断器会获取编译失败的源码文件名,行数和列数以及失败的具体原因,比如局部变量未初始化使用.
```
public class SimpleBean {
    public String whoami(){
        int i;
        return "my name is SimpleBean for testing."+i;
    }
}
// 编译结果
17:11:13:133|ERROR|main|52|编译失败
17:11:13:184|ERROR|main|54|ERROR
17:11:13:184|ERROR|main|55|compiler.err.var.might.not.have.been.initialized
17:11:13:188|ERROR|main|56|/Users/sanxing/blog/note/code/file/SimpleBean.java>4:53:可能尚未初始化变量i
```

## 复杂实现
当我们需要自己在程序运行时候编译Java源码的情况下,大部分源码并非是在磁盘上,很有可能是数据库中.那么我们如何实现呢?下面的例子将会展现Java源码的编译,源码的自由获取,源码编译后的字节码加载到jvm中[类加载器](https://mvilplss.github.io/2018/01/01/Java%E7%B1%BB%E5%8A%A0%E8%BD%BD%E5%99%A8/)并运行其中的方法.
```
@Test
    public void forwardingJavaFileManagerCodeWithInvoke() throws Exception {
        JavaCompiler javaCompiler = ToolProvider.getSystemJavaCompiler();
        // 诊断收集器
        DiagnosticCollector<JavaFileObject> diagnosticCollector = new DiagnosticCollector<JavaFileObject>();
        // 获取标准Java文件管理器
        StandardJavaFileManager standardFileManager = javaCompiler.getStandardFileManager(diagnosticCollector, Locale.CHINA, Charset.forName("UTF-8"));
        List<ClassByteFileObject> classFileList = new ArrayList<>();
        // 创建标准Java文件包装器
        ForwardingJavaFileManager forwardingJavaFileManager = new ForwardingJavaFileManager(standardFileManager) {
            @Override
            public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling) throws IOException {
                // 设置编译后的class对象输出对象
                ClassByteFileObject javaFileObject = new ClassByteFileObject(className);
                classFileList.add(javaFileObject);
                return javaFileObject;
            }
        };
        // 创建Java编译任务
        JavaCompiler.CompilationTask compilerTask = javaCompiler.getTask(null, // 错误输出，null则打印控制台
                forwardingJavaFileManager, // 设置编译后对象输入文件管理器
                diagnosticCollector, // 设置诊断器
                null, null,
                Collections.singleton(new JavaSourceFileObject("SimpleBean.java",getSource("file/SimpleBean.java")))// 设置源文件管理器,我们可以从任何地方加载,包括DB
        );
        Boolean call = compilerTask.call();
        if (call) {
            log.info("编译成功！");
        } else {
            log.error("编译失败！");
            diagnosticCollector.getDiagnostics().forEach(diagnostic -> {
                log.error(diagnostic.getKind().name());
                log.error(diagnostic.getCode());
                log.error(diagnostic.getSource().getName() + ">" + diagnostic.getLineNumber() + ":" + diagnostic.getColumnNumber()
                        + ":" + diagnostic.getMessage(Locale.CHINA));
            });
        }

        // 加载编译好的类并调用
        ByteClassLoader byteClassLoader = new ByteClassLoader(classFileList.get(0).getBytes());
        Class<?> aClass = byteClassLoader.findClass("SimpleBean");
        Object whoami = aClass.getMethod("whoami").invoke(aClass.newInstance());
        log.info(s(whoami));
    }

    // 加载字节类加载器
    class ByteClassLoader extends ClassLoader{
        private byte[] bytes;

        public ByteClassLoader(byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            return defineClass(name,bytes,0,bytes.length);
        }
    }

    // 加载Java源码字符串，可以是任何来源
    private String getSource(String path) throws IOException {
        StringBuilder sb = new StringBuilder();
        List<String> allLines = Files.readAllLines(Paths.get(path));
        allLines.forEach(line->{
            sb.append(line);
        });
        return sb.toString();
    }

    // 类编译后的文件输出对象
    class ClassByteFileObject extends SimpleJavaFileObject {
        private ByteArrayOutputStream stream;

        public ClassByteFileObject(String name) {
            super(URI.create("bytes:///" + name), Kind.CLASS);
            stream = new ByteArrayOutputStream();
        }
        // 表示类文件输出
        @Override
        public OutputStream openOutputStream() throws IOException {
            return stream;
        }
        public byte[] getBytes() {
            return stream.toByteArray();
        }
    }

    // Java源码文件对象
    class JavaSourceFileObject extends SimpleJavaFileObject {
        private String source;
        public JavaSourceFileObject(String name,String source) throws IOException {
            super(URI.create("string:///" + name), Kind.SOURCE);
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
            return source;
        }
    }
```
上面代码的简介:
- 如果要获取到编译后的字节码的字节,我们需要定制自己的JavaFileObject来装载编译结果.
- 编译的结果获取需要通过文件管理器的包装类`ForwardingJavaFileManager.getJavaFileForOutput()`的方法中设置我们定义的文件对象`ClassByteFileObject`
- 自定义文件对象来获取输出结果需要继承`SimpleJavaFileObject`并重写`openOutputStream`方法.构造器中Kind为CLASS.
- 我们把输入的结果存入到`classFileList`中,下面类加载需要用到.
- 源码的输入同样需要源码的文件对象`JavaSourceFileObject`继承并重写`SimpleJavaFileObject.getCharContent`方法来自定义源文件的字符串,这样我们可以把远程加载过来的Java源码包装成Java文件对象.
- 定义自己的类字节码加载器`ByteClassLoader`用来加载编译后的class,然后通过反射调用目标方法.

运行结果:
```
17:31:47:745|INFO |main|90|编译成功！
17:31:47:747|INFO |main|105|my name is SimpleBean for testing.
```

## 参考文献
- Java doc
- 《Java核心技术二》









