---
title: Java之线程分析
date: 2018-01-02
categories: 
- 开发技术
tags: 
- java
copyright: true
---

## java线程
线程是操作系统能够进行运算调度的最小单位，它被包含在进程内共享进程范围内的资源(例如内存句柄和文件句柄)，是进程中的实际运作单位，每个线程都有自己的程序计数器(program counter)、栈及局部变量等。我们说的java线程就是jvm程序中执行的线程。

### java线程的创建
Java线程有两个创建方式，可以通过继承Thread，重写run方法来创建。也可以通过实现runnable接口作为构造参数来创建线程。

参数简介：
- ThreadGroup g 线程组，线程需要加到线程组中，方便线程的维护，线程的线程组默认是当前线程的线程组。
- Runnable target 可执行目标，指线程开启后要执行的任务。
- String name 线程的名称
- long stackSize 线程栈期望的大小，默认0 忽略大小，可通过Xss参数配置，线程栈的大小影响递归的深度，线程栈越大递归的深度越大。栈大小默认为1024k，可以通过：-XX:+PrintFlagsFinal 打印`ThreadStackSize`。
- AccessControlContext acc 线程的上下文访问控制 默认null
- boolean inheritThreadLocals 是否继承ThreadLocal，默认是false，表示ThreadLocal

```
private void init(ThreadGroup g, Runnable target, String name,
                      long stackSize, AccessControlContext acc,
                      boolean inheritThreadLocals) {
        if (name == null) {
            throw new NullPointerException("name cannot be null");
        }
        this.name = name;
        Thread parent = currentThread();
        SecurityManager security = System.getSecurityManager();
        if (g == null) {
            /* Determine if it's an applet or not */
            /* If there is a security manager, ask the security manager
               what to do. */
               // 如果是applet 则使用security的线程组
            if (security != null) {
                g = security.getThreadGroup();
            }
            /* If the security doesn't have a strong opinion of the matter
               use the parent thread group. */
               // 如果线程组未指定，则使用当前线程的线程组
            if (g == null) {
                g = parent.getThreadGroup();
            }
        }
        /* checkAccess regardless of whether or not threadgroup is
           explicitly passed in. */
        g.checkAccess();// 校验权限

        /*
         * Do we have the required permissions?
         */
        if (security != null) {
            if (isCCLOverridden(getClass())) {
                security.checkPermission(SUBCLASS_IMPLEMENTATION_PERMISSION);
            }
        }
        // 线程组增加未启动的线程个数
        g.addUnstarted();
        
        this.group = g;
        this.daemon = parent.isDaemon();// 如果父线程是守护线程，则只能创建出来的线程默认也是守护线程
        this.priority = parent.getPriority();// 优先级同父线程
        if (security == null || isCCLOverridden(parent.getClass()))
            this.contextClassLoader = parent.getContextClassLoader();
        else
            this.contextClassLoader = parent.contextClassLoader;
        this.inheritedAccessControlContext =
                acc != null ? acc : AccessController.getContext();
        this.target = target;
        setPriority(priority);// 设置优先级
        if (inheritThreadLocals && parent.inheritableThreadLocals != null)
            this.inheritableThreadLocals =
                ThreadLocal.createInheritedMap(parent.inheritableThreadLocals);
        /* Stash the specified stack size in case the VM cares */
        this.stackSize = stackSize;

        /* Set thread ID */
        tid = nextThreadID();// 线程的id
    }
```

### java线程的状态
线程在创建，执行到销毁对应不同的线程状态，下面将介绍线程的每个状态以及如何进入改状态的。

- NEW 初始化状态：线程创建但未启动的时候为该状态。
- RUNNABLE 运行状态：一个线程在jvm正常执行过程中为该状态，包括正在等待处理器资源，在所有状态切换都需要先进入这个状态。
- BLOCKED 阻塞状态：当线程正在等待monitor lock的时候会进入此状态，或者wait()后重新等待monitor lock。
- WAITING 等待状态：当线程调用了Object.wait(),Thread.join(),LockSupport.park（包括所有AQS)等待的时候为该状态。
- TIMED_WAITING 等待超时：当线程调用了Object.wait(time),Thread.join(time),LockSupport.park(time)，Thread.sleep(time)会等待时间的等待。
- TERMINATED 终止：线程完成执行。

举个例子：
```
    @Test
    public void threadState() throws Exception {
        // 通过引擎启动来演示线程的状态转换
        class Engine {
            synchronized void start() {
                try {
                    TestUtil.sleep(500);
                    wait(1000);
                    TestUtil.sleep(2000);
                } catch (Exception e) {}
            }
        }
        Engine engine = new Engine();
        Runnable runnable = engine::start;
        // 创建演示目标线程
        Thread thread = new Thread(runnable);
        thread.setName("targetThread");
        // 启动干扰线程，触发同步阻塞
        new Thread(runnable).start();
        // 获取线程状态
        new Thread(() -> {
            Thread.State oldState = null;
            while (true) {
                Thread.State state = thread.getState();
                if (oldState != state) {
                    System.err.println(thread.getName() + ":" + state);
                    oldState = state;
                }
            }
        }).start();
        thread.start();
        System.in.read();
    }
    
// 打印结果
targetThread:NEW
targetThread:RUNNABLE
targetThread:BLOCKED
targetThread:RUNNABLE
targetThread:TIMED_WAITING
targetThread:RUNNABLE
targetThread:TIMED_WAITING
targetThread:BLOCKED
targetThread:TIMED_WAITING
targetThread:RUNNABLE
targetThread:TERMINATED
```

由上面例子的运行结果可以得出：当线程创建好为NEW状态，调用start后为RUNNABLE，发送同步锁竞争等待时候为BLOCKED，当进入wait(>0)/sleep时候进入TIMED_WAITING，运行结束进入TERMINATED。所有状态切换都先进入RUNNABLE。
![线程状态转换](https://github.com/mvilplss/note/blob/master/image/线程状态转换.png?raw=true)

### java线程的常用方法
- interrupt() 发起阻断请求，当线程正在处于WAITING，TIMED_WAITING状态时候，将会进入RUNNABLE状态并收到InterruptedException异常。
- isInterrupted() 单纯的判断阻断状态。
- Thread.interrupted();// 判断是否被阻断并清除阻断状态，将阻断状态设置为false。
- setUncaughtExceptionHandler() 设置线程内未捕获的异常处理器。
- join() 等待线程执行完毕

```
    public final synchronized void join(long millis)
    throws InterruptedException {
        long base = System.currentTimeMillis();
        long now = 0;
        if (millis < 0) {
            throw new IllegalArgumentException("timeout value is negative");
        }
        if (millis == 0) {
            while (isAlive()) {// 如果线程存活则继续等待。
                wait(0);
            }
        } else {
            while (isAlive()) {
                long delay = millis - now;
                if (delay <= 0) {
                    break;
                }
                wait(delay);
                now = System.currentTimeMillis() - base;
            }
        }
    }
```

- setDaemon() 设置为守护线程，线程启动前可以设置，默认随父线程（当前线程）；当所有非守护线程关闭后守护线程也会被jvm关闭。

```
public final void setDaemon(boolean on) {
        checkAccess();
        if (isAlive()) {// 必须为存活状态
            throw new IllegalThreadStateException();
        }
        daemon = on;
    }
```

- run() 执行线程中target.run方法，如果直接调用thread.run这样会失效线程的作用，变成线程直接执行。

```
    public void run() {
        if (target != null) {
            target.run();
        }
    }
```

- setPriority() 设置线程优先级，级别1-10，默认优先级随父线程（当前线程），官方不建议通过优先级来决定线程的执行顺序，因为不同平台的优先级可能不一样，也可能某些平台不支持。

### 关于线程的stackSize的研究：

```
    // -XX:+PrintFlagsFinal -XX:MaxDirectMemorySize=512
    // -XX:NativeMemoryTracking=detail -Xmx4g -Xss180k
    @Test
    public void stackSize() throws Exception {
        class Door {
            int deepNum;

            public void openDoor() {
                deepNum++;
                if (deepNum < 10000) {
                    openDoor();
                }
            }
        }
        Door door = new Door();
        Thread thread = new Thread(null, () -> {
            log.info("running...");
            door.openDoor();
        }, "xxxxxxx", 1024 * 512);

        thread.start();
        Field stackSizeField = Thread.class.getDeclaredField("stackSize");
        stackSizeField.setAccessible(true);
        Object o = stackSizeField.get(thread);
        log.info("stackSize:" + s(o));
        thread.join();
        log.error("exp:{}", door.deepNum);
    }
```

经过实验，发现栈的深度并不是随着stackSize增加而线性增加，而是当stackSize大于某个些值时候才会增加栈的深度。确定的是stackSize越大则栈的深度越深。

### Java虚拟机栈（Java virtual machine stack）
关于jvm的运行时数据区中和线程紧密相关的就是Java虚拟机栈，每一条Java线程都有一个私有的虚拟机栈，这个栈与线程同时创建，用于存储栈帧。栈帧是用来存储数据和部分过程结果的数据结构，随着方法的调用而创建，随着方法的结束而销毁。
- 当线程请求分配的栈容量超过Java虚拟机允许的最大容量，Java虚拟机会抛出一个StackOverFlowError异常。
- 当创建线程过多，新的线程无法申请到足够的内存的时候，Java虚拟机会抛出一个OutOfMemoryError异常。
![线程栈帧结构](https://github.com/mvilplss/note/blob/master/image/线程栈帧结构.png?raw=true)

### 线程交替打印1，2，3 实现方式
#### 使用wait和notifyAll实现
通过同步方法对线程数取模来交替的唤醒和阻塞，设置初始值为0，第一个线程取模为0则进行打印后递增，唤醒其他线程同时阻塞自己；当另外两个线程被唤醒后启动并检测取模后的值为真则打印，递增，唤醒所有，阻塞，以此类推。

```
@Test
    public void printOrderThread1() throws Exception {
        AtomicInteger atomicInteger = new AtomicInteger(0);
        new Thread(new Runnable() {
            @Override
            public void run() {
                synchronized (atomicInteger) {
                    try {
                        while (true) {
                            if (atomicInteger.get() % 3 == 0) {
                                log.info(s(0));
                                atomicInteger.incrementAndGet();
                                atomicInteger.notifyAll();
                            } else {
                                atomicInteger.wait();
                            }
                        }
                    } catch (Exception e) {
                    }
                }
            }
        }).start();
        new Thread(new Runnable() {
            @Override
            public void run() {
                synchronized (atomicInteger) {
                    try {
                        while (true) {
                            if (atomicInteger.get() % 3 == 1) {
                                log.info(s(1));
                                atomicInteger.incrementAndGet();
                                atomicInteger.notifyAll();
                            } else {
                                atomicInteger.wait();
                            }
                        }
                    } catch (Exception e) {
                    }
                }
            }
        }).start();
        new Thread(new Runnable() {
            @Override
            public void run() {
                synchronized (atomicInteger) {
                    try {
                        while (true) {
                            if (atomicInteger.get() % 3 == 2) {
                                log.info(s(2));
                                atomicInteger.incrementAndGet();
                                atomicInteger.notifyAll();
                            } else {
                                atomicInteger.wait();
                            }
                        }
                    } catch (Exception e) {
                    }
                }
            }
        }).start();

        System.in.read();
    }
```

#### 借助阻塞队列的阻塞机制实现
通过阻塞队列就比较清晰，声明三个队列，初始化第一个队列增加一个元素，当线程启动后获取当前队列的元素，如果没有则阻塞，否则打印然后放入第二个队列中一个元素，第二个线程获取元素并打印后放入第三个队列中一个元素，第三线程同样获取元素并打印，然后放入到第一个队列元素，以此类推。

```
    @Test
    public void printOrderThread2() throws Exception {
        BlockingQueue q1 = new ArrayBlockingQueue(1);
        BlockingQueue q2 = new ArrayBlockingQueue(1);
        BlockingQueue q3 = new ArrayBlockingQueue(1);
        q1.put(new Object());
        new Thread(() -> {
            try {
                while (q1.take() != null) {
                    log.info(s(0));
                    q2.put(new Object());
                }
            } catch (Exception e) {
            }
        }).start();
        new Thread(() -> {
            try {
                while (q2.take() != null) {
                    log.info(s(1));
                    q3.put(new Object());
                }
            } catch (Exception e) {
            }
        }).start();
        new Thread(() -> {
            try {
                while (q3.take() != null) {
                    log.info(s(2));
                    q1.put(new Object());
                }
            } catch (Exception e) {
            }
        }).start();

        System.in.read();
    }
```

### 参考资料
- 《Java核心技术I》
- 《Java虚拟机规范java SE 8》
-  Java虚拟机规范：https://docs.oracle.com/javase/specs/jvms/se8/jvms8.pdf

### 相关源码

> 相关源码：https://github.com/mvilplss/note
