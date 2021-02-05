---
title: VirtualBox网络设置实践
date: 2019-08-02
categories: 
- 开发技术
tags: 
- 开发工具
copyright: true
---

## VirtualBox网络设置实践
>需求：使用virtualBox安装linux虚拟机做实验，需要linux可以访问外网（用来下载安装软件等），同时也需要和物理机互通，又要保证ip不变（不用每次ssh时候查看ip）。
### 实现思路
- 首先实现虚拟机可以访问外网，可以通过设置【桥接网卡】，物理机和虚拟机在同一个网络，具备物理机同样的网络连通性，但是当换办公场地时候ip会变。
- 为了实现ip不变，可以增加一个【仅主机(Host-Only)网络】，则物理机会创建个网络共享，多个虚拟机可以互联，将虚拟机设置为固定ip，ssh就不用更换ip。
- 设置虚拟机网卡配置，虚拟主机设为静态ip，桥接网卡设置为dhcp，两个网卡设为开机启动。
通过以上两个网卡来实现我们的需求，下面将介绍如何配置。
### 配置方法
#### 桥接网卡配置
这个比较简单，直接设置》网络》桥接网卡 即可。
![桥接网卡](https://gitee.com/mvilplss/note/raw/master/image/VirtualBox网络设置实践/桥接网卡.png)
#### 仅主机(Host-Only)网络配置
添加主机共享网络
![添加主机网络](https://gitee.com/mvilplss/note/raw/master/image/VirtualBox网络设置实践/添加主机网络.png)
创建主机共享网络，使用默认的ip配置即可
![创建主机网络](https://gitee.com/mvilplss/note/raw/master/image/VirtualBox网络设置实践/创建主机网络.png)
设置第二个网卡，选择配置好的主机共享网络
![设置第二个网卡](https://gitee.com/mvilplss/note/raw/master/image/VirtualBox网络设置实践/设置第二个网卡.png)
#### 配置虚拟机网卡
查看网卡：
```
[root@vworld network-scripts]# ip addr
1: lo: <LOOPBACK,UP,LOWER_UP> mtu 65536 qdisc noqueue state UNKNOWN group default qlen 1000
    link/loopback 00:00:00:00:00:00 brd 00:00:00:00:00:00
    inet 127.0.0.1/8 scope host lo
       valid_lft forever preferred_lft forever
    inet6 ::1/128 scope host 
       valid_lft forever preferred_lft forever
2: enp0s3: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 qdisc pfifo_fast state UP group default qlen 1000
    link/ether 08:00:27:37:8f:4f brd ff:ff:ff:ff:ff:ff
    inet 192.168.31.250/24 brd 192.168.31.255 scope global noprefixroute dynamic enp0s3
       valid_lft 42995sec preferred_lft 42995sec
    inet6 fe80::b32:2e09:3fd3:4406/64 scope link noprefixroute 
       valid_lft forever preferred_lft forever
3: enp0s8: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 qdisc pfifo_fast state UP group default qlen 1000
    link/ether 08:00:27:f8:e6:2c brd ff:ff:ff:ff:ff:ff
    inet 192.168.66.111/24 brd 192.168.66.255 scope global noprefixroute enp0s8
       valid_lft forever preferred_lft forever
    inet6 fe80::d55:8ac4:5086:a899/64 scope link noprefixroute 
       valid_lft forever preferred_lft forever

```
其中lo为默认网卡，enp0s3为【桥接网卡】，enp0s8为【共享网卡】。
```
[root@vworld network-scripts]#cd /etc/sysconfig/network-scripts/
[root@vworld network-scripts]#ls
ifcfg-enp0s3  ifdown-ippp    ifdown-sit       ifup-bnep  ifup-plusb   ifup-TeamPort
ifdown-ipv6    ifdown-Team      ifup-eth   ifup-post    ifup-tunnel
ifcfg-lo      ifdown-isdn    ifdown-TeamPort  ifup-ippp  ifup-ppp     ifup-wireless
ifdown        ifdown-post    ifdown-tunnel    ifup-ipv6  ifup-routes  init.ipv6-global
ifdown-bnep   ifdown-ppp     ifup             ifup-isdn  ifup-sit     network-functions
ifdown-eth    ifdown-routes  ifup-aliases     ifup-plip  ifup-Team    network-functions-ipv6
```
下面将共享网卡设置为固定IP，但是发现没有enp0s8网卡配置，我们就新增一个，同时配置固定IP。
```
[root@vworld network-scripts]# cp ifcfg-enp0s3 ifcfg-enp0s8
# 配置共享网卡IP
TYPE=Ethernet
PROXY_METHOD=none
BROWSER_ONLY=no
BOOTPROTO=static # 静态IP
DEFROUTE=yes
IPV4_FAILURE_FATAL=no
IPV6INIT=yes
IPV6_AUTOCONF=yes
IPV6_DEFROUTE=yes
IPV6_FAILURE_FATAL=no
IPV6_ADDR_GEN_MODE=stable-privacy
NAME=enp0s8
UUID=6dc95768-5f00-4d6b-acad-041888e74386 # UUID为了不重复，可以随便修改下。
DEVICE=enp0s8 # 名称
ONBOOT=yes # 开机启动

# 固定IP配置
IPADDR=192.168.66.111
GATEWAY=192.168.66.1
```

配置桥接网卡为开机启动
```

TYPE=Ethernet
PROXY_METHOD=none
BROWSER_ONLY=no
BOOTPROTO=dhcp
DEFROUTE=yes
IPV4_FAILURE_FATAL=no
IPV6INIT=yes
IPV6_AUTOCONF=yes
IPV6_DEFROUTE=yes
IPV6_FAILURE_FATAL=no
IPV6_ADDR_GEN_MODE=stable-privacy
NAME=enp0s3
UUID=5dc95768-5f00-4d6b-acad-041888e74386
DEVICE=enp0s3
ONBOOT=yes # 开机启动
```
重启后配置生效。

## MACOS下的设置
MacOS下的设置和windows的设置稍微有点不同。
1. 网卡1
![img.png](https://gitee.com/mvilplss/note/raw/master/image/VirtualBox网络设置实践/img.png)
   
2. 网卡2
![img.png](https://gitee.com/mvilplss/note/raw/master/image/VirtualBox网络设置实践/img2.png)
   
修改配置：
cd /etc/sysconfig/network-scripts
ifcfg-enp0s3
```shell
TYPE=Ethernet
PROXY_METHOD=none
BROWSER_ONLY=no
BOOTPROTO=dhcp
DEFROUTE=yes
IPV4_FAILURE_FATAL=no
IPV6INIT=yes
IPV6_AUTOCONF=yes
IPV6_DEFROUTE=yes
IPV6_FAILURE_FATAL=no
IPV6_ADDR_GEN_MODE=stable-privacy
NAME=enp0s3
UUID=55748483-a791-4a5f-acac-2cc8db27f73b
DEVICE=enp0s3
ONBOOT=yes
HWADDR=08:00:27:DB:F4:33
MACADDR=08:00:27:DB:F4:33
```
ifcfg-eth0
```shell
HWADDR=08:00:27:2D:CB:B6
TYPE=Ethernet
PROXY_METHOD=none
BROWSER_ONLY=no
BOOTPROTO=staic
DEFROUTE=yes
IPV4_FAILURE_FATAL=no
IPV6INIT=yes
IPV6_AUTOCONF=yes
IPV6_DEFROUTE=yes
IPV6_FAILURE_FATAL=no
IPV6_ADDR_GEN_MODE=stable-privacy
NAME="eth0"
UUID=a4cf419a-5fc7-3b9e-8f9d-bb31cf459d22
ONBOOT=yes
AUTOCONNECT_PRIORITY=-999

IPADDR=192.168.56.11
GATEWAY=192.168.56.1
```
## 参考
- https://www.jianshu.com/p/e84c19effeea

