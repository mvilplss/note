---
title: Mac绿色安装redis
date: 2021-02-05
categories: 
- 开发日常
tags: 
- 电脑技巧
---

# centos7.4绿色安装redis
## 下载安装redis服务
```shell
$ wget https://download.redis.io/releases/redis-6.0.10.tar.gz
$ tar xzf redis-6.0.10.tar.gz
$ cd redis-6.0.10
$ make
```
当make的时候报错，提示`make: *** [server.o] 错误 1` ，原因是因为当前默认的gcc版本太低，需要手动升级gcc。
## 升级GCC
```shell
yum -y install centos-release-scl
yum -y install devtoolset-9-*
#临时启动gcc9
scl enable devtoolset-9 bash
```
永久使用gcc9
```shell
echo "source /opt/rh/devtoolset-9/enable" >>/etc/profile
```
## redis启动和关闭
```shell
$ src/redis-server
```
后台启动设置，修改redis.conf，设置`daemonize yes`，重新启动。
```shell
$ src/redis-server redis.conf
```
客户端链接
```shell
$ src/redis-cli
```
## 开放6379端口
查看网络
```shell
firewall-cmd --get-active-zones
```
查看某个端口是否打开
```shell
firewall-cmd --query-port=6379/tcp
```
打开对应的端口
```shell
firewall-cmd --zone=public --add-port=6379/tcp --permanent
```
重启防火墙
```shell
firewall-cmd --reload
```
## 关闭linux7防火墙
查看防火墙状态
```shell
systemctl status firewalld.service
```
暂时关闭防火墙
```shell
systemctl stop firewalld.service
```
永久关闭防火墙
```shell
systemctl disable firewalld.service
```
临时开启防火墙
```shell
systemctl start firewalld.service
```
永久开启防火墙
```shell
systemctl enable firewalld.service
```

## 参考
- https://redis.io/download
- https://www.cnblogs.com/ToBeExpert/p/10297697.html
