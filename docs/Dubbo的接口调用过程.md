---
title: Dubbo的接口调用过程
date: 2020-12-03
categories:
- 开发技术
tags:
- java
- dubbo
copyright: true
cover: https://gitee.com/mvilplss/note/raw/master/image/dubbo1.png.png
---
# 简介
本篇文件我们研究dubbo服务的调用过程，即从消费端发起接口调用到服务端接收请求，然后返回到消费端结果的整个一个调用过程。

# 消费端发起调用
## invoker的调用
直接看在服务引用过程中被代理后的接口源码：
```java
public class proxy0 implements ClassGenerator.DC, Destroyable, EchoService, DemoService {
    public static Method[] methods;
    private InvocationHandler handler;
    // 调用sayHello，最终会委托给InvokerInvocationHandler
    public String sayHello(String string) {
        Object[] arrobject = new Object[]{string};
        Object object = this.handler.invoke(this, methods[0], arrobject);
        return (String)object;
    }
    public proxy0(InvocationHandler invocationHandler) {
        this.handler = invocationHandler;
    }
     //其他代码省略...
}
```
由上面源码可知，最终调用的sayHello方法会委托给InvokerInvocationHandler增强类，其源码如下：
```java
public class InvokerInvocationHandler implements InvocationHandler {
    private static final Logger logger = LoggerFactory.getLogger(InvokerInvocationHandler.class);
    private final Invoker<?> invoker;
    private ConsumerModel consumerModel;
    public InvokerInvocationHandler(Invoker<?> handler) {
        this.invoker = handler;
        String serviceKey = invoker.getUrl().getServiceKey();
        if (serviceKey != null) {
            this.consumerModel = ApplicationModel.getConsumerModel(serviceKey);
        }
    }
    @Override// 消费者接口代理类拦截方法
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return method.invoke(invoker, args);
        }
        String methodName = method.getName();
        Class<?>[] parameterTypes = method.getParameterTypes();
        // 其他代码省略...

        // RPC会话类，包括rpc调用的方法，参数，返回值等属性
        RpcInvocation rpcInvocation = new RpcInvocation(method, invoker.getInterface().getName(), args);
        // org.apache.dubbo.demo.DemoService
        String serviceKey = invoker.getUrl().getServiceKey();
        rpcInvocation.setTargetServiceUniqueName(serviceKey);
        if (consumerModel != null) {
            rpcInvocation.put(Constants.CONSUMER_MODEL, consumerModel);
            rpcInvocation.put(Constants.METHOD_MODEL, consumerModel.getMethodModel(method));
        }
        // 发起调用 MockClusterInvoker.invoke
        return invoker.invoke(rpcInvocation).recreate();
    }
}
```
继续根据调用链传给MockClusterInvoker类:
```java
public class MockClusterInvoker<T> implements ClusterInvoker<T> {
    private static final Logger logger = LoggerFactory.getLogger(MockClusterInvoker.class);
    private final Directory<T> directory;
    private final Invoker<T> invoker;
    public MockClusterInvoker(Directory<T> directory, Invoker<T> invoker) {
        this.directory = directory;
        this.invoker = invoker;
    }
    // 其他代码省略...
    @Override
    public Result invoke(Invocation invocation) throws RpcException {
        Result result = null;
        String value = getUrl().getMethodParameter(invocation.getMethodName(), MOCK_KEY, Boolean.FALSE.toString()).trim();
        if (value.length() == 0 || "false".equalsIgnoreCase(value)) {
            // 非mock调用，继续下一个请求
            result = this.invoker.invoke(invocation);
        } else if (value.startsWith("force")) {
            if (logger.isWarnEnabled()) {
                logger.warn("force-mock: " + invocation.getMethodName() + " force-mock enabled , url : " + getUrl());
            }
            //force:direct mock
            result = doMockInvoke(invocation, null);
        } else {
            //fail-mock
            try {
                result = this.invoker.invoke(invocation);
                //fix:#4585
                if(result.getException() != null && result.getException() instanceof RpcException){
                    RpcException rpcException= (RpcException)result.getException();
                    if(rpcException.isBiz()){
                        throw  rpcException;
                    }else {
                        result = doMockInvoke(invocation, rpcException);
                    }
                }

            } catch (RpcException e) {
                // 业务异常则抛出异常
                if (e.isBiz()) {
                    throw e;
                }
                if (logger.isWarnEnabled()) {
                    logger.warn("fail-mock: " + invocation.getMethodName() + " fail-mock enabled , url : " + getUrl(), e);
                }
                // 非业务异常则降级
                result = doMockInvoke(invocation, e);
            }
        }
        return result;
    }
    //其他代码省略...

}
继续下一个请求，把请求传递给AbstractCluster的内部类：InterceptorInvokerNode,进行cluster拦截器的调用前后调用：
```java
    protected class InterceptorInvokerNode<T> extends AbstractClusterInvoker<T> {
        private AbstractClusterInvoker<T> clusterInvoker;
        private ClusterInterceptor interceptor;
        private AbstractClusterInvoker<T> next;

        @Override
        public Result invoke(Invocation invocation) throws RpcException {
            Result asyncResult;
            try {
                // 拦截器before调用，默认拦截器为：ConsumerContextClusterInterceptor，主要是对消费端的RpcContext对象上下文属性设置
                interceptor.before(next, invocation);
                // 调用拦截器拦截方法，传递next(FailoverClusterInvoker实例)
                asyncResult = interceptor.intercept(next, invocation);
            } catch (Exception e) {
                // onError callback
                if (interceptor instanceof ClusterInterceptor.Listener) {
                    ClusterInterceptor.Listener listener = (ClusterInterceptor.Listener) interceptor;
                    listener.onError(e, clusterInvoker, invocation);
                }
                throw e;
            } finally {
            // 拦截器after调用
                interceptor.after(next, invocation);
            }
            return asyncResult.whenCompleteWithContext((r, t) -> {
                // onResponse callback
                if (interceptor instanceof ClusterInterceptor.Listener) {
                    ClusterInterceptor.Listener listener = (ClusterInterceptor.Listener) interceptor;
                    if (t == null) {
                        listener.onMessage(r, clusterInvoker, invocation);
                    } else {
                        listener.onError(t, clusterInvoker, invocation);
                    }
                }
            });
        }

    }
```
调用FailoverClusterInvoker.doInvoke之前，会先执行其父类的invoke方法：
父类：AbstractClusterInvoker
```java
public abstract class AbstractClusterInvoker<T> implements ClusterInvoker<T> {

    // 执行invoke
    @Override
    public Result invoke(final Invocation invocation) throws RpcException {
        checkWhetherDestroyed();// 判断是否要停机，如果要停机，则中断调用。（优雅停机）

        // binding attachments into invocation.
        Map<String, Object> contextAttachments = RpcContext.getContext().getObjectAttachments();
        if (contextAttachments != null && contextAttachments.size() != 0) {
            ((RpcInvocation) invocation).addObjectAttachments(contextAttachments);
        }
        // 从注册表获取可调用服务地址列表
        List<Invoker<T>> invokers = list(invocation);
        // 初始化负载均衡器
        LoadBalance loadbalance = initLoadBalance(invokers, invocation);
        RpcUtils.attachInvocationIdIfAsync(getUrl(), invocation);
        // 调用子类实现方法（默认是FailoverClusterInvoker的实例，可以通过<dubbo:reference cluster="" ... 指定集群）
        // 子类的具体实现请看下面源码
        return doInvoke(invocation, invokers, loadbalance);
    }

    protected void checkWhetherDestroyed() {
        if (destroyed.get()) {
            throw new RpcException("Rpc cluster invoker for " + getInterface() + " on consumer " + NetUtils.getLocalHost()
                    + " use dubbo version " + Version.getVersion()
                    + " is now destroyed! Can not invoke any more.");
        }
    }

    protected List<Invoker<T>> list(Invocation invocation) throws RpcException {
        // 最终由RegistryDirectory.doList提供服务列表，见下面的RegistryDirectory源码分析
        return directory.list(invocation);
    }
    // 根据负载均衡配置通过SPI获取均衡扩展点，默认为random
    protected LoadBalance initLoadBalance(List<Invoker<T>> invokers, Invocation invocation) {
        if (CollectionUtils.isNotEmpty(invokers)) {
            return ExtensionLoader.getExtensionLoader(LoadBalance.class).getExtension(invokers.get(0).getUrl()
                    .getMethodParameter(RpcUtils.getMethodName(invocation), LOADBALANCE_KEY, DEFAULT_LOADBALANCE));
        } else {
            return ExtensionLoader.getExtensionLoader(LoadBalance.class).getExtension(DEFAULT_LOADBALANCE);
        }
    }
    //
    protected Invoker<T> select(LoadBalance loadbalance, Invocation invocation,
                                List<Invoker<T>> invokers, List<Invoker<T>> selected) throws RpcException {

        if (CollectionUtils.isEmpty(invokers)) {
            return null;
        }
        String methodName = invocation == null ? StringUtils.EMPTY_STRING : invocation.getMethodName();

        boolean sticky = invokers.get(0).getUrl()
                .getMethodParameter(methodName, CLUSTER_STICKY_KEY, DEFAULT_CLUSTER_STICKY);

        if (stickyInvoker != null && !invokers.contains(stickyInvoker)) {
            stickyInvoker = null;
        }
        if (sticky && stickyInvoker != null && (selected == null || !selected.contains(stickyInvoker))) {
            if (availablecheck && stickyInvoker.isAvailable()) {
                return stickyInvoker;
            }
        }
        Invoker<T> invoker = doSelect(loadbalance, invocation, invokers, selected);
        if (sticky) {
            stickyInvoker = invoker;
        }
        return invoker;
    }

    private Invoker<T> doSelect(LoadBalance loadbalance, Invocation invocation,
                                List<Invoker<T>> invokers, List<Invoker<T>> selected) throws RpcException {
        if (invokers.size() == 1) {
            return invokers.get(0);
        }
        Invoker<T> invoker = loadbalance.select(invokers, getUrl(), invocation);
        //If the `invoker` is in the  `selected` or invoker is unavailable && availablecheck is true, reselect.
        if ((selected != null && selected.contains(invoker))
                || (!invoker.isAvailable() && getUrl() != null && availablecheck)) {
            try {
                // 如果选出的invker被调用过，或者不可用，则需要重新在选择一个
                Invoker<T> rInvoker = reselect(loadbalance, invocation, invokers, selected, availablecheck);
                if (rInvoker != null) {
                    invoker = rInvoker;
                } else {
                    //Check the index of current selected invoker, if it's not the last one, choose the one at index+1.
                    int index = invokers.indexOf(invoker);
                    try {
                        //Avoid collision
                        invoker = invokers.get((index + 1) % invokers.size());
                    } catch (Exception e) {
                        logger.warn(e.getMessage() + " may because invokers list dynamic change, ignore.", e);
                    }
                }
            } catch (Throwable t) {
                logger.error("cluster reselect fail reason is :" + t.getMessage() + " if can not solve, you can set cluster.availablecheck=false in url", t);
            }
        }
        return invoker;
    }
    // 重新选择
    private Invoker<T> reselect(LoadBalance loadbalance, Invocation invocation,
                                List<Invoker<T>> invokers, List<Invoker<T>> selected, boolean availablecheck) throws RpcException {

        //Allocating one in advance, this list is certain to be used.
        List<Invoker<T>> reselectInvokers = new ArrayList<>(
                invokers.size() > 1 ? (invokers.size() - 1) : invokers.size());

        // First, try picking a invoker not in `selected`.
        for (Invoker<T> invoker : invokers) {
            if (availablecheck && !invoker.isAvailable()) {
                continue;
            }

            if (selected == null || !selected.contains(invoker)) {
                reselectInvokers.add(invoker);
            }
        }

        if (!reselectInvokers.isEmpty()) {
            return loadbalance.select(reselectInvokers, getUrl(), invocation);
        }

        // Just pick an available invoker using loadbalance policy
        if (selected != null) {
            for (Invoker<T> invoker : selected) {
                if ((invoker.isAvailable()) // available first
                        && !reselectInvokers.contains(invoker)) {
                    reselectInvokers.add(invoker);
                }
            }
        }
        if (!reselectInvokers.isEmpty()) {
            return loadbalance.select(reselectInvokers, getUrl(), invocation);
        }

        return null;
    }


    protected abstract Result doInvoke(Invocation invocation, List<Invoker<T>> invokers,
                                       LoadBalance loadbalance) throws RpcException;


}
```
AbstractClusterInvoker集群抽象类有多个实现，包括以下几个：
- mock=org.apache.dubbo.rpc.cluster.support.wrapper.MockClusterWrapper
- failover=org.apache.dubbo.rpc.cluster.support.FailoverCluster
- failfast=org.apache.dubbo.rpc.cluster.support.FailfastCluster
- failsafe=org.apache.dubbo.rpc.cluster.support.FailsafeCluster
- failback=org.apache.dubbo.rpc.cluster.support.FailbackCluster
- forking=org.apache.dubbo.rpc.cluster.support.ForkingCluster
- available=org.apache.dubbo.rpc.cluster.support.AvailableCluster
- mergeable=org.apache.dubbo.rpc.cluster.support.MergeableCluster
- broadcast=org.apache.dubbo.rpc.cluster.support.BroadcastCluster
- zone-aware=org.apache.dubbo.rpc.cluster.support.registry.ZoneAwareCluster
每个具体实现会在后续的文章进行介绍，本次只简单介绍下FailoverCluster的源码：
```java
public class FailoverClusterInvoker<T> extends AbstractClusterInvoker<T> {
    private static final Logger logger = LoggerFactory.getLogger(FailoverClusterInvoker.class);
    public FailoverClusterInvoker(Directory<T> directory) {
        super(directory);
    }
    // 此集群实现逻辑是：在调用失败后，根据重试次数进行更换其他服务进行重试，一般用在只读的场景。
    @Override
    public Result doInvoke(Invocation invocation, final List<Invoker<T>> invokers, LoadBalance loadbalance) throws RpcException {
        List<Invoker<T>> copyInvokers = invokers;
        String methodName = RpcUtils.getMethodName(invocation);
        // 获取重试次数
        int len = getUrl().getMethodParameter(methodName, RETRIES_KEY, DEFAULT_RETRIES) + 1;
        if (len <= 0) {
            len = 1;
        }
        // retry loop.
        RpcException le = null; // last exception.
        List<Invoker<T>> invoked = new ArrayList<Invoker<T>>(copyInvokers.size()); // invoked invokers.
        Set<String> providers = new HashSet<String>(len);
        for (int i = 0; i < len; i++) {
            //Reselect before retry to avoid a change of candidate `invokers`.
            //NOTE: if `invokers` changed, then `invoked` also lose accuracy.
            if (i > 0) {
                // 检测是否要停机
                super.checkWhetherDestroyed();
                // 重新获取服务列表
                copyInvokers = super.list(invocation);
            }
            // 调用父类根据负载均衡，排除被调用过的服务筛选出一个invoker
            Invoker<T> invoker = super.select(loadbalance, invocation, copyInvokers, invoked);
            // 加入已调用
            invoked.add(invoker);
            // 设置当前线程到上下文
            RpcContext.getContext().setInvokers((List) invoked);
            try {
                // 到这里，我们获取到最终的invoker，其结构如下：
                // InvokerWrapper.invoke
                // > ProtocolFilterWrapper$1.invoke 进行过滤器调用链
                //  > ListenerInvokerWrapper.invoke
                //   > AsyncToSyncInvoker.invoke 默认结果是通过CompletableFuture异步获取的，转同步则通过调用get获取
                //    > DubboInvoker.super.invoke
                Result result = invoker.invoke(invocation);
                return result;
            } catch (RpcException e) {
                if (e.isBiz()) { // biz exception.
                    throw e;
                }
                le = e;
            } catch (Throwable e) {
                le = new RpcException(e.getMessage(), e);
            } finally {
                providers.add(invoker.getUrl().getAddress());
            }
        }
        throw new RpcException();
    }

}
```
稍微看下异步转同步的相关源码，这样在最后返回结果的时候不会迷茫:
```java
    public Result invoke(Invocation invocation) throws RpcException {
        // 获取到Future
        Result asyncResult = invoker.invoke(invocation);
        try {
            if (InvokeMode.SYNC == ((RpcInvocation) invocation).getInvokeMode()) {
                /**
                 * NOTICE!
                 * must call {@link java.util.concurrent.CompletableFuture#get(long, TimeUnit)} because
                 * {@link java.util.concurrent.CompletableFuture#get()} was proved to have serious performance drop.
                 */
                // AsyncRpcResult.get 
                asyncResult.get(Integer.MAX_VALUE, TimeUnit.MILLISECONDS);
            }
        }
        return asyncResult;
    }
    //AsyncRpcResult.get源码：
    @Override
    public Result get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        if (executor != null && executor instanceof ThreadlessExecutor) {
            ThreadlessExecutor threadlessExecutor = (ThreadlessExecutor) executor;
            // 等待执行结果执行完毕，然后才会调用下面的responseFuture.get
            threadlessExecutor.waitAndDrain();
        }
        return responseFuture.get(timeout, unit);
    }
    // ThreadlessExecutor.waitAndDrain源码
    public void waitAndDrain() throws InterruptedException {
        if (finished) {
            return;
        }
        Runnable runnable = queue.take();
        synchronized (lock) {
            waiting = false;
            // 等待一个结果执行完毕
            runnable.run();
        }
        runnable = queue.poll();
        while (runnable != null) {
            try {
                runnable.run();
            } catch (Throwable t) {
                logger.info(t);
            }
            runnable = queue.poll();
        }
        finished = true;
    }
```

下面我们将略过过滤器调用链，直接分析AbstractInvoker和DubboInvoker源码：
```java
public abstract class AbstractInvoker<T> implements Invoker<T> {
    private final Class<T> type;
    private final URL url;
    private final Map<String, Object> attachment;
    private volatile boolean available = true;
    private AtomicBoolean destroyed = new AtomicBoolean(false);
    // 先调用invoke方法
    @Override
    public Result invoke(Invocation inv) throws RpcException {
        // 完善RpcInvocation属性
        RpcInvocation invocation = (RpcInvocation) inv;
        invocation.setInvoker(this);
        if (CollectionUtils.isNotEmptyMap(attachment)) {
            invocation.addObjectAttachmentsIfAbsent(attachment);
        }
        Map<String, Object> contextAttachments = RpcContext.getContext().getObjectAttachments();
        if (CollectionUtils.isNotEmptyMap(contextAttachments)) {
            invocation.addObjectAttachments(contextAttachments);
        }
        invocation.setInvokeMode(RpcUtils.getInvokeMode(url, invocation));
        RpcUtils.attachInvocationIdIfAsync(getUrl(), invocation);
        AsyncRpcResult asyncResult;
        try {
            // 发起调用
            asyncResult = (AsyncRpcResult) doInvoke(invocation);
        } catch (InvocationTargetException e) { // biz exception
            Throwable te = e.getTargetException();
            if (te == null) {
                asyncResult = AsyncRpcResult.newDefaultAsyncResult(null, e, invocation);
            } else {
                if (te instanceof RpcException) {
                    ((RpcException) te).setCode(RpcException.BIZ_EXCEPTION);
                }
                asyncResult = AsyncRpcResult.newDefaultAsyncResult(null, te, invocation);
            }
        } catch (RpcException e) {
            if (e.isBiz()) {
                asyncResult = AsyncRpcResult.newDefaultAsyncResult(null, e, invocation);
            } else {
                throw e;
            }
        } catch (Throwable e) {
            asyncResult = AsyncRpcResult.newDefaultAsyncResult(null, e, invocation);
        }
        // 设置响应future到当前线程上下文
        RpcContext.getContext().setFuture(new FutureAdapter(asyncResult.getResponseFuture()));
        return asyncResult;
    }
    // 获取线程池执行器，默认是fixed，其他还有（cached，limited，eager），可以通过threadpool=fixed来定制
    protected ExecutorService getCallbackExecutor(URL url, Invocation inv) {
        ExecutorService sharedExecutor = ExtensionLoader.getExtensionLoader(ExecutorRepository.class).getDefaultExtension().getExecutor(url);
        if (InvokeMode.SYNC == RpcUtils.getInvokeMode(getUrl(), inv)) {
            return new ThreadlessExecutor(sharedExecutor);
        } else {
            return sharedExecutor;
        }
    }
    // 由子类实现
    protected abstract Result doInvoke(Invocation invocation) throws Throwable;

}
```
AbstractInvoker根据不同的协议有多个实现包括dubboInvoker，redisInvoker，thriftInvoker，grpcInvoker等，我们这次简单研究下dubboInvoker的实现：
```java
public class DubboInvoker<T> extends AbstractInvoker<T> {
    private final ExchangeClient[] clients;
    private final AtomicPositiveInteger index = new AtomicPositiveInteger();
    private final String version;
    private final ReentrantLock destroyLock = new ReentrantLock();
    private final Set<Invoker<?>> invokers;

    @Override
    protected Result doInvoke(final Invocation invocation) throws Throwable {
        // 设置调用服务的全限定路径（path -> org.apache.dubbo.demo.DemoService）和版本号
        RpcInvocation inv = (RpcInvocation) invocation;
        final String methodName = RpcUtils.getMethodName(invocation);
        inv.setAttachment(PATH_KEY, getUrl().getPath());
        inv.setAttachment(VERSION_KEY, version);
        // 选择一个交换器客户端，默认是一个，需要多个可以通过conections=n定制
        ExchangeClient currentClient;
        if (clients.length == 1) {
            currentClient = clients[0];
        } else {
            currentClient = clients[index.getAndIncrement() % clients.length];// 多个进行轮训获取
        }
        try {
            // 判断是否是单程访问
            boolean isOneway = RpcUtils.isOneway(getUrl(), invocation);
            int timeout = calculateTimeout(invocation, methodName);
            if (isOneway) {
                boolean isSent = getUrl().getMethodParameter(methodName, Constants.SENT_KEY, false);
                currentClient.send(inv, isSent);
                // 单向立即返回空结果
                return AsyncRpcResult.newDefaultAsyncResult(invocation);
            } else {
                // 获取业务线程池执行器
                ExecutorService executor = getCallbackExecutor(getUrl(), inv);
                // 最终由交换器（ReferenceCountExchangeClient为起点逐步执行）发起请求
                CompletableFuture<Object> request = currentClient.request(inv, timeout, executor);
                // 结果进行类型转换
                CompletableFuture<AppResponse> appResponseFuture = request.thenApply(obj -> (AppResponse) obj);
                // save for 2.6.x compatibility, for example, TraceFilter in Zipkin uses com.alibaba.xxx.FutureAdapter
                FutureContext.getContext().setCompatibleFuture(appResponseFuture);
                AsyncRpcResult result = new AsyncRpcResult(appResponseFuture, inv);
                result.setExecutor(executor);
                // 返回异步结果，最终由异步结果根据url转换成同步，通过result.get()获取最终返回结果
                return result;
            }
        } catch (TimeoutException e) {
            throw new RpcException(RpcException.TIMEOUT_EXCEPTION, "Invoke remote method timeout. method: " + invocation.getMethodName() + ", provider: " + getUrl() + ", cause: " + e.getMessage(), e);
        } catch (RemotingException e) {
            throw new RpcException(RpcException.NETWORK_EXCEPTION, "Failed to invoke remote method: " + invocation.getMethodName() + ", provider: " + getUrl() + ", cause: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isAvailable() {
        if (!super.isAvailable()) {
            return false;
        }
        for (ExchangeClient client : clients) {
            if (client.isConnected() && !client.hasAttribute(Constants.CHANNEL_ATTRIBUTE_READONLY_KEY)) {
                return true;
            }
        }
        return false;
    }
}
```
到此我们整个invoker的调用执行分析完毕，大致调用流程为：
- proxy0.sayHello                   代理类执行
- InvokerInvocationHandler.invoke   代理类增强器执行
- MockClusterInvoker.invoke mock    集群执行
- AbstractCluster$InterceptorInvokerNode.invoke 执行cluster拦截器
- FailoverClusterInvoker.doInvoke   容错集群执行
- InvokerWrapper.invoke             invoker包装类
- ProtocolFilterWrapper$1.invoke    进行过滤器调用链
- ListenerInvokerWrapper.invoke     执行监听wrapper
- AsyncToSyncInvoker.invoke         结果获取异步转同步
- DubboInvoker.doInvoke             最终交给交换器发起request请求

## 发起请求
1. ReferenceCountExchangeClient.request 引用计数器交换器，只做当前被调用服务引用个数的统计
2. HeaderExchangeClient.request 初始化头协议通道`所谓头协议，是因为协议的参数放在请求头中`
3. HeaderExchangeChannel.request 将请求数据封装Request，然后将Request，channel，线程池执行器excutor封装到DefaultFuture（里面封装了id和DefaultFuture映射）
4. NettyClient.send 根据netty的NioSocketChannel获取或创建dubbo定义的NettyChannel
5. NettyChannel.send 内部发送数据是通过netty的NioSocketChannel进行发送
看下HeaderExchangeChannel封装的Request对象：
```java
    @Override
    public CompletableFuture<Object> request(Object request, int timeout, ExecutorService executor) throws RemotingException {
        if (closed) {
            throw new RemotingException(this.getLocalAddress(), null, "Failed to send request " + request + ", cause: The channel " + this + " is closed!");
        }
        // create request.
        Request req = new Request();
        req.setVersion(Version.getProtocolVersion());// 设置协议版本号
        req.setTwoWay(true);// 设置双向为true
        req.setData(request);// 设置RpcInvocation对象
        // 创建一个future
        DefaultFuture future = DefaultFuture.newFuture(channel, req, timeout, executor);
        try {
            // 继续传递
            channel.send(req);
        } catch (RemotingException e) {
            future.cancel();
            throw e;
        }
        return future;
    }
```
最终经过多次调用，会经过NettyChannel的send方法:
```java
    public void send(Object message, boolean sent) throws RemotingException {
        // whether the channel is closed
        super.send(message, sent);// 调用父类抽象方法判断客户端是否已经关闭了
        boolean success = true;
        int timeout = 0;
        try {
            // 调用netty的io.netty.channel.socket.nio.NioSocketChannel.writeAndFlush方法进行发送数据
            ChannelFuture future = channel.writeAndFlush(message);
            if (sent) {
                // wait timeout ms
                timeout = getUrl().getPositiveParameter(TIMEOUT_KEY, DEFAULT_TIMEOUT);
                success = future.await(timeout);
            }
            Throwable cause = future.cause();
            if (cause != null) {
                throw cause;
            }
        } catch (Throwable e) {
            removeChannelIfDisconnected(channel);
            throw new RemotingException(this, "Failed to send message " + PayloadDropper.getRequestWithoutData(message) + " to " + getRemoteAddress() + ", cause: " + e.getMessage(), e);
        }
        if (!success) {
            throw new RemotingException(this, "Failed to send message " + PayloadDropper.getRequestWithoutData(message) + " to " + getRemoteAddress()
                    + "in timeout(" + timeout + "ms) limit");
        }
    }
```

将我们封装好的Request做为message通过netty.NioSocketChannel.writeAndFlush准备发送到服务端，不过在发送之前，我们需要要对message进行编码。还记得吗？我们在引用服务创建的netty客户端的时候已经设置：
```java
    protected void doOpen() throws Throwable {
        final NettyClientHandler nettyClientHandler = new NettyClientHandler(getUrl(), this);
        bootstrap = new Bootstrap();
        // 这里的netty配置可以参考：https://netty.io/wiki/
        bootstrap.group(NIO_EVENT_LOOP_GROUP)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .channel(socketChannelClass());
        bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.max(3000, getConnectTimeout()));
        bootstrap.handler(new ChannelInitializer<SocketChannel>() {

            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                int heartbeatInterval = UrlUtils.getHeartbeat(getUrl());
                // 新版加入的ssl支持
                if (getUrl().getParameter(SSL_ENABLED_KEY, false)) {
                    ch.pipeline().addLast("negotiation", SslHandlerInitializer.sslClientHandler(getUrl(), nettyClientHandler));
                }
                // 这里是我们关注的重点🌟
                // 通过NettyCodecAdapter设置解码器decoder，编码器encoder，事件处理器handler
                // 当netty发送数据前首先会调用encoder进行对要发送的object进行编码，接收到消息后会进行调用decoder进行解码，最后再调用handler。
                NettyCodecAdapter adapter = new NettyCodecAdapter(getCodec(), getUrl(), NettyClient.this);
                ch.pipeline()
                        .addLast("decoder", adapter.getDecoder())// ByteToMessageDecoder
                        .addLast("encoder", adapter.getEncoder())// MessageToByteEncoder
                        .addLast("client-idle-handler", new IdleStateHandler(heartbeatInterval, 0, 0, MILLISECONDS))
                        .addLast("handler", nettyClientHandler);

                String socksProxyHost = ConfigUtils.getProperty(SOCKS_PROXY_HOST);
                if(socksProxyHost != null) {
                    int socksProxyPort = Integer.parseInt(ConfigUtils.getProperty(SOCKS_PROXY_PORT, DEFAULT_SOCKS_PROXY_PORT));
                    Socks5ProxyHandler socks5ProxyHandler = new Socks5ProxyHandler(new InetSocketAddress(socksProxyHost, socksProxyPort));
                    ch.pipeline().addFirst(socks5ProxyHandler);
                }
            }
        });
    }
```
因为这是客户端发送数据，所以发送前会执行InternalEncoder进行编码，这里只针对dubbo协议的编码进行介绍：
```java
    // 编码器
    private class InternalEncoder extends MessageToByteEncoder {
        @Override
        protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws Exception {
            // 对byteBuf包装
            org.apache.dubbo.remoting.buffer.ChannelBuffer buffer = new NettyBackedChannelBuffer(out);
            // 获取 io.netty.channel.socket.nio.NioSocketChannel
            Channel ch = ctx.channel();
            // 根据spi获取NettyChannel
            NettyChannel channel = NettyChannel.getOrAddChannel(ch, url, handler);
            // 交给DubboCountCodec
            codec.encode(channel, buffer, msg);
        }
    }
```
其中DubboCountCodec主要作用是用来解决接收数据时候数据包的问题，在接收数据的时候会直接交给DubboCodec，首先会执行DubboCodec的父类ExchangeCodec.encode的方法：
```java
    public void encode(Channel channel, ChannelBuffer buffer, Object msg) throws IOException {
        // 判断是请求数据还是响应数据
        if (msg instanceof Request) {
            encodeRequest(channel, buffer, (Request) msg);
        } else if (msg instanceof Response) {
            encodeResponse(channel, buffer, (Response) msg);
        } else {
            super.encode(channel, buffer, msg);
        }
    }
```
我们这次是发送数据，因此会进入encodeRequest方法进行真正的编码，我们分析编码先了解下dubbo的协议：
![](https://raw.githubusercontent.com/mvilplss/note/master/image/.Dubbo的接口调用过程_images/c28fb30f.png)
Dubbo 数据包分为消息头和消息体，消息头用于存储一些元信息，比如魔数（Magic），数据包类型（Request/Response），消息体长度（Data Length）等。消息体中用于存储具体的调用消息，比如方法名称，参数列表等。下面简单列举一下消息头的内容。
|偏移量(Bit)	|字段	|取值    |
|----|----|----|
|0 ~ 7	|魔数高位|	0xda00   |
|8 ~ 15	|魔数低位|	0xbb    |
|16	|数据包类型|	0 - Response, 1 - Request        |
|17	|调用方式|	仅在第16位被设为1的情况下有效，0 - 单向调用，1 - 双向调用     |
|18	|事件标识|	0 - 当前数据包是请求或响应包，1 - 当前数据包是心跳包    |
|19 ~ 23	|序列化器编号|	2 - Hessian2Serialization   3 - JavaSerialization  4 - CompactedJavaSerialization  6 - FastJsonSerialization  7 - NativeJavaSerialization  8 - KryoSerialization  9 - FstSerialization    |
|24 ~ 31|	状态	|20 - OK |
|30 - CLIENT_TIMEOUT 31 - SERVER_TIMEOUT  40 - BAD_REQUEST  50 - BAD_RESPONSE  |
……
|32 ~ 95|	请求编号|	共8字节，运行时生成 |
|96 ~ 127|	消息体长度|	运行时计算 |

然后我们再看下面的编码就会很轻松：
```java
    // 编码：
    protected void encodeRequest(Channel channel, ChannelBuffer buffer, Request req) throws IOException {
        // 首先会通过SPI获取序列化扩展点，默认：hessian2
        Serialization serialization = getSerialization(channel);
        // header. 设置请求头16位
        byte[] header = new byte[HEADER_LENGTH];
        // set magic number.：0xdabb 魔法数字，类似Java的0xCAFEBABE
        Bytes.short2bytes(MAGIC, header);
        // set request and serialization flag. 设置序列号类型标识
        header[2] = (byte) (FLAG_REQUEST | serialization.getContentTypeId());
        if (req.isTwoWay()) {
            header[2] |= FLAG_TWOWAY;
        }
        if (req.isEvent()) {
            header[2] |= FLAG_EVENT;
        }
        // set request id. 请求ID
        Bytes.long2bytes(req.getId(), header, 4);
        // encode request data.
        int savedWriteIndex = buffer.writerIndex();
        buffer.writerIndex(savedWriteIndex + HEADER_LENGTH);// 设置请求头16字节位置，占用宽度
        // 对buffer包装，在请求头后面写入序列化数据
        ChannelBufferOutputStream bos = new ChannelBufferOutputStream(buffer);
        ObjectOutput out = serialization.serialize(channel.getUrl(), bos);
        if (req.isEvent()) {
            encodeEventData(channel, out, req.getData());
        } else {
            encodeRequestData(channel, out, req.getData(), req.getVersion());
        }
        out.flushBuffer();
        if (out instanceof Cleanable) {
            ((Cleanable) out).cleanup();
        }
        bos.flush();
        bos.close();
        int len = bos.writtenBytes();
        checkPayload(channel, len);// 检测数据长度是否超过默认的8M
        Bytes.int2bytes(len, header, 12);// 将body长度写入header
        // write 
        buffer.writerIndex(savedWriteIndex); // 写入开始位置
        buffer.writeBytes(header); // write header. 写入请求头
        buffer.writerIndex(savedWriteIndex + HEADER_LENGTH + len); // 写入结束位置
    }
```
到此，我们已经将请求发送到了服务端，服务端的具体会在下面单独分析，我们继续分析消费端获取到响应后的操作。首先肯定是netty收到返回数据时候先调用解码器进行响应数据解码NettyCodecAdapter$InternalDecoder：
```java
    // 解码器
    private class InternalDecoder extends ByteToMessageDecoder {
        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf input, List<Object> out) throws Exception {
            // 对返回数据包装
            ChannelBuffer message = new NettyBackedChannelBuffer(input);
            NettyChannel channel = NettyChannel.getOrAddChannel(ctx.channel(), url, handler);
            // decode object.
            // 循环获取数据
            do {
                // 获取数据的起始下标
                int saveReaderIndex = message.readerIndex();
                // 调用DubboCountCodec.decode
                Object msg = codec.decode(channel, message);
                if (msg == Codec2.DecodeResult.NEED_MORE_INPUT) {
                    message.readerIndex(saveReaderIndex);
                    break;
                } else {
                    //is it possible to go here ?
                    if (saveReaderIndex == message.readerIndex()) {
                        throw new IOException("Decode without read data.");
                    }
                    if (msg != null) {
                        out.add(msg);
                    }
                }
            } while (message.readable());
        }
    }
```
DubboCountCodec.decode源码如下，主要是将接收多个消息然后封装到MultiMessage对象中，最终还是委托给DubboCodec来解码
```java
    @Override
    public Object decode(Channel channel, ChannelBuffer buffer) throws IOException {
        int save = buffer.readerIndex();
        MultiMessage result = MultiMessage.create();
        do {
            Object obj = codec.decode(channel, buffer);
            if (Codec2.DecodeResult.NEED_MORE_INPUT == obj) {
                buffer.readerIndex(save);
                break;
            } else {
                result.addMessage(obj);
                logMessageLength(obj, buffer.readerIndex() - save);
                save = buffer.readerIndex();
            }
        } while (true);
        if (result.isEmpty()) {
            return Codec2.DecodeResult.NEED_MORE_INPUT;
        }
        if (result.size() == 1) {
            return result.get(0);
        }
        return result;
    }
```
DubboCodec的解码方法源码如下：
```java
    // 此方法只是先对head进行读取
    public Object decode(Channel channel, ChannelBuffer buffer) throws IOException {
        int readable = buffer.readableBytes();
        byte[] header = new byte[Math.min(readable, HEADER_LENGTH)];
        buffer.readBytes(header);
        return decode(channel, buffer, readable, header);
    }
    // 解码前数据包校验
    protected Object decode(Channel channel, ChannelBuffer buffer, int readable, byte[] header) throws IOException {
        // check magic number. 根据版本号来判断是否正常，非正常情况交给父类TelnetCodec处理
        if (readable > 0 && header[0] != MAGIC_HIGH || readable > 1 && header[1] != MAGIC_LOW) {
            int length = header.length;
            if (header.length < readable) {
                header = Bytes.copyOf(header, readable);
                buffer.readBytes(header, length, readable - length);
            }
            for (int i = 1; i < header.length - 1; i++) {
                if (header[i] == MAGIC_HIGH && header[i + 1] == MAGIC_LOW) {
                    buffer.readerIndex(buffer.readerIndex() - header.length + i);
                    header = Bytes.copyOf(header, i);
                    break;
                }
            }
            // 父类TelnetCodec
            return super.decode(channel, buffer, readable, header);
        }
        // check length. 判断请求头是否缺包
        if (readable < HEADER_LENGTH) {
            return DecodeResult.NEED_MORE_INPUT;
        }
        // get data length. 获取body长度
        int len = Bytes.bytes2int(header, 12);

        checkPayload(channel, len);// 检测长度

        int tt = len + HEADER_LENGTH;
        if (readable < tt) { // 判断总数据长度是否缺包
            return DecodeResult.NEED_MORE_INPUT;
        }
        // limit input stream.读取固定长度的数据
        ChannelBufferInputStream is = new ChannelBufferInputStream(buffer, len);
        try {
            return decodeBody(channel, is, header);
        } finally {
            //...
        }
    }
    // 对数据体进行解码，然后返回Request/Response对象
    protected Object decodeBody(Channel channel, InputStream is, byte[] header) throws IOException {
        byte flag = header[2], // 数据包类型 0 - Response, 1 - Request
            proto = (byte) (flag & SERIALIZATION_MASK);
        // get request id.
        long id = Bytes.bytes2long(header, 4);// 获取请求id
        if ((flag & FLAG_REQUEST) == 0) {// 当时响应为：flag=2，FLAG_REQUEST=-128，此处暂时看不懂！
            // decode response. 
            Response res = new Response(id);// 封装数据到response中
            if ((flag & FLAG_EVENT) != 0) {
                res.setEvent(true);
            }
            // get status.
            byte status = header[3];// 20为ok
            res.setStatus(status);
            try {
                if (status == Response.OK) {
                    Object data;
                    if (res.isEvent()) {// 如果是事件，则直接反序列化
                        ObjectInput in = CodecSupport.deserialize(channel.getUrl(), is, proto);
                        data = decodeEventData(channel, in);
                    } else {
                        DecodeableRpcResult result;
                        // 判断是否是要在io线程模型上执行解码，如果是则直接解码，默认是false（在2.7.4前版本是true），可以通过decode.in.io配置🌟
                        if (channel.getUrl().getParameter(DECODE_IN_IO_THREAD_KEY, DEFAULT_DECODE_IN_IO_THREAD)) {
                            result = new DecodeableRpcResult(channel, res, is,
                                    (Invocation) getRequestData(id), proto);
                            result.decode();// 直接解码
                        } else {
                            // 先不解码，直接返回可解码的result对象
                            // 此处getRequestData(id)值得看下，为了方便我就贴在这里了，通过id在DefaultFuture的map缓存中获取请求时的Invocation对象，这种实现方式是io线程和biz线程解耦的关键点。
                            /*
                                protected Object getRequestData(long id) {
                                    DefaultFuture future = DefaultFuture.getFuture(id);
                                    Request req = future.getRequest();
                                    return req.getData();
                                }
                            */
                            result = new DecodeableRpcResult(channel, res,
                                    new UnsafeByteArrayInputStream(readMessageData(is)),
                                    (Invocation) getRequestData(id), proto);
                        }
                        data = result;
                    }
                    res.setResult(data);
                } else {
                    // 如果异常则直接在io线程上解码
                    ObjectInput in = CodecSupport.deserialize(channel.getUrl(), is, proto);
                    res.setErrorMessage(in.readUTF());
                }
            } catch (Throwable t) {
                if (log.isWarnEnabled()) {
                    log.warn("Decode response failed: " + t.getMessage(), t);
                }
                res.setStatus(Response.CLIENT_ERROR);
                res.setErrorMessage(StringUtils.toString(t));
            }
            return res;
        } else {
            // decode request.
            Request req = new Request(id);
            req.setVersion(Version.getProtocolVersion());
            req.setTwoWay((flag & FLAG_TWOWAY) != 0);
            if ((flag & FLAG_EVENT) != 0) {
                req.setEvent(true);
            }
            try {
                Object data;
                if (req.isEvent()) {
                    ObjectInput in = CodecSupport.deserialize(channel.getUrl(), is, proto);
                    data = decodeEventData(channel, in);
                } else {
                    DecodeableRpcInvocation inv;
                    // 如果是request 同样会判断是否要在io线程解码
                    if (channel.getUrl().getParameter(DECODE_IN_IO_THREAD_KEY, DEFAULT_DECODE_IN_IO_THREAD)) {
                        inv = new DecodeableRpcInvocation(channel, req, is, proto);
                        inv.decode();
                    } else {
                        inv = new DecodeableRpcInvocation(channel, req,
                                new UnsafeByteArrayInputStream(readMessageData(is)), proto);
                    }
                    data = inv;
                }
                req.setData(data);
            } catch (Throwable t) {
                if (log.isWarnEnabled()) {
                    log.warn("Decode request failed: " + t.getMessage(), t);
                }
                // bad request
                req.setBroken(true);
                req.setData(t);
            }

            return req;
        }
    }
```
netty调用解码器进行解码后，很快就会传递给当时设置的handler（nettyClientHandler），调用channelRead方法：
```java
// NettyClientHandler
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        // 通过SPI获取到NettyChannel
        NettyChannel channel = NettyChannel.getOrAddChannel(ctx.channel(), url, handler);
        // NettyClient.received > MultiMessageHandler.received(循环处理多个消息）>HeartbeatHandler.received（处理心跳）>AllChannelHandler
        handler.received(channel, msg);
    }
//
```
AllChannelHandler将是进入消费端业务线程池第一步,但是我们在这里不进行详细介绍，后期会单独介绍io线程和biz线程的
```java
    public void received(Channel channel, Object message) throws RemotingException {
        // 获取一个共享的线程池，默认cached
        ExecutorService executor = getPreferredExecutorService(message);
        try {
            // 提交一个任务到线程池中执行，此处
            executor.execute(new ChannelEventRunnable(channel, handler, ChannelState.RECEIVED, message));
        } catch (Throwable t) {
        	if(message instanceof Request && t instanceof RejectedExecutionException){
                sendFeedback(channel, (Request) message, t);
                return;
        	}
            throw new ExecutionException(message, channel, getClass() + " error when process received event .", t);
        }
    }
```
提交的任务的run方法是这样的，这里的hander是：DecodeHandler，channel是NettyChannel，message是Response，从现在开始，所有的执行在biz线程中工作。
```java
    @Override
    public void run() {
        if (state == ChannelState.RECEIVED) {
            try {
                // 交给DecodeHandler来处理，判断是否完全解码，从上面我们知道此时的Response的数据还未反序列化
                handler.received(channel, message);
            } catch (Exception e) {
                logger.warn("ChannelEventRunnable handle " + state + " operation error, channel is " + channel
                        + ", message is " + message, e);
            }
        } else {
            switch (state) {
            case CONNECTED:
                try {
                    handler.connected(channel);
                } catch (Exception e) {
                    logger.warn("ChannelEventRunnable handle " + state + " operation error, channel is " + channel, e);
                }
                break;
            case DISCONNECTED:
                try {
                    handler.disconnected(channel);
                } catch (Exception e) {
                    logger.warn("ChannelEventRunnable handle " + state + " operation error, channel is " + channel, e);
                }
                break;
            case SENT:
                try {
                    handler.sent(channel, message);
                } catch (Exception e) {
                    logger.warn("ChannelEventRunnable handle " + state + " operation error, channel is " + channel
                            + ", message is " + message, e);
                }
                break;
            case CAUGHT:
                try {
                    handler.caught(channel, exception);
                } catch (Exception e) {
                    logger.warn("ChannelEventRunnable handle " + state + " operation error, channel is " + channel
                            + ", message is: " + message + ", exception is " + exception, e);
                }
                break;
            default:
                logger.warn("unknown state: " + state + ", message is " + message);
            }
        }

    }
```
DecodeHandler进行进一步解码：io线程中返回的message数据有可能是为完全解码的数据，比如我们的反序列化在io线程中并未操作，而是交给了biz线程，所以需要进一步解码。
```java
    public void received(Channel channel, Object message) throws RemotingException {
        if (message instanceof Decodeable) {
            decode(message);
        // 下面是解码好的数据
        if (message instanceof Request) {
            decode(((Request) message).getData());
        }
        if (message instanceof Response) {
            decode(((Response) message).getResult());
        }
        // HeaderExchangeHandler.received
        handler.received(channel, message);
    }
    private void decode(Object message) {
        if (message instanceof Decodeable) {
            try {
                ((Decodeable) message).decode();// 最终交给消息自身的解码器解码：DecodeableRpcResult.decode
                
            } catch (Throwable e) {
            } // ~ end of catch
        } // ~ end of if
    } // ~ end of method decode
    
    //DecodeableRpcResult.decode源码：
    public Object decode(Channel channel, InputStream input) throws IOException {
        // spi获取序列化器进行反序列化
        ObjectInput in = CodecSupport.getSerialization(channel.getUrl(), serializationType)
                .deserialize(channel.getUrl(), input);

        byte flag = in.readByte();// 这里没有看懂？难道是在序列化的时候第一个字节写入了一个标记？
        switch (flag) {
            case DubboCodec.RESPONSE_NULL_VALUE:
                break;
            case DubboCodec.RESPONSE_VALUE:
                handleValue(in);
                break;
            case DubboCodec.RESPONSE_WITH_EXCEPTION:
                handleException(in);
                break;
            case DubboCodec.RESPONSE_NULL_VALUE_WITH_ATTACHMENTS:
                handleAttachment(in);
                break;
            case DubboCodec.RESPONSE_VALUE_WITH_ATTACHMENTS:// 通过debug会进入这里
                handleValue(in);// 设置返回值对象
                handleAttachment(in);// 设置附加值
                break;
            case DubboCodec.RESPONSE_WITH_EXCEPTION_WITH_ATTACHMENTS:
                handleException(in);
                handleAttachment(in);
                break;
            default:
                throw new IOException("Unknown result flag, expect '0' '1' '2' '3' '4' '5', but received: " + flag);
        }
        if (in instanceof Cleanable) {
            ((Cleanable) in).cleanup();
        }
        return this;
    }
```
解码完毕后，紧接着交给HeaderExchangeHandler处理：
```java
    public void received(Channel channel, Object message) throws RemotingException {
        final ExchangeChannel exchangeChannel = HeaderExchangeChannel.getOrAddChannel(channel);
        if (message instanceof Request) {
            // handle request.
            Request request = (Request) message;
            if (request.isEvent()) {
                handlerEvent(channel, request);
            } else {
                if (request.isTwoWay()) {
                    handleRequest(exchangeChannel, request);
                } else {
                    handler.received(exchangeChannel, request.getData());
                }
            }
        } else if (message instanceof Response) {
            // 做为消费端，返回的是response，因此会执行这里
            handleResponse(channel, (Response) message);
        } else if (message instanceof String) {
            
        } else {
            handler.received(exchangeChannel, message);
        }
    }
    // 最终交给了最初我们设置的DefaultFuture处理结果
    static void handleResponse(Channel channel, Response response) throws RemotingException {
        if (response != null && !response.isHeartbeat()) {
            DefaultFuture.received(channel, response);
        }
    }
```
DefaultFuture可谓是功能多，但是功不可没，承接了跨不同线程的发送请求和获取结果的桥梁！
```java
public class DefaultFuture extends CompletableFuture<Object> {
    // 省略大部分其他代码...
    private DefaultFuture(Channel channel, Request request, int timeout) {
        this.channel = channel;
        this.request = request;
        this.id = request.getId();
        this.timeout = timeout > 0 ? timeout : channel.getUrl().getPositiveParameter(TIMEOUT_KEY, DEFAULT_TIMEOUT);
        // put into waiting map.
        FUTURES.put(id, this);
        CHANNELS.put(id, channel);
    }
    // 发送请求的时候，我们创建了一个DefaultFuture对象，并设置到FUTURES缓存中，request.id做为key。
    public static DefaultFuture newFuture(Channel channel, Request request, int timeout, ExecutorService executor) {
        final DefaultFuture future = new DefaultFuture(channel, request, timeout);
        future.setExecutor(executor);
        if (executor instanceof ThreadlessExecutor) {
            ((ThreadlessExecutor) executor).setWaitingFuture(future);
        }
        // timeout check
        timeoutCheck(future);
        return future;
    }

    public static void received(Channel channel, Response response) {
        received(channel, response, false);
    }
    // 接收响应的时候，再通过key也就是id获取发送时创建的DefaultFuture，然后执行doReceived
    public static void received(Channel channel, Response response, boolean timeout) {
        try {
            DefaultFuture future = FUTURES.remove(response.getId());
            if (future != null) {
                future.doReceived(response);
            } else {
            }
        } finally {
            CHANNELS.remove(response.getId());
        }
    }

    // 完成DefaultFuture 异步结果获取
    private void doReceived(Response res) {
        if (res == null) {
            throw new IllegalStateException("response cannot be null");
        }
        if (res.getStatus() == Response.OK) {
            // 完成结果获取
            this.complete(res.getResult());
        } else if (res.getStatus() == Response.CLIENT_TIMEOUT || res.getStatus() == Response.SERVER_TIMEOUT) {
            this.completeExceptionally(new TimeoutException(res.getStatus() == Response.SERVER_TIMEOUT, channel, res.getErrorMessage()));
        } else {
            this.completeExceptionally(new RemotingException(channel, res.getErrorMessage()));
        }
        // the result is returning, but the caller thread may still waiting
        // to avoid endless waiting for whatever reason, notify caller thread to return. 
        // 如果结果已经返回，biz线程还在等待状态，则通知异常。
        // 因为在发送请求后，ThreadlessExecutor会阻塞，一直等待队列中有任务（也就是上面提到的ChannelEventRunable），然后执行run后等待结果。
        // 上面的异步转同步的源码还有印象吗？
        if (executor != null && executor instanceof ThreadlessExecutor) {
            ThreadlessExecutor threadlessExecutor = (ThreadlessExecutor) executor;
            if (threadlessExecutor.isWaiting()) {
                threadlessExecutor.notifyReturn(new IllegalStateException("The result has returned, but the biz thread is still waiting" +
                        " which is not an expected state, interrupt the thread manually by returning an exception."));
            }
        }
    }
}
```
至此响应结果获取完毕，经过过滤器逐步回到调用层InvokerInvocationHandler,然后返回到代理对象proxy0到业务层，客户端整个调用过程比较复杂，要有耐心，了解消费端调用和获取响应结果的过程后，我们分析服务端对调用的处理就会轻松很多。
趁着还有感觉，下面我们就直接分析服务端收到请求后的一系列操作过程。
# 服务端处理请求
## 请求的接收
## 找到服务端的invoker
## 服务的响应
