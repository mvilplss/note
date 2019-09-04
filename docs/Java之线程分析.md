---
title: Java之线程分析
date: 2017-01-04
categories: 
- 开发技术
tags: 
- java
copyright: true
---

# java线程分析
## java线程
java线程是jvm程序中执行的线程。 Java虚拟机允许应用程序同时运行多个执行线程。每个线程都有优先权。具有较高优先级的线程优先于具有较低优先级的线程执行。当在某个线程中运行的代码创建一个新的Thread对象时，新线程的优先级最初设置为等于创建线程的优先级(默认5，1-10级别)，并且当且仅当创建线程是守护进程时才是守护进程线程。
## java线程的创建
参数简介：
- ThreadGroup g 线程组，线程需要加到线程组中，方便线程的维护，线程组默认是当前线程的线程组。
- Runnable target 可执行目标，指线程开启后要执行的任务。
- String name 线程的名称
- long stackSize 线程栈期望的大小，默认0 忽略大小。
- AccessControlContext acc 线程的上下文 默认null
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
        tid = nextThreadID();
    }
```
## java线程的状态
线程在创建，执行到销毁对应不同的线程状态，下面将介绍线程的每个状态以及如何进入改状态的。

- NEW 初始化状态：线程创建但未启动的时候为该状态。
- RUNNABLE 运行状态：一个线程在jvm正常执行过程中为该状态，包括正在等待处理器资源。
- BLOCKED 阻塞状态：当线程正在等待monitor lock的时候会进入此状态，或者wait()后重新等待monitor lock。
- WAITING 等待状态：当线程调用了Object.wait(),Thread.join(),LockSupport.park（包括所有AQS)等待的时候为该状态。
- TIMED_WAITING 等待超时：当线程调用了Object.wait(time),Thread.join(time),LockSupport.park(time)，Thread.sleep(time)会等待时间的等待。
- TERMINATED 终止：线程完成执行。

[线程状态转换图]
## java线程的常用方法
- interrupt() 阻断，当线程正在处于WAITING，TIMED_WAITING状态时候，将会进入RUNNABLE状态并收到InterruptedException异常。
- isInterrupted() 判断是否在阻塞。
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
- setDaemon() 设置为守护线程，线程启动前可以设置。
```
public final void setDaemon(boolean on) {
        checkAccess();
        if (isAlive()) {// 必须为存活状态
            throw new IllegalThreadStateException();
        }
        daemon = on;
    }
```
- run() 执行线程中target.run方法，这样会失效线程的作用，变成线程直接执行。
```
    public void run() {
        if (target != null) {
            target.run();
        }
    }
```
- setPriority() 设置线程优先级，级别1-10。

## 一个事例
```
Gan gan = new Gan();
new Thread(new Runnable() {
    @Override
    public void run() {
        gan.doit();
    }
}).start();
new Thread(new Runnable() {
    @Override
    public void run() {
        gan.doit();
    }
}).start();
public synchronized void  doit( ){
            try {
                log.info("等待中"+Thread.currentThread().getName());
                this.wait(10000);
                Thread.sleep(10000);
                log.info("干完"+Thread.currentThread().getName());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
```
- 两个线程都会进入wait状态中，因为当第一个线程获取锁进入同步方法中，执行wait(10000)时候会释放锁，这样第二个线程就会进入到wait(10000)中。两个线程都进入TIMED_WAITING状态。
- 当10s后，wait执行完毕，将会重新获取同步锁，获取到同步锁的线程金融sleep()中，也就是TIMED_WAITING状态，由于sleep不会释放锁的，因此第二个线程阻塞在等待锁处于BLOCKED状态。
- 当第一个线程sleep完毕后释放锁，第二个线程获取锁后进入sleep中。
执行结果：
等待中thread1
等待中thread2
干完thread1
干完thread2

# java线程的底层工作原理