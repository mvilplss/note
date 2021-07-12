---
title: ES和LogStash结合导入mysql数据
date: 2021-01-04
categories:
    - 开发技术
tags:
    - java
    - ElasticSearch
    - LogStash
copyright: true
---
# ES和LogStash结合导入mysql数据
我们安装和启动好ES，学会了ES的用法后，现在要应用到我们实际的项目中，我们如何将现有的数据在ES中创建索引呢？下面将介绍两种方式，通过ES的api直接索引和通过LogStash组件进行索引。

## 通过ES的API进行索引
本示例我们采用的Java语言开发，使用maven构建的工程，我们先把api相关依赖加入pom.xml
```shell
        <dependency>
            <groupId>org.elasticsearch.client</groupId>
            <artifactId>elasticsearch-rest-high-level-client</artifactId>
            <version>7.12.1</version>
        </dependency>
        <dependency>
            <groupId>org.elasticsearch</groupId>
            <artifactId>elasticsearch</artifactId>
            <version>7.12.1</version>
        </dependency>
```
创建索引
```shell
// 创建高版本客户端
RestHighLevelClient client = new RestHighLevelClient(RestClient.builder(new HttpHost("localhost", 9200, "http")));
// 创建索引对象
IndexRequest indexRequest = new IndexRequest("posts")
                .index("db_test")
                .type("user")
                .id("5")
                .source("user", "kimchy",
                        "postDate", null,
                        "message", "trying out Elasticsearch");
// 调用客户端创建索引
IndexResponse index = client.index(indexRequest, RequestOptions.DEFAULT);
// 关闭索引
client.close();                    
```
通过API创建索引的逻辑比较简单，关于查询索引，删除索引，更新索引请参考官方文档：https://www.elastic.co/guide/en/elasticsearch/client/java-rest/current/index.html

## LogStash导入数据
### 下载安装LogStash
下面我们先下载安装LogStash，下载地址：https://www.elastic.co/cn/downloads/logstash
```shell
wget https://artifacts.elastic.co/downloads/logstash/logstash-7.12.1-linux-x86_64.tar.gz
tail -zxvf logstash-7.12.1-linux-x86_64.tar.gz
```
运行测试
```shell
cd logstash-7.12.1
./bin/logstash -e 'input { stdin { } } output { stdout {} }'
```
运行完成后，我们控制台输入hello logstash。
```shell
The stdin plugin is now waiting for input:
hello logstash
{
      "@version" => "1",
    "@timestamp" => 2020-05-14T03:56:07.035Z,
       "message" => "hello logstash",
          "host" => "apple"
}
```
上面执行过程就是：输入（hello logstash）》通过logstash管道经过多个过滤器加工数据》输出结果。
### 导入mysql数据
为了方便演示，我创建了一个tb_article表，并初始化了一些数据：
```sql
create table tb_article(
                           id int(11) auto_increment primary key ,
                           article varchar(128) comment '文章',
                           tilte varchar(64) comment '标题',
                           author varchar(16) comment '作者'
);

insert into tb_article(article, tilte, author) VALUES ('所有人都要得死，所有人都要奉承。这是出自《权利的游戏》电影中人们常说的一句话。','名言','张三'),
                                                      ('现在写在代码就是为了将来可以可写代码。','哲理','李四'),
                                                      ('当发生雪崩时，没有一片雪花是无辜的；这个观点的角度应该是上帝视角。','哲理','张三');
```
导入mysql的数据前，我们要准备mysql驱动，存放位置要写到下面的配置中

编写logstash数据处理配置
```shell
# 创建一个conf文件，将下面配置写入
vi config/mysql.conf

input {
         stdin {}
         jdbc {
               # mysql 数据库链接,shop为数据库名
               jdbc_connection_string => "jdbc:mysql://172.21.24.215:3306/article"
               # 用户名和密码
               jdbc_user => "root"
               jdbc_password => "root"
               # 驱动
               jdbc_driver_library => "/root/Downloads/logstash-7.12.1/lib/mlib/mysql-connector-java-8.0.24/mysql-connector-java-8.0.24.jar"
               # 驱动类名
               jdbc_driver_class => "com.mysql.cj.jdbc.Driver"
               jdbc_paging_enabled => "true"
               jdbc_page_size => "100"
               # 执行的sql 文件路径+名称
               #statement_filepath => "/root/Downloads/logstash-7.12.1/config/sql/tbinfo.sql"
               statement => "select * from tb_article"
               # 设置监听间隔  各字段含义（由左至右）分、时、天、月、年，全部为*默认含义为每分钟都更新
               schedule => "* * * * *"
               # 索引类型
               type => "article"
         }
   }
   filter {
	   json {
			source => "message"
			remove_field => ["message"]
		}
    }
     output {
         if [type]=="article"{
             elasticsearch {
                 hosts => ["localhost:9200"]
                 index => "article"
                 document_id => "%{id}"
             }
         }
         stdout {
               codec => "json_lines"
        }
    }
```
启动logstash
```shell
./bin/logstash -f config/mysql.conf
```
日志会输出导入ES的json数据。
```shell
{"@version":"1","type":"article","@timestamp":"2020-05-14T07:32:08.869Z","id":"1","article":"所有人都要得死，所有人都要奉承。这是出自《权利的游戏》电影中人们常说的一句话。","title":"名言","author":"张三"}

{"@version":"1","type":"article","@timestamp":"2020-05-14T07:32:08.869Z","id":"1","article":"现在写在代码就是为了将来可以可写代码。","title":"哲理","author":"李四"}

{"@version":"1","type":"article","@timestamp":"2020-05-14T07:32:08.869Z","id":"1","article":"当发生雪崩时，没有一片雪花是无辜的；这个观点的角度应该是上帝视角。","title":"哲理","author":"张三"}

```
到此我们已经将mysql的数据导入了ES中。
### 通过binlog进行同步
参考文章：https://cloud.tencent.com/document/product/845/35562

## 怎么快速把mysql的大数据量导入ES？
线上有个场景，就是有两千多万的一个订单数据，通过上述的mysql导入太慢，每秒导入100条数据，大约需要56小时，耗时主要是jdbc的IO和ES的写入数据IO。
优化方案是减少网络IO：
1. 先将数据库数据导出cvs文件中，避免jdbc的IO。
2. 批量写入ES，减少IO次数。

导出mysql数据:
```shell
mysql> SELECT * FROM tb_article 
    -> INTO OUTFILE 'tb_article.cvs';
```
增加logstash的cvs导入配置：
```shell
  input {
         file {
           path => ["/Users/atomic/Desktop/tb_article.csv"]
           start_position => "beginning",
           type => "article"
         }
   }
   filter {
    csv{
        separator => ","
        columns => ["id","article","title","author"]
        skip_empty_columns => true
        remove_field => ["host", "tags", "path", "message"]
       }
    mutate{
            convert => {
              "id" => "integer"
              "article" => "string"
              "title" => "string"
              "author" => "string"
            }
      }
    }
     output {
         if [type]=="article"{
             elasticsearch {
                 hosts => ["localhost:9200"]
                 index => "article"
                 document_id => "%{id}"
             }
         }
         stdout {
           codec => "json_lines"
          }      
    }
```
执行导入：
```shell
./bin/logstash -f config/input/tb_artical.conf
```
此时，每秒写入约1W数据，处理两千万数据只需要半个小时，在可接受范围内。
# 参考文章
- https://cloud.tencent.com/developer/article/1647080
- https://www.elastic.co/cn/downloads/logstash
- https://blog.csdn.net/pengge2/article/details/114585863
- LogStash性能优化 http://www.leiyawu.com/2018/04/13/logstash%E4%BC%98%E5%8C%96/

