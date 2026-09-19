package com.hmdp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/*
 * 逻辑过期后的缓存重建必须异步执行。
 *
 * 不能每次过期都 new Thread()：
 * 线程数量失控会反过来压垮 JVM。
 */

//这里是线程池管理对象，
//ThreadPoolTaskExecutor 是 Spring 对 JDK ThreadPoolExecutor 的一层封装。
@Configuration
public class CacheExecutorConfig {
    @Bean("cacheRebuildExecutor")
    public ThreadPoolTaskExecutor cacheRebuildExecutor(){
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);

        executor.setThreadNamePrefix("cache-rebuild-");

        //队列满拒绝任务时；CacheClient会释放获得的锁
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());

        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);

        return executor;

    }
}
