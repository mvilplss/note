---
title: Mac绿色安装mysql数据库
date: 2020-01-05
categories: 
- 开发日常
tags: 
- 电脑技巧
---

# Mac绿色安装mysql数据库
## 下载msyql服务
下载地址：https://dev.mysql.com/downloads/mysql/
选择`macOS 10.15 (x86, 64-bit), Compressed TAR Archive` 版本进行下载
## 数据库安装配置
解压下载的mysql数据库，然后拷贝到一个你喜欢的目录下，如：/Users/atomic/.app/mysql
```shell
# 移动到目标目录下
mv mysql-8.0.22-macos10.15-x86_64 /Users/atomic/.app/mysql
```
修改新目录的所属用户和用户组
```shell
sudo chown -R atomic:wheel /Users/atomic/.app/mysql
```
初始化mysql
```shell
cd /Users/atomic/.app/mysql/bin
./mysqld --initialize
```
初始化后会打印初始化日志，同时也会打印出初始化后的随机密码，这是登入mysql的密码，**需要记下来**
![img.png](https://raw.githubusercontent.com/mvilplss/note/master/image/img.png)

修改启动脚本`mysql.server`，指定basedir和datadir的目录
```shell
vi /Users/atomic/.app/mysql/support-files/mysql.server

# 设置basedir和datadir的目录
basedir=/Users/atomic/.app/mysql
datadir=/Users/atomic/.app/mysql/data
```

## 启动mysql服务
```shell
cd /Users/atomic/.app/mysql

# 启动
sudo support-files/mysql.server start

# 重启
sudo support-files/mysql.server restart

# 停止
sudo support-files/mysql.server stop

# 检查 MySQL 运行状态
sudo support-files/mysql.server status
```
## 登录
```shell
cd /Users/atomic/.app/mysql/bin
./mysql -u root -p
```
<your-password>（初始化时候随机生成的密码）

## 修改密码
```mysql
# 修改密码
SET PASSWORD = PASSWORD('your new password');
# 设置密码有效期为永久
ALTER USER 'root'@'localhost' PASSWORD EXPIRE NEVER;
# 刷新权限
flush privileges;
```
## 其他
如果启动mysql服务的时候出现问题，先在进程中关闭与mysql有关的进程。在应用程序中打开其他找到活动监视器。关闭相关进程即可。

## 参考
- 更简单一种方法：https://blog.csdn.net/F1300684594/article/details/54647306
