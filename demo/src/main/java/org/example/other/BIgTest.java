package org.example.other;

import org.junit.Test;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;

public class BIgTest {
    @Test
    public void test_Serial_Serial_old() throws Exception{
        List<GarbageCollectorMXBean> garbageCollectorMXBeans = ManagementFactory.getGarbageCollectorMXBeans();
        garbageCollectorMXBeans.forEach(g->{
            System.out.println(g.getName());
        });
        System.out.println(ManagementFactory.getRuntimeMXBean().getName()
        );
        Thread.sleep(Integer.MAX_VALUE);
    }
    @Test
    public void test_UseConcMarkSweepGC() throws Exception{
        List<GarbageCollectorMXBean> garbageCollectorMXBeans = ManagementFactory.getGarbageCollectorMXBeans();
        garbageCollectorMXBeans.forEach(g->{
            System.out.println(g.getName());
        });
        System.out.println(ManagementFactory.getRuntimeMXBean().getName()
        );
        Thread.sleep(Integer.MAX_VALUE);
    }
    @Test
    public void test_UseParallelGC() throws Exception{
        List<GarbageCollectorMXBean> garbageCollectorMXBeans = ManagementFactory.getGarbageCollectorMXBeans();
        garbageCollectorMXBeans.forEach(g->{
            System.out.println(g.getName());
        });
        System.out.println(ManagementFactory.getRuntimeMXBean().getName()
        );
        Thread.sleep(Integer.MAX_VALUE);
    }
    @Test
    public void test_UseG1() throws Exception{
        List<GarbageCollectorMXBean> garbageCollectorMXBeans = ManagementFactory.getGarbageCollectorMXBeans();
        garbageCollectorMXBeans.forEach(g->{
            System.out.println(g.getName());
        });
        System.out.println(ManagementFactory.getRuntimeMXBean().getName()
        );
        Thread.sleep(Integer.MAX_VALUE);
    }
}
