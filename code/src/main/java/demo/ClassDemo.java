package demo;

import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.beans.Transient;
import java.io.DataOutputStream;
import java.io.FileOutputStream;

/**
 * 字节码工程
 */
@Slf4j
public class ClassDemo extends BaseDemo {


    @Transient
   public  void getObj (){
       synchronized (ClassDemo.class){
           int i = 0;
       }
   }

    @Transient(false)
   public  void getObj1 (){
       synchronized (ClassDemo.class){
           int i = 0;
       }
   }

}
