---
title: Dubbo的简介
date: 2020-11-29
categories: 
- 开发技术
tags: 
- java
- dubbo
copyright: true
cover: https://raw.githubusercontent.com/mvilplss/note/master/image/dubbo1.png
---
## Dubbo是什么？
Apache Dubbo是一款高性能、轻量级的开源服务框架。
## 开发团队
![](https://raw.githubusercontent.com/mvilplss/note/master/image/dubbo1.png)
## Dubbo有哪些核心能力
### 面向接口代理的高性能RPC调用
提供高性能的基于代理的远程调用能力，服务以接口为粒度，为开发者屏蔽远程调用底层细节。

### 集群容错和负载均衡
内置多种负载均衡策略，智能感知下游节点健康状况，显著减少调用延迟，提高系统吞吐量。

### 服务自动注册和发现
支持多种注册中心服务，服务实例上下线实时感知。

### 高度可扩展能力
遵循微内核+插件的设计原则，所有核心能力如Protocol、Transport、Serialization被设计为扩展点，平等对待内置实现和第三方实现。

### 运行期流量调度
内置条件、脚本等路由策略，通过配置不同的路由规则，轻松实现灰度发布，同机房优先等功能。

### 可视化的服务治理与运维
提供丰富服务治理、运维工具：随时查询服务元数据、服务健康状态及调用统计，实时下发路由策略、调整配置参数。

## dubbo的框架设计
![](https://raw.githubusercontent.com/mvilplss/note/master/image/.Dubbo的简介_images/7a9403f0.png)
### 各层说明 
- config 配置层：对外配置接口，以 ServiceConfig, ReferenceConfig 为中心，可以直接初始化配置类，也可以通过 spring 解析配置生成配置类
- proxy 服务代理层：服务接口透明代理，生成服务的客户端 Stub 和服务器端 Skeleton, 以 ServiceProxy 为中心，扩展接口为 ProxyFactory
- registry 注册中心层：封装服务地址的注册与发现，以服务 URL 为中心，扩展接口为 RegistryFactory, Registry, RegistryService
- cluster 路由层：封装多个提供者的路由及负载均衡，并桥接注册中心，以 Invoker 为中心，扩展接口为 Cluster, Directory, Router, LoadBalance
- monitor 监控层：RPC 调用次数和调用时间监控，以 Statistics 为中心，扩展接口为 MonitorFactory, Monitor, MonitorService
- protocol 远程调用层：封装 RPC 调用，以 Invocation, Result 为中心，扩展接口为 Protocol, Invoker, Exporter
- exchange 信息交换层：封装请求响应模式，同步转异步，以 Request, Response 为中心，扩展接口为 Exchanger, ExchangeChannel, ExchangeClient, ExchangeServer
- transport 网络传输层：抽象 mina 和 netty 为统一接口，以 Message 为中心，扩展接口为 Channel, Transporter, Client, Server, Codec
- serialize 数据序列化层：可复用的一些工具，扩展接口为 Serialization, ObjectInput, ObjectOutput, ThreadPool
### 模块依赖关系
![](https://raw.githubusercontent.com/mvilplss/note/master/image/.Dubbo的简介_images/4eb8663e.png)
### 调用链
![](https://raw.githubusercontent.com/mvilplss/note/master/image/.Dubbo的简介_images/6f2cc26e.png)
## 参考文献
- http://dubbo.apache.org/zh/docs/v2.7/dev/design/