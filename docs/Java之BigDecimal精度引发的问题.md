---
title: Java之Bigdecimal精度引发的问题
date: 2018-02-04
categories: 
- 开发技术
tags: 
- java
copyright: true
---
# BigDecimal精度问题
## 背景
### 案例
> 有一次维护老系统碰到这样一个问题，给定一批车总放款金额，每辆车的实际价格（整数），让根据实际价格的比例进行计算每辆车的放款金额（整数）。
```
解决方案：
1.循环每辆车
2.前n-1辆车的放款金额=总放款金额*当前车的实际价格/总实际价格；
3.最后一辆车放款金额=总放款金额-（n-1)车的总放款金额;
```
经过几次测试发现计算没有问题，就发布上线了，安全运行了一百多天，直到有一天出现了最后一辆的放款金额为负数，一个精度问题就发生了（突然想到墨菲定律）！

通过查看日志和数据的模拟，发现是一个四舍五入的问题，当实际价格/总实际价格的时候，如果结果是0.106经过四舍五入为0.11，这样前面每辆车就会多分配一些放款金额，最终导致（n-1）辆车总放款金额大于给定的总放款金额。
```
最终通过配置BigDecimal的RoundMode，将四舍五入改为了舍去，这样保证n-1辆车都不会出现多算的情况，从而解决问题。    
想这种问题在实际开发中很难去发现问题，因此我们用BigDecimal一定要清楚他的API，从而避免不适当的使用。
```

## BigDecimal的使用
>Java在java.math包中提供的API类BigDecimal，
用来对超过16位有效位的数进行精确的运算。双精度浮点型变量double可以处理16位有效数。
在实际应用中，需要对更大或者更小的数进行运算和处理。float和double只能用来做科学计算或者是工程计算，
在商业计算中要用java.math.BigDecimal。BigDecimal所创建的是对象，我们不能使用传统的+、-、*、/等算术运算符直接对其对象进行数学运算，
而必须调用其相对应的方法。方法中的参数也必须是BigDecimal的对象。构造器是类的特殊方法，专门用来创建对象，特别是带有参数的对象。

### 构造方法
BigDecimal一共有4个构造方法:
- BigDecimal(int) 创建一个具有参数所指定整数值的对象。
- BigDecimal(double) 创建一个具有参数所指定双精度值的对象。（不建议采用）
- BigDecimal(long) 创建一个具有参数所指定长整数值的对象。
- BigDecimal(String) 创建一个具有参数所指定以字符串表示的数值的对象

第四个方法不建议使用是因为double本身会有精度问题，比如：
```
BigDecimal a = new BigDecimal(0.1);
BigDecimal b = new BigDecimal("0.1");
BigDecimal c = BigDecimal.valueOf(0.1);
System.out.println(a);
System.out.println(b);
System.out.println(c);
System.out.println(a.equals(b));
System.out.println(b.equals(c));
```

输出结果：
```
0.1000000000000000055511151231257827021181583404541015625
0.1
0.1
false
true
```

原因：JDK的描述：1、参数类型为double的构造方法的结果有一定的不可预知性。
有人可能认为在Java中写入newBigDecimal(0.1)所创建的BigDecimal正好等于 0.1（非标度值 1，其标度为 1），
但是它实际上等于0.1000000000000000055511151231257827021181583404541015625。这是因为0.1无法准确地表示为 
double（或者说对于该情况，不能表示为任何有限长度的二进制小数）。这样，传入到构造方法的值不会正好等于 0.1
（虽然表面上等于该值）。2、另一方面，String 构造方法是完全可预知的：写入 newBigDecimal("0.1") 将创建一个 BigDecimal，
它正好等于预期的 0.1。因此，比较而言，通常建议优先使用String构造方法。

### BigDecimal加减乘除运算
```
public BigDecimal add(BigDecimal value); //加法
public BigDecimal subtract(BigDecimal value); //减法 
public BigDecimal multiply(BigDecimal value); //乘法
public BigDecimal divide(BigDecimal value); //除法
```

除法的时候一定要注意，当出现不能整除的情况会会报错java.lang.ArithmeticException: Non-terminating decimal expansion; no exact representable decimal result.
其实divide方法有可以传三个参数：public BigDecimal divide(BigDecimal divisor, int scale, int roundingMode) 第一参数表示除数， 第二个参数表示小数点后保留位数，第三个参数表示舍入模式。
#### roundingMode舍入模式
> 舍入模式和scale配合使用，其中scale是保留小数点后面的位数，而roundingMode是表示如何进行舍入，舍入模式有八种，下面将为介绍。

```
UP // 舍入模式来远离零。
5.5     6
2.5     3
1.6     2
1.1     2
1.0     1
-1.0        -1
-1.1        -2
-1.6        -2
-2.5        -3
-5.5        -6

DOWN // 舍入模式为零.
5.5     5
2.5     2
1.6     1
1.1     1
1.0     1
-1.0        -1
-1.1        -1
-1.6        -1
-2.5        -2
-5.5        -5

CEILING// 舍入模式正无穷;
5.5     6
2.5     3
1.6     2
1.1     2
1.0     1
-1.0        -1
-1.1        -1
-1.6        -1
-2.5        -2
-5.5        -5

FLOOR// 舍入模式向负无穷
5.5     5
2.5     2
1.6     1
1.1     1
1.0     1
-1.0        -1
-1.1        -2
-1.6        -2
-2.5        -3
-5.5        -6

HALF_UP// 四舍五入
5.5     6
2.5     3
1.6     2
1.1     2
1.0     1
-1.0        -1
-1.1        -1
-1.6        -2
-2.5        -3
-5.5        -6

HALF_DOWN// 五舍六入
5.5     5
2.5     2
1.6     2
1.1     1
1.0     1
-1.0        -1
-1.1        -1
-1.6        -2
-2.5        -2
-5.5        -5

HALF_EVEN// 当=.5的时候向者偶数靠近
5.5     6
2.5     2
1.6     2
1.1     1
1.0     1
-1.0        -1
-1.1        -1
-1.6        -2
-2.5        -2
-5.5        -6

UNNECESSARY// 不允许需要舍入的，否则抛出异常：ArithmeticException
```

### 其他常用方法
```
// 比较两个数的大小：
// -1, 0, or 1 as this BigDecimal is numerically less than, equal to, or greater than val
public int compareTo(BigDecimal val);
```
## 参考文献
- java doc
- https://baike.baidu.com/item/BigDecimal/5131707
