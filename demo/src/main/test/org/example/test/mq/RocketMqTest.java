package org.example.test.mq;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.producer.*;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageQueue;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class RocketMqTest {

    @Test
    public void test_producer_send() throws Exception{
        DefaultMQProducer producer = new DefaultMQProducer("test_group");
        producer.setNamesrvAddr("localhost:9876");
        producer.start();
        Message msg = new Message();
        msg.setBody("hello rocketmq".getBytes(StandardCharsets.UTF_8));
        msg.setTopic("tx_mq_topic");
        SendResult send = producer.send(msg);
        System.out.println(send);
        producer.shutdown();
    }

    @Test
    public void test_producer_send_callback() throws Exception{
        DefaultMQProducer producer = new DefaultMQProducer("test_group");
        producer.setNamesrvAddr("localhost:9876");
        producer.start();
        Message msg = new Message();
        msg.setBody("hello rocketmq".getBytes(StandardCharsets.UTF_8));
        msg.setTopic("tx_mq_topic");
       producer.send(msg, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                System.out.println(sendResult.getMsgId());
            }

            @Override
            public void onException(Throwable e) {

            }
        });
       Thread.sleep(1000);
        producer.shutdown();
    }

    ThreadLocal<ConcurrentHashMap<String,Long>> threadLocal = new ThreadLocal<>();
    public void begin(){
        begin("");
    }
    public Long cost(){
        return cost("");
    }
    public void begin(String key){
        long begin = System.currentTimeMillis();
        ConcurrentHashMap<String, Long> hashMap = threadLocal.get();
        if (hashMap==null){
            hashMap = new ConcurrentHashMap<>();
            threadLocal.set(hashMap);
            hashMap.put(key,begin);
        }
    }
    public Long cost(String key){
        long end = System.currentTimeMillis();
        ConcurrentHashMap<String, Long> hashMap = threadLocal.get();
        Long begin ;
        if (hashMap!=null && (begin=hashMap.get(key))!=null){
            return end-begin;
        }
        return null;
    }

    @Test
    public void test_producer_sendOneway() throws Exception{
        DefaultMQProducer producer = new DefaultMQProducer("test_group");
        producer.setNamesrvAddr("127.0.0.1:9876");
        producer.setSendMsgTimeout(30000);
        producer.start();
        System.out.println("生产端启动成功！");
        int count = 1000;
        begin("test");
        for (int i = 0; i < count; i++) {
            Message msg = new Message("tx_mq_topic",("hello test_producer_"+i).getBytes(StandardCharsets.UTF_8));
//            begin("i");
            producer.send(msg);
//            System.out.println(cost("i"));
        }
        Long cost = cost("test");
        System.out.println("耗时："+cost);
        System.out.println("平均耗时："+cost*1.0/count);
        producer.shutdown();
    }

    @Test
    public void test_transaction_msg_producer() throws Exception{
        AtomicInteger atomicInteger = new AtomicInteger();
        TransactionListener transactionListener = new TransactionListener() {
            @Override
            public LocalTransactionState executeLocalTransaction(Message msg, Object arg) {
                System.out.println("执行本地事务："+msg);
                return LocalTransactionState.UNKNOW;
            }

            @Override
            public LocalTransactionState checkLocalTransaction(MessageExt msg) {
                System.out.println("检查本地事务是否提交"+msg);
                int i = atomicInteger.incrementAndGet();
                if (i == 10) {
                    return LocalTransactionState.COMMIT_MESSAGE;
                }
                return LocalTransactionState.UNKNOW;
            }
        };
        TransactionMQProducer producer = new TransactionMQProducer("tx_test_group");
        producer.setNamesrvAddr("localhost:9876");
        ExecutorService executorService = new ThreadPoolExecutor(2, 5, 100, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(100), new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread();
                t.setName("tx_p_");
                return t;
            }
        });
        producer.setExecutorService(executorService);
        producer.setTransactionListener(transactionListener);
        producer.start();
        Message message = new Message();
        message.setTopic("tx_mq_topic");
        message.setBody("test_transaction_msg_producer".getBytes(StandardCharsets.UTF_8));
        TransactionSendResult result = producer.sendMessageInTransaction(message, null);
        System.out.println(result);

        Thread.sleep(100000);
        producer.shutdown();
    }

    @Test
    public void test_consumer() throws Exception{
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("test_group");
        consumer.setNamesrvAddr("localhost:9876");
        consumer.subscribe("mq_t1", "*");
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
//        consumer.setConsumerGroup("group1");x
        consumer.registerMessageListener(new MessageListenerConcurrently() {
            @Override
            public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs,
                                                            ConsumeConcurrentlyContext context) {
                System.out.println(1);

                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
        });

        //Launch the consumer instance.
        consumer.start();
        System.out.println(consumer);
        System.out.printf("Consumer Started.%n");
        Thread.sleep(System.currentTimeMillis()+System.currentTimeMillis());
    }
}
