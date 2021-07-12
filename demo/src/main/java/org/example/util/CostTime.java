package org.example.util;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 时间监测
 */
public class CostTime {
    static ThreadLocal<ConcurrentHashMap<String,Long>> threadLocal = new ThreadLocal<>();
    public static void begin(){
        begin("");
    }
    public static Long cost(){
        return cost("");
    }
    public static void begin(String key){
        long begin = System.currentTimeMillis();
        ConcurrentHashMap<String, Long> hashMap = threadLocal.get();
        if (hashMap==null){
            hashMap = new ConcurrentHashMap<>();
            threadLocal.set(hashMap);
            hashMap.put(key,begin);
        }
    }
    public static Long cost(String key){
        long end = System.currentTimeMillis();
        ConcurrentHashMap<String, Long> hashMap = threadLocal.get();
        Long begin ;
        if (hashMap!=null && (begin=hashMap.get(key))!=null){
            return end-begin;
        }
        return null;
    }
}
