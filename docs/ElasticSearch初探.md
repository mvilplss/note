---
title: ElasticSearch初探
date: 2020-12-03
categories:
    - 开发技术
tags:
    - java
    - ElasticSearch
copyright: true
---
# ElasticSearch搭建单机应用
## 下载解压
进入官网，下载安装最新的版：https://www.elastic.co/guide/en/elasticsearch/reference/current/targz.html
```shell
wget https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-7.12.1-linux-x86_64.tar.gz
wget https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-7.12.1-linux-x86_64.tar.gz.sha512
shasum -a 512 -c elasticsearch-7.12.1-linux-x86_64.tar.gz.sha512 
tar -xzf elasticsearch-7.12.1-linux-x86_64.tar.gz
cd elasticsearch-7.12.1/
```
## 启动
```shell
./bin/elasticsearch
```
启动异常1：
```shell
org.elasticsearch.bootstrap.StartupException: java.lang.RuntimeException: can not run elasticsearch as root
	at org.elasticsearch.bootstrap.Elasticsearch.init(Elasticsearch.java:163) ~[elasticsearch-7.12.1.jar:7.12.1]
	at org.elasticsearch.bootstrap.Elasticsearch.execute(Elasticsearch.java:150) ~[elasticsearch-7.12.1.jar:7.12.1]
	at org.elasticsearch.cli.EnvironmentAwareCommand.execute(EnvironmentAwareCommand.java:75) ~[elasticsearch-7.12.1.jar:7.12.1]
	at org.elasticsearch.cli.Command.mainWithoutErrorHandling(Command.java:116) ~[elasticsearch-cli-7.12.1.jar:7.12.1]
	at org.elasticsearch.cli.Command.main(Command.java:79) ~[elasticsearch-cli-7.12.1.jar:7.12.1]
	at org.elasticsearch.bootstrap.Elasticsearch.main(Elasticsearch.java:115) ~[elasticsearch-7.12.1.jar:7.12.1]
	at org.elasticsearch.bootstrap.Elasticsearch.main(Elasticsearch.java:81) ~[elasticsearch-7.12.1.jar:7.12.1]
Caused by: java.lang.RuntimeException: can not run elasticsearch as root
	at org.elasticsearch.bootstrap.Bootstrap.initializeNatives(Bootstrap.java:101) ~[elasticsearch-7.12.1.jar:7.12.1]
	at org.elasticsearch.bootstrap.Bootstrap.setup(Bootstrap.java:168) ~[elasticsearch-7.12.1.jar:7.12.1]
	at org.elasticsearch.bootstrap.Bootstrap.init(Bootstrap.java:397) ~[elasticsearch-7.12.1.jar:7.12.1]
	at org.elasticsearch.bootstrap.Elasticsearch.init(Elasticsearch.java:159) ~[elasticsearch-7.12.1.jar:7.12.1]
```
因为elasticsearch不能使用root账户启动，因此我们需要创建个账户进行启动：
```shell
# 添加elastic用户，并将文件放到elastic用户目录下
useradd elastic
mv elasticsearch-7.12.1 /home/elastic
cd /home/elastic

# 授权目录权限给elastic用户
su root
chown -R elastic:elastic elasticsearch-7.12.1

# 切换为elastic用户再次启动
su elastic
cd elasticsearch-7.12.1/

./bin/elasticsearch
```
启动异常2：
```shell
max virtual memory areas vm.max_map_count [65530] is too low, increase to at least [262144]
```
root用户修改配置limit.cnf配置
```shell
vi /etc/security/limits.conf 
```
增加以下两行
```shell
*               soft    nofile          65536
*               hard    nofile          65536
```

*重启Linux系统* 再次启动，测试是否成功

> 如果还有问题请参考：https://blog.csdn.net/happyzxs/article/details/89156068

```shell
curl localhost:9200

{
  "name" : "apple",
  "cluster_name" : "elasticsearch",
  "cluster_uuid" : "vsP40XZ7S06aM1DEcl2ibw",
  "version" : {
    "number" : "7.12.1",
    "build_flavor" : "default",
    "build_type" : "tar",
    "build_hash" : "3186837139b9c6b6d23c3200870651f10d3343b7",
    "build_date" : "2020-04-20T20:56:39.040728659Z",
    "build_snapshot" : false,
    "lucene_version" : "8.8.0",
    "minimum_wire_compatibility_version" : "6.8.0",
    "minimum_index_compatibility_version" : "6.0.0-beta1"
  },
  "tagline" : "You Know, for Search"
}
```
## 简单使用
为了快速入门，我们参考《ElasticSearch权威指南》上的示例，创建三个用户，然后对三个用户进行查询统计等操作。
使用之前我们要了解ElasticSearch也是一个数据库，分为索引，类型，文档，属性和关系型数据库对比关系如下：
```shell
Relational DB -> Databases -> Tables -> Rows -> Columns
Elasticsearch -> Indices -> Types -> Documents -> Fields
```
### 创建索引
```shell
PUT http://localhost:9200/db_test/user/1
{
	"first_name":"John",
	"last_name":"Smith",
	"age":25,
	"about":"i love to go rock climbing",
	"interests":["sports","music"]
}

# 返回
{
    "_index": "db_test",
    "_type": "user",
    "_id": "1",
    "_version": 1,
    "result": "created",
    "_shards": {
        "total": 2,
        "successful": 1,
        "failed": 0
    },
    "_seq_no": 0,
    "_primary_term": 1
}
```
其中`db_test`为索引（数据库），`user`为类型（表），`1`为文档的id。
接着我们再创建两个用户
```shell
PUT http://localhost:9200/db_test/user/2
{
	"first_name":"Jane",
	"last_name":"Smith",
	"age":32,
	"about":"i like to collect rock albums",
	"interests":["music"]
}
PUT http://localhost:9200/db_test/user/3
{
	"first_name":"Douglas",
	"last_name":"Fir",
	"age":35,
	"about":"i like to build cabinets",
	"interests":["forestry"]
}
```
### 查询索引
#### 查询所有索引
GET http://v11:9200/db_test/user/_search
```shell
{
    "took": 420,
    "timed_out": false,
    "_shards": {
        "total": 1,
        "successful": 1,
        "skipped": 0,
        "failed": 0
    },
    "hits": {
        "total": {
            "value": 3,
            "relation": "eq"
        },
        "max_score": 1.0,
        "hits": [
            {
                "_index": "db_test",
                "_type": "user",
                "_id": "1",
                "_score": 1.0,
                "_source": {
                    "first_name": "John",
                    "last_name": "Smith",
                    "age": 25,
                    "about": "i love to go rock climbing",
                    "interests": [
                        "sports",
                        "music"
                    ]
                }
            },
            {
                "_index": "db_test",
                "_type": "user",
                "_id": "2",
                "_score": 1.0,
                "_source": {
                    "first_name": "Jane",
                    "last_name": "Smith",
                    "age": 32,
                    "about": "i like to collect rock albums",
                    "interests": [
                        "music"
                    ]
                }
            },
            {
                "_index": "db_test",
                "_type": "user",
                "_id": "3",
                "_score": 1.0,
                "_source": {
                    "first_name": "Douglas",
                    "last_name": "Fir",
                    "age": 35,
                    "about": "i like to build cabinets",
                    "interests": [
                        "forestry"
                    ]
                }
            }
        ]
    }
}
```
#### 根据ID查询索引
GET http://v11:9200/db_test/user/2
```shell
{
    "_index": "db_test",
    "_type": "user",
    "_id": "2",
    "_version": 1,
    "_seq_no": 4,
    "_primary_term": 1,
    "found": true,
    "_source": {
        "first_name": "Jane",
        "last_name": "Smith",
        "age": 32,
        "about": "i like to collect rock albums",
        "interests": [
            "music"
        ]
    }
}
```
#### 根据某个属性查询索引
GET http://v11:9200/db_test/user/_search?q=last_name:Smith
```shell
{
    "took": 2,
    "timed_out": false,
    "_shards": {
        "total": 1,
        "successful": 1,
        "skipped": 0,
        "failed": 0
    },
    "hits": {
        "total": {
            "value": 2,
            "relation": "eq"
        },
        "max_score": 0.44183272,
        "hits": [
            {
                "_index": "db_test",
                "_type": "user",
                "_id": "1",
                "_score": 0.44183272,
                "_source": {
                    "first_name": "John",
                    "last_name": "Smith",
                    "age": 25,
                    "about": "i love to go rock climbing",
                    "interests": [
                        "sports",
                        "music"
                    ]
                }
            },
            {
                "_index": "db_test",
                "_type": "user",
                "_id": "2",
                "_score": 0.44183272,
                "_source": {
                    "first_name": "Jane",
                    "last_name": "Smith",
                    "age": 32,
                    "about": "i like to collect rock albums",
                    "interests": [
                        "music"
                    ]
                }
            }
        ]
    }
}
```
#### 使用DSL语句查询
GET http://v11:9200/db_test/user/_search
```shell
{
    "query": {
        "match": {
            "last_name": "Smith"
        }
    }
}
```
返回：
```shell
{
    "took": 4,
    "timed_out": false,
    "_shards": {
        "total": 1,
        "successful": 1,
        "skipped": 0,
        "failed": 0
    },
    "hits": {
        "total": {
            "value": 2,
            "relation": "eq"
        },
        "max_score": 0.44183272,
        "hits": [
            {
                "_index": "db_test",
                "_type": "user",
                "_id": "1",
                "_score": 0.44183272,
                "_source": {
                    "first_name": "John",
                    "last_name": "Smith",
                    "age": 25,
                    "about": "i love to go rock climbing",
                    "interests": [
                        "sports",
                        "music"
                    ]
                }
            },
            {
                "_index": "db_test",
                "_type": "user",
                "_id": "2",
                "_score": 0.44183272,
                "_source": {
                    "first_name": "Jane",
                    "last_name": "Smith",
                    "age": 32,
                    "about": "i like to collect rock albums",
                    "interests": [
                        "music"
                    ]
                }
            }
        ]
    }
}
```

#### 高级查询
GET http://v11:9200/db_test/user/_search
```shell
{
    "query" : {
        "bool": {
            "must": {
                "match" : {
                    "last_name" : "smith" 
                }
            },
            "filter": {
                "range" : {
                    "age" : { "gt" : 30 } 
                }
            }
        }
    }
}
```
返回：
```shell
{
    "took": 50,
    "timed_out": false,
    "_shards": {
        "total": 1,
        "successful": 1,
        "skipped": 0,
        "failed": 0
    },
    "hits": {
        "total": {
            "value": 1,
            "relation": "eq"
        },
        "max_score": 0.4700036,
        "hits": [
            {
                "_index": "db_test",
                "_type": "user",
                "_id": "2",
                "_score": 0.4700036,
                "_source": {
                    "first_name": "Jane",
                    "last_name": "Smith",
                    "age": 32,
                    "about": "i like to collect rock albums",
                    "interests": [
                        "music"
                    ]
                }
            }
        ]
    }
}
```
#### 全文搜索
到目前为止搜索都很简单:简单的名字，通过年龄筛选。让我们尝试一种更高级的搜索，全文搜索——一种传统数据库很难现的功能。
```shell
GET http://v11:9200/db_test/user/_search
{
    "query": {
        "match": {
            "about": "rock climbing"
        }
    }
}
```
返回：
```shell
{
    "took": 24,
    "timed_out": false,
    "_shards": {
        "total": 1,
        "successful": 1,
        "skipped": 0,
        "failed": 0
    },
    "hits": {
        "total": {
            "value": 2,
            "relation": "eq"
        },
        "max_score": 1.4167401,
        "hits": [
            {
                "_index": "db_test",
                "_type": "user",
                "_id": "1",
                "_score": 1.4167401,
                "_source": {
                    "first_name": "John",
                    "last_name": "Smith",
                    "age": 25,
                    "about": "i love to go rock climbing",
                    "interests": [
                        "sports",
                        "music"
                    ]
                }
            },
            {
                "_index": "db_test",
                "_type": "user",
                "_id": "2",
                "_score": 0.4589591,
                "_source": {
                    "first_name": "Jane",
                    "last_name": "Smith",
                    "age": 32,
                    "about": "i like to collect rock albums",
                    "interests": [
                        "music"
                    ]
                }
            }
        ]
    }
}
```
你可以看到我们使用与之前一致的 match 查询搜索 about 字段中的"rock climbing"，我们会得到两个匹配文档，这是因为搜索引擎进行搜索时候进行了分词，然后根据分词结果查询出来结果，然后根据匹配度评分进行倒叙排列。

#### 短语查询
```shell
GET http://v11:9200/db_test/user/_search
{
    "query": {
        "match_phrase": {
            "about": "rock climbing"
        }
    }
}
```
毫无疑问，返回包含短语的文档：
```shell
{
    "took": 23,
    "timed_out": false,
    "_shards": {
        "total": 1,
        "successful": 1,
        "skipped": 0,
        "failed": 0
    },
    "hits": {
        "total": {
            "value": 1,
            "relation": "eq"
        },
        "max_score": 1.4167401,
        "hits": [
            {
                "_index": "db_test",
                "_type": "user",
                "_id": "1",
                "_score": 1.4167401,
                "_source": {
                    "first_name": "John",
                    "last_name": "Smith",
                    "age": 25,
                    "about": "i love to go rock climbing",
                    "interests": [
                        "sports",
                        "music"
                    ]
                }
            }
        ]
    }
}
```
#### 高亮我们的搜索
```shell
GET http://v11:9200/db_test/user/_search
{
    "query": {
        "match_phrase": {
            "about": "rock climbing"
        }
    },
    "highlight": {
        "fields": {
            "about": {}
        }
    }
}
```
返回带有高亮标示的结果：
```shell
{
    "took": 83,
    "timed_out": false,
    "_shards": {
        "total": 1,
        "successful": 1,
        "skipped": 0,
        "failed": 0
    },
    "hits": {
        "total": {
            "value": 1,
            "relation": "eq"
        },
        "max_score": 1.4167401,
        "hits": [
            {
                "_index": "db_test",
                "_type": "user",
                "_id": "1",
                "_score": 1.4167401,
                "_source": {
                    "first_name": "John",
                    "last_name": "Smith",
                    "age": 25,
                    "about": "i love to go rock climbing",
                    "interests": [
                        "sports",
                        "music"
                    ]
                },
                "highlight": {
                    "about": [
                        "i love to go <em>rock</em> <em>climbing</em>"
                    ]
                }
            }
        ]
    }
}
```
####
#### 覆盖更新索引
```shell
PUT http://v11:9200/db_test/user/2
{
    "first_name": "Jane",
    "last_name": "Smith",
    "age": 18,
    "about": "i like to collect rock albums",
    "interests": [
        "music"
    ]
}
```
更新操作会递增索引的版本'_version'，如下：
```shell
{
    "_index": "db_test",
    "_type": "user",
    "_id": "2",
    "_version": 2,
    "result": "updated",
    "_shards": {
        "total": 2,
        "successful": 1,
        "failed": 0
    },
    "_seq_no": 6,
    "_primary_term": 1
}
```
#### 局部更新
```shell
POST http://v11:9200/db_test/user/3/_update
{
	"doc":{
        "about":"i love coding"
    }
}
```
局部更新结果：
```shell
{
    "_index": "db_test",
    "_type": "user",
    "_id": "3",
    "_version": 2,
    "result": "updated",
    "_shards": {
        "total": 2,
        "successful": 1,
        "failed": 0
    },
    "_seq_no": 8,
    "_primary_term": 1
}
```

### 删除索引
```shell
DELETE http://v11:9200/db_test/user/2
```
删除索引ElasticSearch不会立即删除此索引的，他会升级他的版本号，并标记删除：
```shell
{
    "_index": "db_test",
    "_type": "user",
    "_id": "2",
    "_version": 3,
    "result": "deleted",
    "_shards": {
        "total": 2,
        "successful": 1,
        "failed": 0
    },
    "_seq_no": 7,
    "_primary_term": 1
}
```


