---
title: Mac文件夹名称本地化
date: 2020-01-02
categories: 
- 开发日常
tags: 
- 电脑技巧
---
# Mac文件夹名称本地化
在使用mac的时候，自带的一些文件夹如：桌面，下载，文稿等在Finder中都是中文的，但是在命令行中看到的是英文的。作为一名开发者，创建的某些文件夹需要程序访问，为了防止中文问题，就用了英文，但是在Finder看着有点别扭，就想把这些文件也设置为本地化，下面将介绍方法。
## 首先关闭SPI权限
重启mac电脑，长按`command+r`进入系统引导界面，在左上角菜单中找到终端，输入以下命令关闭SPI保护：
```
csrutil disable
```
如果要恢复SPI保护则执行以下命令：
```
csrutil enable
```

## 文件夹本地化配置
首先创建一个英文的文件夹，已经创建的可以忽略。
```
mkdir Works
```
开始对文件夹Works配置本地化：
```
sudo mount -uw /

cd /System/Library/CoreServices/SystemFolderLocalizations/zh_CN.lproj

sudo /usr/libexec/PlistBuddy -c "Add 'Works' string '工作'" SystemFolderLocalizations.strings

cd /System/Library/CoreServices/SystemFolderLocalizations/en.lproj

sudo /usr/libexec/PlistBuddy -c "Add 'Works' string 'Works'" SystemFolderLocalizations.strings

```
本地化配置好后，进入Works创建本地化标示文件 .localized 文件
```
cd Works
touch .localized
```
重启Finder
```
pkill Finder
```
至此我们就操作完了，正常情况下你创建的那个英文文件夹就展示为中文的了，如果没有则重启系统在观察下。
备注：我操作的mac os为：Catalina
## 其他
查看某个文件夹名称是否有本地化配置：
```
cd /System/Library/CoreServices/SystemFolderLocalizations/zh_CN.lproj
/usr/libexec/PlistBuddy -c 'Print :'Works'' SystemFolderLocalizations.strings
```

如果要删除某个文件的本地化配置，操作如下：
```
cd /System/Library/CoreServices/SystemFolderLocalizations/zh_CN.lproj
/usr/libexec/PlistBuddy -c "Delete 'Works'" SystemFolderLocalizations.strings

cd /System/Library/CoreServices/SystemFolderLocalizations/en.lproj
/usr/libexec/PlistBuddy -c "Delete 'Works'" SystemFolderLocalizations.strings
```

## 参考
- 关于 PlistBuddy 的语法，详见 https://blog.csdn.net/chqj_163/article/details/102590609
