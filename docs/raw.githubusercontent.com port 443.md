---
title: raw.githubusercontent.com port
date: 2020-01-02
categories: 
- 开发日常
tags: 
- 电脑技巧
---
# Failed to connect to raw.githubusercontent.com:443
我们在执行一些通过raw.githubusercontent.com进行安装的工具时候，总是会出现如下问题：
```
curl: (7) Failed to connect to raw.githubusercontent.com port 443: Connection refused
```
这里将提供两种解决方案，方案如下：
## 如果你在使用科学上网
如果你是科学上网，则执行下面命令：
```
# 7890 和 789 需要换成你自己的端口
export https_proxy=http://127.0.0.1:7890 http_proxy=http://127.0.0.1:7890 all_proxy=socks5://127.0.0.1:789
```
再次执行之前连接 http://raw.githubusercontent.com:443 被拒绝的命令应该就成功了。

## 其他方式
如果没有科学上网，则可以通过修改hosts来解决。
打开 https://www.ipaddress.com/ 输入访问不了的域名，获取对应的ip:199.232.68.133
然后修改host，在末尾增加以下内容：
```
199.232.68.133 raw.githubusercontent.com
199.232.68.133 user-images.githubusercontent.com
199.232.68.133 avatars2.githubusercontent.com
199.232.68.133 avatars1.githubusercontent.com
```
上面的IP换成你查出来的IP地址即可，修改host可参考：https://github.com/oldj/SwitchHosts




