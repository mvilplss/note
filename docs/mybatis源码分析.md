---
title: mybatis源码分析
date: 2019-01-04
categories: 
- 开发技术
tags: 
- mybatis
copyright: true
cover: https://gitee.com/mvilplss/note/raw/master/image/mybatis架构.jpeg
---

# 概要
本文通过通过jdbc到mybatis查询数据库代码来分析mybatis的优势，同时进一步分析mybatis的源码，分析mybatis的整体架构设计，最后再对mybatis的缓存进行剖析。

# JDBC与mybatis
下面介绍jdbc和mybatis操作数据库的方式。

## jdbc访问数据库
通过jdbc直接操作数据库：
```
    @Test
    public void jdbcTest() throws Exception{
        // 1.获取链接
        Connection connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/test_db", "root", "root");
        // 2.准备会话
        PreparedStatement statement = connection.prepareStatement("select * from tb_user");
        // 3.执行查询
        statement.execute();
        // 4.获取查询结果
        ResultSet resultSet = statement.getResultSet();
        // 5.封装结果到对象中
        List<User> users = new ArrayList<>();
        while (resultSet.next()){
            Object id = resultSet.getObject("id");
            Object name = resultSet.getObject("name");
            User user = new User();
            user.setId(Long.valueOf(id.toString()));
            user.setName(name.toString());
            users.add(user);
        }
        System.out.println(users);
        // 6.关闭相关资源
        resultSet.close();
        statement.close();
        connection.close();
    }
```
通过实例可以看出，jdbc对数据库的操作有多处可以改进：
1. 获取链接可以交给数据库连接池来实现，不用每次都打开一个链接。
2. 准备会话的sql可以放置到配置文件中，实现统一管理。
3. 获取查询结果时可以增加结果处理器来实现返回结果自动封装到对象中。
4. 查询的结果可以放到缓存中，提高查询效率。
5. 关闭资源可以交给动态代理来实现自动关闭。

## mybatis访问数据
创建一个maven工程，然后加入mybaits依赖：
```
<dependency>
  <groupId>org.mybatis</groupId>
  <artifactId>mybatis</artifactId>
  <version>x.x.x</version>
</dependency>
```

### 增加mybatis的配置项
```
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE configuration
  PUBLIC "-//mybatis.org//DTD Config 3.0//EN"
  "http://mybatis.org/dtd/mybatis-3-config.dtd">
<configuration>
  <environments default="development">
    <environment id="development">
      <transactionManager type="JDBC"/>
      <dataSource type="POOLED">
        <property name="driver" value="${driver}"/>
        <property name="url" value="${url}"/>
        <property name="username" value="${username}"/>
        <property name="password" value="${password}"/>
      </dataSource>
    </environment>
  </environments>
  <mappers>
    <mapper resource="org/mybatis/example/BlogMapper.xml"/>
  </mappers>
</configuration>
```
通过配置可以管理数据库的链接配置，数据源的配置，同时还支持插件开发和配置。

### 增加Mapper接口类
```
public interface UserMapper {
    List<User> selectAll();
}
```
通过编写接口来关联查询的具体语句，实现面向对象开发。

### 增加对应Mapper.xml配置
```
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper
        PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.mybatis.demo.UserMapper">
    <resultMap id="BaseResultMap" type="com.mybatis.demo.User">
        <result column="id" property="id"/>
        <result column="name" property="name"/>
    </resultMap>
    <select id="selectAll" resultMap="BaseResultMap">
    select * from tb_user where id = #{id}
  </select>
</mapper>
```
配置读经的Mapper.xml，将sql放到配置文件中。同时可以配置查询是否开启缓存等可以参考官方文档。
### 编写测试代码：
```
    @Test
    public void mybatisTest() throws Exception{
        InputStream inputStream = Resources.getResourceAsStream("mybatis/mybatis-config.xml");
        DefaultSqlSessionFactory sqlSessionFactory = (DefaultSqlSessionFactory) new SqlSessionFactoryBuilder().build(inputStream);
        SqlSession sqlSession = sqlSessionFactory.openSession();
        UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
        System.out.println(userMapper.selectAll());
    }
```
执行结果：
```
54:48:375|DEBUG|main|137|Opening JDBC Connection
54:49:888|DEBUG|main|406|Created connection 1474957626.
54:49:889|DEBUG|main|101|Setting autocommit to false on JDBC Connection [com.mysql.cj.jdbc.ConnectionImpl@57ea113a]
54:49:892|DEBUG|main|159|==>  Preparing: select * from tb_user 
54:49:958|DEBUG|main|159|==> Parameters: 
54:49:997|DEBUG|main|159|<==      Total: 3
[User(id=1, name=a), User(id=2, name=b), User(id=3, name=c)]
```

## 总结
mybtis的优点：
- 数据库连接可以通过配置指定的数据源来管理
- sql语句与代码分离，存放于xml配置文件中
- 通过映射实现通过接口直接调用方法来操作数据库
- 增加缓存，提高数据的查询效率
- 自动封装数据查询结果

# mybatis源码分析
通过上面mybatis的例子，我们来进一步分析下对应的源码。
## 读取配置

```
    // 根据指定类加载器》默认类加载器》当前线程类加载器》系统类加载器 顺序创建一个类加载器数组
    ClassLoader[] getClassLoaders(ClassLoader classLoader) {
      return new ClassLoader[]{
          classLoader,
          defaultClassLoader,
          Thread.currentThread().getContextClassLoader(),
          getClass().getClassLoader(),
          systemClassLoader};
    }
    // 循环类加载器，如果获取到配置内容则返回
   InputStream getResourceAsStream(String resource, ClassLoader[] classLoader) {
      for (ClassLoader cl : classLoader) {
        if (null != cl) {
          // try to find the resource as passed
          InputStream returnValue = cl.getResourceAsStream(resource);
  
          // now, some class loaders want this leading "/", so we'll add it and try again if we didn't find the resource
          if (null == returnValue) {
            returnValue = cl.getResourceAsStream("/" + resource);
          }
  
          if (null != returnValue) {
            return returnValue;
          }
        }
      }
      return null;
    }
```
通过Resources.getResourceAsStream读取配置的最终原理由classload来加载文件流。

## 创建DefaultSqlSessionFactory工厂对象

org.apache.ibatis.session.SqlSessionFactoryBuilder#build方法来构建SqlSessionFactory对象
```
  public SqlSessionFactory build(InputStream inputStream, String environment, Properties properties) {
    try {
      XMLConfigBuilder parser = new XMLConfigBuilder(inputStream, environment, properties);
      return build(parser.parse());
    } catch (Exception e) {
      throw ExceptionFactory.wrapException("Error building SqlSession.", e);
    } finally {
      ErrorContext.instance().reset();
      try {
        inputStream.close();
      } catch (IOException e) {
        // Intentionally ignore. Prefer previous error.
      }
    }
  }
```
在构建new XMLConfigBuilder()对象时候,将文件流解析为Document
```
// 通过JDK的javax.xml.parsers解析配置为Document
private Document createDocument(InputSource inputSource) {
    // important: this must only be called AFTER common constructor
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setValidating(validation);

      factory.setNamespaceAware(false);
      factory.setIgnoringComments(true);
      factory.setIgnoringElementContentWhitespace(false);
      factory.setCoalescing(false);
      factory.setExpandEntityReferences(true);

      DocumentBuilder builder = factory.newDocumentBuilder();
      builder.setEntityResolver(entityResolver);
      builder.setErrorHandler(new ErrorHandler() {
        @Override
        public void error(SAXParseException exception) throws SAXException {
          throw exception;
        }

        @Override
        public void fatalError(SAXParseException exception) throws SAXException {
          throw exception;
        }

        @Override
        public void warning(SAXParseException exception) throws SAXException {
        }
      });
      return builder.parse(inputSource);
    } catch (Exception e) {
      throw new BuilderException("Error creating document instance.  Cause: " + e, e);
    }
  }
```

构建SqlSessionFactory对象前，需要通过org.apache.ibatis.builder.xml.XMLConfigBuilder#parse解析配置到Configuration中
```
// 将配置读取到Configuration中
public Configuration parse() {
  if (parsed) {
    throw new BuilderException("Each XMLConfigBuilder can only be used once.");
  }
  parsed = true;
  parseConfiguration(parser.evalNode("/configuration"));
  return configuration;
}

// XMLConfigBuilder 解析每个配置元素到Configuration中
  private void parseConfiguration(XNode root) {
    try {
      //issue #117 read properties first
      propertiesElement(root.evalNode("properties"));
      Properties settings = settingsAsProperties(root.evalNode("settings"));
      loadCustomVfs(settings);
      typeAliasesElement(root.evalNode("typeAliases"));
      pluginElement(root.evalNode("plugins"));// 解析并配置拦截器
      objectFactoryElement(root.evalNode("objectFactory"));
      objectWrapperFactoryElement(root.evalNode("objectWrapperFactory"));
      reflectorFactoryElement(root.evalNode("reflectorFactory"));
      settingsElement(settings);
      // read it after objectFactory and objectWrapperFactory issue #631
      environmentsElement(root.evalNode("environments"));
      databaseIdProviderElement(root.evalNode("databaseIdProvider"));
      typeHandlerElement(root.evalNode("typeHandlers"));
      mapperElement(root.evalNode("mappers"));// mapper元素解析(重点)
    } catch (Exception e) {
      throw new BuilderException("Error parsing SQL Mapper Configuration. Cause: " + e, e);
    }
  }

```
mapper元素解析，根据mapper的配置（resouce,url,class)来使用不同的读取方式获取到mapper文件，使用XMLMapperBuilder解析或者直接获取class加入到configurution中。
```
  // org.apache.ibatis.builder.xml.XMLConfigBuilder#mapperElement
  // mapper元素解析，包括resource,url(支持远程）,class
  private void mapperElement(XNode parent) throws Exception {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        if ("package".equals(child.getName())) {
          String mapperPackage = child.getStringAttribute("name");
          configuration.addMappers(mapperPackage);
        } else {
          String resource = child.getStringAttribute("resource");
          String url = child.getStringAttribute("url");
          String mapperClass = child.getStringAttribute("class");
          if (resource != null && url == null && mapperClass == null) {
            ErrorContext.instance().resource(resource);
            InputStream inputStream = Resources.getResourceAsStream(resource);
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, resource, configuration.getSqlFragments());
            mapperParser.parse();
          } else if (resource == null && url != null && mapperClass == null) {
            ErrorContext.instance().resource(url);
            InputStream inputStream = Resources.getUrlAsStream(url);
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, url, configuration.getSqlFragments());
            mapperParser.parse();
          } else if (resource == null && url == null && mapperClass != null) {
            Class<?> mapperInterface = Resources.classForName(mapperClass);
            configuration.addMapper(mapperInterface);
          } else {
            throw new BuilderException("A mapper element may only specify a url, resource or class, but not more than one.");
          }
        }
      }
    }
  }

  // org.apache.ibatis.binding.MapperRegistry#addMapper 增加Mapper到knownMappers中，同时创建对应的MapperProxyFactory代理工厂类。
  public <T> void addMapper(Class<T> type) {
    if (type.isInterface()) {
      if (hasMapper(type)) {
        throw new BindingException("Type " + type + " is already known to the MapperRegistry.");
      }
      boolean loadCompleted = false;
      try {
        knownMappers.put(type, new MapperProxyFactory<T>(type));
        // It's important that the type is added before the parser is run
        // otherwise the binding may automatically be attempted by the
        // mapper parser. If the type is already known, it won't try.
        // 解析通过注解实现的sql映射。
        MapperAnnotationBuilder parser = new MapperAnnotationBuilder(config, type);
        parser.parse();
        loadCompleted = true;
      } finally {
        // 注解解析失败则移出
        if (!loadCompleted) {
          knownMappers.remove(type);
        }
      }
    }
  }

```

url或resource方式的资源配置的mapper的解析
```
  // org.apache.ibatis.builder.xml.XMLMapperBuilder#parse
  public void parse() {
    if (!configuration.isResourceLoaded(resource)) {
      // 1. 解析mapper配置到configuration中
      configurationElement(parser.evalNode("/mapper"));
      configuration.addLoadedResource(resource);
      // 2. 根据命名空间来实现Mapper接口和mapper配置进行绑定
      bindMapperForNamespace();
    }
    // 3. 解析结果映射
    parsePendingResultMaps();
    // 4. 解析缓存配置
    parsePendingCacheRefs();
    parsePendingStatements();
  }

  // 解析mapper.xml内容,包括命名空间,缓存配置,参数,结果映射,sql
  private void configurationElement(XNode context) {
    try {
      String namespace = context.getStringAttribute("namespace");
      if (namespace == null || namespace.equals("")) {
        throw new BuilderException("Mapper's namespace cannot be empty");
      }
      builderAssistant.setCurrentNamespace(namespace);
      cacheRefElement(context.evalNode("cache-ref"));
      cacheElement(context.evalNode("cache"));
      parameterMapElement(context.evalNodes("/mapper/parameterMap"));
      resultMapElements(context.evalNodes("/mapper/resultMap"));
      sqlElement(context.evalNodes("/mapper/sql"));
      // 创建XMLStatementBuilder并放入Configurtion中
      buildStatementFromContext(context.evalNodes("select|insert|update|delete"));
    } catch (Exception e) {
      throw new BuilderException("Error parsing Mapper XML. The XML location is '" + resource + "'. Cause: " + e, e);
    }
  }
  // 创建XMLStatementBuilder对象
  private void buildStatementFromContext(List<XNode> list, String requiredDatabaseId) {
    for (XNode context : list) {
      final XMLStatementBuilder statementParser = new XMLStatementBuilder(configuration, builderAssistant, context, requiredDatabaseId);
      try {
        statementParser.parseStatementNode();
      } catch (IncompleteElementException e) {
        configuration.addIncompleteStatement(statementParser);
      }
    }
  }

```

org.apache.ibatis.builder.xml.XMLStatementBuilder#parseStatementNode,解析Statement，包含生成MappedStatement对象，这个方法比较复杂，我们只要知道这个方法是读取Mapper的所有配置最终创建MappedStatement，然后赋值给configuration对象。
```
  public void parseStatementNode() {
    String id = context.getStringAttribute("id");
    String databaseId = context.getStringAttribute("databaseId");

    if (!databaseIdMatchesCurrent(id, databaseId, this.requiredDatabaseId)) {
      return;
    }

    Integer fetchSize = context.getIntAttribute("fetchSize");
    Integer timeout = context.getIntAttribute("timeout");
    String parameterMap = context.getStringAttribute("parameterMap");
    String parameterType = context.getStringAttribute("parameterType");
    Class<?> parameterTypeClass = resolveClass(parameterType);
    String resultMap = context.getStringAttribute("resultMap");
    String resultType = context.getStringAttribute("resultType");
    String lang = context.getStringAttribute("lang");
    LanguageDriver langDriver = getLanguageDriver(lang);

    Class<?> resultTypeClass = resolveClass(resultType);
    String resultSetType = context.getStringAttribute("resultSetType");
    StatementType statementType = StatementType.valueOf(context.getStringAttribute("statementType", StatementType.PREPARED.toString()));
    ResultSetType resultSetTypeEnum = resolveResultSetType(resultSetType);

    String nodeName = context.getNode().getNodeName();
    SqlCommandType sqlCommandType = SqlCommandType.valueOf(nodeName.toUpperCase(Locale.ENGLISH));
    boolean isSelect = sqlCommandType == SqlCommandType.SELECT;
    boolean flushCache = context.getBooleanAttribute("flushCache", !isSelect);
    boolean useCache = context.getBooleanAttribute("useCache", isSelect);
    boolean resultOrdered = context.getBooleanAttribute("resultOrdered", false);

    // Include Fragments before parsing
    XMLIncludeTransformer includeParser = new XMLIncludeTransformer(configuration, builderAssistant);
    includeParser.applyIncludes(context.getNode());

    // Parse selectKey after includes and remove them.
    processSelectKeyNodes(id, parameterTypeClass, langDriver);
    
    // Parse the SQL (pre: <selectKey> and <include> were parsed and removed)
    SqlSource sqlSource = langDriver.createSqlSource(configuration, context, parameterTypeClass);
    String resultSets = context.getStringAttribute("resultSets");
    String keyProperty = context.getStringAttribute("keyProperty");
    String keyColumn = context.getStringAttribute("keyColumn");
    KeyGenerator keyGenerator;
    String keyStatementId = id + SelectKeyGenerator.SELECT_KEY_SUFFIX;
    keyStatementId = builderAssistant.applyCurrentNamespace(keyStatementId, true);
    if (configuration.hasKeyGenerator(keyStatementId)) {
      keyGenerator = configuration.getKeyGenerator(keyStatementId);
    } else {
      keyGenerator = context.getBooleanAttribute("useGeneratedKeys",
          configuration.isUseGeneratedKeys() && SqlCommandType.INSERT.equals(sqlCommandType))
          ? Jdbc3KeyGenerator.INSTANCE : NoKeyGenerator.INSTANCE;
    }

    builderAssistant.addMappedStatement(id, sqlSource, statementType, sqlCommandType,
        fetchSize, timeout, parameterMap, parameterTypeClass, resultMap, resultTypeClass,
        resultSetTypeEnum, flushCache, useCache, resultOrdered, 
        keyGenerator, keyProperty, keyColumn, databaseId, langDriver, resultSets);
  }

  // 构建MappedStatement，并赋值给configuration
  public MappedStatement addMappedStatement(XXX) {
    if (unresolvedCacheRef) {
      throw new IncompleteElementException("Cache-ref not yet resolved");
    }
    id = applyCurrentNamespace(id, false);
    boolean isSelect = sqlCommandType == SqlCommandType.SELECT;
    MappedStatement.Builder statementBuilder = new MappedStatement.Builder(configuration, id, sqlSource, sqlCommandType)
        .resource(resource)
        .fetchSize(fetchSize)
        .timeout(timeout)
        .statementType(statementType)
        .keyGenerator(keyGenerator)
        .keyProperty(keyProperty)
        .keyColumn(keyColumn)
        .databaseId(databaseId)
        .lang(lang)
        .resultOrdered(resultOrdered)
        .resultSets(resultSets)
        .resultMaps(getStatementResultMaps(resultMap, resultType, id))
        .resultSetType(resultSetType)
        .flushCacheRequired(valueOrDefault(flushCache, !isSelect))
        .useCache(valueOrDefault(useCache, isSelect))
        .cache(currentCache);
    ParameterMap statementParameterMap = getStatementParameterMap(parameterMap, parameterType, id);
    if (statementParameterMap != null) {
      statementBuilder.parameterMap(statementParameterMap);
    }
    MappedStatement statement = statementBuilder.build();
    configuration.addMappedStatement(statement);
    return statement;
  }
```

通过上面几个步骤获取Configuration对象,并赋给DefaultSqlSessionFactory

```
// 最终根据config对象创建SqlSessionFactory对象并返回
public SqlSessionFactory build(Configuration config) {
  return new DefaultSqlSessionFactory(config);
}
```

## 通过SqlSessionFactory工厂获取SqlSession

org.apache.ibatis.session.defaults.DefaultSqlSessionFactory#openSession()获取SqlSession对象,SqlSession对象的获取其实就是事物管理器和执行器的设置.

```
// 获取会话
public SqlSession openSession() {
    return openSessionFromDataSource(configuration.getDefaultExecutorType(), null, false);
}

private SqlSession openSessionFromDataSource(ExecutorType execType, TransactionIsolationLevel level, boolean autoCommit) {
  Transaction tx = null;
  try {
    final Environment environment = configuration.getEnvironment();
    
    // 根据环境来获取事务管理工厂：JdbcTransactionFactory和ManagedTransactionFactory
    // 通过这两个事务工厂来获取不同的事务管理器：JdbcTransaction（由Jdbc来管理）和ManagedTransaction（事务交给外部管理器管理，如jboss)
    final TransactionFactory transactionFactory = getTransactionFactoryFromEnvironment(environment);
    
    // 构造函数创建事务管理器：JdbcTransaction
    tx = transactionFactory.newTransaction(environment.getDataSource(), level, autoCommit);
    
    // 创建执行器，默认是SimpleExecutor
    final Executor executor = configuration.newExecutor(tx, execType);
    
    // 创建DefaultSqlSession对象并返回，此时的sqlsession拥有configuration和executor对象。
    return new DefaultSqlSession(configuration, executor, autoCommit);
  } catch (Exception e) {
    closeTransaction(tx); // may have fetched a connection so lets call close()
    throw ExceptionFactory.wrapException("Error opening session.  Cause: " + e, e);
  } finally {
    ErrorContext.instance().reset();
  }
}

  // 执行器的获取
  public Executor newExecutor(Transaction transaction, ExecutorType executorType) {
    executorType = executorType == null ? defaultExecutorType : executorType;
    executorType = executorType == null ? ExecutorType.SIMPLE : executorType;
    Executor executor;
    if (ExecutorType.BATCH == executorType) {
      // 批量执行器
      executor = new BatchExecutor(this, transaction);
    } else if (ExecutorType.REUSE == executorType) {
      // 复用执行器
      executor = new ReuseExecutor(this, transaction);
    } else {
      // 默认简单执行器
      executor = new SimpleExecutor(this, transaction);
    }
    if (cacheEnabled) {
      // 如果开启缓存,则使用缓存执行器
      executor = new CachingExecutor(executor);
    }
    // 将执行器放入责任链中
    executor = (Executor) interceptorChain.pluginAll(executor);
    return executor;
  }
```

## 通过SqlSession来获取mapper对象
通过SqlSession来获取mapper对象：UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
我们已知UserMapper是个接口类,那么mybatis生成的实现类一定是个代理类,下面我们将分析如何获取这个代理对象以及这个代理对象的方法执行的过程;
```
// DefaultSqlSession委托configuration
  public <T> T getMapper(Class<T> type) {
    return configuration.<T>getMapper(type, this);
  }
// Configuration委托MapperRegistry
  public <T> T getMapper(Class<T> type, SqlSession sqlSession) {
    return mapperRegistry.getMapper(type, sqlSession);
  }
// 最后MapperRegistry获取代理工厂
public <T> T getMapper(Class<T> type, SqlSession sqlSession) {
  final MapperProxyFactory<T> mapperProxyFactory = (MapperProxyFactory<T>) knownMappers.get(type);
  if (mapperProxyFactory == null) {
    throw new BindingException("Type " + type + " is not known to the MapperRegistry.");
  }
  try {
    return mapperProxyFactory.newInstance(sqlSession);
  } catch (Exception e) {
    throw new BindingException("Error getting mapper instance. Cause: " + e, e);
  }
}
// 根据代理工厂获取代理类    
  public T newInstance(SqlSession sqlSession) {
    final MapperProxy<T> mapperProxy = new MapperProxy<T>(sqlSession, mapperInterface, methodCache);
    return newInstance(mapperProxy);
  }
  // 最终根据jdk的代理工具获取代理对象
  protected T newInstance(MapperProxy<T> mapperProxy) {
    return (T) Proxy.newProxyInstance(mapperInterface.getClassLoader(), new Class[] { mapperInterface }, mapperProxy);
  }

```

## 调用mapper方法执行数据库操作。
为了研究mapper的真实调用，我们需要通过对代理对象进行反编译，可以通过`arthas`工具来获取，下面是UserMapper的代理对象的部分代码.
```
public final class $Proxy7
extends Proxy
implements UserMapper {
    private static Method m1;
    private static Method m5;
    private static Method m2;
    private static Method m4;
    private static Method m3;
    private static Method m0;

    public $Proxy7(InvocationHandler invocationHandler) {
        super(invocationHandler);
    }

    // 调用
    public final List selectAll() {
        try {
            // 最终调用的是invocationHandler的invoke方法。
            return (List)this.h.invoke(this, m3, null);
        }
        catch (Error | RuntimeException throwable) {
            throw throwable;
        }
        catch (Throwable throwable) {
            throw new UndeclaredThrowableException(throwable);
        }
    }
}
```

下面是最终调用的org.apache.ibatis.binding.MapperProxy#invoke
```
public class MapperProxy<T> implements InvocationHandler, Serializable {

  private static final long serialVersionUID = -6424540398559729838L;
  private final SqlSession sqlSession;
  private final Class<T> mapperInterface;
  private final Map<Method, MapperMethod> methodCache;

  public MapperProxy(SqlSession sqlSession, Class<T> mapperInterface, Map<Method, MapperMethod> methodCache) {
    this.sqlSession = sqlSession;
    this.mapperInterface = mapperInterface;
    this.methodCache = methodCache;
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    try {
      if (Object.class.equals(method.getDeclaringClass())) {
        return method.invoke(this, args);
      } else if (isDefaultMethod(method)) {
        return invokeDefaultMethod(proxy, method, args);
      }
    } catch (Throwable t) {
      throw ExceptionUtil.unwrapThrowable(t);
    }
    // 从缓存中获取映射方法，如果没有则创建一个
    final MapperMethod mapperMethod = cachedMapperMethod(method);
    // 执行映射方法
    return mapperMethod.execute(sqlSession, args);
  }

    // 创建映射方法并缓存起来
  private MapperMethod cachedMapperMethod(Method method) {
    MapperMethod mapperMethod = methodCache.get(method);
    if (mapperMethod == null) {
      mapperMethod = new MapperMethod(mapperInterface, method, sqlSession.getConfiguration());
      methodCache.put(method, mapperMethod);
    }
    return mapperMethod;
  }

  /**
   * Backport of java.lang.reflect.Method#isDefault()
   */
  private boolean isDefaultMethod(Method method) {
    return (method.getModifiers()
        & (Modifier.ABSTRACT | Modifier.PUBLIC | Modifier.STATIC)) == Modifier.PUBLIC
        && method.getDeclaringClass().isInterface();
  }
}
```

执行org.apache.ibatis.binding.MapperMethod#execute
```
  public Object execute(SqlSession sqlSession, Object[] args) {
    Object result;
    // 根据执行类型来判断sqlSession的执行类型
    switch (command.getType()) {
      case INSERT: {
      Object param = method.convertArgsToSqlCommandParam(args);
        result = rowCountResult(sqlSession.insert(command.getName(), param));
        break;
      }
      case UPDATE: {
        Object param = method.convertArgsToSqlCommandParam(args);
        result = rowCountResult(sqlSession.update(command.getName(), param));
        break;
      }
      case DELETE: {
        Object param = method.convertArgsToSqlCommandParam(args);
        result = rowCountResult(sqlSession.delete(command.getName(), param));
        break;
      }
      case SELECT:// 如果是查询，则还要根据返回值进行判断执行方法
        if (method.returnsVoid() && method.hasResultHandler()) {
          executeWithResultHandler(sqlSession, args);
          result = null;
        } else if (method.returnsMany()) {
          result = executeForMany(sqlSession, args);// 执行获取多个结果
        } else if (method.returnsMap()) {
          result = executeForMap(sqlSession, args);
        } else if (method.returnsCursor()) {
          result = executeForCursor(sqlSession, args);
        } else {
          Object param = method.convertArgsToSqlCommandParam(args);
          result = sqlSession.selectOne(command.getName(), param);
        }
        break;
      case FLUSH:
        result = sqlSession.flushStatements();
        break;
      default:
        throw new BindingException("Unknown execution method for: " + command.getName());
    }
    if (result == null && method.getReturnType().isPrimitive() && !method.returnsVoid()) {
      throw new BindingException("Mapper method '" + command.getName() 
          + " attempted to return null from a method with a primitive return type (" + method.getReturnType() + ").");
    }
    return result;
  }
  
    // 获取多个结果查询方法
  private <E> Object executeForMany(SqlSession sqlSession, Object[] args) {
    List<E> result;
    // 参数拼接为sql
    Object param = method.convertArgsToSqlCommandParam(args);
    if (method.hasRowBounds()) {// 判断是否需要分页查询
      RowBounds rowBounds = method.extractRowBounds(args);
      result = sqlSession.<E>selectList(command.getName(), param, rowBounds);
    } else {
    // 最终又委托defaultSqlSession进行selectList方法调用
      result = sqlSession.<E>selectList(command.getName(), param);
    }
    // issue #510 Collections & arrays support
    if (!method.getReturnType().isAssignableFrom(result.getClass())) {
      if (method.getReturnType().isArray()) {
        return convertToArray(result);
      } else {
        return convertToDeclaredCollection(sqlSession.getConfiguration(), result);
      }
    }
    return result;
  }
```

DefaultSqlSession进行selectList方法的调用
```
  @Override
  public <E> List<E> selectList(String statement, Object parameter, RowBounds rowBounds) {
    try {
      // 通过configuration 获取MappedStatement（包含sql对应的各种描述，如结果，缓存，sql语句等，在创建SqlSessionFactory时候生成并放在configuration中）
      MappedStatement ms = configuration.getMappedStatement(statement);
      // 委托executor进行执行查询
      return executor.query(ms, wrapCollection(parameter), rowBounds, Executor.NO_RESULT_HANDLER);
    } catch (Exception e) {
      throw ExceptionFactory.wrapException("Error querying database.  Cause: " + e, e);
    } finally {
      ErrorContext.instance().reset();
    }
  }  
```
org.apache.ibatis.executor.BaseExecutor#query查询
```
  // BaseExecutor 进行query
  public <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, CacheKey key, BoundSql boundSql) throws SQLException {
    ErrorContext.instance().resource(ms.getResource()).activity("executing a query").object(ms.getId());
    if (closed) {
      throw new ExecutorException("Executor was closed.");
    }
    if (queryStack == 0 && ms.isFlushCacheRequired()) {
      clearLocalCache();// 根据flushCache配置清理缓存
    }
    List<E> list;
    try {
      queryStack++;
      // 判断是否有缓存，如果有则使用缓存
      list = resultHandler == null ? (List<E>) localCache.getObject(key) : null;
      if (list != null) {
        handleLocallyCachedOutputParameters(ms, key, parameter, boundSql);
      } else {
        // 从数据库查询数据
        list = queryFromDatabase(ms, parameter, rowBounds, resultHandler, key, boundSql);
      }
    } finally {
      queryStack--;
    }
    if (queryStack == 0) {
      for (DeferredLoad deferredLoad : deferredLoads) {
        deferredLoad.load();
      }
      // issue #601
      deferredLoads.clear();
      if (configuration.getLocalCacheScope() == LocalCacheScope.STATEMENT) {
        // issue #482
        clearLocalCache();
      }
    }
    return list;
  }

  // 数据库查询前，放置缓存一个占位
    private <E> List<E> queryFromDatabase(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, CacheKey key, BoundSql boundSql) throws SQLException {
      List<E> list;
      localCache.putObject(key, EXECUTION_PLACEHOLDER);
      try {
      // 数据库查询
        list = doQuery(ms, parameter, rowBounds, resultHandler, boundSql);
      } finally {
        localCache.removeObject(key);
      }
      localCache.putObject(key, list);
      if (ms.getStatementType() == StatementType.CALLABLE) {
        localOutputParameterCache.putObject(key, parameter);
      }
      return list;
    }
```

执行org.apache.ibatis.executor.SimpleExecutor#doQuery方法
```
   // 执行org.apache.ibatis.executor.SimpleExecutor#doQuery方法
  public <E> List<E> doQuery(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) throws SQLException {
    Statement stmt = null;
    try {
      Configuration configuration = ms.getConfiguration();
      StatementHandler handler = configuration.newStatementHandler(wrapper, ms, parameter, rowBounds, resultHandler, boundSql);
      stmt = prepareStatement(handler, ms.getStatementLog());
      // 委托给RoutingStatementHandler
      return handler.<E>query(stmt, resultHandler);
    } finally {
      // 关闭声明
      closeStatement(stmt);
    }
  }
  
  // 通过JdbcTransaction获取Connection，然后Statement
  private Statement prepareStatement(StatementHandler handler, Log statementLog) throws SQLException {
    Statement stmt;
    Connection connection = getConnection(statementLog);
    stmt = handler.prepare(connection, transaction.getTimeout());
    handler.parameterize(stmt);
    return stmt;
  }
```

调用org.apache.ibatis.executor.statement.RoutingStatementHandler#query方法
```
  // org.apache.ibatis.executor.statement.RoutingStatementHandler#query
  // 由RoutingStatementHandler委派给对应的StatementHandler
    @Override
  public <E> List<E> query(Statement statement, ResultHandler resultHandler) throws SQLException {
    return delegate.<E>query(statement, resultHandler);
  }
```

最终委派给org.apache.ibatis.executor.statement.PreparedStatemen方法进行查询，通过resultSetHandler封装获取结果
```
  // 最终委派给PreparedStatemen方法进行查询，通过resultSetHandler封装获取结果
    @Override
    public <E> List<E> query(Statement statement, ResultHandler resultHandler) throws SQLException {
      // JDBC的原始类
      PreparedStatement ps = (PreparedStatement) statement;
      ps.execute();
      // 封装查询结果
      return resultSetHandler.<E> handleResultSets(ps);
    }
```

最后由org.apache.ibatis.executor.resultset.DefaultResultSetHandler对查询结果进行封装
```
// org.apache.ibatis.executor.resultset.DefaultResultSetHandler#handleResultSets
// 对查询结果进行封装
  @Override
  public List<Object> handleResultSets(Statement stmt) throws SQLException {
    ErrorContext.instance().activity("handling results").object(mappedStatement.getId());

    final List<Object> multipleResults = new ArrayList<Object>();

    int resultSetCount = 0;
    ResultSetWrapper rsw = getFirstResultSet(stmt);

    List<ResultMap> resultMaps = mappedStatement.getResultMaps();
    int resultMapCount = resultMaps.size();
    validateResultMapsCount(rsw, resultMapCount);
    while (rsw != null && resultMapCount > resultSetCount) {
      ResultMap resultMap = resultMaps.get(resultSetCount);
      handleResultSet(rsw, resultMap, multipleResults, null);
      rsw = getNextResultSet(stmt);
      cleanUpAfterHandlingResultSet();
      resultSetCount++;
    }

    String[] resultSets = mappedStatement.getResultSets();
    if (resultSets != null) {
      while (rsw != null && resultSetCount < resultSets.length) {
        ResultMapping parentMapping = nextResultMaps.get(resultSets[resultSetCount]);
        if (parentMapping != null) {
          String nestedResultMapId = parentMapping.getNestedResultMapId();
          ResultMap resultMap = configuration.getResultMap(nestedResultMapId);
          handleResultSet(rsw, resultMap, null, parentMapping);
        }
        rsw = getNextResultSet(stmt);
        cleanUpAfterHandlingResultSet();
        resultSetCount++;
      }
    }

    return collapseSingleResultList(multipleResults);
  }
```

## mybatis结构图
### 整体架构图
![](https://gitee.com/mvilplss/note/raw/master/image/mybatis架构.jpeg)
https://gitee.com/mvilplss/note/raw/master/image/mybatis架构.jpeg

### 层次结构图
![](https://gitee.com/mvilplss/note/raw/master/image/mybatis层次结构.jpg)
https://gitee.com/mvilplss/note/raw/master/image/mybatis层次结构.jpg

### 调用时序图
![](https://gitee.com/mvilplss/note/raw/master/image/mybatis时序图.jpeg)
https://gitee.com/mvilplss/note/raw/master/image/mybatis时序图.jpeg

## 参考文章
- https://mybatis.org/mybatis-3/zh/getting-started.html
- https://baike.baidu.com/item/MyBatis/2824918?fr=aladdin
