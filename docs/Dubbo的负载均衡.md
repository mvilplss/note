---
title: Dubbo的负载均衡
date: 2020-12-10
categories:
- 开发技术 
tags:
- java
- dubbo
copyright: true
cover: https://raw.githubusercontent.com/mvilplss/note/master/image/Dubbo的负载均衡_images/05058665.png
---
# 简介
负载均衡其实就是一个同样的工作任务下，对具有同样一批工作能力的人根据不同策略进行分工的一个哲学问题。不过在程序中进行任务分配就比较单纯了，在Dubbo
中分为这几个负载均衡策略：随机加权负载均衡、轮询加权负载均衡、最小活跃数负载均衡、一致性哈希负载均衡、还有在dubbo新版本中新增的最短响应负载均衡。比起Dubbo
服务的导出，引用和调用过程的源码，负载均衡的源码更值得进行研究分析，因为这些算法不仅在Dubbo中出现，在apache，nginx都有类似的算法。下面我们会逐个进行分析。

# 源码分析
分析负载均衡源码前，我们先了解下负载均衡的加载方式。在通过集群调用某个 inovker 之前，需要先选择出一个 invoker ，这时需要通过SPI加载一个负载均衡器，其源码如下：
```java
    // AbstractClusterInvoker
    protected LoadBalance initLoadBalance(List<Invoker<T>> invokers, Invocation invocation) {
        // 通过配置 loadbalance 获取配置的负载均衡器，缺省配置为 random
        if (CollectionUtils.isNotEmpty(invokers)) {
            return ExtensionLoader.getExtensionLoader(LoadBalance.class).getExtension(invokers.get(0).getUrl()
                    .getMethodParameter(RpcUtils.getMethodName(invocation), LOADBALANCE_KEY, DEFAULT_LOADBALANCE));
        } else {
            return ExtensionLoader.getExtensionLoader(LoadBalance.class).getExtension(DEFAULT_LOADBALANCE);
        }
    }
```
在 Dubbo 中，所有负载均衡实现类均继承自 AbstractLoadBalance，该类实现了 LoadBalance 接口，并封装了一些公共的逻辑。所以在分析负载均衡实现之前，先来看一下 AbstractLoadBalance 
的逻辑。首先来看一下负载均衡的入口方法 select，如下：
```java
    public <T> Invoker<T> select(List<Invoker<T>> invokers, URL url, Invocation invocation) {
        if (CollectionUtils.isEmpty(invokers)) {
            return null;
        }
        // 只有一个 invoker 直接返回，无需进行负载均衡
        if (invokers.size() == 1) {
            return invokers.get(0);
        }
        // 调用 doSelect 方法进行负载均衡，该方法为抽象方法，由子类实现
        return doSelect(invokers, url, invocation);
    }
    protected abstract <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation);

    // 获取权重（在加权算法中需要此方法获取权重）
    int getWeight(Invoker<?> invoker, Invocation invocation) {
        int weight;
        URL url = invoker.getUrl();
        // Multiple registry scenario, load balance among multiple registries.
        if (REGISTRY_SERVICE_REFERENCE_PATH.equals(url.getServiceInterface())) {
            weight = url.getParameter(REGISTRY_KEY + "." + WEIGHT_KEY, DEFAULT_WEIGHT);
        } else {
            // 通过url 获取 weight 配置，缺省配置为 100
            weight = url.getMethodParameter(invocation.getMethodName(), WEIGHT_KEY, DEFAULT_WEIGHT);
            if (weight > 0) {
                // 获取服务的启动时间戳
                long timestamp = invoker.getUrl().getParameter(TIMESTAMP_KEY, 0L);
                if (timestamp > 0L) {
                    // 计算服务的运行时间
                    long uptime = System.currentTimeMillis() - timestamp;
                    if (uptime < 0) {
                        return 1;
                    }
                    // 获取服务预热时间 warmup ，默认为 10 分钟
                    int warmup = invoker.getUrl().getParameter(WARMUP_KEY, DEFAULT_WARMUP);
                    if (uptime > 0 && uptime < warmup) {
                        // 重新计算服务权重
                        weight = calculateWarmupWeight((int)uptime, warmup, weight);
                    }
                }
            }
        }
        return Math.max(weight, 0);
    }
    // 计算预热权重
    static int calculateWarmupWeight(int uptime, int warmup, int weight) {
        // 计算权重，下面代码逻辑上等价于 (uptime / warmup) * weight。
        // 随着服务运行时间 uptime 增大，权重计算值 ww 会慢慢接近配置值 weight
        int ww = (int) ( uptime / ((float) warmup / weight));
        return ww < 1 ? 1 : (Math.min(ww, weight));
    }
```
上面是权重的计算过程，该过程主要用于保证当服务运行时长小于服务预热时间时，对服务进行降权，避免让服务在启动之初就处于高负载状态。服务预热是一个优化手段，与此类似的还有 JVM 
预热（https://blog.csdn.net/zhoufanyang_china/article/details/89888689）。主要目的是让服务启动后“低功率”运行一段时间，使其效率慢慢提升至最佳状态。

关于 AbstractLoadBalance 就先分析到这，接下来分析各个实现类的代码。首先，我们从 Dubbo 缺省的实现类 RandomLoadBalance 看起。


## RandomLoadBalance
RandomLoadBalance 是加权随机算法的具体实现，它的算法思想很简单。假设我们有一组服务器 servers = [A, B, C]，他们对应的权重为 weights = [5, 3, 2]
，权重总和为10。现在把这些权重值平铺在一维坐标值上，[0, 5) 区间属于服务器 A，[5, 8) 区间属于服务器 B，[8, 10) 区间属于服务器 C。接下来通过随机数生成器生成一个范围在 [0, 10) 
之间的随机数，然后计算这个随机数会落到哪个区间上。比如数字3会落到服务器 A 对应的区间上，此时返回服务器 A 即可。权重越大的机器，在坐标轴上对应的区间范围就越大，
因此随机数生成器生成的数字就会有更大的概率落到此区间内。只要随机数生成器产生的随机数分布性很好，在经过多次选择后，每个服务器被选中的次数比例接近其权重比例。比如，经过一万次选择后，服务器 A 被选中的次数大约为5000次，服务器 
B 被选中的次数约为3000次，服务器 C 被选中的次数约为2000次。

以上就是 RandomLoadBalance 背后的算法思想，比较简单。下面开始分析源码。
```java
public class RandomLoadBalance extends AbstractLoadBalance {
    public static final String NAME = "random";

    /**
     * Select one invoker between a list using a random criteria
     */
    @Override
    protected <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation) {
        // Number of invokers
        int length = invokers.size();
        // Every invoker has the same weight?
        boolean sameWeight = true;
        // the weight of every invokers
        int[] weights = new int[length];
        // the first invoker's weight
        int firstWeight = getWeight(invokers.get(0), invocation);
        weights[0] = firstWeight;
        // The sum of weights
        int totalWeight = firstWeight;
        for (int i = 1; i < length; i++) {
            int weight = getWeight(invokers.get(i), invocation);
            // save for later use
            weights[i] = weight;
            // Sum
            totalWeight += weight;
            if (sameWeight && weight != firstWeight) {
                sameWeight = false;
            }
        }
        if (totalWeight > 0 && !sameWeight) {
            // If (not every invoker has the same weight & at least one invoker's weight>0), select randomly based on totalWeight.
            int offset = ThreadLocalRandom.current().nextInt(totalWeight);
            // Return a invoker based on the random value.
            for (int i = 0; i < length; i++) {
                offset -= weights[i];
                if (offset < 0) {
                    return invokers.get(i);
                }
            }
        }
        // If all invokers have the same weight value or totalWeight=0, return evenly.
        return invokers.get(ThreadLocalRandom.current().nextInt(length));
    }
}
```
RandomLoadBalance 的算法思想比较简单，在经过多次请求后，能够将调用请求按照权重值进行“均匀”分配。当然 RandomLoadBalance 也存在一定的缺点，当调用次数比较少时，Random 
产生的随机数可能会比较集中，此时多数请求会落到同一台服务器上。这个缺点并不是很严重，多数情况下可以忽略。RandomLoadBalance 是一个简单，高效的负载均衡实现，因此 Dubbo 选择它作为缺省实现。
> 思考下，如果设计一个抽奖算法，奖品分为一等奖，二等奖，三等奖，中间概率可以配置，这个场景是否和随机加权有点类似？

## LeastActiveLoadBalance
LeastActiveLoadBalance 翻译过来是最小活跃数负载均衡。活跃调用数越小，表明该服务提供者效率越高，单位时间内可处理更多的请求。此时应优先将请求分配给该服务提供者。在具体实现中，每个服务提供者对应一个活跃数
active。初始情况下，所有服务提供者活跃数均为0。每发起一个请求，活跃数加1，完成请求后则将活跃数减1。在服务运行一段时间后，
性能好的服务提供者处理请求的速度更快，因此活跃数下降的也越快，此时这样的服务提供者能够优先获取到新的服务请求、这就是最小活跃数负载均衡算法的基本思想。除了最小活跃数，LeastActiveLoadBalance
在实现上还引入了权重值。所以准确的来说，LeastActiveLoadBalance 是基于加权最小活跃数算法实现的。举个例子说明一下，在一个服务提供者集群中，有两个性能优异的服务提供者。某一时刻它们的活跃数相同，此时 Dubbo
会根据它们的权重去分配请求，权重越大，获取到新请求的概率就越大。如果两个服务提供者权重相同，此时随机选择一个即可。关于 LeastActiveLoadBalance 的背景知识就先介绍到这里，下面开始分析源码。
```java
public class LeastActiveLoadBalance extends AbstractLoadBalance {
    public static final String NAME = "leastactive";

    @Override
    protected <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation) {
        // Number of invokers
        int length = invokers.size();
        // The least active value of all invokers
        int leastActive = -1;
        // The number of invokers having the same least active value (leastActive)
        int leastCount = 0;
        // The index of invokers having the same least active value (leastActive)
        int[] leastIndexes = new int[length];
        // the weight of every invokers
        int[] weights = new int[length];
        // The sum of the warmup weights of all the least active invokers
        int totalWeight = 0;
        // The weight of the first least active invoker
        int firstWeight = 0;
        // Every least active invoker has the same weight value?
        boolean sameWeight = true;
        
        // 选择出所有最小活跃数 invokers 
        // Filter out all the least active invokers
        for (int i = 0; i < length; i++) {
            Invoker<T> invoker = invokers.get(i);
            // Get the active number of the invoker
            int active = RpcStatus.getStatus(invoker.getUrl(), invocation.getMethodName()).getActive();
            // Get the weight of the invoker's configuration. The default value is 100.
            int afterWarmup = getWeight(invoker, invocation);
            // save for later use
            weights[i] = afterWarmup;
            // If it is the first invoker or the active number of the invoker is less than the current least active number
            // 当出现一个比所有都小的一个活跃数，则重新设置一些属性
            if (leastActive == -1 || active < leastActive) {
                // Reset the active number of the current invoker to the least active number
                leastActive = active;
                // Reset the number of least active invokers
                leastCount = 1;
                // Put the first least active invoker first in leastIndexes
                leastIndexes[0] = i;
                // Reset totalWeight
                totalWeight = afterWarmup;
                // Record the weight the first least active invoker
                firstWeight = afterWarmup;
                // Each invoke has the same weight (only one invoker here)
                sameWeight = true;
                // If current invoker's active value equals with leaseActive, then accumulating.
                // 如果最小活跃数相同，则将最小活跃数的 invoker 的索引放入数组，然后累加总权重
            } else if (active == leastActive) {
                // Record the index of the least active invoker in leastIndexes order
                leastIndexes[leastCount++] = i;
                // Accumulate the total weight of the least active invoker
                totalWeight += afterWarmup;
                // If every invoker has the same weight?
                if (sameWeight && afterWarmup != firstWeight) {
                    sameWeight = false;
                }
            }
        }
        // Choose an invoker from all the least active invokers
        if (leastCount == 1) {
            // If we got exactly one invoker having the least active value, return this invoker directly.
            return invokers.get(leastIndexes[0]);
        }
        // 权重不同，并且有多个最小活跃数，则进行随机加权算法选出一个 invoker 
        if (!sameWeight && totalWeight > 0) {
            // If (not every invoker has the same weight & at least one invoker's weight>0), select randomly based on 
            // totalWeight.
            int offsetWeight = ThreadLocalRandom.current().nextInt(totalWeight);
            // Return a invoker based on the random value.
            for (int i = 0; i < leastCount; i++) {
                int leastIndex = leastIndexes[i];
                offsetWeight -= weights[leastIndex];
                if (offsetWeight < 0) {
                    return invokers.get(leastIndex);
                }
            }
        }
        // If all invokers have the same weight value or totalWeight=0, return evenly.
        // 所有权重都一样，则随机选取一个 invoker
        return invokers.get(leastIndexes[ThreadLocalRandom.current().nextInt(leastCount)]);
    }
}
```
上面代码的逻辑比较多，我们在代码中写了大量的注释，有帮助大家理解代码逻辑。下面简单总结一下以上代码所做的事情，如下：

- 遍历 invokers 列表，寻找活跃数最小的 Invoker
- 如果有多个 Invoker 具有相同的最小活跃数，此时记录下这些 Invoker 在 invokers 集合中的下标，并累加它们的权重，比较它们的权重值是否相等
- 如果只有一个 Invoker 具有最小的活跃数，此时直接返回该 Invoker 即可
- 如果有多个 Invoker 具有最小活跃数，且它们的权重不相等，此时处理方式和 `RandomLoadBalance` 一致
- 如果有多个 Invoker 具有最小活跃数，但它们的权重相等，此时随机返回一个即可

以上就是 LeastActiveLoadBalance 大致的实现逻辑，大家在阅读的源码的过程中要注意区分活跃数与权重这两个概念，不要混为一谈。

## RoundRobinLoadBalance
我们来看一下 Dubbo 中加权轮询负载均衡的实现 RoundRobinLoadBalance。在详细分析源码前，我们先来了解一下什么是加权轮询。
这里从最简单的轮询开始讲起，所谓轮询是指将请求轮流分配给每台服务器。举个例子，我们有三台服务器 A、B、C。我们将第一个请求分配给服务器 A，第二个请求分配给服务器 B，第三个请求分配给服务器 C，第四个请求再次分配给服务器 
A。这个过程就叫做轮询。轮询是一种无状态负载均衡算法，实现简单，适用于每台服务器性能相近的场景下。但现实情况下 ，我们并不能保证每台服务器性能均相近。
如果我们将等量的请求分配给性能较差的服务器，这显然是不合理的。因此，这个时候我们需要对轮询过程进行加权，
以调控每台服务器的负载。经过加权后，每台服务器能够得到的请求数比例，接近或等于他们的权重比。比如服务器 A、B、C 权重比为 
5:2:1。那么在8次请求中，服务器 A 将收到其中的5次请求，服务器 B 会收到其中的2次请求，服务器 C 则收到其中的1次请求。
```java
public class RoundRobinLoadBalance extends AbstractLoadBalance {
    public static final String NAME = "roundrobin";

    private static final int RECYCLE_PERIOD = 60000;// 回收间隔时间 1 分钟

    // 权重对象，用来记录权重和当前权重，上次更新时间戳信息
    protected static class WeightedRoundRobin {
        private int weight;
        private AtomicLong current = new AtomicLong(0);
        // 上次更新时间，主要是当上次更新时间超过 RECYCLE_PERIOD = 60000 时候，则删除此权重对象，防止服务失效，长期占用
        private long lastUpdate;
        // 设置权重，并初始化当前值 current
        public void setWeight(int weight) {
            this.weight = weight;
            current.set(0);
        }
        
        // 每次增加当前权重大小
        public long increaseCurrent() {
            return current.addAndGet(weight);
        }
        // 当前值减去总值
        public void sel(int total) {
            current.addAndGet(-1 * total);
        }
        // 省略getter setter
    }
    // 目标方法签名和服务地址的权重轮询的映射
    private ConcurrentMap<String, ConcurrentMap<String, WeightedRoundRobin>> methodWeightMap = new ConcurrentHashMap<String, ConcurrentMap<String, WeightedRoundRobin>>();
    
    @Override
    protected <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation) {
        // key = 全限定类名 + "." + 方法名，比如 com.xxx.DemoService.sayHello，属于方法级别key
        String key = invokers.get(0).getUrl().getServiceKey() + "." + invocation.getMethodName();
        // 如果不存在则加入一个
        ConcurrentMap<String, WeightedRoundRobin> map = methodWeightMap.computeIfAbsent(key, k -> new ConcurrentHashMap<>());
        int totalWeight = 0;
        long maxCurrent = Long.MIN_VALUE;
        long now = System.currentTimeMillis();
        Invoker<T> selectedInvoker = null;
        WeightedRoundRobin selectedWRR = null;
        for (Invoker<T> invoker : invokers) {
            // 类似 dubbo://172.0.0.1:2808/org.apache.dubbo.demo.DemoService ，属于服务级别key
            String identifyString = invoker.getUrl().toIdentityString();
            int weight = getWeight(invoker, invocation);
            WeightedRoundRobin weightedRoundRobin = map.computeIfAbsent(identifyString, k -> {
                WeightedRoundRobin wrr = new WeightedRoundRobin();
                wrr.setWeight(weight);
                return wrr;
            });
            // 如果权重不相等，则更新权重值
            if (weight != weightedRoundRobin.getWeight()) {
                //weight changed
                weightedRoundRobin.setWeight(weight);
            }
            // 当前值增加一个权重
            long cur = weightedRoundRobin.increaseCurrent();
            // 更新时间
            weightedRoundRobin.setLastUpdate(now);
            // 如果当前值大于最大当前值，则为要选择的 invoker
            if (cur > maxCurrent) {
                maxCurrent = cur;
                selectedInvoker = invoker;
                selectedWRR = weightedRoundRobin;
            }
            totalWeight += weight;// 累加总权重
        }
        // 当新的 invokers 数量和map缓存的不一致，则根据更新时间移除距离现在大于1分钟的 invoker
        if (invokers.size() != map.size()) {
            map.entrySet().removeIf(item -> now - item.getValue().getLastUpdate() > RECYCLE_PERIOD);
        }
        // 因为这里是轮询，因此 选中的 invoker 的当前值将会被减去总权重
        if (selectedInvoker != null) {
            selectedWRR.sel(totalWeight);
            return selectedInvoker;
        }
        // should not happen here
        return invokers.get(0);
    }
}
```
上面轮询加权的算法比较难理解，但是我们列举以下计算过程就比较清晰了，假设有三台服务器[ A , B , C ] ，其对应权重为[ 3 : 2 : 1]，w 代表权重，current* 
代表当前服务的当前值，totalWeight=3+2+1=6，每次执行 current* 都会递增一个w，然后选中的服务的current值会减去总权重totalWeight，那么按照上面算法轮询顺序如下：

| 次数 | A(w=3) | B(w=2) | C(w=1) | 选中 | 更新current |
| --- | ------ | ------ | ------ | ---- | ---------- |
| 1 | currentA=3 | currentB=2 | currentC=1 | A | currentA=(3-totalWeight)=-3 |
| 2 | currentA=0 | currentB=4 | currentC=2 | B | currentB=(4-totalWeight)=-2 |
| 3 | currentA=3 | currentB=0 | currentC=3 | A | currentA=(3-totalWeight)=-3 |
| 4 | currentA=0 | currentB=2 | currentC=4 | C | currentC=(4-totalWeight)=-2 |
| 5 | currentA=3 | currentB=4 | currentC=-1 | B | currentB=(4-totalWeight)=-2 |
| 6 | currentA=6 | currentB=0 | currentC=0 | A | currentA=(6-totalWeight)=0 |
| 7 | currentA=3 | currentB=2 | currentC=1 | A | currentA=(3-totalWeight)=-3 |

通过上面的执行过程可以发现，这种算法不仅巧妙的实现了轮询，而且还很平滑，即调用服务依次为A-B-A-C-B-A-A。
> 除了上面的算法，思考下加入要求你实现轮询加权，还有没有其他的实现方式？如优先队列？计数器？或其他算法？

## ConsistentHashLoadBalance

一致性 hash 算法由麻省理工学院的 Karger 及其合作作者于1997年提出的，算法提出之初是用于大规模缓存系统的负载均衡。它的工作过程是这样的，首先根据 ip 或者其他的信息为缓存节点生成一个 hash，并将这个 hash
投射到 [0, 2^32 - 1] 的圆环上。当有查询或写入请求时，则为缓存项的 key 生成一个 hash 值。然后查找第一个大于或等于该 hash
值的缓存节点，并到这个节点中查询或写入缓存项。如果当前节点挂了，则在下一次查询或写入缓存时，为缓存项查找另一个大于其 hash 值的缓存节点即可。大致效果如下图所示，每个缓存节点在圆环上占据一个位置。如果缓存项的 key 的 hash
值小于缓存节点 hash 值，则到该缓存节点中存储或读取缓存项。比如下面绿色点对应的缓存项将会被存储到 cache-2 节点中。由于 cache-3 挂了，原本应该存到该节点中的缓存项最终会存储到 cache-4 节点中。
![](https://raw.githubusercontent.com/mvilplss/note/master/image/Dubbo的负载均衡_images/6ae78d7e.png)
下面来看看一致性 hash 在 Dubbo 中的应用。我们把上图的缓存节点替换成 Dubbo 的服务提供者，于是得到了下图：
![](https://raw.githubusercontent.com/mvilplss/note/master/image/Dubbo的负载均衡_images/05058665.png)
这里相同颜色的节点均属于同一个服务提供者，比如 Invoker1-1，Invoker1-2，……, Invoker1-160。这样做的目的是通过引入虚拟节点，让 Invoker
在圆环上分散开来，避免数据倾斜问题。所谓数据倾斜是指，由于节点不够分散，导致大量请求落到了同一个节点上，而其他节点只会接收到了少量请求的情况。比如：
![](https://raw.githubusercontent.com/mvilplss/note/master/image/Dubbo的负载均衡_images/16808650.png)
如上，由于 Invoker-1 和 Invoker-2 在圆环上分布不均，导致系统中75%的请求都会落到 Invoker-1 上，只有 25% 的请求会落到 Invoker-2
上。解决这个问题办法是引入虚拟节点，通过虚拟节点均衡各个节点的请求量。

```java
public class ConsistentHashLoadBalance extends AbstractLoadBalance {
    public static final String NAME = "consistenthash";
    public static final String HASH_NODES = "hash.nodes";// 设置 hash 的虚拟节点个数，默认 160
    public static final String HASH_ARGUMENTS = "hash.arguments";// 配置的 hash 计算参与的参数下标，如：0,1,2，默认0

    private final ConcurrentMap<String, ConsistentHashSelector<?>> selectors = new ConcurrentHashMap<String, ConsistentHashSelector<?>>();

    @Override
    protected <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation) {
        String methodName = RpcUtils.getMethodName(invocation);
        // 类似：org.apache.dubbo.demo.DemoService.sayHello
        String key = invokers.get(0).getUrl().getServiceKey() + "." + methodName;
        // using the hashcode of list to compute the hash only pay attention to the elements in the list
        // 获取 集合 invokers 的原始 hash 值，其目的用来判断集合是否有改变
        int invokersHashCode = invokers.hashCode();
        ConsistentHashSelector<T> selector = (ConsistentHashSelector<T>) selectors.get(key);
        // 如果 selector 为 null 或者 invokers 集合有变动，则进行重新hash计算
        if (selector == null || selector.identityHashCode != invokersHashCode) {
            // 对服务列表 invokers 进行创建一个 hash 节点
            ConsistentHashSelector<T> hashSelector = new ConsistentHashSelector<>(invokers, methodName, invokersHashCode);
            selectors.put(key, hashSelector);
            selector = (ConsistentHashSelector<T>) selectors.get(key);
        }
        return selector.select(invocation);
    }

    private static final class ConsistentHashSelector<T> {
        private final TreeMap<Long, Invoker<T>> virtualInvokers;
        private final int replicaNumber;
        private final int identityHashCode;
        private final int[] argumentIndex;
        // 初始化 hash 虚拟节点
        ConsistentHashSelector(List<Invoker<T>> invokers, String methodName, int identityHashCode) {
            // new 一个 TreeMap 用来存储 hash 和 invoker 映射
            this.virtualInvokers = new TreeMap<Long, Invoker<T>>();
            this.identityHashCode = identityHashCode;
            URL url = invokers.get(0).getUrl();
            // 获取要创建的节点个数，缺省配置为160
            this.replicaNumber = url.getMethodParameter(methodName, HASH_NODES, 160);
            // 获取参与 hash 计算的参数下标
            String[] index = COMMA_SPLIT_PATTERN.split(url.getMethodParameter(methodName, HASH_ARGUMENTS, "0"));
            argumentIndex = new int[index.length];
            for (int i = 0; i < index.length; i++) {
                argumentIndex[i] = Integer.parseInt(index[i]);
            }
            // 进行循环服务列表，计算 hash 并设置到虚拟节点 virtualInvokers 中
            for (Invoker<T> invoker : invokers) {
                // 获取服务器地址，类似：127.0.0.1:8082
                String address = invoker.getUrl().getAddress();
                // 循环节点的四分之一次，因为每次会进行虚拟4个节点
                for (int i = 0; i < replicaNumber / 4; i++) {
                    // 对地址进行+i，然后做md5计算：127.0.0.1:80820
                    // 对 address + i 进行 md5 运算，得到一个长度为16的字节数组
                    byte[] digest = md5(address + i);
                    // 对 digest 部分字节进行4次 hash 运算，得到四个不同的 long 型正整数
                    for (int h = 0; h < 4; h++) {
                        // h = 0 时，取 digest 中下标为 0 ~ 3 的4个字节进行位运算
                        // h = 1 时，取 digest 中下标为 4 ~ 7 的4个字节进行位运算
                        // h = 2, h = 3 时过程同上
                        long m = hash(digest, h);
                        // 将 hash 到 invoker 的映射关系存储到 virtualInvokers 中，
                        // virtualInvokers 需要提供高效的查询操作，因此选用 TreeMap 作为存储结构
                        virtualInvokers.put(m, invoker);
                    }
                }
            }
        }
        // 
        public Invoker<T> select(Invocation invocation) {
            String key = toKey(invocation.getArguments());
            byte[] digest = md5(key);
            // 取 digest 数组的前四个字节进行 hash 运算，再将 hash 值传给 selectForKey 方法，
            // 寻找合适的 Invoker
            return selectForKey(hash(digest, 0));
        }

        private String toKey(Object[] args) {
            StringBuilder buf = new StringBuilder();
            for (int i : argumentIndex) {
                if (i >= 0 && i < args.length) {
                    buf.append(args[i]);
                }
            }
            return buf.toString();
        }

        private Invoker<T> selectForKey(long hash) {
            // 到 TreeMap 中查找第一个节点值大于或等于当前 hash 的 Invoker
            Map.Entry<Long, Invoker<T>> entry = virtualInvokers.ceilingEntry(hash);
            // 如果 hash 大于 Invoker 在圆环上最大的位置，此时 entry = null，
            // 需要将 TreeMap 的头节点赋值给 entry
            if (entry == null) {
                entry = virtualInvokers.firstEntry();
            }
            return entry.getValue();
        }

        // [-128, 127, -128, 127, -128, 127, -128, 127, -128, 127, -128, 127, -128, 127, -128, 127]
        // 10000000,01111111,10000000,01111111,10000000,01111111,10000000,01111111,10000000,01111111,10000000,01111111,
        //        10000000,01111111,10000000,01111111
        private long hash(byte[] digest, int number) {
            return (((long) (digest[3 + number * 4] & 0xFF) << 24)
                    | ((long) (digest[2 + number * 4] & 0xFF) << 16)
                    | ((long) (digest[1 + number * 4] & 0xFF) << 8)
                    | (digest[number * 4] & 0xFF))
                    & 0xFFFFFFFFL;
        }

        private byte[] md5(String value) {
            MessageDigest md5;
            try {
                md5 = MessageDigest.getInstance("MD5");
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
            md5.reset();
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            md5.update(bytes);
            return md5.digest();
        }

    }

}
```
一致性hash负载均衡的难点在于理解虚拟节点的生成和根据 hash 值选择节点，也就是对treeMap数据结构的理解 和 hash 相关的位算法的理解。
## ShortestResponseLoadBalance
在2.7.7版本中由 August 贡献( https://github.com/apache/dubbo/pull/6064 )，作者添加此负载理由当使用 LeastActiveLoadBalance 
时候，如果服务直接性能差距较大时候，就可能会出现性能好的服务触发限流了，但是性能不好的服务可能还比较空闲。因此实现来一个根据服务响应快慢来做负载均衡的一个算法：
```java
public class ShortestResponseLoadBalance extends AbstractLoadBalance {

    public static final String NAME = "shortestresponse";

    @Override
    protected <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation) {
        // Number of invokers
        int length = invokers.size();
        // Estimated shortest response time of all invokers
        long shortestResponse = Long.MAX_VALUE;
        // The number of invokers having the same estimated shortest response time
        int shortestCount = 0;
        // The index of invokers having the same estimated shortest response time
        int[] shortestIndexes = new int[length];
        // the weight of every invokers
        int[] weights = new int[length];
        // The sum of the warmup weights of all the shortest response  invokers
        int totalWeight = 0;
        // The weight of the first shortest response invokers
        int firstWeight = 0;
        // Every shortest response invoker has the same weight value?
        boolean sameWeight = true;

        // Filter out all the shortest response invokers
        for (int i = 0; i < length; i++) {
            Invoker<T> invoker = invokers.get(i);
            RpcStatus rpcStatus = RpcStatus.getStatus(invoker.getUrl(), invocation.getMethodName());
            // Calculate the estimated response time from the product of active connections and succeeded average elapsed time.
            long succeededAverageElapsed = rpcStatus.getSucceededAverageElapsed();
            int active = rpcStatus.getActive();
            long estimateResponse = succeededAverageElapsed * active;
            int afterWarmup = getWeight(invoker, invocation);
            weights[i] = afterWarmup;
            // Same as LeastActiveLoadBalance
            if (estimateResponse < shortestResponse) {
                shortestResponse = estimateResponse;
                shortestCount = 1;
                shortestIndexes[0] = i;
                totalWeight = afterWarmup;
                firstWeight = afterWarmup;
                sameWeight = true;
            } else if (estimateResponse == shortestResponse) {
                shortestIndexes[shortestCount++] = i;
                totalWeight += afterWarmup;
                if (sameWeight && i > 0
                        && afterWarmup != firstWeight) {
                    sameWeight = false;
                }
            }
        }
        if (shortestCount == 1) {
            return invokers.get(shortestIndexes[0]);
        }
        if (!sameWeight && totalWeight > 0) {
            int offsetWeight = ThreadLocalRandom.current().nextInt(totalWeight);
            for (int i = 0; i < shortestCount; i++) {
                int shortestIndex = shortestIndexes[i];
                offsetWeight -= weights[shortestIndex];
                if (offsetWeight < 0) {
                    return invokers.get(shortestIndex);
                }
            }
        }
        return invokers.get(shortestIndexes[ThreadLocalRandom.current().nextInt(shortestCount)]);
    }
}
```
最短响应负载均衡和最少活跃数负载均衡类似，只不过是最少活跃数的计算点是响应时间，其逻辑也是先获取最少响应时间的 invokers 
的下标，如果只有一个最少响应时间的服务就直接返回，否则判断是否权重相同，如果权重相同则随机抽取一个服务返回，否则根据权重选择一个服务然后返回。

# 总结
本章内容设计到一些经典的算法，理解起来不是很难，但是千亿级的服务器的访问下就是这些负载均衡在高效稳定的分配者一个个请求到服务端，弄懂和掌握这些负载均衡的逻辑至关重要。

# 参考文献
- http://dubbo.apache.org/zh/docs/v2.7/dev/source/loadbalance/#3%E6%80%BB%E7%BB%93