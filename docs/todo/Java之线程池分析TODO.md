---
title: Java之线程池分析
date: 2018-01-15
categories: 
- 开发技术
tags: 
- java
copyright: true
---

## java线程池ThreadPoolExecutor原理
### 线程池构造器的每个参数含义
```
    public ThreadPoolExecutor(int corePoolSize,
                              int maximumPoolSize,
                              long keepAliveTime,
                              TimeUnit unit,
                              BlockingQueue<Runnable> workQueue,
                              ThreadFactory threadFactory,
                              RejectedExecutionHandler handler) {
        if (corePoolSize < 0 ||
            maximumPoolSize <= 0 ||
            maximumPoolSize < corePoolSize ||
            keepAliveTime < 0)
            throw new IllegalArgumentException();
        if (workQueue == null || threadFactory == null || handler == null)
            throw new NullPointerException();
        this.acc = System.getSecurityManager() == null ?
                null :
                AccessController.getContext();
        this.corePoolSize = corePoolSize;
        this.maximumPoolSize = maximumPoolSize;
        this.workQueue = workQueue;
        this.keepAliveTime = unit.toNanos(keepAliveTime);
        this.threadFactory = threadFactory;
        this.handler = handler;
    }
```
- corePoolSize 线程池的核心线程数大小，当线程数小于这个值的时候，新任务过来会创建线程并执行任务。
- maximumPoolSize 线程池的总线程数大小，当队列满的时候会判断线程数是否小于这个值，如果小于则进行创建线程并执行任务。
- keepAliveTime 当超过核心线程数创建的线程空闲时间，如果超过这个时间当前线程就会执行完毕并关闭，通过获取队列超时来实现的。
- unit 上面时间值的单位 毫秒，秒，分钟。。。
- workQueue 工作队列，当核心线程数满的时候就会把任务放进工作队列。
- threadFactory 创建线程的线程工厂，可以实现接口ThreadFactory，实现newThread方法来创建自己定义的线程，默认：Executors.defaultThreadFactory()。
- handler 拒绝处理器，当队列满时候并且达到最大线程数，新增任务会触发拒绝处理器，拒绝器会获取当前要执行的任务，默认是拒绝并抛异常。

## 任务提交到线程池执行过程

### 线程池状态：
```
    // 通过一个原子性整数的高低位来保存线程池的运行状态和工作线程数。
    private final AtomicInteger ctl = new AtomicInteger(ctlOf(RUNNING, 0));
    private static final int COUNT_BITS = Integer.SIZE - 3;
    private static final int CAPACITY   = (1 << COUNT_BITS) - 1;

    // runState is stored in the high-order bits 高位表示状态
    private static final int RUNNING    = -1 << COUNT_BITS;
    private static final int SHUTDOWN   =  0 << COUNT_BITS;
    private static final int STOP       =  1 << COUNT_BITS;
    private static final int TIDYING    =  2 << COUNT_BITS;
    private static final int TERMINATED =  3 << COUNT_BITS;

    // Packing and unpacking ctl
    // 获取状态
    private static int runStateOf(int c)     { return c & ~CAPACITY; }
    // 获取工作线程数
    private static int workerCountOf(int c)  { return c & CAPACITY; }
    // 状态和工作线程数合并成一个整数
    private static int ctlOf(int rs, int wc) { return rs | wc; }
```
- RUNNING 线程池在运行中。
- SHUTDOWN 线程池关闭状态，
新的任务不允许加入线程池，等待剩余老的任务和队列执行完毕，shutdown()会进入此状态。
- STOP 线程池处于停止状态，shutdownNow()会进入此状态。
- TIDYING 当工作线程为0的时候进入此状态。
- TERMINATED 线程池终止。

### 提交任务：
 ```
     public void execute(Runnable command) {
         if (command == null)
             throw new NullPointerException();
         /*
          * Proceed in 3 steps:
          *
          * 1. If fewer than corePoolSize threads are running, try to
          * start a new thread with the given command as its first
          * task.  The call to addWorker atomically checks runState and
          * workerCount, and so prevents false alarms that would add
          * threads when it shouldn't, by returning false.
          *
          * 2. If a task can be successfully queued, then we still need
          * to double-check whether we should have added a thread
          * (because existing ones died since last checking) or that
          * the pool shut down since entry into this method. So we
          * recheck state and if necessary roll back the enqueuing if
          * stopped, or start a new thread if there are none.
          *
          * 3. If we cannot queue task, then we try to add a new
          * thread.  If it fails, we know we are shut down or saturated
          * and so reject the task.
          */
          
          // 如果工作线程数小于核心线程则直接增加工作线程和任务执行。
         int c = ctl.get();
         if (workerCountOf(c) < corePoolSize) {
             if (addWorker(command, true))
                 return;
             c = ctl.get();
         }
         // 如果超过核心数，则任务放入队列，然后再次检测线程池是否在执行，如果不在执行则移除当前任务并拒绝任务。否则判断工作线程是否为0，如果为0则通过增加一个空的任务增加一个线程。
         if (isRunning(c) && workQueue.offer(command)) {
             int recheck = ctl.get();
             if (! isRunning(recheck) && remove(command))
                 reject(command);
             else if (workerCountOf(recheck) == 0)
                 addWorker(null, false);
         }
         // 如果任务满了则增加非核心线程和任务，如果失败则拒绝任务。
         else if (!addWorker(command, false))
             reject(command);
     }
 ```
 
### 增加工作线程和任务：
 ```
     // firstTask表示当前线程的第一个任务，如果任务为空，则只增加一个线程。
     // core 表示是否是核心线程，主要是判断工作线程和核心线程或最大线程数做对比。
     private boolean addWorker(Runnable firstTask, boolean core) {
         retry:
         for (;;) {
             int c = ctl.get();
             int rs = runStateOf(c);
 
             // Check if queue empty only if necessary.
             if (rs >= SHUTDOWN &&
                 ! (rs == SHUTDOWN &&
                    firstTask == null &&
                    ! workQueue.isEmpty()))
                 return false;
 
             for (;;) {
                 int wc = workerCountOf(c);
                 if (wc >= CAPACITY ||
                     wc >= (core ? corePoolSize : maximumPoolSize))
                     return false;
                 if (compareAndIncrementWorkerCount(c))
                     break retry;
                 c = ctl.get();  // Re-read ctl
                 if (runStateOf(c) != rs)
                     continue retry;
                 // else CAS failed due to workerCount change; retry inner loop
             }
         }
 
         boolean workerStarted = false;
         boolean workerAdded = false;
         Worker w = null;
         try {
             w = new Worker(firstTask);
             final Thread t = w.thread;
             if (t != null) {
                 final ReentrantLock mainLock = this.mainLock;
                 mainLock.lock();
                 try {
                     // Recheck while holding lock.
                     // Back out on ThreadFactory failure or if
                     // shut down before lock acquired.
                     int rs = runStateOf(ctl.get());
                     if (rs < SHUTDOWN ||
                         (rs == SHUTDOWN && firstTask == null)) {
                         if (t.isAlive()) // precheck that t is startable
                             throw new IllegalThreadStateException();
                         workers.add(w);
                         int s = workers.size();
                         if (s > largestPoolSize)
                             largestPoolSize = s;// 出现过最大线程数。
                         workerAdded = true;
                     }
                 } finally {
                     mainLock.unlock();
                 }
                 if (workerAdded) {
                     t.start();
                     workerStarted = true;
                 }
             }
         } finally {
             if (! workerStarted)
                 addWorkerFailed(w);
         }
         return workerStarted;
     }

 ```
 
### 工作线程内部类：
 ```
private final class Worker
        extends AbstractQueuedSynchronizer
        implements Runnable
    {
        private static final long serialVersionUID = 6138294804551838833L;
        /** Thread this worker is running in.  Null if factory fails. */
        final Thread thread;
        /** Initial task to run.  Possibly null. */
        Runnable firstTask;
        /** Per-thread task counter */
        volatile long completedTasks;
        // 创建新的线程，并把this赋给新的线程
        Worker(Runnable firstTask) {
            setState(-1); // inhibit interrupts until runWorker
            this.firstTask = firstTask;
            this.thread = getThreadFactory().newThread(this);
        }
        // 当addWorker成功后，调用worker.t.start()则执行此方法（线程会调用Runnable的run方法）。
        public void run() {
            runWorker(this);
        }
        // Lock methods
        //
        // The value 0 represents the unlocked state.
        // The value 1 represents the locked state.

        protected boolean isHeldExclusively() {
            return getState() != 0;
        }

        protected boolean tryAcquire(int unused) {
            if (compareAndSetState(0, 1)) {
                setExclusiveOwnerThread(Thread.currentThread());
                return true;
            }
            return false;
        }

        protected boolean tryRelease(int unused) {
            setExclusiveOwnerThread(null);
            setState(0);
            return true;
        }

        public void lock()        { acquire(1); }
        public boolean tryLock()  { return tryAcquire(1); }
        public void unlock()      { release(1); }
        public boolean isLocked() { return isHeldExclusively(); }

        void interruptIfStarted() {
            Thread t;
            if (getState() >= 0 && (t = thread) != null && !t.isInterrupted()) {
                try {
                    t.interrupt();
                } catch (SecurityException ignore) {
                }
            }
        }
    }
 ```
 
### 执行任务：
 ```
     final void runWorker(Worker w) {
         Thread wt = Thread.currentThread();
         Runnable task = w.firstTask;
         w.firstTask = null;
         w.unlock(); // allow interrupts
         boolean completedAbruptly = true;
         try {
             // 循环执行当前任务或获取任务并执行任务。
             while (task != null || (task = getTask()) != null) {
                 w.lock();
                 // If pool is stopping, ensure thread is interrupted;
                 // if not, ensure thread is not interrupted.  This
                 // requires a recheck in second case to deal with
                 // shutdownNow race while clearing interrupt
                 if ((runStateAtLeast(ctl.get(), STOP) ||
                      (Thread.interrupted() &&
                       runStateAtLeast(ctl.get(), STOP))) &&
                     !wt.isInterrupted())
                     wt.interrupt();
                 try {
                     beforeExecute(wt, task);// 用来重写实现自己的执行任务前方法
                     Throwable thrown = null;
                     try {
                         task.run();
                     } catch (RuntimeException x) {
                         thrown = x; throw x;
                     } catch (Error x) {
                         thrown = x; throw x;
                     } catch (Throwable x) {
                         thrown = x; throw new Error(x);
                     } finally {
                         afterExecute(task, thrown);// 用来重写实现自己的执行任务后执行方法
                     }
                 } finally {
                     task = null;
                     w.completedTasks++;
                     w.unlock();
                 }
             }
             // 正常结束
             completedAbruptly = false;
         } finally {
             // 处理工作退出操作
             processWorkerExit(w, completedAbruptly);
         }
     }
 ```
### 清理工作线程和变更线程计数，异常结束的线程则补充一个。
```
    private void processWorkerExit(Worker w, boolean completedAbruptly) {
        // 异常结束则减少工作线程，否则线程数目不变。
        if (completedAbruptly) // If abrupt, then workerCount wasn't adjusted
            decrementWorkerCount();

        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            completedTaskCount += w.completedTasks;// 计算完成任务数
            workers.remove(w);// 移除任务队列
        } finally {
            mainLock.unlock();
        }

        tryTerminate();// 有工作线程退出，怀疑是线程池关闭，因此尝试终止线程池。

        int c = ctl.get();
        if (runStateLessThan(c, STOP)) {// 如果还没有到STOP状态
            if (!completedAbruptly) {// 正常结束的线程
                int min = allowCoreThreadTimeOut ? 0 : corePoolSize;
                if (min == 0 && ! workQueue.isEmpty())
                    min = 1;
                if (workerCountOf(c) >= min)
                    return; // replacement not needed
            }
            // 非正常结束或者工作队列不空并且线程数为0则增加一个线程。
            addWorker(null, false);
        }
    }

```
 
### 获取任务：
```
    private Runnable getTask() {
        boolean timedOut = false; // Did the last poll() time out?

        for (;;) {
            int c = ctl.get();
            int rs = runStateOf(c);

            // Check if queue empty only if necessary.
            if (rs >= SHUTDOWN && (rs >= STOP || workQueue.isEmpty())) {
                decrementWorkerCount();
                return null;
            }

            int wc = workerCountOf(c);

            // Are workers subject to culling? 根据工作线程数判断是否需要杀死线程
            boolean timed = allowCoreThreadTimeOut || wc > corePoolSize;

            if ((wc > maximumPoolSize || (timed && timedOut))
                && (wc > 1 || workQueue.isEmpty())) {
                if (compareAndDecrementWorkerCount(c))
                    return null;
                continue;
            }

            try {
            // 此处就是根据timed判断来决定线程是否需要超时或者阻塞。
                Runnable r = timed ?
                    workQueue.poll(keepAliveTime, TimeUnit.NANOSECONDS) :
                    workQueue.take();
                if (r != null)
                    return r;
                timedOut = true;
            } catch (InterruptedException retry) {
                timedOut = false;
            }
        }
    }

```
### 关闭线程池
shutdown:启动有序关闭，其中先前提交的任务将被执行，但不会接受任何新任务。
```
    public void shutdown() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            checkShutdownAccess();
            advanceRunState(SHUTDOWN);// 设为关闭状态
            interruptIdleWorkers();// 中断空闲的工作线程
            onShutdown(); // hook for ScheduledThreadPoolExecutor
        } finally {
            mainLock.unlock();
        }
        tryTerminate(); // 尝试终止线程池
    }
```

showdownNow:尝试停止所有正在执行的任务(调用线程的interrupt，不保证一定停止)，停止等待任务的处理，并返回等待执行的任务列表，停止接受任何新的任务。
```
    public List<Runnable> shutdownNow() {
        List<Runnable> tasks;
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            checkShutdownAccess();
            advanceRunState(STOP);// 设置为停止状态
            interruptWorkers();// 中断所有工作线程
            tasks = drainQueue();// 排出所有任务队列中的任务
        } finally {
            mainLock.unlock();
        }
        tryTerminate();
        return tasks;
    }
    // 排出所有任务队列中的任务
    private List<Runnable> drainQueue() {
        BlockingQueue<Runnable> q = workQueue;
        ArrayList<Runnable> taskList = new ArrayList<Runnable>();
        q.drainTo(taskList);
        if (!q.isEmpty()) {
            for (Runnable r : q.toArray(new Runnable[0])) {
                if (q.remove(r))
                    taskList.add(r);
            }
        }
        return taskList;
    }

```
### 线程池其他方法
允许核心线程超时：
```
    // allowCoreThreadTimeOut默认是false，当设置为true的时候，核心线程数也会超时被回收。
    public void allowCoreThreadTimeOut(boolean value) {
        if (value && keepAliveTime <= 0)
            throw new IllegalArgumentException("Core threads must have nonzero keep alive times");
        if (value != allowCoreThreadTimeOut) {
            allowCoreThreadTimeOut = value;
            if (value)
                interruptIdleWorkers();
        }
    }
```
准备所有核心线程数：
```
    public int prestartAllCoreThreads() {
        int n = 0;
        while (addWorker(null, true))
            ++n;
        return n;
    }
```


# Spring框架中的线程池

# Dubbo框架中的线程池

