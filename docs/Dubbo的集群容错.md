---
title: Dubbo的集群容错
date: 2020-12-09
categories:
- 开发技术
  tags:
- java
- dubbo
copyright: true
cover: https://raw.githubusercontent.com/mvilplss/note/master/image/dubbo1.png
---
# 简介
为了避免单点故障，现在的应用通常至少会部署在两台服务器上。对于一些负载比较高的服务，会部署更多的服务器。这样，在同一环境下的服务提供者数量会大于1。对于服务消费者来说，同一环境下出现了多个服务提供者。这时会出现一个问题，服务消费者需要决定选择哪个服务提供者进行调用。另外服务调用失败时的处理措施也是需要考虑的，是重试呢，还是抛出异常，亦或是只打印异常等。为了处理这些问题，Dubbo 定义了集群接口 Cluster 以及 Cluster Invoker。集群 Cluster 用途是将多个服务提供者合并为一个 Cluster Invoker，并将这个 Invoker 暴露给服务消费者。这样一来，服务消费者只需通过这个 Invoker 进行远程调用即可，至于具体调用哪个服务提供者，以及调用失败后如何处理等问题，现在都交给集群模块去处理。集群模块是服务提供者和服务消费者的中间层，为服务消费者屏蔽了服务提供者的情况，这样服务消费者就可以专心处理远程调用相关事宜。比如发请求，接受服务提供者返回的数据等。这就是集群的作用。

Dubbo 提供了多种集群实现，包含但不限于 Failover Cluster、Failfast Cluster 和 Failsafe Cluster 等。每种集群实现类的用途不同，接下来会一一进行分析。

# 集群容错
在对集群相关代码进行分析之前，这里有必要先来介绍一下集群容错的所有组件。包含 Cluster、Cluster Invoker、Directory、Router 和 LoadBalance 等。
![](https://raw.githubusercontent.com/mvilplss/note/master/image/Dubbo的集群容错_images/f0369658.png)
集群工作过程可分为两个阶段，第一个阶段是在服务消费者初始化期间，集群 Cluster 实现类为服务消费者创建 Cluster Invoker 实例，即上图中的 merge 操作。第二个阶段是在服务消费者进行远程调用时。以 FailoverClusterInvoker 为例，该类型 Cluster Invoker 首先会调用 Directory 的 list 方法列举 Invoker 列表（可将 Invoker 简单理解为服务提供者）。Directory 的用途是保存 Invoker，可简单类比为 List<Invoker>。其实现类 RegistryDirectory 是一个动态服务目录，可感知注册中心配置的变化，它所持有的 Invoker 列表会随着注册中心内容的变化而变化。每次变化后，RegistryDirectory 会动态增删 Invoker，并调用 Router 的 route 方法进行路由，过滤掉不符合路由规则的 Invoker。当 FailoverClusterInvoker 拿到 Directory 返回的 Invoker 列表后，它会通过 LoadBalance 从 Invoker 列表中选择一个 Invoker。最后 FailoverClusterInvoker 会将参数传给 LoadBalance 选择出的 Invoker 实例的 invoke 方法，进行真正的远程调用。

以上就是集群工作的整个流程，这里并没介绍集群是如何容错的。Dubbo 主要提供了这样几种容错方式：
- Failover Cluster - 失败自动切换重试（默认）
- Failfast Cluster - 快速失败
- Failsafe Cluster - 安全失败
- Failback Cluster - 失败自动恢复
- Forking Cluster - 并行调用多个服务提供者

# 源码分析
## Cluster实现类分析
集群接口 Cluster 和 Cluster Invoker，这两者是不同的。Cluster 是接口，而 Cluster Invoker 是一种 Invoker。服务提供者的选择逻辑，以及远程调用失败后的的处理逻辑均是封装在 
Cluster Invoker 中。那么 Cluster 接口和相关实现类有什么用呢？用途比较简单，仅用于生成 Cluster Invoker。下面我们来看一下源码。
```java
public class FailoverCluster implements Cluster {
    public final static String NAME = "failover";
    @Override
    public <T> Invoker<T> join(Directory<T> directory) throws RpcException {
        // 创建并返回 FailoverClusterInvoker 对象
        return new FailoverClusterInvoker<T>(directory);
    }
}
```
如上，FailoverCluster 总共就包含这几行代码，用于创建 FailoverClusterInvoker 对象，很简单。下面再看一个。

```java
public class FailbackCluster implements Cluster {
    public final static String NAME = "failback";
    @Override
    public <T> Invoker<T> join(Directory<T> directory) throws RpcException {
        // 创建并返回 FailbackClusterInvoker 对象
        return new FailbackClusterInvoker<T>(directory);
    }
}
```
如上，FailbackCluster 的逻辑也是很简单，无需解释了。所以接下来，我们把重点放在各种 Cluster Invoker 上

## ClusterInvoker分析
当服务消费者进行远程调用时，在执行具体的cluster invoker时，其父类的 
AbstractClusterInvoker 的invoke方法总会被调用，这种设计思想在实际项目中也常见，即在在执行子类具体的实现方法前先执行一些共同的逻辑。

我们看下AbstractClusterInvoker的inovke源码：
```java
    public Result invoke(final Invocation invocation) throws RpcException {
        // 首先检测当前invoker是否被销毁
        checkWhetherDestroyed();
        // binding attachments into invocation. 绑定附加值到invocation中
        Map<String, Object> contextAttachments = RpcContext.getContext().getObjectAttachments();
        if (contextAttachments != null && contextAttachments.size() != 0) {
            ((RpcInvocation) invocation).addObjectAttachments(contextAttachments);
        }
        // 通过服务注册表中获取可调用服务地址列表
        List<Invoker<T>> invokers = directory.list(invocation);
        // 初始化负载均衡器
        LoadBalance loadbalance = initLoadBalance(invokers, invocation);
        RpcUtils.attachInvocationIdIfAsync(getUrl(), invocation);
        // 调用具体的子类实现方法
        return doInvoke(invocation, invokers, loadbalance);
    }

    protected void checkWhetherDestroyed() {
        if (destroyed.get()) {
            throw new RpcException("Rpc cluster invoker for " + getInterface() + " on consumer " + NetUtils.getLocalHost()
                    + " use dubbo version " + Version.getVersion()
                    + " is now destroyed! Can not invoke any more.");
        }
    }
```
AbstractClusterInvoker 的 invoke 方法主要是获取可用的服务列表 List<Inovker> ，然后初始化负载均衡器 LoadBalance ，最后再调用模板方法 doInvoke 进行后续操作。

## FailoverClusterInvoker
FailoverClusterInvoker 在调用失败时，会自动切换 Invoker 进行多次重试。默认配置下，Dubbo 会使用这个类作为缺省，重试三次。
```java
    public Result doInvoke(Invocation invocation, final List<Invoker<T>> invokers, LoadBalance loadbalance) throws RpcException {
        List<Invoker<T>> copyInvokers = invokers;
        checkInvokers(copyInvokers, invocation);
        String methodName = RpcUtils.getMethodName(invocation);
        int len = getUrl().getMethodParameter(methodName, RETRIES_KEY, DEFAULT_RETRIES) + 1;
        if (len <= 0) {
            len = 1;
        }
        // retry loop.
        RpcException le = null; // last exception.
        List<Invoker<T>> invoked = new ArrayList<Invoker<T>>(copyInvokers.size()); // invoked invokers.
        Set<String> providers = new HashSet<String>(len);// 记录提供者地址，目前版本是没有用到
        for (int i = 0; i < len; i++) {
            if (i > 0) {
                checkWhetherDestroyed();
                // 在进行重试前重新列举 Invoker，这样做的好处是，如果某个服务挂了，
                // 通过调用 list 可得到最新可用的 Invoker 列表
                copyInvokers = list(invocation);
                // check again
                checkInvokers(copyInvokers, invocation);
            }
            // 通过负载均衡选择 Invoker，首先在未调用过的进行选择，如果都调用过则进行选择下一个。
            Invoker<T> invoker = select(loadbalance, invocation, copyInvokers, invoked);
            // 加入被调用列表，再次重试时排除已调用的 invoker
            invoked.add(invoker);
            RpcContext.getContext().setInvokers((List) invoked);
            try {
                Result result = invoker.invoke(invocation);
                if (le != null && logger.isWarnEnabled()) {
                    logger.warn("重试日志");
                }
                return result;
            } catch (RpcException e) {
                if (e.isBiz()) { // 判断是否是业务异常，如果是则直接抛出异常，终止重试！
                    throw e;
                }
                le = e;
            } catch (Throwable e) {
                le = new RpcException(e.getMessage(), e);
            } finally {
                providers.add(invoker.getUrl().getAddress());
            }
        }
        throw new RpcException("...");
    }
```
不知道你是否发现，并不是所有的异常都进行重试，那哪些异常是不进行重试呢？我们分析下上面的这段代码:
```java
try{
    // 省略重试逻辑
}catch (RpcException e) {
  if (e.isBiz()) { // 判断是否是业务异常，如果是则直接抛出异常，终止重试！
    throw e;
  }
  le = e;
}
```
```java
public /**final**/ class RpcException extends RuntimeException {
    public static final int UNKNOWN_EXCEPTION = 0;// 未知异常
    public static final int NETWORK_EXCEPTION = 1;// 网络异常
    public static final int TIMEOUT_EXCEPTION = 2;// 超时异常
    public static final int BIZ_EXCEPTION = 3;// 业务异常
    public static final int FORBIDDEN_EXCEPTION = 4;// 禁止异常
    public static final int SERIALIZATION_EXCEPTION = 5;// 序列化异常
    public static final int NO_INVOKER_AVAILABLE_AFTER_FILTER = 6;// 过滤后无可用 invoker 异常
    public static final int LIMIT_EXCEEDED_EXCEPTION = 7;// 超过限制异常
    public static final int TIMEOUT_TERMINATE = 8;// 终止超时异常
    private int code;
    // 判断是否是业务异常
    public boolean isBiz() {
        return code == BIZ_EXCEPTION;
    }
}
```
通过上面我们可以清晰的知道，只有非业务异常的时候才会进行自动重试。那么现在思考一个问题：`如果服务端抛出一个非业务异常的RpcException，那么消费端会进行自动重试吗？`。

答案：服务端任何异常都不会触发消费端进行重试的，因为在上面 FailoverClusterInvoker 的 doInvoke 方法回去调用结果 result 
的时候，服务端异常已经封装对象中并不会在此方法中抛出异常，而是将带有异常信息的结果传递业务调用层来处理。

## FailbackClusterInvoker
FailbackClusterInvoker 会在调用失败后，返回一个空结果给服务消费者。并通过定时任务对失败的调用进行重试，适合执行消息通知等操作。下面来看一下它的实现逻辑。
```java
    protected Result doInvoke(Invocation invocation, List<Invoker<T>> invokers, LoadBalance loadbalance) throws RpcException {
        Invoker<T> invoker = null;
        try {
            checkInvokers(invokers, invocation);
            invoker = select(loadbalance, invocation, invokers, null);
            return invoker.invoke(invocation);
        } catch (Throwable e) {
            logger.error("Failback to invoke method " + invocation.getMethodName() + ", wait for retry in background. Ignored exception: "
                    + e.getMessage() + ", ", e);
            // 将所有失败，包括业务失败都到重试计时任务中
            addFailed(loadbalance, invocation, invokers, invoker);
            // 立即返回一个空结果给服务消费者
            return AsyncRpcResult.newDefaultAsyncResult(null, null, invocation); // ignore
        }
    }
    
    // 添加重试计时任务
    private void addFailed(LoadBalance loadbalance, Invocation invocation, List<Invoker<T>> invokers, Invoker<T> lastInvoker) {
        if (failTimer == null) {
            synchronized (this) {
                if (failTimer == null) {
                    // 创建一个 hash轮计时器，学习资料：https://www.cnblogs.com/eryuan/p/7955677.html
                    // http://www.cse.wustl.edu/~cdgill/courses/cs6874/TimingWheels.ppt
                    failTimer = new HashedWheelTimer(
                            new NamedThreadFactory("failback-cluster-timer", true),
                            1,
                            TimeUnit.SECONDS, 32, failbackTasks);
                }
            }
        }
        // 创建一个重试计时任务
        RetryTimerTask retryTimerTask = new RetryTimerTask(loadbalance, invocation, invokers, lastInvoker, retries, RETRY_FAILED_PERIOD);
        try {
            // 加入计时器中
            failTimer.newTimeout(retryTimerTask, RETRY_FAILED_PERIOD, TimeUnit.SECONDS);
        } catch (Throwable e) {
            logger.error("Failback background works error,invocation->" + invocation + ", exception: " + e.getMessage());
        }
    }
    
    private class RetryTimerTask implements TimerTask {
      // ...
      @Override
      public void run(Timeout timeout) {
        try {
          // 重试的时候，根据均衡器选一个下一个 invoker  
          Invoker<T> retryInvoker = select(loadbalance, invocation, invokers, Collections.singletonList(lastInvoker));
          lastInvoker = retryInvoker;
          retryInvoker.invoke(invocation);
        } catch (Throwable e) {
          logger.error("Failed retry to invoke method " + invocation.getMethodName() + ", waiting again.", e);
          if ((++retryTimes) >= retries) {
            logger.error("Failed retry times exceed threshold (" + retries + "), We have to abandon, invocation->" + invocation);
          } else {
            // 如果定时重试失败，则不超过重试次数前会再次放入重试任务中  
            rePut(timeout);
          }
        }
      }
      private void rePut(Timeout timeout) {
        if (timeout == null) {
          return;
        }
        Timer timer = timeout.timer();
        if (timer.isStop() || timeout.isCancelled()) {
          return;
        }
        timer.newTimeout(timeout.task(), tick, TimeUnit.SECONDS);
      }
    }
```
以上就是调用失败后，加入hash时间轮计时器，每间隔n秒重试一次，重试m次未成功则停止，n和m都可以配置。

## FailfastClusterInvoker
FailfastClusterInvoker 只会进行一次调用，失败后立即抛出异常。适用于幂等操作，比如新增记录。源码如下：
```java
    public Result doInvoke(Invocation invocation, List<Invoker<T>> invokers, LoadBalance loadbalance) throws RpcException {
        checkInvokers(invokers, invocation);
        // 选择 Invoker
        Invoker<T> invoker = select(loadbalance, invocation, invokers, null);
        try {
            // 调用 Invoker
            return invoker.invoke(invocation);
        } catch (Throwable e) {
            if (e instanceof RpcException && ((RpcException) e).isBiz()) { // biz exception.
                throw (RpcException) e;// 业务异常抛出原异常
            }
            // 其他异常抛出解析过的异常
            throw new RpcException(e instanceof RpcException ? ((RpcException) e).getCode() : 0,
                  "Failfast invoke providers " + invoker.getUrl() + " " + loadbalance.getClass().getSimpleName()
                  + " select from all providers " + invokers + " for service " + getInterface().getName()
                  + " method " + invocation.getMethodName() + " on consumer " + NetUtils.getLocalHost()
                  + " use dubbo version " + Version.getVersion()
                  + ", but no luck to perform the invocation. Last error is: " + e.getMessage(),
                  e.getCause() != null ? e.getCause() : e);
            }
    }
```
快速失败的代码比较简单，就是出现任何调用异常立即抛出异常。

## FailsafeClusterInvoker
FailsafeClusterInvoker 是一种失败安全的 Cluster Invoker。所谓的失败安全是指，当调用过程中出现异常时，FailsafeClusterInvoker 
仅会打印异常，而不会抛出异常。适用于写入审计日志等操作。下面分析源码。
```java
    public Result doInvoke(Invocation invocation, List<Invoker<T>> invokers, LoadBalance loadbalance) throws RpcException {
        try {
            checkInvokers(invokers, invocation);
            // 选择 invoker
            Invoker<T> invoker = select(loadbalance, invocation, invokers, null);
            return invoker.invoke(invocation);
        } catch (Throwable e) {
            // 打印调用异常
            logger.error("Failsafe ignore exception: " + e.getMessage(), e);
            // 异常抛出空结果
            return AsyncRpcResult.newDefaultAsyncResult(null, null, invocation); // ignore
        }
    }
```
## ForkingClusterInvoker
ForkingClusterInvoker 会在运行时通过线程池创建多个线程，并发调用多个服务提供者。只要有一个服务提供者成功返回了结果，doInvoke 方法就会立即结束运行。ForkingClusterInvoker 
的应用场景是在一些对实时性要求比较高读操作（注意是读操作，并行写操作可能不安全）下使用，但这将会耗费更多的资源。下面来看该类的实现。
```java
public class ForkingClusterInvoker<T> extends AbstractClusterInvoker<T> {
    // 缓存线程池
    private final ExecutorService executor = Executors.newCachedThreadPool( new NamedInternalThreadFactory
            ("forking-cluster-timer", true));

    @Override
    public Result doInvoke(final Invocation invocation, List<Invoker<T>> invokers, LoadBalance loadbalance) throws RpcException {
        try {
            checkInvokers(invokers, invocation);
            final List<Invoker<T>> selected;
            // 获取 fork 的个数
            final int forks = getUrl().getParameter(FORKS_KEY, DEFAULT_FORKS);
            final int timeout = getUrl().getParameter(TIMEOUT_KEY, DEFAULT_TIMEOUT);
          // 如果 forks 配置不合理，则直接将 invokers 赋值给 selected
          if (forks <= 0 || forks >= invokers.size()) {
                selected = invokers;
            } else {
                selected = new ArrayList<>(forks);
                while (selected.size() < forks) {
                    // 根据均衡器和 selected 集合选择一个 invoker
                    Invoker<T> invoker = select(loadbalance, invocation, invokers, selected);
                    if (!selected.contains(invoker)) {
                        // 记录调用过的 invoker
                        selected.add(invoker);
                    }
                }
            }
            // 设置调用集合到上下文
            RpcContext.getContext().setInvokers((List) selected);
            final AtomicInteger count = new AtomicInteger();
            // 创建一个阻塞队列
            final BlockingQueue<Object> ref = new LinkedBlockingQueue<>();
            for (final Invoker<T> invoker : selected) {
                // 循环提交 invoker 到线程池中
                executor.execute(() -> {
                    try {
                        // 调用 invoker
                        Result result = invoker.invoke(invocation);
                        // 将返回结果放入阻塞队列中
                        ref.offer(result);
                    } catch (Throwable e) {
                        int value = count.incrementAndGet();
                        // 如果全部失败，则将异常放入阻塞队列
                        if (value >= selected.size()) {
                            ref.offer(e);
                        }
                    }
                });
            }
            try {
                // 等待第一个结果
                Object ret = ref.poll(timeout, TimeUnit.MILLISECONDS);
                if (ret instanceof Throwable) {
                    Throwable e = (Throwable) ret;
                    throw new RpcException(e instanceof RpcException ? ((RpcException) e).getCode() : 0, "Failed to forking invoke provider " + selected + ", but no luck to perform the invocation. Last error is: " + e.getMessage(), e.getCause() != null ? e.getCause() : e);
                }
                return (Result) ret;
            } catch (InterruptedException e) {
                throw new RpcException("Failed to forking invoke provider " + selected + ", but no luck to perform the invocation. Last error is: " + e.getMessage(), e);
            }
        } finally {
            RpcContext.getContext().clearAttachments();
        }
    }
}
```
ForkingClusterInvoker 代码也不算复杂，其原理是循环forks次数，每次通过负载均衡器和已筛选列表筛选出一个 invoker 放入 selected 中，然后再循环筛选出来的 selected 
逐个放入到线程池，进行执行。在线程池中的任务执行完毕后放入阻塞队列，在线程池外通过阻塞队列等待第一个结果返回。
> 此集群不太常用，否则还有很多优化的空间，比如所有线程共享一个线程池，阻塞队列可以独享。

## BroadcastClusterInvoker
我们再来看一下 BroadcastClusterInvoker。BroadcastClusterInvoker 会逐个调用每个服务提供者，如果其中一台报错，在循环调用结束后，BroadcastClusterInvoker 
会抛出异常。该类通常用于通知所有提供者更新缓存或日志等本地资源信息。源码如下。
```java
    public Result doInvoke(final Invocation invocation, List<Invoker<T>> invokers, LoadBalance loadbalance) throws RpcException {
        checkInvokers(invokers, invocation);
        RpcContext.getContext().setInvokers((List) invokers);
        RpcException exception = null;
        Result result = null;
        // 循环调用每个服务
        for (Invoker<T> invoker : invokers) {
            try {
                result = invoker.invoke(invocation);
            } catch (RpcException e) {
                exception = e;
                logger.warn(e.getMessage(), e);
            } catch (Throwable e) {
                exception = new RpcException(e.getMessage(), e);
                logger.warn(e.getMessage(), e);
            }
        }
        // 如果有异常则在最后抛出
        if (exception != null) {
            throw exception;
        }
        return result;
    }
```
# 总结
本文介绍了一些常见的集群容错策略，集群容错对于 Dubbo 框架来说，是很重要的逻辑。集群模块处于服务提供者和消费者之间，对于服务消费者来说，集群可向其屏蔽服务提供者集群的情况，使其能够专心进行远程调用。除此之外，
通过集群模块，我们还可以对服务之间的调用链路进行编排优化，治理服务。总的来说，对于 Dubbo 而言，集群容错相关逻辑是非常重要的。想要对 Dubbo 有比较深的理解，集群容错是必须要掌握的。

# 参考文献
- http://dubbo.apache.org/zh/docs/v2.7/dev/source/cluster/