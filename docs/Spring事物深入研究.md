---
title: Spring事物深入研究
date: 2019-02-04
categories: 
- 开发技术
tags: 
- Spring
- 事务
copyright: true
cover: https://gitee.com/mvilplss/note/raw/master/image/内部调用未增强.jpg
---

## Spring事务是如何实现的
### 事务类的增强

Spring的事务是通对目标类生成增强后的代理类，然后自动在执行目标方法前开启事务，目标方法后关闭事务等。
下面是生成代理类的增强拦截器：
```
public class TransactionInterceptor extends TransactionAspectSupport implements MethodInterceptor, Serializable {
    @Override
    //实现了MethodInterceptor的invoke方法
    public Object invoke(final MethodInvocation invocation) throws Throwable {
        //获取目标类
　　　　 Class<?> targetClass = (invocation.getThis() != null ? AopUtils.getTargetClass(invocation.getThis()) : null);
　　　　 //父类TransactionAspectSupport的模板方法
        return invokeWithinTransaction(invocation.getMethod(), targetClass, new InvocationCallback() {
            @Override
　　　　　　　//InvocationCallback接口的回调方法
            public Object proceedWithInvocation() throws Throwable {
　　　　　　　　　 //执行目标方法
                return invocation.proceed();
            }
        });
    }
}
```
最终执行的父类方法：
```
public abstract class TransactionAspectSupport implements BeanFactoryAware, InitializingBean {
　　 //protected修饰，不允许其他包和无关类调用
    protected Object invokeWithinTransaction(Method method, Class<?> targetClass, final InvocationCallback invocation) throws Throwable {
        // 获取对应事务属性.如果事务属性为空（则目标方法不存在事务）
        final TransactionAttribute txAttr = getTransactionAttributeSource().getTransactionAttribute(method, targetClass);
　　　　 // 根据事务的属性获取beanFactory中的PlatformTransactionManager(spring事务管理器的顶级接口)，一般这里或者的是DataSourceTransactiuonManager
        final PlatformTransactionManager tm = determineTransactionManager(txAttr);
 　　　　// 目标方法唯一标识（类.方法，如service.UserServiceImpl.save）
        final String joinpointIdentification = methodIdentification(method, targetClass);
　　　　 //如果txAttr为空或者tm 属于非CallbackPreferringPlatformTransactionManager，执行目标增强     ①
        if (txAttr == null || !(tm instanceof CallbackPreferringPlatformTransactionManager)) {
            //看是否有必要创建一个事务，根据事务传播行为，做出相应的判断
            TransactionInfo txInfo = createTransactionIfNecessary(tm, txAttr, joinpointIdentification);
            Object retVal = null;
            try {
　　　　　　　　　 //回调方法执行，执行目标方法（原有的业务逻辑）
                retVal = invocation.proceedWithInvocation();
            }
            catch (Throwable ex) {
                // 异常回滚
                completeTransactionAfterThrowing(txInfo, ex);
                throw ex;
            }
            finally {
　　　　　　　　　 //清除信息
                cleanupTransactionInfo(txInfo);
            }
　　　　　　  //提交事务
            commitTransactionAfterReturning(txInfo);
            return retVal;
        }
　　　　 //编程式事务处理(CallbackPreferringPlatformTransactionManager) 不做重点分析
        else {
            try {
                Object result = ((CallbackPreferringPlatformTransactionManager) tm).execute(txAttr,
                        new TransactionCallback<Object>() {
                            @Override
                            public Object doInTransaction(TransactionStatus status) {
                                TransactionInfo txInfo = prepareTransactionInfo(tm, txAttr, joinpointIdentification, status);
                                try {
                                    return invocation.proceedWithInvocation();
                                }
                                catch (Throwable ex) {
                                    if (txAttr.rollbackOn(ex)) {
                                        // A RuntimeException: will lead to a rollback.
                                        if (ex instanceof RuntimeException) {
                                            throw (RuntimeException) ex;
                                        }
                                        else {
                                            throw new ThrowableHolderException(ex);
                                        }
                                    }
                                    else {
                                        // A normal return value: will lead to a commit.
                                        return new ThrowableHolder(ex);
                                    }
                                }
                                finally {
                                    cleanupTransactionInfo(txInfo);
                                }
                            }
                        });

                // Check result: It might indicate a Throwable to rethrow.
                if (result instanceof ThrowableHolder) {
                    throw ((ThrowableHolder) result).getThrowable();
                }
                else {
                    return result;
                }
            }
            catch (ThrowableHolderException ex) {
                throw ex.getCause();
            }
        }
    }
}
```
执行流程为：
当我们调用Service的事务方法时候，其实是调用的Service的增强后的代理类，然后调用TransactionInterceptor的invoke方法，最终执行了TransactionAspectSupport.invokeWithinTransaction方法。

## 事务的核心接口
```
// 事务的各种属性定义
public interface TransactionDefinition {
    int PROPAGATION_REQUIRED = 0;
    int PROPAGATION_SUPPORTS = 1;
    int PROPAGATION_MANDATORY = 2;
    int PROPAGATION_REQUIRES_NEW = 3;
    int PROPAGATION_NOT_SUPPORTED = 4;
    int PROPAGATION_NEVER = 5;
    int PROPAGATION_NESTED = 5;
    int ISOLATION_DEFAULT = -1;
    
    int ISOLATION_READ_UNCOMMITTED = Connection.TRANSACTION_READ_UNCOMMITTED;
    int ISOLATION_READ_COMMITTED = Connection.TRANSACTION_READ_COMMITTED;
    int ISOLATION_REPEATABLE_READ = Connection.TRANSACTION_REPEATABLE_READ;
    int ISOLATION_SERIALIZABLE = Connection.TRANSACTION_SERIALIZABLE;
    int TIMEOUT_DEFAULT = -1;
    
    int getPropagationBehavior();
    int getIsolationLevel();
    int getTimeout();
    boolean isReadOnly();
    String getName();
}
// 事务的状态
public interface TransactionStatus extends SavepointManager, Flushable {
    boolean isNewTransaction();
    boolean hasSavepoint();
    void setRollbackOnly();
    boolean isRollbackOnly();
    void flush();
    boolean isCompleted();

}
// 事务的获取，提交和回滚
public interface PlatformTransactionManager {
    TransactionStatus getTransaction(TransactionDefinition definition) throws TransactionException;
    void commit(TransactionStatus status) throws TransactionException;
    void rollback(TransactionStatus status) throws TransactionException;
}
```
对应关系如下图所示：
![事务相关接口.jpg](https://gitee.com/mvilplss/note/raw/master/image/事务相关接口.jpg)

## Spring事务的隔离级别
|隔离级别|值|含义|
|----|----|----|
|ISOLATION_DEFAULT|	-1	|这是一个PlatfromTransactionManager默认的隔离级别，使用数据库默认的事务隔离级别。另外四个与JDBC的隔离级别相对应|
|ISOLATION_READ_UNCOMMITTED|	1|	这是事务最低的隔离级别，它充许另外一个事务可以看到这个事务未提交的数据。这种隔离级别会产生脏读，不可重复读和幻读。|
|ISOLATION_READ_COMMITTED|	2|	保证一个事务修改的数据提交后才能被另外一个事务读取。另外一个事务不能读取该事务未提交的数据。|
|ISOLATION_REPEATABLE_READ|	4|	这种事务隔离级别可以防止脏读，不可重复读。但是可能出现幻读。|
|ISOLATION_SERIALIZABLE	|8|	这是花费最高代价但是最可靠的事务隔离级别。事务被处理为顺序执行。除了防止脏读，不可重复读外，还避免了幻读。|

## 什么是事务传播
事务传播就是多级事务方法（可以是同一个方法）调用时，事务方法是如何管理事务的行为。例如：A事务方法调用B事务方法，B事务方法是沿用A的事务还是新开一个事务或不用事务，这个是由事务的传播行为决定的。

### Spring的7个事务传播行为
|传播行为|含义|
|----|----|
|PROPAGATION_REQUIRED|支持当前事务，如果不存在则创建一个事务。|
|PROPAGATION_SUPPORTS|如果存在一个事务，支持当前事务。如果没有事务，则非事务的执行。|
|PROPAGATION_MANDATORY|如果存在一个事务，支持当前事务。如果没有事务，则抛出异常IllegalTransactionStateException。|
|PROPAGATION_REQUIRES_NEW|创建一个新的事务，如果当前已经存在事务则进行挂起。|
|PROPAGATION_NOT_SUPPORTED|不支持事务，如果当前已经存在事务则进行挂起。|
|PROPAGATION_NEVER|不支持事务，如果当前已经存在事务则抛出异常IllegalTransactionStateException。|
|PROPAGATION_NESTED|如果当前事务存在，则在嵌套事务中执行，嵌套事务支持事务提交和回滚；如果当前事务不存在则类似PROPAGATION_REQUIRED。|

#### PROPAGATION_REQUIRED
如果存在一个事务，则支持当前事务。如果没有事务则开启一个新的事务。
```
    @Transactional(propagation = Propagation.REQUIRED)
    public void methodA(){
        self.methodB();
        // do something
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void methodB(){
        // do something
    }
```
当单独执行方法B时，发现不存在事务则会打开一个事务；如果单独执行方法A时，发现不存在事务则开启一个事务，然后内部调用方法B时，由于传播行为PROPAGATION_REQUIRED，因此会使用当前事务。

#### PROPAGATION_SUPPORTS
如果存在一个事务，支持当前事务。如果没有事务，则非事务的执行。
```
    @Transactional(propagation = Propagation.REQUIRED)
    public void methodA(){
        self.methodB();
        // do something
    }

    @Transactional(propagation = Propagation.PROPAGATION_SUPPORTS)
    public void methodB(){
        // do something
    }
```
单独调用方法B时，根据当前事务传播行为B将不使用事务执行；当调用方法A时，开启事务，内部调用B时，B使用A的事务。

#### PROPAGATION_MANDATORY
如果存在一个事务，支持当前事务。如果没有事务，则抛出事务不存在异常。
```
    @Transactional(propagation = Propagation.REQUIRED)
    public void methodA(){
        self.methodB();
        // do something
    }

    @Transactional(propagation = Propagation.PROPAGATION_MANDATORY)
    public void methodB(){
        // do something
    }
```
单独调用方法B时，由于B未在事务中执行，则抛出异常(`IllegalTransactionStateException: No existing transaction found for transaction marked with propagation 'mandatory'`)；当调用方法A时，开启事务，内部调用B时，B使用A的事务。

#### PROPAGATION_REQUIRES_NEW
创建一个新的事务，如果当前已经存在事务则进行挂起。
```
    @Transactional(propagation = Propagation.REQUIRED)
    public void methodA(){
        self.methodB();
        // do something
    }

    @Transactional(propagation = Propagation.PROPAGATION_REQUIRES_NEW)
    public void methodB(){
        // do something
    }
```
无论是单独调用方法B还是通过方法A内部调用B都会挂起已存在的事务，然后创建一个新的事务执行。

#### PROPAGATION_NOT_SUPPORTED
不支持事务，如果当前已经存在事务则进行挂起。
```
    @Transactional(propagation = Propagation.REQUIRED)
    public void methodA(){
        self.methodB();
        // do something
    }

    @Transactional(propagation = Propagation.PROPAGATION_NOT_SUPPORTED)
    public void methodB(){
        // do something
    }
```
无论是单独调用方法B还是通过方法A内部调用B都会挂起已经存在的事务，然后以非事务执行。

#### PROPAGATION_NEVER
不支持事务，如果当前已经存在事务则抛出异常。
```
    @Transactional(propagation = Propagation.REQUIRED)
    public void methodA(){
        self.methodB();
        // do something
    }

    @Transactional(propagation = Propagation.PROPAGATION_NEVER)
    public void methodB(){
        // do something
    }
```
无论是单独调用方法B还是通过方法A内部调用B，如果存在事务，则抛出异常(`IllegalTransactionStateException: Existing transaction found for transaction marked with propagation 'never'`')。

#### PROPAGATION_NESTED
如果当前事务存在，则在嵌套事务中执行，嵌套事务支持事务提交和回滚；如果当前事务不存在则类似PROPAGATION_REQUIRED。
```
    @Transactional(propagation = Propagation.REQUIRED)
    public void methodA(){
        self.methodB();
        // do something
    }

    @Transactional(propagation = Propagation.PROPAGATION_NESTED)
    public void methodB(){
        // do something
    }
```
如果一个活动的事务存在，则运行在一个嵌套的事务中。 如果没有活动事务, 则按TransactionDefinition.PROPAGATION_REQUIRED 属性执行。

## 相关问题
### PROPAGATION_NESTED和PROPAGATION_REQUIRES_NE区别
它们非常类似,都像一个嵌套事务，如果不存在一个活动的事务，都会开启一个新的事务。 
使用 PROPAGATION_REQUIRES_NEW时，内层事务与外层事务就像两个独立的事务一样，一旦内层事务进行了提交后，外层事务不能对其进行回滚。两个事务互不影响。两个事务不是一个真正的嵌套事务。同时它需要JTA事务管理器的支持。
使用PROPAGATION_NESTED时，外层事务的回滚可以引起内层事务的回滚。而内层事务的异常并不会导致外层事务的回滚，它是一个真正的嵌套事务。DataSourceTransactionManager使用savepoint支持PROPAGATION_NESTED时，需要JDBC 3.0以上驱动及1.4以上的JDK版本支持。其它的JTATrasactionManager实现可能有不同的支持方式。

### 嵌套调用时候，事务传播行为未生效
```
    @Transactional(propagation = Propagation.REQUIRED)
    public void methodA(){
        methodB();// 直接调用
        // do something
    }

    @Transactional(propagation = Propagation.PROPAGATION_SUPPORTS)
    public void methodB(){
        // do something
    }
```
如上面代码，当methodA调用methodB时候，methodB理论上应该在methodA开启的事务中执行，但是现实是methodB并未开启事务，这是什么原因呢？
我们知道Spring的事务管理是通过AOP对Service对象进行增强实现的，因此我们从bean工厂中获取到的Service为增强过的类，当我们直接调用时会直接调用增强的方法，然后在调用目标方。
如图：
![内部调用未增强.jpg](https://gitee.com/mvilplss/note/raw/master/image/内部调用未增强.jpg)
红色调用部分`this.methodA`则是目标类自己调用自己，因此是未经过增强的方法，不会按照Spring的事务传播行为执行的。

### Transaction rolled back because it has been marked as rollback-only
这篇文章之所以会写出来，主要是在维护老系统时候，发现了下面一个错误.
首先看下这段代码有什么问题：
```
    @Transactional(propagation = Propagation.REQUIRED)
    public void methodA(){
        try{
            methodB();// 直接调用
            // do something
            }catch(Exception e){
                log.error(e);
            }
    }

    @Transactional(propagation = Propagation.REQUIRED,rollbackFor = Exception.class)
    public void methodB(){
        // do something
        i = 1/0;
    }
```
当我们调用methodA时，内嵌调用methodB，methodB执行时候发生异常，将事务标记为rollback-only=true，然后异常被methodA捕获，并打印出异常栈（其实并没有打印出1/0的异常）。
异常竟然是UnexpectedRollbackException异常，这是为什么呢？
```
org.springframework.transaction.UnexpectedRollbackException: Transaction rolled back because it has been marked as rollback-only
```
原因是methodB设置了rollbackFor = Exception.class，异常后将当前事务设置为了rollback-only，因此当methodA调用methodB完成后将异常catch后进行了提交事务操作，因此就出现UnexpectedRollbackException异常。

