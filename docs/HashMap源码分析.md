---
title: Java之HashMap分析
date: 2017-01-04
categories: 
- 开发技术
tags: 
- java
copyright: true
---
## hashmap分析前准备
> 当前jdk版本：jdk1.8

为了更好的研究hashmap的数据结构，我们写了个hashmap反射函数，可以打印出当前数据存放结构可视化和各项属性参数，这样可以帮助我们展示hashmap对象的具体情况和参数，下面是源码。
```
    @Test
    public void hashMapRefectionStudy() throws Exception {
        // 初始化map
        Map<Integer, String> map = new HashMap<>();
        for (int i = 0; i < 15; i++) {
            map.put((int) Math.pow(2, i), String.valueOf(i));
        }   
        map.put(96,15+"");
        printMapStructure(map);
    }
    // 打印map数据结构
    private void printMapStructure(HashMap map) throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
        Class<?> nodeClass = Class.forName("java.util.HashMap$Node");
        Class<?> treeNodeClass = Class.forName("java.util.HashMap$TreeNode");
        Object size = getFieldValue("size", map);
        log.info("size:" + size);
        Object modCount = getFieldValue("modCount", map);
        log.info("modCount:" + modCount);
        Object threshold = getFieldValue("threshold", map);
        log.info("threshold:" + threshold);
        Object loadFactor = getFieldValue("loadFactor", map);
        log.info("loadFactor:" + loadFactor);
        // 获取table大小和相关属性
        Object[] table = (Object[]) getFieldValue("table", map);
        log.info("table:" + table.length);
        // 格式化打印数据
        for (int i = 0; i < table.length; i++) {
            Object o = table[i];
            System.out.print("[" + i + "\t] ");
            if (o != null) {
                if (o.getClass() == nodeClass) {// 默认为链表
                    System.out.print("link ");
                    while (o != null) {
                        System.out.print(o + " ");
                        o = getFieldValue("next", o);
                    }
                    System.out.println();
                } else if (o.getClass() == treeNodeClass) {// 如果是红黑树
                    System.out.println("tree ");
                    // 获取root
                    Object root = o;
                    // 通过队列上线广度搜索打印
                    LinkedList<Object> queue = new LinkedList();
                    queue.add(root);
                    int treeCnt = 1;
                    while (!queue.isEmpty()) {
                        Object firstNode = queue.removeFirst();
                        System.out.print(firstNode + "\t\t");
                        if (firstNode != null) {
                            Object leftNode = getFieldValue("left", firstNode);
                            queue.addLast(leftNode);
                            Object rightNode = getFieldValue("right", firstNode);
                            queue.addLast(rightNode);
                        }
                        if (isLayerLastTreeNode(treeCnt)) {
                            System.out.println();
                        }
                        treeCnt++;
                    }
                    System.out.println();
                }
            } else {
                System.out.println("link " + o);
            }
        }
    }
    
    // 判断是否是一层树叶的最后一个
    // 1 3 7 15 31 ...
    public boolean isLayerLastTreeNode(int num) {
        double n = (Math.log(num + 1) / Math.log(2));
        return n == (int) n;
    }

    // 通过反射获取对象的任意属性
    public Object getFieldValue(String field, Object obj) throws NoSuchFieldException, IllegalAccessException {
        Class<?> aClass = obj.getClass();
        Field declaredField = aClass.getDeclaredField(field);
        if (declaredField == null) {
            return null;
        }
        declaredField.setAccessible(true);
        return declaredField.get(obj);
    }
```
上面函数介绍：
- 先初始化map，用来后序的分析。
- 准备hashmap中的链表节点`java.util.HashMap$Node`和树节点`java.util.HashMap$TreeNode`的class。
- 通过反射获取hashmap的桶（或者称为槽）`table`属性，通过可以看到是个数组，所以我们可以转为对象数组。
- 然后分别获取map的属性：`size、modCount、threshold、loadFactor`,下面我们会介绍每个属性。
- 循环table数组，我们获取每个元素，然后根据元素来判断是链表还是树。
- 如果是链表则根据链表方式打印，如果是树则根据树的广度优先搜索打印。
运行结果：
```
11:21:02:444|DEBUG|main|28|pid=34297@sanhang.local
11:21:02:463|INFO |main|74|table:64
11:21:02:476|INFO |main|76|size:16
11:21:02:477|INFO |main|78|modCount:16
11:21:02:477|INFO |main|80|threshold:48
11:21:02:481|INFO |main|82|loadFactor:0.75
[0	] tree 
512=9		
128=7		2048=11		
64=6		256=8		1024=10		8192=13		
null		null		null		null		null		null		4096=12		16384=14		
                                                                        null		null		null		null		// 树结构
[1	] link 1=0 
[2	] link 2=1 
[3	] link null
[4	] link 4=2 
[5	] link null
[6	] link null
[7	] link null
[8	] link 8=3 
[9	] link null
[10	] link null
...
[30	] link null
[31	] link null
[32	] link 32=5 96=15 // 链表结构出现
...
[61	] link null
[62	] link null
[63	] link null
```

## hashmap是个什么数据结构
通过我们的对map得分析运行结果可以得出，hashmap是由数组+链表+红黑树数据结果构成的。
```
// 数组
transient Node<K,V>[] table;
// 链表
static class Node<K,V> implements Map.Entry<K,V> {
    final int hash;
    final K key;
    V value;
    Node<K,V> next;

    Node(int hash, K key, V value, Node<K,V> next) {
        this.hash = hash;
        this.key = key;
        this.value = value;
        this.next = next;
    }
}
// 红黑树
static final class TreeNode<K,V> extends LinkedHashMap.Entry<K,V> {
    TreeNode<K,V> parent;  // red-black tree links
    TreeNode<K,V> left;
    TreeNode<K,V> right;
    TreeNode<K,V> prev;    // needed to unlink next upon deletion
    boolean red;
    TreeNode(int hash, K key, V val, Node<K,V> next) {
        super(hash, key, val, next);
    }
}
```
// TODO 此处需要一张结构图

说完结构，我们看些hashmap的一些重要常量和成员变量：
*六个常量*：
```
    /**
     * The default initial capacity - MUST be a power of two.
     */
     // 初始化容量
    static final int DEFAULT_INITIAL_CAPACITY = 1 << 4; // aka 16

    /**
     * The maximum capacity, used if a higher value is implicitly specified
     * by either of the constructors with arguments.
     * MUST be a power of two <= 1<<30.
     */
     // 最大容量
    static final int MAXIMUM_CAPACITY = 1 << 30;

    /**
     * The load factor used when none specified in constructor.
     */
     // 负载因子
    static final float DEFAULT_LOAD_FACTOR = 0.75f;

    /**
     * The bin count threshold for using a tree rather than list for a
     * bin.  Bins are converted to trees when adding an element to a
     * bin with at least this many nodes. The value must be greater
     * than 2 and should be at least 8 to mesh with assumptions in
     * tree removal about conversion back to plain bins upon
     * shrinkage.
     */
     // 链表转树的阈值
    static final int TREEIFY_THRESHOLD = 8;

    /**
     * The bin count threshold for untreeifying a (split) bin during a
     * resize operation. Should be less than TREEIFY_THRESHOLD, and at
     * most 6 to mesh with shrinkage detection under removal.
     */
     // 树转链表的阈值
    static final int UNTREEIFY_THRESHOLD = 6;

    /**
     * The smallest table capacity for which bins may be treeified.
     * (Otherwise the table is resized if too many nodes in a bin.)
     * Should be at least 4 * TREEIFY_THRESHOLD to avoid conflicts
     * between resizing and treeification thresholds.
     */
     // 最小树化的容量大小
    static final int MIN_TREEIFY_CAPACITY = 64;
```
关于常量的几个疑问：
1.为什么容量必须是2的n次方？
  为了提高计算效率，hashmap把原来的`取模%`换成了`与&`(两个数都为1则为1，否则为0)位运算，而hash%length==hash&(length-1)的前提是length是2的n次方。
  hashmap的最小容量是2。
2.链表转树的阈值和最小树化的容量大小的关系？
  当hashmap的容量小于64(`MIN_TREEIFY_CAPACITY`)的时候（也就是2，4，8，16，32）如果某个桶上的链表长度大于8（`TREEIFY_THRESHOLD`），则hashmap首先做扩容而不是做树化。如果容量大于等于`MIN_TREEIFY_CAPACITY`的时候有链表大于等于`TREEIFY_THRESHOLD`则做树化。

*六个变量*：
```
    /**
     * The table, initialized on first use, and resized as
     * necessary. When allocated, length is always a power of two.
     * (We also tolerate length zero in some operations to allow
     * bootstrapping mechanics that are currently not needed.)
     */
     // 桶数组
    transient Node<K,V>[] table;

    /**
     * Holds cached entrySet(). Note that AbstractMap fields are used
     * for keySet() and values().
     */
     // 调用hashmap.entrySet()的结果缓存。
    transient Set<Map.Entry<K,V>> entrySet;

    /**
     * The number of key-value mappings contained in this map.
     */
     // 存放数据的个数
    transient int size;

    /**
     * The number of times this HashMap has been structurally modified
     * Structural modifications are those that change the number of mappings in
     * the HashMap or otherwise modify its internal structure (e.g.,
     * rehash).  This field is used to make iterators on Collection-views of
     * the HashMap fail-fast.  (See ConcurrentModificationException).
     */
     // 修改总数
    transient int modCount;

    /**
     * The next size value at which to resize (capacity * load factor).
     *
     * @serial
     */
    // (The javadoc description is true upon serialization.
    // Additionally, if the table array has not been allocated, this
    // field holds the initial array capacity, or zero signifying
    // DEFAULT_INITIAL_CAPACITY.)
    // 扩容的阈值
    int threshold;

    /**
     * The load factor for the hash table.
     *
     * @serial
     */
     // 加载因子
    final float loadFactor;
```
关于变量的几个疑问：
1. hashmap最大可以存多少个key-value？
  以前我认为size为int变量，最多可以存储2的32次方个，否则size计数将会出现负数。然而我做了个实验发现确实出现负数了，但是还可以继续存储，所以hashmap理论可以存无限个（取决与你的内存）。
```
    @Test
    public void maxSize() throws Exception{
        Map map = new HashMap();
        // 通过反射将size设置为Integer.MAX_VALUE
        Class<? extends Map> mapClass = map.getClass();
        Field sizeField = mapClass.getDeclaredField("size");
        sizeField.setAccessible(true);
        sizeField.set(map,Integer.MAX_VALUE);
        // 输出
        log.info(s(map.size()));
        map.put("last","");// 添加一个
        log.info(s(map.size()));
        map.put("really_last","");// 添加一个
        log.info(s(map.size()));
    }
```
运行结果：
```
13:34:54:136|INFO |main|307|2147483647
13:34:54:137|INFO |main|309|-2147483648 // 负数
13:34:54:137|INFO |main|311|-2147483647 // 负数增加1
```

2. `modCount`记录每次修改+1，有什么作用？
  如果你看过jdk的其他集合源码也会看到这个变量，这个变量通过记录修改次数来实现`fail-fast`的，fail-fast可以防止新同学在多线程中使用hashmap导致的潜在问题，早发现早解决。
```
    // fail-fast
    @Test
    public void failFast() throws Exception{
        // 初始化map
        Map map = new HashMap();
        for (int i = 0; i < 10; i++) {
            map.put(i,i);
        }
        // 创建一个线程来循环map的元素
        Thread thread = new Thread(() -> {
            Set set = map.entrySet();
            for (Object s : set) {
                System.out.println(s);
                TestUtil.sleep(100);
            }
        });
        thread.start();
        // 操作map
        map.put("100",'1');
        thread.join();
    }
```
运行结果：
```
0=0
Exception in thread "Thread-0" java.util.ConcurrentModificationException
	at java.util.HashMap$HashIterator.nextNode(HashMap.java:1445)
	at java.util.HashMap$EntryIterator.next(HashMap.java:1479)
	at java.util.HashMap$EntryIterator.next(HashMap.java:1477)
	at demo.HashMapDemo$1.run(HashMapDemo.java:326)
	at java.lang.Thread.run(Thread.java:748)
```
循环源码：
```
    final class EntrySet extends AbstractSet<Map.Entry<K,V>> {
        public final int size()                 { return size; }
        public final void clear()               { HashMap.this.clear(); }
        
        public final void forEach(Consumer<? super Map.Entry<K,V>> action) {
            Node<K,V>[] tab;
            if (action == null)
                throw new NullPointerException();
            if (size > 0 && (tab = table) != null) {
                int mc = modCount;// 暂存mc = modCount
                for (int i = 0; i < tab.length; ++i) {
                    for (Node<K,V> e = tab[i]; e != null; e = e.next)
                        action.accept(e);
                }
                if (modCount != mc)// 校验 mc 和 modCount 如果不等则抛出异常。
                    throw new ConcurrentModificationException();
            }
        }
    }

```
3. `threshold`和`loadFactor`的关系？
我们先看下resize()函数的部分源码：
```
        {   // zero initial threshold signifies using defaults
            newCap = DEFAULT_INITIAL_CAPACITY;
            newThr = (int)(DEFAULT_LOAD_FACTOR * DEFAULT_INITIAL_CAPACITY);
        }
        if (newThr == 0) {
            float ft = (float)newCap * loadFactor;
            newThr = (newCap < MAXIMUM_CAPACITY && ft < (float)MAXIMUM_CAPACITY ?
                      (int)ft : Integer.MAX_VALUE);
        }
        threshold = newThr;
```
通过源码可以看出：`threshold = cap * loadFactor`,`threshold`最大为Integer.MAX_VALUE。
另外我们在创建HashMap的时候可以传入loadFactor来定制负载因子，负载因子可以为>0的任意数，通过loadFactor我们可以控制hashmap的扩容阈值，假如我们要时间换空间，那么loadFactor可以设置大些，如果空间换时间可以设置小点。
```
Map<Integer, String> map = new HashMap<>(10,3);
// 存储结构，16个桶位可以容纳三十个元素
[0	] link 0=0 16=4 64=8 144=12 256=16 400=20 576=24 784=28 
[1	] link 1=1 49=7 81=9 225=15 289=17 529=23 625=25 
[2	] link null
[3	] link null
[4	] link 4=2 36=6 100=10 196=14 324=18 484=22 676=26 
[5	] link null
[6	] link null
[7	] link null
[8	] link null
[9	] link 9=3 25=5 121=11 169=13 361=19 441=21 729=27 841=29 
[10	] link null
[11	] link null
[12	] link null
[13	] link null
[14	] link null
[15	] link null
```
## hashmap几个关键函数
我们的hashmap结构和一些重要常量和变量了解完后，下面我们将进行分析一些关键的函数。

### sizeForTable()
```
    // hashmap构造
    public HashMap(int initialCapacity, float loadFactor) {
        if (initialCapacity < 0)
            throw new IllegalArgumentException("Illegal initial capacity: " +
                                               initialCapacity);
        if (initialCapacity > MAXIMUM_CAPACITY)
            initialCapacity = MAXIMUM_CAPACITY;
        if (loadFactor <= 0 || Float.isNaN(loadFactor))
            throw new IllegalArgumentException("Illegal load factor: " +
                                               loadFactor);
        this.loadFactor = loadFactor;
        // 将容量大小暂存到threshold中，在resize的时候会用来做newCap来初始化容量。
        this.threshold = tableSizeFor(initialCapacity);
    }
    
    // 主角
    static final int tableSizeFor(int cap) {
        int n = cap - 1;
        n |= n >>> 1;
        n |= n >>> 2;
        n |= n >>> 4;
        n |= n >>> 8;
        n |= n >>> 16;
        return (n < 0) ? 1 : (n >= MAXIMUM_CAPACITY) ? MAXIMUM_CAPACITY : n + 1;
    }
    
    // resize()部分
    final Node<K,V>[] resize() {
            Node<K,V>[] oldTab = table;
            int oldCap = (oldTab == null) ? 0 : oldTab.length;
            int oldThr = threshold;
            ...省略
            newCap = oldThr;
            ...省略
            Node<K,V>[] newTab = (Node<K,V>[])new Node[newCap];// 初始化
            ...省略
        }
```
通过源码我们可以看到当我们构造一个指定容量大小的hashmap时候会调用`tableSizeFor`生成新的容量，然后赋值给threshold，通过初始化`resize`函数最终传递给newCap：`newCap = oldThr`，实现newTab初始化的的容量大小.
那么`tableSizeFor`函数具体在干嘛呢？这一堆`>>>`计算什么？
还记得常量容量的注释上有这么一句话：MUST be a power of two.而我们定义的容量怎么转化为a power of two就是`tableSizeFor`要做的事情。
我们使用自定义容量cap=14来举例整个计算过程，14的二进制表示为：00000000 00000000 00000000  00001110。
> "|"或：从高位开始比较，两个数只要有一个为1则为1，否则就为0。">>>"向右无符号移动。
```
1、int n = cap - 1;
n = 00000000 00000000 00000000  00001100

2、n |= n >>> 1;
[>>>1]:
n = 00000000 00000000 00000000  00000110
[n|= ]:
n = 00000000 00000000 00000000  00001110

3、n |= n >>> 2;
[>>>2]:
n = 00000000 00000000 00000000  00000001
[n|= ]:
n = 00000000 00000000 00000000  00001111

4、n |= n >>> 4;
[>>>4]:
n = 00000000 00000000 00000000  00000000
[n|= ]:
n = 00000000 00000000 00000000  00001111

5、n |= n >>> 8;
[>>>8]:
n = 00000000 00000000 00000000  00000000
[n|= ]:
n = 00000000 00000000 00000000  00001111

6、n |= n >>> 16;
[>>>16]:
n = 00000000 00000000 00000000  00000000
[n|= ]:
n = 00000000 00000000 00000000  00001111

7、return (n < 0) ? 1 : (n >= MAXIMUM_CAPACITY) ? MAXIMUM_CAPACITY : n + 1;
n = 00000000 00000000 00000000  00010000
最终转为十进制为：n=16
```
这个函数的最终目的就是把传入的cap-1然后把最高位1的后面所有0转为1，最后在加1得大于等于cap的最小2的n次方值。这里还有个小细节就是当你传入的cap=0的时候，在第6步计算出n为负数，第7步通过两个三元表达式保证n最小为2。

### hash（）
```
    static final int hash(Object key) {
        int h;
        return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
    }
```
这个哈希函数最核心的地方就是：(h = key.hashCode()) ^ (h >>> 16)，实现hashCode的高低位运算。
> "^"从高位开始比较，如果相同则为0，不相同则为1。
```
比如key=88888888：
key二进制：00000101 01001100 01010110 00111000
key>>>16：00000000 00000000 00000101 01001100
求出^结果：00000101 01001100 01010011 01110100
```
通过高低位运算提高了hash攻击的难度，让hash更散。

### putVal()
```
public V put(K key, V value) {
        return putVal(hash(key), key, value, false, true);
    }
    
final V putVal(int hash, K key, V value, boolean onlyIfAbsent,
                   boolean evict) {
        Node<K,V>[] tab; Node<K,V> p; int n, i;
        // 判断是否初始化，否则调用resize进行初始化。
        if ((tab = table) == null || (n = tab.length) == 0)
            n = (tab = resize()).length;
        // 进行桶位计算并判断当前桶是否为空，空则创建个node放进去。
        if ((p = tab[i = (n - 1) & hash]) == null)
            tab[i] = newNode(hash, key, value, null);
        else {
            Node<K,V> e; K k;
            // 如果hash相等并且key相等则说明当前key已经存在，则把已经存在的p赋给e用于后续操作。
            if (p.hash == hash &&
                ((k = p.key) == key || (key != null && key.equals(k))))
                e = p;
            // 如果是树则按照树的方式存入或获取相同的key的节点赋值给e。
            else if (p instanceof TreeNode)
                e = ((TreeNode<K,V>)p).putTreeVal(this, tab, hash, key, value);
            // 否则为链表
            else {
                for (int binCount = 0; ; ++binCount) {
                    if ((e = p.next) == null) {
                        p.next = newNode(hash, key, value, null);
                        // 通过binCount来判断更新后的链表是否大于等于（TREEIFY_THRESHOLD=8）则进行树化。
                        if (binCount >= TREEIFY_THRESHOLD - 1) // -1 for 1st
                            treeifyBin(tab, hash);
                        break;
                    }
                    if (e.hash == hash &&
                        ((k = e.key) == key || (key != null && key.equals(k))))
                        break;
                    p = e;
                }
            }
            // 如果e不为null则说明key已经存在，默认是替换原来的value，除非onlyIfAbsent=true
            if (e != null) { // existing mapping for key
                V oldValue = e.value;
                if (!onlyIfAbsent || oldValue == null)
                    e.value = value;
                afterNodeAccess(e);// Callbacks to allow LinkedHashMap post-actions
                return oldValue;
            }
        }
        ++modCount;
        if (++size > threshold)// 当元素大于threshold时候进行扩容
            resize();
        afterNodeInsertion(evict);// Callbacks to allow LinkedHashMap post-actions
        return null;
    }
    
   /**
     * Replaces all linked nodes in bin at index for given hash unless
     * table is too small, in which case resizes instead.
     */
     // 判断是要转换为树
    final void treeifyBin(Node<K,V>[] tab, int hash) {
        int n, index; Node<K,V> e;
        // 如果桶的长度小于64则进行扩容
        if (tab == null || (n = tab.length) < MIN_TREEIFY_CAPACITY)
            resize();
         // 否则进行转为树。
        else if ((e = tab[index = (n - 1) & hash]) != null) {
            TreeNode<K,V> hd = null, tl = null;
            do {
                TreeNode<K,V> p = replacementTreeNode(e, null);
                if (tl == null)
                    hd = p;
                else {
                    p.prev = tl;
                    tl.next = p;
                }
                tl = p;
            } while ((e = e.next) != null);
            if ((tab[index] = hd) != null)
                hd.treeify(tab);
        }
    }
```
通过源码分析，put操作主要有以下几个重要步骤：
1. 判断是否初始化，没有初始化则进行resize操作。
2. 通过hash值和容量算出桶位，判断桶位上是否已经有元素，没有则创建个node对象直接放进去。
3. 如果有值则判断当前值的key是否相等（p.hash == hash &&((k = p.key) == key || (key != null && key.equals(k)))，若相等则缓存当前元素到e。
4. 否则进行判断当前节点是否是树，如果是树，则按照树的方式加入元素。
5. 如果不是树则按照链表方式加入元素，如果新增元素后链表长度大于8则进行调用treeifyBin，treeifyBin会根据树化阈值和树化最小容量来决定是否要扩容或者树化。
6. 最后根据onlyIfAbsent来决定已经存在的key是否要替换位新的value，默认是false（替换）。
7. 在最后的最后在判断当前元素个数size是否大于扩容阈值threshold，如果大于则进行调用resize()进行扩容。

### resize()
当调用hashmap的put操作时候，如果有下面情况则发生扩容：
1. 当table变量为null，调用resize进行初始化。
2. 当新加入一个元素时候发现链表长度>=8但是当前容量小于64，则进行扩容。
3. 当新加入一个元素后元素总数大于threshold

```
    /**
     * Initializes or doubles table size.  If null, allocates in
     * accord with initial capacity target held in field threshold.
     * Otherwise, because we are using power-of-two expansion, the
     * elements from each bin must either stay at same index, or move
     * with a power of two offset in the new table.
     *
     * @return the table
     */
    final Node<K,V>[] resize() {
        Node<K,V>[] oldTab = table;
        int oldCap = (oldTab == null) ? 0 : oldTab.length;
        int oldThr = threshold;
        int newCap, newThr = 0;
        if (oldCap > 0) {
            // 如果容量达到最大还进行扩容，则将阈值设为最大。
            if (oldCap >= MAXIMUM_CAPACITY) {
                threshold = Integer.MAX_VALUE;
                return oldTab;
            }
            // 否则新的容量进行*2
            else if ((newCap = oldCap << 1) < MAXIMUM_CAPACITY &&
                     oldCap >= DEFAULT_INITIAL_CAPACITY)
                newThr = oldThr << 1; // double threshold 扩容阈值也翻倍
        }
        // 如果使用带指定容量的构造器时候会将容量暂存到threshold上，然后在这里赋值给newCap.
        else if (oldThr > 0) // initial capacity was placed in threshold
            newCap = oldThr;
        // 默认构造器第一次初始化
        else {               // zero initial threshold signifies using defaults
            newCap = DEFAULT_INITIAL_CAPACITY;
            newThr = (int)(DEFAULT_LOAD_FACTOR * DEFAULT_INITIAL_CAPACITY);
        }
        //  如果使用带指定容量的构造器时候newThr需要根据newCap计算获取。
        if (newThr == 0) {
            float ft = (float)newCap * loadFactor;
            newThr = (newCap < MAXIMUM_CAPACITY && ft < (float)MAXIMUM_CAPACITY ?
                      (int)ft : Integer.MAX_VALUE);
        }
        threshold = newThr;// 最终赋值给threshold。
        @SuppressWarnings({"rawtypes","unchecked"})
        Node<K,V>[] newTab = (Node<K,V>[])new Node[newCap];// 创建新数组
        table = newTab;
        // 如果老的table不为空则需要重新计算桶位和节点上的元素位置
        if (oldTab != null) {
            for (int j = 0; j < oldCap; ++j) {
                Node<K,V> e;
                if ((e = oldTab[j]) != null) {
                    oldTab[j] = null;// 帮助垃圾回收
                    // 如果只有一个节点那么直接通过hash计算出新的桶位。
                    if (e.next == null)
                        newTab[e.hash & (newCap - 1)] = e;
                    // 如果节点是个树，那么按照树的方式分解
                    else if (e instanceof TreeNode)
                        ((TreeNode<K,V>)e).split(this, newTab, j, oldCap);
                    else { // preserve order
                        // 否则是链表，这里链表通过生成两组高低位链表，并且保持链表原来的顺序
                        Node<K,V> loHead = null, loTail = null;
                        Node<K,V> hiHead = null, hiTail = null;
                        Node<K,V> next;
                        do {
                            next = e.next;
                            // 如果hash&oldCap为0则为低位，也就是原来位置，下面会介绍为什么这样。
                            if ((e.hash & oldCap) == 0) {
                                if (loTail == null)
                                    loHead = e;
                                else
                                    loTail.next = e;
                                loTail = e;
                            }
                            else {// 否则放到高位链表上
                                if (hiTail == null)
                                    hiHead = e;
                                else
                                    hiTail.next = e;
                                hiTail = e;
                            }
                        } while ((e = next) != null);
                        // 将低位的链表头放到原来的桶位
                        if (loTail != null) {
                            loTail.next = null;
                            newTab[j] = loHead;
                        }
                        // 将高位的链表头放到原来位置+原来容量的位置
                        if (hiTail != null) {
                            hiTail.next = null;
                            newTab[j + oldCap] = hiHead;
                        }
                    }
                }
            }
        }
        return newTab;
    }
```
通过源码分析，resize()可分为以下步骤：
1. 容量`newCap`、`threshold`阈值的计算和newTab的创建。
2. 老的单节点直接向新的桶中拷贝。
3. 老的树节点向新的桶中拷贝。
4. 老的链节点向新的桶中拷贝，这里通过链表分组方式进行拷贝，同时还保留了原来的链表顺序。

这里有个问题，为什么`(e.hash & oldCap) == 0`位置不变，而需要变的位置为`j + oldCap`?
- oldCap一定是2的整数次幂, 这里假设是2^m
- newCap是oldCap的两倍, 则会是2^(m+1)
- hash对数组大小取模(n - 1) & hash 其实就是取hash的低m位.

比如当容量32的时候35值放在第3个桶位，当扩容到64容量时候放到第35桶位的需要移位情况计算：
```
容量cap = 32:
index = (cap-1)&35:
cap-1:00000000 00000000 00000000 00011111
&35  :00000000 00000000 00000000 00100011
=3   :00000000 00000000 00000000 00000011

扩容后新容量nap = 64:
index = (nap-1)&35:
nap-1:00000000 00000000 00000000 00111111
&35  :00000000 00000000 00000000 00100011
=35  :00000000 00000000 00000000 00100011

当和老的容量cap=32做&运算来判断是否移位，其实就是算下：
index = cap&35:
cap  :00000000 00000000 00000000 00100000
&35  :00000000 00000000 00000000 00100011
=32  :00000000 00000000 00000000 00100000
显然结果cap&35=32!=0，需要移位（3+cap）=35。
```

比如当容量32的时候67值放在第3个桶位，当扩容到64容量时候还是放到第3个桶位：
```
容量cap = 32:
index = (cap-1)&67:
cap-1:00000000 00000000 00000000 00011111
&67  :00000000 00000000 00000000 01000011
=3   :00000000 00000000 00000000 00000011

扩容后新容量nap = 64:
index = (nap-1)&67:
nap-1:00000000 00000000 00000000 00111111
&67  :00000000 00000000 00000000 01000011
=3   :00000000 00000000 00000000 00000011

当和老的容量cap=32做&运算来判断是否移位，其实就是算下：
index = cap&67:
cap  :00000000 00000000 00000000 00100000
&67  :00000000 00000000 00000000 01000011
=0   :00000000 00000000 00000000 00000000
显然结果cap&35=0==0，不需要移位。
```

## hashmap每个阶段分析
到这里的同学应该已经大致了解hashmap的基本原理和关键的函数了，下面我们将通过想hashmap不断的增加数据和减少数据总结每个阶段：
1. 创建对象阶段
  当我们使用new HashMap()创建一个对象的时候，只是初始化`loadFactor=DEFAULT_LOAD_FACTOR`值。
```
14:02:264|INFO |main|33|size:0
14:02:265|INFO |main|35|modCount:0
14:02:265|INFO |main|37|threshold:0
14:02:274|INFO |main|39|loadFactor:0.75
```
2. 初始化容量阶段
  当添加第一个元素的时候，hashmap进行真正的初始化，主要是创建table容量，默认长度为16。
```
16:32:850|INFO |main|33|size:1
16:32:850|INFO |main|35|modCount:1
16:32:851|INFO |main|37|threshold:12
16:32:854|INFO |main|39|loadFactor:0.75
16:32:854|INFO |main|42|table:16
```
3. 扩容量阶段
  扩容的时候执行resize()，threshold和table容量加倍.
  - 当元素个数size大于threshold=12时候进行扩容。
```
25:36:262|INFO |main|33|size:13
25:36:263|INFO |main|35|modCount:13
25:36:263|INFO |main|37|threshold:24
25:36:272|INFO |main|39|loadFactor:0.75
25:36:275|INFO |main|42|table:32
[0	] link 0=0 64=8 
[1	] link 1=1 
[2	] link null
[3	] link null
[4	] link 4=2 36=6 100=10 
[5	] link null...
[8	] link null
[9	] link 9=3 
[10	] link null...
[15	] link null
[16	] link 16=4 144=12 
[17	] link 49=7 81=9 
[18	] link null...
[25	] link 25=5 121=11 
[30	] link null
[31	] link null
```
  - 当容量小于64时，某个桶的长度大于8的时候无论size是否大于threshold都进行扩容。
```
29:34:140|INFO |main|34|size:12
29:34:141|INFO |main|36|modCount:12
29:34:142|INFO |main|38|threshold:24
29:34:145|INFO |main|40|loadFactor:0.75
29:34:146|INFO |main|43|table:32
[0	] link 32=5 64=6 128=7 256=8 512=9 1024=10 2048=11 4096=13 
[1	] link null
[2	] link 2=1 
[3	] link null
[4	] link 4=2 
[5	] link null..
[7	] link null
[8	] link 8=3 
[9	] link null...
[15	] link null
[16	] link 16=4 
[17	] link null...
[31	] link null
```
5. 树化阶段
  当容量大于等于64时候，某个桶的长度大于8，则进行树化操作。
```
32:03:049|INFO |main|33|size:15
32:03:050|INFO |main|35|modCount:15
32:03:050|INFO |main|37|threshold:48
32:03:080|INFO |main|39|loadFactor:0.75
32:03:081|INFO |main|42|table:64
[0	] tree 
512=9		
128=7		2048=11		
64=6		256=8		1024=10		8192=13		
null		null		null		null		null		null		4096=12		16384=14		
                                                                        null		null		null		null		
[1	] link 1=0 
[2	] link 2=1 
[3	] link null
[4	] link 4=2 
[5	] link null...
[7	] link null
[8	] link 8=3 
[9	] link null...
[15	] link null
[16	] link 16=4 
[17	] link null...
[31	] link null
[32	] link 32=5 
[33	] link null...
[63	] link null
```
6. 树退化阶段
  树的退化并不是我们像链表转为树那样，树的节点小于6就退化为链表，而是分为两种情况：
  1. 当扩容的时候，如果树的节点小于等于`UNTREEIFY_THRESHOLD=6`则转换为链表。
  2. 如果是操作remove删除节点的时候，树要转化为链表的的节点个数是小于等于2。
  
## hashmap多线程下的问题
- 在jdk1.7下，当并发插入元素时，由于链表操作会产生环链，当后续有操作环链上的数据时就会进入死循环，在jdk1.8及更高版本后，链表操作进行了分组拷贝，并保持了原来的链表顺序，所以就避免了死循环。
- 在jdk1.8下，当并发插入元素时候可能会获取不到，原因是当一个线程计算好桶位的时候，而另一个线程出发了map的扩容，这样第一个线程计算的桶位放到了新的扩容后的桶中，当再次查这个key的时候就会使用新的桶长度进行取模，这样就获取不到这个元素了。
丢失的元素复现demo源码：
```
    @Test
    public void concurrentOpretor() throws Exception{
        while (true){
            CountDownLatch countDownLatch = new CountDownLatch(2);
            HashMap map = new HashMap(0);// 容量设置为0，增加扩容次数
            // 两个线程同时插入元素
            Thread t1 = new Thread(() -> {
                for (int i = 0; i < 1000; i++) {
                    map.put(i*2, String.valueOf(i));
                }
                countDownLatch.countDown();
            });
            Thread t2 = new Thread(() -> {
                for (int i = 0; i < 1000; i++) {
                    map.put(i*3, String.valueOf(i));
                }
                countDownLatch.countDown();
            });
            t1.start();
            t2.start();
            countDownLatch.await();
            // 再次获取每个元素，空则抛出异常
            for (int i = 0; i < 1000; i++) {
                Object o = map.get(i*2);
                if (o==null){
                    printMapStructure(map);
                    log.error("key:{}",s(i*2));
                    throw new RuntimeException();
                }
            }
            for (int i = 0; i < 1000; i++) {
                Object o =  map.get(i*3);
                if (o==null){
                    printMapStructure(map);
                    log.error("key:{}",s(i*3));
                    throw new RuntimeException();
                }
            }
            TestUtil.sleep(300);
        }
    }

// 运行结果：
32:54:631|INFO |main|45|size:1582
32:54:631|INFO |main|47|modCount:1580
32:54:631|INFO |main|49|threshold:3072
32:54:634|INFO |main|51|loadFactor:0.75
32:54:635|INFO |main|54|table:4096
[0	] link 0=0 
[1	] link null...
[22	] link 22=11 
[23	] link null
[24	] link 24=8 152=76 
[25	] link null
[26	] link 26=13 
[27	] link 27=9...
[4094	] link null
[4095	] link null
32:54:736|ERROR|main|354|index:152
32:54:736|DEBUG|main|33|take up time:1728 ms
Disconnected from the target VM, address: '127.0.0.1:60922', transport: 'socket'

java.lang.RuntimeException
	at demo.HashMapDemo.concurrentOpretor(HashMapDemo.java:355)
```
通过运行结果可以看出当前容量为4096，但是有个元素152=76 放到了桶位为24的位置，显然是错误的。我们经过hash桶位计算得出key=152的元素只有当容量为64的时候桶位为24。如果容量为4096则key=152的桶位应为152。
另外我们也看出map的size属性和modCount也是错误的，都是因为并发时候++size和++modCount也不是原子性的。

## 其他说明
因为这里要消化的东西不算少，所以关于红黑树的知识就单独抽出来介绍了。

## 参考资料

- https://www.cnblogs.com/zhimingxin/p/8609545.html
- https://www.cnblogs.com/yesiamhere/p/6675067.html

> 相关源码：https://github.com/mvilplss/note