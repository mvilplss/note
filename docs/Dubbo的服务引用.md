---
title: Dubbo服务的引用
date: 2020-12-03
categories:
- 开发技术
tags:
- java
- dubbo
copyright: true
cover: https://gitee.com/mvilplss/note/raw/master/image/dubbo1.png.png
---

## 简介
我们可以通过两种方式引用远程服务。第一种是使用服务直连的方式引用服务，第二种方式是基于注册中心进行引用。服务直连的方式仅适合在调试或测试服务的场景下使用，不适合在线上环境使用。因此，本文我将重点分析通过注册中心引用服务的过程。从注册中心中获取服务配置只是服务引用过程中的一环，除此之外，服务消费者还需要经历 Invoker 创建、代理类创建等步骤。

## 服务的引用
### 引用示例
为了研究方便研究源码引用过程，我们通过api引用方式进行研究，示例代码如下：
```java
    public static void main(String[] args) {
        ReferenceConfig<DemoService> reference = new ReferenceConfig<>();
        reference.setApplication(new ApplicationConfig("dubbo-demo-api-consumer"));
        reference.setRegistry(new RegistryConfig("zookeeper://127.0.0.1:2181"));
        reference.setInterface(DemoService.class);
        DemoService service = reference.get();
        String message = service.sayHello("dubbo");
        System.out.println(message);
    }

```
### 源码分析
#### 配置参数收集和组装
```java
    public synchronized T get() {
        if (ref == null) {
            init();// 初始化
        }
        return ref;
    }

    public synchronized void init() {
        if (bootstrap == null) {
            bootstrap = DubboBootstrap.getInstance();
            bootstrap.init();
        }
        checkAndUpdateSubConfigs();
        checkStubAndLocal(interfaceClass);
        ConfigValidationUtils.checkMock(interfaceClass, this);

        Map<String, String> map = new HashMap<String, String>();
        map.put(SIDE_KEY, CONSUMER_SIDE);// 设置side为消费端
        ReferenceConfigBase.appendRuntimeParameters(map);
        if (!ProtocolUtils.isGeneric(generic)) {
            String revision = Version.getVersion(interfaceClass, version);
            if (revision != null && revision.length() > 0) {
                map.put(REVISION_KEY, revision);
            }
            String[] methods = Wrapper.getWrapper(interfaceClass).getMethodNames();
            if (methods.length == 0) {
                logger.warn("No method found in service interface " + interfaceClass.getName());
                map.put(METHODS_KEY, ANY_VALUE);
            } else {
                map.put(METHODS_KEY, StringUtils.join(new HashSet<String>(Arrays.asList(methods)), COMMA_SEPARATOR));
            }
        }
        map.put(INTERFACE_KEY, interfaceName);
        AbstractConfig.appendParameters(map, getMetrics());
        AbstractConfig.appendParameters(map, getApplication());
        AbstractConfig.appendParameters(map, getModule());
        AbstractConfig.appendParameters(map, consumer);
        AbstractConfig.appendParameters(map, this);
        Map<String, AsyncMethodInfo> attributes = null;
        if (CollectionUtils.isNotEmpty(getMethods())) {
            attributes = new HashMap<>();
            for (MethodConfig methodConfig : getMethods()) {
                // ...
            }
        }

        String hostToRegistry = ConfigUtils.getSystemProperty(DUBBO_IP_TO_REGISTRY);
        if (StringUtils.isEmpty(hostToRegistry)) {
            hostToRegistry = NetUtils.getLocalHost();
        } else if (isInvalidLocalHost(hostToRegistry)) {
            throw new IllegalArgumentException("Specified invalid registry ip from property:" + DUBBO_IP_TO_REGISTRY + ", value:" + hostToRegistry);
        }
        map.put(REGISTER_IP_KEY, hostToRegistry);
        serviceMetadata.getAttachments().putAll(map);
        // 根据收集的参数创建客户端服务代理类
        ref = createProxy(map);
        serviceMetadata.setTarget(ref);
        serviceMetadata.addAttribute(PROXY_CLASS_REF, ref);
        ConsumerModel consumerModel = repository.lookupReferredService(serviceMetadata.getServiceKey());
        consumerModel.setProxyObject(ref);
        // 注入配置的属性
        consumerModel.init(attributes);
        initialized = true;
        // dispatch a ReferenceConfigInitializedEvent since 2.7.4 
        // 转发一个引用配置已经初始化事件
        dispatch(new ReferenceConfigInitializedEvent(this, invoker));
    }
```
以上源码逻辑主要是对消费者配置的收集和封装到一个map中，下面将根据map进行创建代理。
#### 获取注册中心地址
```java
    private T createProxy(Map<String, String> map) {
        if (shouldJvmRefer(map)) {
            //...
        } else {
            urls.clear();
            // 用户指定url时候，直接使用
            if (url != null && url.length() > 0) { // user specified URL, could be peer-to-peer address, or register center's address.
                String[] us = SEMICOLON_SPLIT_PATTERN.split(url);
                if (us != null && us.length > 0) {
                    for (String u : us) {
                        URL url = URL.valueOf(u);
                        if (StringUtils.isEmpty(url.getPath())) {
                            url = url.setPath(interfaceName);
                        }
                        if (UrlUtils.isRegistry(url)) {
                            urls.add(url.addParameterAndEncoded(REFER_KEY, StringUtils.toQueryString(map)));
                        } else {
                            urls.add(ClusterUtils.mergeUrl(url, map));
                        }
                    }
                }
            } else { 
            	// assemble URL from register center's configuration
                // if protocols not injvm checkRegistry
                if (!LOCAL_PROTOCOL.equalsIgnoreCase(getProtocol())) {
                    checkRegistry();
                    List<URL> us = ConfigValidationUtils.loadRegistries(this, false);// 组装注册中心URL
                    if (CollectionUtils.isNotEmpty(us)) {
                        for (URL u : us) {
                            URL monitorUrl = ConfigValidationUtils.loadMonitor(this, u);
                            if (monitorUrl != null) {
                                map.put(MONITOR_KEY, URL.encode(monitorUrl.toFullString()));
                            }
                            urls.add(u.addParameterAndEncoded(REFER_KEY, StringUtils.toQueryString(map)));
                        }
                    }
                    if (urls.isEmpty()) {
                        throw new IllegalStateException("No such any registry to reference " + interfaceName + " on the consumer " + NetUtils.getLocalHost() + " use dubbo version " + Version.getVersion() + ", please config <dubbo:registry address=\"...\" /> to your spring config.");
                    }
                }
            }

            if (urls.size() == 1) { // 1个注册中心
            	// 根据协议接口获取协议扩展点，此处和服务导出类似，获取为：ProtocolFilterWrapper>ProtocolListenerWrapper>RegistryProtocol
                invoker = REF_PROTOCOL.refer(interfaceClass, urls.get(0));
            } else {
                List<Invoker<?>> invokers = new ArrayList<Invoker<?>>();
                URL registryURL = null;
                for (URL url : urls) {
                    invokers.add(REF_PROTOCOL.refer(interfaceClass, url));
                    if (UrlUtils.isRegistry(url)) {
                        registryURL = url; // use last registry url
                    }
                }
                if (registryURL != null) { // registry url is available
                    // for multi-subscription scenario, use 'zone-aware' policy by default
                    String cluster = registryURL.getParameter(CLUSTER_KEY, ZoneAwareCluster.NAME);
                    // The invoker wrap sequence would be: ZoneAwareClusterInvoker(StaticDirectory) -> FailoverClusterInvoker(RegistryDirectory, routing happens here) -> Invoker
                    invoker = Cluster.getCluster(cluster, false).join(new StaticDirectory(registryURL, invokers));
                } else { // not a registry url, must be direct invoke.
                    String cluster = CollectionUtils.isNotEmpty(invokers)
                            ? (invokers.get(0).getUrl() != null ? invokers.get(0).getUrl().getParameter(CLUSTER_KEY, ZoneAwareCluster.NAME) : Cluster.DEFAULT)
                            : Cluster.DEFAULT;
                    invoker = Cluster.getCluster(cluster).join(new StaticDirectory(invokers));
                }
            }
        }

        if (shouldCheck() && !invoker.isAvailable()) {
            invoker.destroy();
            throw new IllegalStateException();
        }
        // create service proxy 通过javassist把封装好的invoker再次封装到被调用接口的代理类中供应用调用 7⃣️
        return (T) PROXY_FACTORY.getProxy(invoker, ProtocolUtils.isGeneric(generic));
    }

```
#### 获取register对象
获取注册中心的`register`对象：
```java
	// ProtocolFilterWrapper 传递给ProtocolListenerWrapper
    public <T> Invoker<T> refer(Class<T> type, URL url) throws RpcException {
        if (UrlUtils.isRegistry(url)) {
            return protocol.refer(type, url);
        }
        return buildInvokerChain(protocol.refer(type, url), REFERENCE_FILTER_KEY, CommonConstants.CONSUMER);
    }
    // ProtocolListenerWrapper 传递给RegistryProtocol
    public <T> Invoker<T> refer(Class<T> type, URL url) throws RpcException {
	    if (UrlUtils.isRegistry(url)) {
	        return protocol.refer(type, url);
	    }
	    return new ListenerInvokerWrapper<T>(protocol.refer(type, url),
	            Collections.unmodifiableList(
	                    ExtensionLoader.getExtensionLoader(InvokerListener.class)
	                            .getActivateExtension(url, INVOKER_LISTENER_KEY)));
	}
	// RegistryProtocol 
    public <T> Invoker<T> refer(Class<T> type, URL url) throws RpcException {
    	// 获取注册中心URL
        url = getRegistryUrl(url);
        // 获取注册中心对象(ListenerRegistryWrapper实例)
        Registry registry = registryFactory.getRegistry(url); // 0⃣️
        if (RegistryService.class.equals(type)) {
        	// 构造invoker 2⃣️
            return proxyFactory.getInvoker((T) registry, type, url);
        }

        // group="a,b" or group="*"
        Map<String, String> qs = StringUtils.parseQueryString(url.getParameterAndDecoded(REFER_KEY));
        String group = qs.get(GROUP_KEY);
        if (group != null && group.length() > 0) {
            if ((COMMA_SPLIT_PATTERN.split(group)).length > 1 || "*".equals(group)) {
                return doRefer(Cluster.getCluster(MergeableCluster.NAME), registry, type, url);
            }
        }
        // 获取容错集群MockClusterWrapper 3⃣️
        Cluster cluster = Cluster.getCluster(qs.get(CLUSTER_KEY));
        // 获取invoker4⃣️
        return doRefer(cluster, registry, type, url);
    }

```
在上述0⃣️位置`registryFactory` 为扩展器加载到的对象：RegistryFactoryWrapper>ZookeeperRegistryFactory，最终获取到一个创建链接后的注册中心客户端
```java
	// RegistryFactoryWrapper
    public Registry getRegistry(URL url) {
        return new ListenerRegistryWrapper(registryFactory.getRegistry(url),//1⃣️
                Collections.unmodifiableList(ExtensionLoader.getExtensionLoader(RegistryServiceListener.class)
                        .getActivateExtension(url, "registry.listeners")));
    }
    // 1⃣️ZookeeperRegistryFactory.super()
    public Registry getRegistry(URL url) {
    	// 获取注册中心地址
        url = URLBuilder.from(url)
                .setPath(RegistryService.class.getName())
                .addParameter(INTERFACE_KEY, RegistryService.class.getName())
                .removeParameters(EXPORT_KEY, REFER_KEY)
                .build();
        // 获取缓存key
        String key = createRegistryCacheKey(url);
        // Lock the registry access process to ensure a single instance of the registry
        LOCK.lock();
        try {
            Registry registry = REGISTRIES.get(key);
            if (registry != null) {
                return registry;
            }
            //create registry by spi/ioc 创建一个registry
            registry = createRegistry(url);
            // 缓存注册中心
            REGISTRIES.put(key, registry);
            return registry;
        } finally {
            // Release the lock
            LOCK.unlock();
        }
    }
    // ZookeeperRegistryFactory
    public Registry createRegistry(URL url) {
        return new ZookeeperRegistry(url, zookeeperTransporter);
    }
    // 最终通过zookeeperTransporter获取到已链接的zk客户端
    public ZookeeperRegistry(URL url, ZookeeperTransporter zookeeperTransporter) {
        super(url);
        if (url.isAnyHost()) {
            throw new IllegalStateException("registry address == null");
        }
        String group = url.getParameter(GROUP_KEY, DEFAULT_ROOT);
        if (!group.startsWith(PATH_SEPARATOR)) {
            group = PATH_SEPARATOR + group;
        }
        this.root = group;
        zkClient = zookeeperTransporter.connect(url);
        zkClient.addStateListener((state) -> {
            if (state == StateListener.RECONNECTED) {
                ZookeeperRegistry.this.fetchLatestAddresses();
            } else if (state == StateListener.NEW_SESSION_CREATED) {
                logger.warn("Trying to re-register urls and re-subscribe listeners of this instance to registry...");
                try {
                    ZookeeperRegistry.this.recover();
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                }
            }
        });
    }

```
#### 通过register订阅服务
在上述代码中获取到了注册中心对象，下面将进行服务的订阅
在上面代码3⃣️`Cluster cluster = Cluster.getCluster(qs.get(CLUSTER_KEY));`可知，获取invoker前，要先获取容错集群：
```java
	// Cluster 获取容错集群，默认为failover：失败后重试
    static Cluster getCluster(String name) {
        return getCluster(name, true);
    }
    static Cluster getCluster(String name, boolean wrap) {
        if (StringUtils.isEmpty(name)) {
            name = Cluster.DEFAULT;
        }
        // 最终返回扩展对象为：FailoverCluster
        return ExtensionLoader.getExtensionLoader(Cluster.class).getExtension(name, wrap);
    }
```
在上述代码4⃣️中`doRefer(cluster, registry, type, url)`获取容错集群后继续创建invoker：
```java
    private <T> Invoker<T> doRefer(Cluster cluster, Registry registry, Class<T> type, URL url) {
    	// 先创建注册表，在创建注册表时候，会自动new ConsumerConfigurationListener()，同时会初始化规则配置动态监听器，用来监听路由规则的变化
        RegistryDirectory<T> directory = new RegistryDirectory<T>(type, url);
        directory.setRegistry(registry);
        // 设置协议自适应扩展对象
        directory.setProtocol(protocol);
        // all attributes of REFER_KEY
        Map<String, String> parameters = new HashMap<String, String>(directory.getConsumerUrl().getParameters());
        URL subscribeUrl = new URL(CONSUMER_PROTOCOL, parameters.remove(REGISTER_IP_KEY), 0, type.getName(), parameters);
        if (directory.isShouldRegister()) {
            directory.setRegisteredConsumerUrl(subscribeUrl);
            registry.register(directory.getRegisteredConsumerUrl());// 注册消费者到注册中心，类似服务的注册地址在zk的创建
        }
        // 通过spi获取自动激活的路由扩展点：MockInvokersSelector，TagRouter，AppRouter，ServiceRouter 
        directory.buildRouterChain(subscribeUrl);
        // 获取zk中的服务地址，并注册zk并监听服务地址的变化 5⃣️
        directory.subscribe(toSubscribeUrl(subscribeUrl));
        // 进一步获取invoker 6⃣️
        Invoker<T> invoker = cluster.join(directory);
        // 获取协议注册监听
        List<RegistryProtocolListener> listeners = findRegistryProtocolListeners(url);
        if (CollectionUtils.isEmpty(listeners)) {
            return invoker;
        }
        // 如果有监听器，则调用监听器通知refer完毕
        RegistryInvokerWrapper<T> registryInvokerWrapper = new RegistryInvokerWrapper<>(directory, cluster, invoker);
        for (RegistryProtocolListener listener : listeners) {
            listener.onRefer(this, registryInvokerWrapper);
        }
        return registryInvokerWrapper;
    }
```
在上述代码5⃣️地方订阅注册中心，详细代码如下：
```java
	// 首先RegistryDirectory增加一些订阅监听器后，委托register进行订阅
	public void subscribe(URL url) {
        setConsumerUrl(url);
        CONSUMER_CONFIGURATION_LISTENER.addNotifyListener(this);
        serviceConfigurationListener = new ReferenceConfigurationListener(this, url);
        registry.subscribe(url, this);
    }
    // FailbackRegistry订阅服务包装,订阅失败则尝试从缓存中获取服务列表，如果是启动过程中订阅失败，则直接抛出异常
    public void subscribe(URL url, NotifyListener listener) {
        super.subscribe(url, listener);
        removeFailedSubscribed(url, listener);
        try {
            // Sending a subscription request to the server side
            doSubscribe(url, listener);
        } catch (Exception e) {
            Throwable t = e;
            List<URL> urls = getCacheUrls(url);
            if (CollectionUtils.isNotEmpty(urls)) {
                notify(url, listener, urls);
            } else {
                // If the startup detection is opened, the Exception is thrown directly.
                boolean check = getUrl().getParameter(Constants.CHECK_KEY, true)
                        && url.getParameter(Constants.CHECK_KEY, true);
                boolean skipFailback = t instanceof SkipFailbackWrapperException;
                if (check || skipFailback) {
                    if (skipFailback) {
                        t = t.getCause();
                    }
                    throw new IllegalStateException("Failed to subscribe " + url + ", cause: " + t.getMessage(), t);
                }
            }

            addFailedSubscribed(url, listener);
        }
    }
    // ZookeeperRegistry 订阅服务
    public void doSubscribe(final URL url, final NotifyListener listener) {
        try {
        	// 判断是否订阅所有服务
            if (ANY_VALUE.equals(url.getServiceInterface())) {
                String root = toRootPath();
                ConcurrentMap<NotifyListener, ChildListener> listeners = zkListeners.computeIfAbsent(url, k -> new ConcurrentHashMap<>());
                ChildListener zkListener = listeners.computeIfAbsent(listener, k -> (parentPath, currentChilds) -> {
                    for (String child : currentChilds) {
                        child = URL.decode(child);
                        if (!anyServices.contains(child)) {
                            anyServices.add(child);
                            subscribe(url.setPath(child).addParameters(INTERFACE_KEY, child,
                                    Constants.CHECK_KEY, String.valueOf(false)), k);
                        }
                    }
                });
                zkClient.create(root, false);
                List<String> services = zkClient.addChildListener(root, zkListener);
                if (CollectionUtils.isNotEmpty(services)) {
                    for (String service : services) {
                        service = URL.decode(service);
                        anyServices.add(service);
                        subscribe(url.setPath(service).addParameters(INTERFACE_KEY, service,
                                Constants.CHECK_KEY, String.valueOf(false)), listener);
                    }
                }
            // 订阅指定服务（常用）
            } else {
                List<URL> urls = new ArrayList<>();
                for (String path : toCategoriesPath(url)) {
                    ConcurrentMap<NotifyListener, ChildListener> listeners = zkListeners.computeIfAbsent(url, k -> new ConcurrentHashMap<>());
                    ChildListener zkListener = listeners.computeIfAbsent(listener, k -> (parentPath, currentChilds) -> ZookeeperRegistry.this.notify(url, k, toUrlsWithEmpty(url, parentPath, currentChilds)));
                    zkClient.create(path, false);// 此处为何再此创建provider的节点？
                    List<String> children = zkClient.addChildListener(path, zkListener);// 获取服务提供地址和注册zk监听器
                    if (children != null) {
                        urls.addAll(toUrlsWithEmpty(url, path, children));
                    }
                }
                // 通知服务地址变化
                notify(url, listener, urls);
            }
        } catch (Throwable e) {
            throw new RpcException("Failed to subscribe " + url + " to zookeeper " + getUrl() + ", cause: " + e.getMessage(), e);
        }
    }
    // AbstractRegistry 通知服务地址变化
    protected void notify(URL url, NotifyListener listener, List<URL> urls) {
        // keep every provider's category.
        Map<String, List<URL>> result = new HashMap<>();
        for (URL u : urls) {
            if (UrlUtils.isMatch(url, u)) {
                String category = u.getParameter(CATEGORY_KEY, DEFAULT_CATEGORY);
                List<URL> categoryList = result.computeIfAbsent(category, k -> new ArrayList<>());
                categoryList.add(u);
            }
        }
        if (result.size() == 0) {
            return;
        }
        Map<String, List<URL>> categoryNotified = notified.computeIfAbsent(url, u -> new ConcurrentHashMap<>());
        for (Map.Entry<String, List<URL>> entry : result.entrySet()) {
            String category = entry.getKey();
            List<URL> categoryList = entry.getValue();
            categoryNotified.put(category, categoryList);
            listener.notify(categoryList);
            // We will update our cache file after each notification.
            // When our Registry has a subscribe failure due to network jitter, we can return at least the existing cache URL.
            saveProperties(url);
        }
    }
    // 保存服务列表到缓存和文件
        private void saveProperties(URL url) {
        try {
            StringBuilder buf = new StringBuilder();
            Map<String, List<URL>> categoryNotified = notified.get(url);
            if (categoryNotified != null) {
                for (List<URL> us : categoryNotified.values()) {
                    for (URL u : us) {
                        if (buf.length() > 0) {
                            buf.append(URL_SEPARATOR);
                        }
                        buf.append(u.toFullString());
                    }
                }
            }
            // 保存配置对象中
            properties.setProperty(url.getServiceKey(), buf.toString());
            long version = lastCacheChanged.incrementAndGet();
            // 根据版本来决定是否保存服务列表
            if (syncSaveFile) {
                doSaveProperties(version);
            } else {
                registryCacheExecutor.execute(new SaveProperties(version));
            }
        } catch (Throwable t) {
            logger.warn(t.getMessage(), t);
        }
    }

```
到这里，我们从注册中心获取了服务提供者的地址列表，下面我们将进一步创建invoker
#### 创建invoker
由上面代码5⃣️我们获取到了一个容错集群，通过容错集群，在6⃣️`Invoker<T> invoker = cluster.join(directory)`处我们进一步获取invoker:
```java
	// MockClusterWrapper.join
    public <T> Invoker<T> join(Directory<T> directory) throws RpcException {
    	// FailoverCluster.join
    	Invoker invoker = this.cluster.join(directory);
    	// 返回invoker
        return new MockClusterInvoker<T>(directory,invoker);
    }
    //FailoverCluster.join
    public <T> Invoker<T> join(Directory<T> directory) throws RpcException {
    	AbstractClusterInvoker cluster = doJoin(directory); // （1）
        return buildClusterInterceptors(cluster, directory.getUrl().getParameter(REFERENCE_INTERCEPTOR_KEY));// （2）
    }
    // 上面（1）doJoin返回FailoverClusterInvoker
    public <T> AbstractClusterInvoker<T> doJoin(Directory<T> directory) throws RpcException {
        return new FailoverClusterInvoker<>(directory);
    }
    // 上面（2）构造集群cluster拦截器，消费端加载自动激活扩展点ConsumerContextClusterInterceptor拦截器，主要是为了设置RpcContext各项ThreadLocal参数
    private <T> Invoker<T> buildClusterInterceptors(AbstractClusterInvoker<T> clusterInvoker, String key) {
        AbstractClusterInvoker<T> last = clusterInvoker;
        List<ClusterInterceptor> interceptors = ExtensionLoader.getExtensionLoader(ClusterInterceptor.class).getActivateExtension(clusterInvoker.getUrl(), key);
        if (!interceptors.isEmpty()) {
            for (int i = interceptors.size() - 1; i >= 0; i--) {
                final ClusterInterceptor interceptor = interceptors.get(i);
                final AbstractClusterInvoker<T> next = last;
                last = new InterceptorInvokerNode<>(clusterInvoker, interceptor, next);
            }
        }
        return last;
    }
```
#### 对最终invoker进行代理
由代码7⃣️`PROXY_FACTORY.getProxy(invoker, ProtocolUtils.isGeneric(generic))`可知，代理类是通过PROXY_FACTORY获取的，而PROXY_FACTORY是通过SPI获取，如果没有指定proxy的key，默认为javassist：
```java
	// JavassistProxyFactory
    public <T> T getProxy(Invoker<T> invoker, Class<?>[] interfaces) {
        return (T) Proxy.getProxy(interfaces).newInstance(new InvokerInvocationHandler(invoker));
    }
```
Proxy通过interfaces创建代理类
```java

    public static Proxy getProxy(Class<?>... ics) {
    	// 先获取类加载器
        return getProxy(ClassUtils.getClassLoader(Proxy.class), ics);
    }
    // 创建代理类
    public static Proxy getProxy(ClassLoader cl, Class<?>... ics) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ics.length; i++) {
            String itf = ics[i].getName();
            Class<?> tmp = null;
            try {
                tmp = Class.forName(itf, false, cl);
            } catch (ClassNotFoundException e) {
            }
            sb.append(itf).append(';');
        }
        // use interface class name list as key.
        String key = sb.toString();
        // get cache by class loader.
        final Map<String, Object> cache;
        synchronized (PROXY_CACHE_MAP) {
            cache = PROXY_CACHE_MAP.computeIfAbsent(cl, k -> new HashMap<>());
        }
        Proxy proxy = null;
        synchronized (cache) {
            do {
                Object value = cache.get(key);
                if (value instanceof Reference<?>) {
                    proxy = (Proxy) ((Reference<?>) value).get();
                    if (proxy != null) {
                        return proxy;
                    }
                }

                if (value == PENDING_GENERATION_MARKER) {
                    try {
                        cache.wait();
                    } catch (InterruptedException e) {
                    }
                } else {
                    cache.put(key, PENDING_GENERATION_MARKER);
                    break;
                }
            }while (true);
        }

        long id = PROXY_CLASS_COUNTER.getAndIncrement();
        String pkg = null;
        ClassGenerator ccp = null, ccm = null;
        try {
            ccp = ClassGenerator.newInstance(cl);
            Set<String> worked = new HashSet<>();
            List<Method> methods = new ArrayList<>();
            for (int i = 0; i < ics.length; i++) {
                if (!Modifier.isPublic(ics[i].getModifiers())) {
                    String npkg = ics[i].getPackage().getName();
                    if (pkg == null) {
                        pkg = npkg;
                    }
                }
                ccp.addInterface(ics[i]);
                for (Method method : ics[i].getMethods()) {
                    String desc = ReflectUtils.getDesc(method);
                    if (worked.contains(desc) || Modifier.isStatic(method.getModifiers())) {
                        continue;
                    }
                    if (ics[i].isInterface() && Modifier.isStatic(method.getModifiers())) {
                        continue;
                    }
                    worked.add(desc);

                    int ix = methods.size();
                    Class<?> rt = method.getReturnType();
                    Class<?>[] pts = method.getParameterTypes();

                    StringBuilder code = new StringBuilder("Object[] args = new Object[").append(pts.length).append("];");
                    for (int j = 0; j < pts.length; j++) {
                        code.append(" args[").append(j).append("] = ($w)$").append(j + 1).append(";");
                    }
                    code.append(" Object ret = handler.invoke(this, methods[").append(ix).append("], args);");
                    if (!Void.TYPE.equals(rt)) {
                        code.append(" return ").append(asArgument(rt, "ret")).append(";");
                    }
                    methods.add(method);
                    ccp.addMethod(method.getName(), method.getModifiers(), rt, pts, method.getExceptionTypes(), code.toString());
                }
            }

            if (pkg == null) {
                pkg = PACKAGE_NAME;
            }
            // create ProxyInstance class.
            String pcn = pkg + ".proxy" + id;
            ccp.setClassName(pcn);
            ccp.addField("public static java.lang.reflect.Method[] methods;");
            ccp.addField("private " + InvocationHandler.class.getName() + " handler;");
            ccp.addConstructor(Modifier.PUBLIC, new Class<?>[]{InvocationHandler.class}, new Class<?>[0], "handler=$1;");
            ccp.addDefaultConstructor();
            Class<?> clazz = ccp.toClass();
            clazz.getField("methods").set(null, methods.toArray(new Method[0]));
            // create Proxy class.
            String fcn = Proxy.class.getName() + id;
            ccm = ClassGenerator.newInstance(cl);
            ccm.setClassName(fcn);
            ccm.addDefaultConstructor();
            ccm.setSuperClass(Proxy.class);
            ccm.addMethod("public Object newInstance(" + InvocationHandler.class.getName() + " h){ return new " + pcn + "($1); }");
            Class<?> pc = ccm.toClass();
            proxy = (Proxy) pc.newInstance();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            // release ClassGenerator
            if (ccp != null) {
                ccp.release();
            }
            if (ccm != null) {
                ccm.release();
            }
            synchronized (cache) {
                if (proxy == null) {
                    cache.remove(key);
                } else {
                    cache.put(key, new WeakReference<Proxy>(proxy));
                }
                cache.notifyAll();
            }
        }
        return proxy;
    }
```
上面代码通过javasisst技术获取到一个代理类，然后下面再对代理对象构造器注入一个增强器：
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
        if (parameterTypes.length == 0) {
            if ("toString".equals(methodName)) {
                return invoker.toString();
            } else if ("$destroy".equals(methodName)) {
                invoker.destroy();
                return null;
            } else if ("hashCode".equals(methodName)) {
                return invoker.hashCode();
            }
        } else if (parameterTypes.length == 1 && "equals".equals(methodName)) {
            return invoker.equals(args[0]);
        }
        // RPC会话类，包括rpc调用的方法，参数，返回值等属性
        RpcInvocation rpcInvocation = new RpcInvocation(method, invoker.getInterface().getName(), args);
        String serviceKey = invoker.getUrl().getServiceKey();
        rpcInvocation.setTargetServiceUniqueName(serviceKey);
        if (consumerModel != null) {
            rpcInvocation.put(Constants.CONSUMER_MODEL, consumerModel);
            rpcInvocation.put(Constants.METHOD_MODEL, consumerModel.getMethodModel(method));
        }
        return invoker.invoke(rpcInvocation).recreate();
    }
}
```
最终我们获取到refer，经过反编译如下：
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

    public CompletableFuture sayHelloAsync(String string) {
        Object[] arrobject = new Object[]{string};
        Object object = this.handler.invoke(this, methods[1], arrobject);
        return (CompletableFuture)object;
    }

    public Object $echo(Object object) {
        Object[] arrobject = new Object[]{object};
        Object object2 = this.handler.invoke(this, methods[2], arrobject);
        return object2;
    }

    public void $destroy() {
        Object[] arrobject = new Object[]{};
        Object object = this.handler.invoke(this, methods[3], arrobject);
    }
    public proxy0() {
    }
    public proxy0(InvocationHandler invocationHandler) {
        this.handler = invocationHandler;
    }
}
```
#### 流程总结
至此，我们对整个消费端的引用源码已经分析完毕，引用流程大致如下：
- 首先对消费端的配置进行收集和组装
- 获取和连接注册中心
- 向注册中心注册消费者和订阅服务
- 根据集群和注册表创建invoker
- 最后对invoker进行动态代理，实现目标接口

#### 参考文献
- http://dubbo.apache.org/zh/docs/v2.7/dev/source/refer-service/