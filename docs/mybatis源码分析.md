---
title: mybatis源码分析
date: 2019-01-04
categories: 
- 开发技术
tags: 
- mybatis
copyright: true
---

## 先写一个hello world
创建一个maven工程，然后加入mybaits依赖：
```
<dependency>
  <groupId>org.mybatis</groupId>
  <artifactId>mybatis</artifactId>
  <version>x.x.x</version>
</dependency>
```

增加mybatis的配置项
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

增加Mapper接口类
```
public interface UserMapper {

    @Select("select * from tb_user")
    List<User> selectAll();

    User selectById(Long id);

    @Update("update tb_user set name=#{name} where id = #{id}")
    int updateById(User user);

}
```

增加对应Mapper.xml配置
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

    <select id="selectById" resultMap="BaseResultMap">
    select * from tb_user where id = #{id}
  </select>
</mapper>
```
编写测试代码：
```
    @Test
    public void mybatisTest() throws Exception{
        InputStream inputStream = Resources.getResourceAsStream("mybatis/mybatis-config.xml");
        DefaultSqlSessionFactory sqlSessionFactory = (DefaultSqlSessionFactory) new SqlSessionFactoryBuilder().build(inputStream);
        SqlSession sqlSession = sqlSessionFactory.openSession();
        System.out.println(sqlSession.getClass());
        UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
        System.out.println(userMapper.getClass());
        System.out.println(userMapper.selectAll());
        User user = new User();
        user.setId(2L);
        user.setName("bb");
        userMapper.updateById(user);
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
54:50:000|DEBUG|main|159|==>  Preparing: update tb_user set name=? where id = ? 
54:50:001|DEBUG|main|159|==> Parameters: bb(String), 2(Long)
54:50:005|DEBUG|main|159|<==    Updates: 1
54:50:005|DEBUG|main|159|==>  Preparing: select * from tb_user 
54:50:006|DEBUG|main|159|==> Parameters: 
54:50:007|DEBUG|main|159|<==      Total: 3
[User(id=1, name=a), User(id=2, name=bb), User(id=3, name=c)]
```

## 源码分析
1、通过Resources.getResourceAsStream读取配置。
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
2、创建DefaultSqlSessionFactory工厂对象。
```
// 通过javax.xml.parsers解析配置为Document
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
  
// XMLConfigBuilder 解析xml，将配置读取到Configuration中。
public Configuration parse() {
  if (parsed) {
    throw new BuilderException("Each XMLConfigBuilder can only be used once.");
  }
  parsed = true;
  parseConfiguration(parser.evalNode("/configuration"));
  return configuration;
}

// XMLConfigBuilder 解析每个配置元素到Configuration中，
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
      mapperElement(root.evalNode("mappers"));// mapper元素解析
    } catch (Exception e) {
      throw new BuilderException("Error parsing SQL Mapper Configuration. Cause: " + e, e);
    }
  }
  
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

// 将解析到的mapper接口加入到knownMappers中，并设置对应的代理工厂MapperProxyFactory
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
        MapperAnnotationBuilder parser = new MapperAnnotationBuilder(config, type);
        parser.parse();
        loadCompleted = true;
      } finally {
        if (!loadCompleted) {
          knownMappers.remove(type);
        }
      }
    }
  }
  
// 根据配置创建SqlSessionFactory对戏。
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
public SqlSessionFactory build(Configuration config) {
  return new DefaultSqlSessionFactory(config);
}
```
3、通过工厂对象获取session：sqlSessionFactory.openSession();
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
```
4、通过session来获取mapper对象：UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
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
5、调用mapper方法执行数据库操作。
为了研究mapper的真实调用，我们需要通过对代理对象进行反编译，可以通过`arthas`工具来获取，下面是UserMapper的代理对象的部分代码：
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

    public final int updateById(User user) {
        try {
            return (Integer)this.h.invoke(this, m4, new Object[]{user});
        }
        catch (Error | RuntimeException throwable) {
            throw throwable;
        }
        catch (Throwable throwable) {
            throw new UndeclaredThrowableException(throwable);
        }
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

    static {
        try {
            m4 = Class.forName("com.mybatis.demo.UserMapper").getMethod("updateById", Class.forName("com.mybatis.demo.User"));
            m3 = Class.forName("com.mybatis.demo.UserMapper").getMethod("selectAll", new Class[0]);
            return;
        }
        catch (NoSuchMethodException noSuchMethodException) {
            throw new NoSuchMethodError(noSuchMethodException.getMessage());
        }
        catch (ClassNotFoundException classNotFoundException) {
            throw new NoClassDefFoundError(classNotFoundException.getMessage());
        }
    }
}
```
下面是最终调用的invokeHandler类
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
MapperMethod执行映射方法
```
  public Object execute(SqlSession sqlSession, Object[] args) {
    Object result;
    // 根据执行类型来判断sqlSession的执行
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
    // 委托defaultSqlSession进行selectList方法调用
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
  
  // defaultSqlSession执行selectList
  @Override
  public <E> List<E> selectList(String statement, Object parameter, RowBounds rowBounds) {
    try {
      // 通过configuration 获取MappedStatement（包含sql对应的各种描述，如结果，缓存，sql语句等）
      MappedStatement ms = configuration.getMappedStatement(statement);
      // 委托executor进行执行查询
      return executor.query(ms, wrapCollection(parameter), rowBounds, Executor.NO_RESULT_HANDLER);
    } catch (Exception e) {
      throw ExceptionFactory.wrapException("Error querying database.  Cause: " + e, e);
    } finally {
      ErrorContext.instance().reset();
    }
  }  
  
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
   // 最终由SimpleExecutor执行
  public <E> List<E> doQuery(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) throws SQLException {
    Statement stmt = null;
    try {
      Configuration configuration = ms.getConfiguration();
      StatementHandler handler = configuration.newStatementHandler(wrapper, ms, parameter, rowBounds, resultHandler, boundSql);
      stmt = prepareStatement(handler, ms.getStatementLog());
      // 执行
      return handler.<E>query(stmt, resultHandler);
    } finally {
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
  
  // 最终执行PreparedStatement.execute方法，resultSetHandler获取结果
    @Override
    public <E> List<E> query(Statement statement, ResultHandler resultHandler) throws SQLException {
      PreparedStatement ps = (PreparedStatement) statement;
      ps.execute();
      return resultSetHandler.<E> handleResultSets(ps);
    }

```

mybatis整理架构
![](https://github.com/mvilplss/note/blob/master/image/mybatis架构.jpeg?raw=true)



## 待完善
- xml配置的映射和通过注解的实现细节
- JdbcTransaction和和ManagedTransaction什么区别
- 几个执行器什么区别
- mybatis缓存
- 动态sql和静态sql

## 参考文章
- https://mybatis.org/mybatis-3/zh/getting-started.html
- https://baike.baidu.com/item/MyBatis/2824918?fr=aladdin
