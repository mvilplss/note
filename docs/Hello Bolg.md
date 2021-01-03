---
title: Hello Bolg
date: 2017-01-02
categories: 
- 开发日常
tags: 
- 电脑技巧
---
## hexo install
### 安装brew
```
/usr/bin/ruby -e "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/master/install)"
```
### 安装git
```
brew install git
```
### 安装node
```
brew install node
```
### 安装hexo
```
npm install -g hexo-cli
```
### 初始化hexo
```
hexo init hexo
```
### 开启hexo服务器
```
hexo s
```
进入主页：http://localhost:4000/ 


## Quick Start

### Create a new post

``` bash
$ hexo new "My New Post"
```

More info: [Writing](https://hexo.io/docs/writing.html)

### Run server

``` bash
$ hexo server
```

More info: [Server](https://hexo.io/docs/server.html)

### Generate static files

``` bash
$ hexo generate
```

More info: [Generating](https://hexo.io/docs/generating.html)

### Deploy to remote sites

``` bash
$ hexo deploy
```

More info: [Deployment](https://hexo.io/docs/deployment.html)

## ERROR
ERROR Deployer not found: git
执行以下命令：
```
npm install hexo-deployer-git --save
```
extends includes/layout.pug block content include includes/recent-posts.pug include includes/partial
执行以下命令：
```
npm install --save hexo-renderer-jade hexo-generator-feed hexo-generator-sitemap hexo-browsersync hexo-generator-archive
```
## 增加标题数字
```yaml
heading_index:
  enable: true
  index_styles: "{1} {1} {1} {1} {1} {1}"
  connector: "."
  global_prefix: ""
  global_suffix: ". "
```