---
title: Docker搭建rocketmq环境
date: 2022-08-08
categories:
- 开发技术
tags:
- docker
copyright: true
cover: https://raw.githubusercontent.com/mvilplss/note/master/image/docker_s1.png
---
# Docker搭建rocketmq环境
## 搜索rocketmq镜像
```
docker search rocketmq
```
结果如下：
![https://raw.githubusercontent.com/mvilplss/note/master/image/docker_s1.png](img.png)
我这里选择了apache/rocketmq 镜像
```
docker pull apache/rocketmq
```

## 创建namesrv容器
创建本地目录，用来将容器的日志挂载到本地，方便日志查看
```
mkdir -p /Users/atomic/docker/rocketmq/namesrv/logs
```
创建并启动namesrv容器
```
docker run -d -p 9876:9876 \
 -v /Users/atomic/docker/rocketmq/namesrv/logs:/home/rocketmq/logs \
 --name mqnamesrv \
 -e "MAX_POSSIBLE_HEAP=100000000" \
 -e "JAVA_OPTS=-Duser.home=/opt" \
 -e "JAVA_OPT_EXT=-server -Xmx1024m -Xms1024m" \
 apache/rocketmq sh mqnamesrv
```
## 创建broker容器
同样创建本地对应目录
```
mkdir -p /Users/atomic/docker/rocketmq/broker/logs
mkdir -p /Users/atomic/docker/rocketmq/broker/logs
mkdir -p /Users/atomic/docker/rocketmq/broker/store
```
在/Users/atomic/docker/rocketmq目录下创建broker.conf配置，内容如下：
```properties
brokerClusterName = DefaultCluster
brokerName = broker-a
brokerId = 0
deleteWhen = 04
fileReservedTime = 48
brokerRole = ASYNC_MASTER
flushDiskType = ASYNC_FLUSH
#换成link名称（注意）
namesrvAddr = mqnamesrv:9876
#brokerIP1 = mqbroker
#是否允许 Broker 自动创建Topic，建议线下开启，线上关闭 
autoCreateTopicEnable=true
#是否允许 Broker 自动创建订阅组，建议线下开启，线上关闭 
autoCreateSubscriptionGroup=true
```
创建并启动broker容器
```
docker run -d  -p 10911:10911 -p 10909:10909 -p 10912:10912 \
--link mqnamesrv:mqnamesrv \
-v /Users/atomic/docker/rocketmq/broker/logs:/home/rocketmq/logs \
-v /Users/atomic/docker/rocketmq/broker/store:/home/rocketmq/store  \
-v /Users/atomic/docker/rocketmq/broker/broker.conf:/home/rocketmq/broker.conf  \
--name mqbroker \
-e "JAVA_OPTS=-Duser.home=/opt" \
-e "JAVA_OPT_EXT=-server -Xms1024m -Xmx1024m"  \
apache/rocketmq sh mqbroker -c /home/rocketmq/broker.conf
```
## 创建dashboard容器
搜索rocketmq-dashboard
```
docker search rocketmq-dashboard
```
这里也是用的Apache的apacherocketmq/rocketmq-dashboard镜像，下载镜像。
```
docker pull apacherocketmq/rocketmq-dashboard
```
创建并启动dashboard镜像
```
docker run -d --name mqdashboard \
-p 9993:9993 \
--link mqnamesrv:mqnamesrv \
-e "JAVA_OPTS=-Drocketmq.namesrv.addr=mqnamesrv:9876 -Dcom.rocketmq.sendMessageWithVIPChannel=false -Dserver.port=9993" \
-t apacherocketmq/rocketmq-dashboard:latest
```
控制台访问地址：http://localhost:9993

到此，我们的rocketmq环境安装完毕，如果你使用mac本地的脚本发送MQ消息会遇到发送失败，类似以下错误
```bash
org.apache.rocketmq.remoting.exception.RemotingTooMuchRequestException: sendDefaultImpl call timeout

	at org.apache.rocketmq.client.impl.producer.DefaultMQProducerImpl.sendDefaultImpl(DefaultMQProducerImpl.java:667)
	at org.apache.rocketmq.client.impl.producer.DefaultMQProducerImpl.send(DefaultMQProducerImpl.java:1343)
	at org.apache.rocketmq.client.impl.producer.DefaultMQProducerImpl.send(DefaultMQProducerImpl.java:1289)
	at org.apache.rocketmq.client.producer.DefaultMQProducer.send(DefaultMQProducer.java:325)
```
不要着急，这篇文章给出了详细解决方案：Mac宿主机访问Docker容器网络.md
地址：https://mvilplss.github.io/2022/08/08/Mac宿主机访问Docker容器网络/

参考文章
- Docker安装RocketMQ https://segmentfault.com/a/1190000038704231