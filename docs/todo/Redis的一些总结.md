---
title: Redis的一些总结
date: 2020-12-22
categories:
- 开发技术
tags:
- redis 
copyright: true
cover: https://gitee.com/mvilplss/note/raw/master/image/dubbo1.png
---
# 什么是redis
> 我觉得官方解释的是最精准的，所以就引用原文了。

Redis is an open source (BSD licensed), in-memory data structure store, used as a database, cache, and message broker. Redis provides data structures such as strings, hashes, lists, sets, sorted sets with range queries, bitmaps, hyperloglogs, geospatial indexes, and streams. Redis has built-in replication, Lua scripting, LRU eviction, transactions, and different levels of on-disk persistence, and provides high availability via Redis Sentinel and automatic partitioning with Redis Cluster.

# redis的一些数据结构


# redis的一些特性

## 事务

## pipeline

# redis的扩容方式

# redis的缓存淘汰策略

# redis的数据持久化aof和rdb

# redis的高可用实现方式

## 哨兵模式

## 集群切片模式

# redis的一些案例
## 限流

## 锁

## 消息

# redis作为缓存的一些问题
## 缓存穿透

## 缓存击穿

## 缓存崩溃

# 参考资料
- 《Redis开发与运维》(付磊)
- https://time.geekbang.org/column/article/268247
