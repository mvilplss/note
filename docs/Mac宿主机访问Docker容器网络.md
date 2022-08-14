---
title: Mac宿主机访问Docker容器网络
date: 2022-08-08
categories:
- 开发技术
  tags:
- docker
  copyright: true
---
> 在mac上实践在docker中搭建rocketmq环境，发现宿主程序发送MQ消息连接失败，排查原因是broker注册到namesrv上的IP地址是docker容器的地址，而宿主机不能直接访问。后来更换了network host方式依然不行，经查询资料发现mac下的docker部署方式是将docker服务端部署在一台虚拟机里面，导致host方式通信失效。

# 解决方法
## 先安装Mac端的服务mac-docker-connector
```bash
$ brew tap wenjunxiao/brew
$ brew install docker-connector
```
首次配置通过以下命令把所有Docker所有bridge子网放入配置文件，后续的增减可以参考后面的详细配置
```bash
$ docker network ls --filter driver=bridge --format "{{.ID}}" | xargs docker network inspect --format "route {{range .IPAM.Config}}{{.Subnet}}{{end}}" >> "$(brew --prefix)/etc/docker-connector.conf"
```
## 启动Mac端的服务
```bash
$ sudo brew services start docker-connector
```
安装Docker端的容器mac-docker-connector
```bash
$ docker pull wenjunxiao/mac-docker-connector
```
## Docker
启动Docker端的容器，其中网络必须是host，并且添加NET_ADMIN特性
```bash
$ docker run -it -d --restart always --net host --cap-add NET_ADMIN --name mac-connector wenjunxiao/mac-docker-connector

```
# 参考地址
https://github.com/wenjunxiao/mac-docker-connector/blob/master/README-ZH.md