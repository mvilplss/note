package demo;

import demo.BaseDemo;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.util.*;

/**
 * 递归
 */
@Slf4j
public class RecursionDemo extends BaseDemo {


    @Test
    public void run() throws Exception{
        ClassLoader ext = new ClassLoader("ext",null);
        ClassLoader app = new ClassLoader("app",ext);
        ClassLoader cus = new ClassLoader("cus",app);
        String cls = cus.loadClass("Demo.class");
        log.info(cls);

    }

    class ClassLoader{
        public ClassLoader parent;
        public String name;
        public Map<String,String> hadLoad;

        public ClassLoader(String name,ClassLoader parent){
            hadLoad = new HashMap<>();
            this.parent=parent;
            this.name=name;
        }

        public String loadClass(String path) throws ClassNotFoundException {
            log.info(this.name+"invoke loadClass");
            String cls = hadLoad.get(path);
            if (cls==null){
                try {
                    if (this.parent != null) {
                        this.parent.loadClass(path);
                    }
                }catch (ClassNotFoundException e){
                    // ignore
                    log.error(this.name+":"+e.getException());
                }
            }
            if (cls==null){
                this.findClass(path);
            }

            return cls;
        }

        public String findClass(String path) throws ClassNotFoundException {
            log.info(this.name+"invoke findClass");
            throw new ClassNotFoundException(path);
        }
    }
}
