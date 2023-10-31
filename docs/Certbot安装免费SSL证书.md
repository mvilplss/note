---
title: Certbot安装免费SSL证书
date: 2023-10-31
categories:
- 开发日常
tags:
- 电脑技巧
---
## 安装nginx
推荐使用yum安装linux
```
yum install nginx
```
## 安装Certbot
https://certbot.eff.org/
因为 Certbot 打包在 EPEL 中，所以在安装 Certbot 之前要先安装 EPEL
```
yum -y install epel-release
```
然后按着官网给出的步骤提示命令安装 Certbot
```
yum install python2-certbot-nginx
```
## 安装证书
自动安装证书
```
certbot --nginx
```
手动安装证书（只生成证书）
```
certbot certonly --nginx
```
自动更新证书
```
certbot renew
```
