---
title: git的使用技巧
date: 2022-07-28
categories:
- 开发技术 
tags:
- java
- dubbo
copyright: true
cover: https://raw.githubusercontent.com/mvilplss/note/master/image/git.png
---
# 简介
git的各种使用技巧（持续更新）

# 使用技巧
## git的.ignore不生效
原因：在项目开发过程中个，一般都会添加 .gitignore 文件，规则很简单，但有时会发现，规则不生效。
原因是 .gitignore 只能忽略那些原来没有被track的文件，如果某些文件已经被纳入了版本管理中，则修改.gitignore是无效的。
那么解决方法就是先把本地缓存删除（改变成未track状态），然后再提交。
```
git rm -r --cached .
git add .
git commit -m 'update .gitignore'
```

# 参考
- https://git-scm.com/