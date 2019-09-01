import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import javax.tools.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;


@Slf4j
public class JavaCompilerDemo extends BaseDemo {

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

    // 文件管理器包装类
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
                Collections.singleton(new JavaSourceFileObject("SimpleBean.java",getSource("file/SimpleBean.java")))// 设置源文件管理器
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

    // 加载字节类加载器 参考：Java类加载器.md
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

}
