---
title: Dubbo的服务导出
date: 2020-11-29
categories: 
- 开发技术
tags: 
- java
- dubbo
copyright: true
cover: https://raw.githubusercontent.com/mvilplss/note/master/image/dubbo1.png
---
## 简介
Dubbo 服务导出过程始于 Spring 容器发布刷新事件，Dubbo 在接收到事件后，会立即执行服务导出逻辑。也可以通过api直接执行export进行导出。整个逻辑大致可分为三个部分，第一部分是前置工作，主要用于检查参数，组装 URL。第二部分是导出服务，包含导出服务到本地 (JVM)，和导出服务到远程两个过程。第三部分是向注册中心注册服务，用于服务发现。
为了方便研究源码，我们通过api直接导出服务。
## 源码分析
### 示例代码
api方式导出源码示例：
```java
    public static void main(String[] args) throws Exception {
        ServiceConfig<DemoServiceImpl> service = new ServiceConfig<>();
        service.setProtocol(new ProtocolConfig("dubbo"));
        service.setInterface(DemoService.class);
        service.setRef(new DemoServiceImpl());
        service.setApplication(new ApplicationConfig("dubbo-demo-api-provider"));
        service.setRegistry(new RegistryConfig("zookeeper://127.0.0.1:2181"));
        service.export();// 执行导出逻辑
        new CountDownLatch(1).await();
    }
```
### dubbo源码
#### 发布服务前准备
导出服务分为延迟导出和立即导出，最后发布导出事件
```java
    public synchronized void export() {
        // 延迟导出
        if (shouldDelay()) {
            DELAY_EXPORT_EXECUTOR.schedule(this::doExport, getDelay(), TimeUnit.MILLISECONDS);
        } else {
            // 立即导出
            doExport();
        }
        // 发布导出事件
        exported();
    }
```
```java
    protected synchronized void doExport() {
        doExportUrls();
    }
    // 先把要导出的服务注册到服务仓库，然后根据多个协议进行循环导出
    private void doExportUrls() {
        // 扩展一个服务仓库ServiceRepository，存储所有服务信息
        ServiceRepository repository = ApplicationModel.getServiceRepository();
        // 注册服务
        ServiceDescriptor serviceDescriptor = repository.registerService(getInterfaceClass());
        // 把服务注册到提供者缓存中
        repository.registerProvider(
                getUniqueServiceName(),
                ref,
                serviceDescriptor,
                this,
                serviceMetadata
        );
        // 获取注册中心的URL
        List<URL> registryURLs = ConfigValidationUtils.loadRegistries(this, true);

        for (ProtocolConfig protocolConfig : protocols) {
            String pathKey = URL.buildKey(getContextPath(protocolConfig)
                    .map(p -> p + "/" + path)
                    .orElse(path), group, version);
            // In case user specified path, register service one more time to map it to path.
            // 再次注册，防止用户特殊名称路径
            repository.registerService(pathKey, interfaceClass);
            // TODO, uncomment this line once service key is unified
            serviceMetadata.setServiceKey(pathKey);
            // 导出其中一个协议的服务
            doExportUrlsFor1Protocol(protocolConfig, registryURLs);
        }
    }
```
#### 收集配置信息组装URL
下面的方法比较长，主要是收集配置信息，组装URL
```java
    private void doExportUrlsFor1Protocol(ProtocolConfig protocolConfig, List<URL> registryURLs) {
        String name = protocolConfig.getName();
        if (StringUtils.isEmpty(name)) {
            name = DUBBO;
        }
        // 收集参数到map，方便用来生成URL
        Map<String, String> map = new HashMap<String, String>();
        map.put(SIDE_KEY, PROVIDER_SIDE);
        ServiceConfig.appendRuntimeParameters(map);
        AbstractConfig.appendParameters(map, getMetrics());
        AbstractConfig.appendParameters(map, getApplication());
        AbstractConfig.appendParameters(map, getModule());
        AbstractConfig.appendParameters(map, provider);
        AbstractConfig.appendParameters(map, protocolConfig);
        AbstractConfig.appendParameters(map, this);
        // 收集方法上的各种配置参数
        if (CollectionUtils.isNotEmpty(getMethods())) {
            for (MethodConfig method : getMethods()) {
                AbstractConfig.appendParameters(map, method, method.getName());
                String retryKey = method.getName() + ".retry";
                if (map.containsKey(retryKey)) {
                    String retryValue = map.remove(retryKey);
                    if ("false".equals(retryValue)) {
                        map.put(method.getName() + ".retries", "0");
                    }
                }
                //...
            } // end of methods for
        }

        if (ProtocolUtils.isGeneric(generic)) {
            map.put(GENERIC_KEY, generic);
            map.put(METHODS_KEY, ANY_VALUE);
        } else {
            String revision = Version.getVersion(interfaceClass, version);
            if (revision != null && revision.length() > 0) {
                map.put(REVISION_KEY, revision);
            }

            String[] methods = Wrapper.getWrapper(interfaceClass).getMethodNames();
            if (methods.length == 0) {
                logger.warn("No method found in service interface " + interfaceClass.getName());
                map.put(METHODS_KEY, ANY_VALUE);
            } else {
                map.put(METHODS_KEY, StringUtils.join(new HashSet<String>(Arrays.asList(methods)), ","));
            }
        }
        if(ConfigUtils.isEmpty(token) && provider != null) {
            token = provider.getToken();
        }
        if (!ConfigUtils.isEmpty(token)) {
            if (ConfigUtils.isDefault(token)) {
                // 默认token为uuic
                map.put(TOKEN_KEY, UUID.randomUUID().toString());
            } else {
                map.put(TOKEN_KEY, token);
            }
        }
        //init serviceMetadata attachments
        serviceMetadata.getAttachments().putAll(map);
        // export service
        // 获取当前服务所在机器的ip和端口配置
        String host = findConfigedHosts(protocolConfig, registryURLs, map);
        Integer port = findConfigedPorts(protocolConfig, name, map);
        // 根据map的参数生成一个可以导出的参数
        URL url = new URL(name, host, port, getContextPath(protocolConfig).map(p -> p + "/" + path).orElse(path), map);
        // You can customize Configurator to append extra parameters
        if (ExtensionLoader.getExtensionLoader(ConfiguratorFactory.class)
                .hasExtension(url.getProtocol())) {
            url = ExtensionLoader.getExtensionLoader(ConfiguratorFactory.class)
                    .getExtension(url.getProtocol()).getConfigurator(url).configure(url);
        }

        String scope = url.getParameter(SCOPE_KEY);
        // don't export when none is configured
        if (!SCOPE_NONE.equalsIgnoreCase(scope)) {
            // export to local if the config is not remote (export to remote only when config is remote)
            if (!SCOPE_REMOTE.equalsIgnoreCase(scope)) {
                exportLocal(url);                // 默认导出本地
            }
            // export to remote if the config is not local (export to local only when config is local)
            if (!SCOPE_LOCAL.equalsIgnoreCase(scope)) {
                if (CollectionUtils.isNotEmpty(registryURLs)) {
                    for (URL registryURL : registryURLs) {
                        //...
                        // For providers, this is used to enable custom proxy to generate invoker
                        String proxy = url.getParameter(PROXY_KEY);
                        if (StringUtils.isNotEmpty(proxy)) {
                            registryURL = registryURL.addParameter(PROXY_KEY, proxy);
                        }
                        // 注册协议+导出服务协议
                        URL registryURLWithExportUrl = registryURL.addParameterAndEncoded(EXPORT_KEY, url.toFullString());
                        // ProxyFactory自适应扩展点根据URL的proxy的值选择代理工厂，然后创建一个ref的代理对象，并设置URL属性，默认使用javassist
                        Invoker<?> invoker = PROXY_FACTORY.getInvoker(ref, (Class) interfaceClass, registryURLWithExportUrl);
                        // Protocol自适应扩展点根据协议值选择rpc协议，默认是dubbo协议，但是协议接口有个包装类ProtocolFilterWrapper和ProtocolListenerWrapper，因此会返回包装类
                        // 真正调用的是ProtocolFilterWrapper.export
                        // 先链接注册中心，再导出服务
                        DelegateProviderMetaDataInvoker wrapperInvoker = new DelegateProviderMetaDataInvoker(invoker, this);
                        Exporter<?> exporter = PROTOCOL.export(wrapperInvoker);
                        exporters.add(exporter);
                    }
                } else {
                   //...
                }
            }
        }
        this.urls.add(url);
    }
```
#### 服务的导出
服务的导出核心就是`Exporter<?> exporter = PROTOCOL.export(wrapperInvoker);`，其中PROTOCOL是通过扩展加载器获取的`private static final Protocol PROTOCOL = ExtensionLoader.getExtensionLoader(Protocol.class).getAdaptiveExtension();`，要分析导出逻辑就要先分析PROTOCOL的真实身份。
下面是获取到PROTOCOL对象的class源码，从源码可以看出，最终会通过wrapperInvoker的Ulr中的protocol参数来决定加载哪个协议。
```java
public class Protocol$Adaptive
implements Protocol {
    public Exporter export(Invoker invoker) throws RpcException {
        String string;
        URL uRL = invoker.getUrl();
        String string2 = string = uRL.getProtocol() == null ? "dubbo" : uRL.getProtocol();
        // 根据协议名称获取目标扩展点，
        Protocol protocol = (Protocol)ExtensionLoader.getExtensionLoader(Protocol.class).getExtension(string);
        return protocol.export(invoker);
    }
}
```
dubbo支持多个协议，详细见Protocol的SPI配置：
- filter=org.apache.dubbo.rpc.protocol.ProtocolFilterWrapper
- listener=org.apache.dubbo.rpc.protocol.ProtocolListenerWrapper

- dubbo=org.apache.dubbo.rpc.protocol.dubbo.DubboProtocol
- hessian=org.apache.dubbo.rpc.protocol.hessian.HessianProtocol
- http=org.apache.dubbo.rpc.protocol.http.HttpProtocol
- injvm=org.apache.dubbo.rpc.protocol.injvm.InjvmProtocol

- registry=org.apache.dubbo.registry.integration.RegistryProtocol
- service-discovery-registry=org.apache.dubbo.registry.client.ServiceDiscoveryRegistryProtocol
- ...
由于url = registry://127.0.0.1:2181/org.apache.dubbo.registry.RegistryService?application=dubbo-demo-api-provider&dubbo=2.0.2&export=dubbo://xxxxxx，因此会获取名字为registry的扩展点RegistryProtocol，
其中Protocol有两个Wrapper（ProtocolFilterWrapper，ProtocolListenerWrapper），那么通过自适应加载器获取到的PROTOCOL是排序最靠前的ProtocolFilterWrapper对象，执行export顺序如下：
ProtocolFilterWrapper.export() 》ProtocolListenerWrapper.export() 》 RegistryProtocol.export()
```java
    // ProtocolFilterWrapper
    public <T> Exporter<T> export(Invoker<T> invoker) throws RpcException {
        if (UrlUtils.isRegistry(invoker.getUrl())) {// 如果是registry继续往下传递
            return protocol.export(invoker);
        }
        return protocol.export(buildInvokerChain(invoker, SERVICE_FILTER_KEY, CommonConstants.PROVIDER));
    }
    // ProtocolListenerWrapper
    public <T> Exporter<T> export(Invoker<T> invoker) throws RpcException {
        if (UrlUtils.isRegistry(invoker.getUrl())) {// 如果是registry继续往下传递
            return protocol.export(invoker);
        }
        return new ListenerExporterWrapper<T>(protocol.export(invoker),
                Collections.unmodifiableList(ExtensionLoader.getExtensionLoader(ExporterListener.class)
                        .getActivateExtension(invoker.getUrl(), EXPORTER_LISTENER_KEY)));
    }
    // RegistryProtocol
    public <T> Exporter<T> export(final Invoker<T> originInvoker) throws RpcException {
        URL registryUrl = getRegistryUrl(originInvoker);
        URL providerUrl = getProviderUrl(originInvoker);
        //export invoker 真正开始导出服务
        final ExporterChangeableWrapper<T> exporter = doLocalExport(originInvoker, providerUrl);
        // url to registry
        // 创建和链接注册中心
        final Registry registry = getRegistry(originInvoker);
        final URL registeredProviderUrl = getUrlToRegistry(providerUrl, registryUrl);
        // decide if we need to delay publish
        boolean register = providerUrl.getParameter(REGISTER_KEY, true);
        if (register) {
            register(registryUrl, registeredProviderUrl);
        }
        // register stated url on provider model
        registerStatedUrl(registryUrl, registeredProviderUrl, register);
        exporter.setRegisterUrl(registeredProviderUrl);
        exporter.setSubscribeUrl(overrideSubscribeUrl);
        // 通知协议导出后监听器
        notifyExport(exporter);
        //Ensure that a new exporter instance is returned every time export
        return new DestroyableExporter<>(exporter);
    }
```
导出服务
```java
    private <T> ExporterChangeableWrapper<T> doLocalExport(final Invoker<T> originInvoker, URL providerUrl) {
        String key = getCacheKey(originInvoker);
        return (ExporterChangeableWrapper<T>) bounds.computeIfAbsent(key, s -> {
            Invoker<?> invokerDelegate = new InvokerDelegate<>(originInvoker, providerUrl);
            // 此时的invoker的url协议为dubbo，那么protocol的实例对象就是对应者DubboProtocol
            return new ExporterChangeableWrapper<>((Exporter<T>) protocol.export(invokerDelegate), originInvoker);
        });
    }
```
导出服务时候和上面导出协议类似，第一个protocol实例就是ProtocolFilterWrapper，执行顺序为：
ProtocolFilterWrapper.export() 》ProtocolListenerWrapper.export() 》 DubboProtocol.export()
```java
// ProtocolFilterWrapper
    public <T> Exporter<T> export(Invoker<T> invoker) throws RpcException {
        if (UrlUtils.isRegistry(invoker.getUrl())) {
            return protocol.export(invoker);
        }
        // 非注册协议，建立过滤器调用链
        return protocol.export(buildInvokerChain(invoker, SERVICE_FILTER_KEY, CommonConstants.PROVIDER));
    }
    // ProtocolListenerWrapper
    public <T> Exporter<T> export(Invoker<T> invoker) throws RpcException {
        if (UrlUtils.isRegistry(invoker.getUrl())) {
            return protocol.export(invoker);
        }
        // 非注册协议，调用导出监听器exported方法（此处感觉有点早）
        return new ListenerExporterWrapper<T>(protocol.export(invoker),
                Collections.unmodifiableList(ExtensionLoader.getExtensionLoader(ExporterListener.class)
                        .getActivateExtension(invoker.getUrl(), EXPORTER_LISTENER_KEY)));
    }
    // DubboProtocol 执行真正的导出
    @Override
    public <T> Exporter<T> export(Invoker<T> invoker) throws RpcException {
        URL url = invoker.getUrl();
        // export service.
        String key = serviceKey(url);// org.apache.dubbo.demo.DemoService:20880
        DubboExporter<T> exporter = new DubboExporter<T>(invoker, key, exporterMap);
        exporterMap.put(key, exporter);// 缓存
        // 开启服务并导出
        openServer(url);
        optimizeSerialization(url);
        return exporter;
    }
    
    private void openServer(URL url) {
        // find server.
        String key = url.getAddress();// 192.168.32.216:20880
        boolean isServer = url.getParameter(IS_SERVER_KEY, true);
        if (isServer) {
            ProtocolServer server = serverMap.get(key);
            if (server == null) {
                synchronized (this) {
                    server = serverMap.get(key);
                    if (server == null) {
                        // 创建服务
                        serverMap.put(key, createServer(url));
                    }
                }
            }
        }
    }
    
    private ProtocolServer createServer(URL url) {
        url = URLBuilder.from(url)
                .addParameterIfAbsent(CHANNEL_READONLYEVENT_SENT_KEY, Boolean.TRUE.toString())
                .addParameterIfAbsent(HEARTBEAT_KEY, String.valueOf(DEFAULT_HEARTBEAT))
                .addParameter(CODEC_KEY, DubboCodec.NAME)
                .build();
        String str = url.getParameter(SERVER_KEY, DEFAULT_REMOTING_SERVER);// 默认netty
        // 判断协议是否存在
        if (str != null && str.length() > 0 && !ExtensionLoader.getExtensionLoader(Transporter.class).hasExtension(str)) {
            throw new RpcException("Unsupported server type: " + str + ", url: " + url);
        }
        ExchangeServer server;
        try {
            // 调用交换机发起绑定服务
            // requestHandler 就是当服务被调用时候的处理逻辑
            server = Exchangers.bind(url, requestHandler);
        } catch (RemotingException e) {
            throw new RpcException("Fail to start server(url: " + url + ") " + e.getMessage(), e);
        }
        // 返回服务
        return new DubboProtocolServer(server);
    }
```
过滤器调用链的创建细节：
```java
    private static <T> Invoker<T> buildInvokerChain(final Invoker<T> invoker, String key, String group) {
        Invoker<T> last = invoker;
        // 根据url和key及分组获取所有过滤器
        List<Filter> filters = ExtensionLoader.getExtensionLoader(Filter.class).getActivateExtension(invoker.getUrl(), key, group);
        // 将过滤器封装为Invoker，并形成一个调用链
        if (!filters.isEmpty()) {
            for (int i = filters.size() - 1; i >= 0; i--) {
                final Filter filter = filters.get(i);
                final Invoker<T> next = last;
                last = new Invoker<T>() {
                    @Override
                    public Result invoke(Invocation invocation) throws RpcException {
                        Result asyncResult;
                        try {
                            asyncResult = filter.invoke(next, invocation);
                        } catch (Exception e) {
                            if (filter instanceof ListenableFilter) {
                                ListenableFilter listenableFilter = ((ListenableFilter) filter);
                                try {
                                    Filter.Listener listener = listenableFilter.listener(invocation);
                                    if (listener != null) {
                                        listener.onError(e, invoker, invocation);
                                    }
                                } finally {
                                    listenableFilter.removeListener(invocation);
                                }
                            } else if (filter instanceof Filter.Listener) {
                                Filter.Listener listener = (Filter.Listener) filter;
                                listener.onError(e, invoker, invocation);
                            }
                            throw e;
                        } finally {
                        }
                    }
                };
            }
        }
        return last;
    }
```
每个调用链的大致源码如下，通过next链接下一个，调用者调用invoker传入下一个调用者next：
```java
// Invoker在FilterChainMaker中的匿名类
public class FilterChainMaker_1 implements Invoker {
    final InvokerFilter filter;
    final Invoker next;

    FilterChainMaker_1(InvokerFilter invokerFilter, Invoker invoker) {
        this.filter = invokerFilter;
        this.next = invoker;
    }
    @Override
    public String invoke(String doing) {
        return this.filter.invoke(this.next, doing);
    }
}
```
#### 绑定服务
打开和创建一个服务：
```java
    private void openServer(URL url) {
        String key = url.getAddress();// org.apache.dubbo.demo.DemoService:20880
        boolean isServer = url.getParameter(IS_SERVER_KEY, true);
        if (isServer) {
            ProtocolServer server = serverMap.get(key);
            if (server == null) {
                synchronized (this) {
                    // 防止重复绑定
                    server = serverMap.get(key);
                    if (server == null) {
                        // 创建一个服务
                        serverMap.put(key, createServer(url));
                    }
                }
            } else {
                server.reset(url);
            }
        }
    }

    private ProtocolServer createServer(URL url) {
        // 完善url，设置必要参数
        url = URLBuilder.from(url)
                .addParameterIfAbsent(CHANNEL_READONLYEVENT_SENT_KEY, Boolean.TRUE.toString())
                .addParameterIfAbsent(HEARTBEAT_KEY, String.valueOf(DEFAULT_HEARTBEAT))
                .addParameter(CODEC_KEY, DubboCodec.NAME)
                .build();
        String str = url.getParameter(SERVER_KEY, DEFAULT_REMOTING_SERVER);// 默认netty
        ExchangeServer server;
        try {
            // 绑定服务
            server = Exchangers.bind(url, requestHandler);
        } catch (RemotingException e) {}
        return new DubboProtocolServer(server);
    }
```
`Exchangers.bind(url, requestHandler)`，将服务绑定到某个网络服务上。
```java
    // Exchangers
    public static ExchangeServer bind(URL url, ExchangeHandler handler) throws RemotingException {
        if (url == null) {
            throw new IllegalArgumentException("url == null");
        }
        if (handler == null) {
            throw new IllegalArgumentException("handler == null");
        }
        url = url.addParameterIfAbsent(Constants.CODEC_KEY, "exchange");
        return getExchanger(url).bind(url, handler);
    }
   public static Exchanger getExchanger(URL url) {
        // 交换器是header（头部交换器，协议在头部），返回HeaderExchanger
        String type = url.getParameter(Constants.EXCHANGER_KEY, Constants.DEFAULT_EXCHANGER);
        return getExchanger(type);
    }
    //HeaderExchanger.bind();
    @Override
    public ExchangeServer bind(URL url, ExchangeHandler handler) throws RemotingException {
        // 创建头部交换处理器
        HeaderExchangeHandler headerExchangeHandler = new HeaderExchangeHandler(handler);
        // 解码处理器
        DecodeHandler decodeHandler = new DecodeHandler(headerExchangeHandler);
        // 解码器绑定到网络传输器上
        RemotingServer remotingServer = Transporters.bind(url, decodeHandler);
        // 创建交换服务，并将远程服务注入
        HeaderExchangeServer headerExchangeServer = new HeaderExchangeServer(remotingServer);
        return headerExchangeServer;
    }
```
`Transporters.bind(url, decodeHandler)`，绑定地址和解码器到某个网络服务上。
```java
    public static RemotingServer bind(URL url, ChannelHandler... handlers) throws RemotingException {
        if (url == null) {
            throw new IllegalArgumentException("url == null");
        }
        if (handlers == null || handlers.length == 0) {
            throw new IllegalArgumentException("handlers == null");
        }
        ChannelHandler handler;
        if (handlers.length == 1) {
            handler = handlers[0];
        } else {
            handler = new ChannelHandlerDispatcher(handlers);
        }
        // 获取网络传输器，绑定地址和解码器
        return getTransporter().bind(url, handler);
    }
    // 获取网络传输器自动适应扩展，根据url上的Server_key=netty可知为：NettyServer
    public static Transporter getTransporter() {
        return ExtensionLoader.getExtensionLoader(Transporter.class).getAdaptiveExtension();
    }
    // NettyServer
    @Override
    public RemotingServer bind(URL url, ChannelHandler handler) throws RemotingException {
        return new NettyServer(url, handler);
    }
    // NettyServer.super
    public AbstractServer(URL url, ChannelHandler handler) throws RemotingException {
        super(url, handler);
        localAddress = getUrl().toInetSocketAddress();
        String bindIp = getUrl().getParameter(Constants.BIND_IP_KEY, getUrl().getHost());
        int bindPort = getUrl().getParameter(Constants.BIND_PORT_KEY, getUrl().getPort());
        if (url.getParameter(ANYHOST_KEY, false) || NetUtils.isInvalidLocalHost(bindIp)) {
            bindIp = ANYHOST_VALUE;
        }
        bindAddress = new InetSocketAddress(bindIp, bindPort);
        this.accepts = url.getParameter(ACCEPTS_KEY, DEFAULT_ACCEPTS);
        this.idleTimeout = url.getParameter(IDLE_TIMEOUT_KEY, DEFAULT_IDLE_TIMEOUT);
        try {
            // 开启网络服务
            doOpen();
        } catch (Throwable t) {
        }
        executor = executorRepository.createExecutorIfAbsent(url);
    }
```
此处的逻辑就是把服务的处理器绑定到某个网络端口上：
```java
    @Override
    protected void doOpen() throws Throwable {
        NettyHelper.setNettyLoggerFactory();
        ExecutorService boss = Executors.newCachedThreadPool(new NamedThreadFactory("NettyServerBoss", true));
        ExecutorService worker = Executors.newCachedThreadPool(new NamedThreadFactory("NettyServerWorker", true));
        ChannelFactory channelFactory = new NioServerSocketChannelFactory(boss, worker, getUrl().getPositiveParameter(IO_THREADS_KEY, Constants.DEFAULT_IO_THREADS));
        bootstrap = new ServerBootstrap(channelFactory);
        final NettyHandler nettyHandler = new NettyHandler(getUrl(), this);
        channels = nettyHandler.getChannels();
        bootstrap.setOption("child.tcpNoDelay", true);
        bootstrap.setOption("backlog", getUrl().getPositiveParameter(BACKLOG_KEY, Constants.DEFAULT_BACKLOG));
        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
            // 设置通道的rpc解码，编码，和处理器
            @Override
            public ChannelPipeline getPipeline() {
                NettyCodecAdapter adapter = new NettyCodecAdapter(getCodec(), getUrl(), NettyServer.this);
                ChannelPipeline pipeline = Channels.pipeline();
                pipeline.addLast("decoder", adapter.getDecoder());
                pipeline.addLast("encoder", adapter.getEncoder());
                pipeline.addLast("handler", nettyHandler);
                return pipeline;
            }
        });
        // netty服务绑定到某个端口上
        channel = bootstrap.bind(getBindAddress());
    }
```
到此，服务已经绑定到某个网络传输服务的端口上。下面将介绍服务是如何注册到某个注册中心的。
#### 服务的注册
在上面服务导出后，需要注册到注册中心，注册方式如下，我们以zookeeper为例：
```java
    // RegistryProtocol
    public <T> Exporter<T> export(final Invoker<T> originInvoker) throws RpcException {
        //...
        //export invoker 真正开始导出服务
        final ExporterChangeableWrapper<T> exporter = doLocalExport(originInvoker, providerUrl);
        // url to registry
        // 根据spi创建和链接注册中心获取的是ListenerRegistryWrapper
        final Registry registry = getRegistry(originInvoker);
        final URL registeredProviderUrl = getUrlToRegistry(providerUrl, registryUrl);
        // decide if we need to delay publish
        boolean register = providerUrl.getParameter(REGISTER_KEY, true);
        if (register) {
            register(registryUrl, registeredProviderUrl);
        }
        // register stated url on provider model
        registerStatedUrl(registryUrl, registeredProviderUrl, register);
        exporter.setRegisterUrl(registeredProviderUrl);
        exporter.setSubscribeUrl(overrideSubscribeUrl);
        // 通知协议导出后监听器
        notifyExport(exporter);
        //Ensure that a new exporter instance is returned every time export
        return new DestroyableExporter<>(exporter);
    }
    private void register(URL registryUrl, URL registeredProviderUrl) {
        // 此处根据spi获取的是ListenerRegistryWrapper
        Registry registry = registryFactory.getRegistry(registryUrl);
        registry.register(registeredProviderUrl);
    }
```
ListenerRegistryWrapper的register为的是注册后通知注册监听器。
```java
@Override
    public void register(URL url) {
        try {
            // 调用ZookeeperRegistry.的父类的register方法
            registry.register(url);
        } finally {
            // 通知注册监听器
            if (CollectionUtils.isNotEmpty(listeners)) {
                RuntimeException exception = null;
                for (RegistryServiceListener listener : listeners) {
                    if (listener != null) {
                        try {
                            listener.onRegister(url);
                        } catch (RuntimeException t) {
                            logger.error(t.getMessage(), t);
                            exception = t;
                        }
                    }
                }
                if (exception != null) {
                    throw exception;
                }
            }
        }
    }
```
调用ZookeeperRegistry的父类FailbackRegistry的register方法，最终调用到自身的doRegister方法，通过zk客户端向zookeeper中创建持久节点（dubbo/xx.service.xxx/provider/)和临时节点(rpc请求协议地址dubbo://xxx.xxx)。
```java
    // ZookeeperRegistry
    @Override
    public void doRegister(URL url) {
        try {
            zkClient.create(toUrlPath(url), url.getParameter(DYNAMIC_KEY, true));
        } catch (Throwable e) {
            throw new RpcException("Failed to register " + url + " to zookeeper " + getUrl() + ", cause: " + e.getMessage(), e);
        }
    }
    // ZookeeperClient
    // 创建zk节点，注册服务
    @Override
    public void create(String path, boolean ephemeral) {
        if (!ephemeral) {
            if(persistentExistNodePath.contains(path)){
                return;
            }
            if (checkExists(path)) {
                persistentExistNodePath.add(path);
                return;
            }
        }
        int i = path.lastIndexOf('/');
        if (i > 0) {
            // 递归创建上级持久路径
            create(path.substring(0, i), false);
        }
        if (ephemeral) {
            // 创建临时协议
            createEphemeral(path);
        } else {
            createPersistent(path);
            persistentExistNodePath.add(path);
        }
    }
```
#### 服务注册后逻辑
通知注册协议监听器：
```java
    // RegistryProtocol
    private <T> void notifyExport(ExporterChangeableWrapper<T> exporter) {
        List<RegistryProtocolListener> listeners = ExtensionLoader.getExtensionLoader(RegistryProtocolListener.class)
                .getActivateExtension(exporter.getOriginInvoker().getUrl(), "registry.protocol.listener");
        if (CollectionUtils.isNotEmpty(listeners)) {
            for (RegistryProtocolListener listener : listeners) {
                listener.onExport(this, exporter);
            }
        }
    }
```
发布服务已导出事件
```java
    // ServiceConfig
    public void exported() {
        // dispatch a ServiceConfigExportedEvent since 2.7.4
        dispatch(new ServiceConfigExportedEvent(this));
    }
    private void dispatch(Event event) {
        EventDispatcher.getDefaultExtension().dispatch(event);
    }
    // AbstractEventDispatcher
    @Override
    public void dispatch(Event event) {
        Executor executor = getExecutor();
        // execute in sequential or parallel execution model
        executor.execute(() -> {
            sortedListeners(entry -> entry.getKey().isAssignableFrom(event.getClass()))
                    .forEach(listener -> {
                        if (listener instanceof ConditionalEventListener) {
                            ConditionalEventListener predicateEventListener = (ConditionalEventListener) listener;
                            if (!predicateEventListener.accept(event)) { // No accept
                                return;
                            }
                        }
                        // Handle the event
                        listener.onEvent(event);
                    });
        });
    }
```
到这里整个服务的导出和注册就结束了。
#### 大致的流程总结
1. 获取和组装配置参数，并将参数组装为要导出的服务URL和协议URL
2. 根据注册中心的Url的协议register://，获取RegistryProtocol扩展点，连接注册中心
3. 再次根据服务的Url的协议dubbo://,获取DubboProtocol扩展点，然后将服务绑定到netty服务上完成服务导出
4. 根据注册中心的客户端在zookeeper中创建服务节点，完成服务注册

## 参考文献
http://dubbo.apache.org/zh/docs/v2.7/dev/source/export-service/

