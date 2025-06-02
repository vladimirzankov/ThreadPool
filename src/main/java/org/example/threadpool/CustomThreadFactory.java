package org.example.threadpool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class CustomThreadFactory implements ThreadFactory {
    private static final Logger logger = LoggerFactory.getLogger(CustomThreadFactory.class);
    private final String poolName;
    private final AtomicInteger threadNumber = new AtomicInteger(1);
    private final ThreadGroup group;

    public CustomThreadFactory(String poolName) {
        this.poolName = poolName;
        SecurityManager s = System.getSecurityManager();
        this.group = (s != null) ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();
    }

    @Override
    public Thread newThread(Runnable r) {
        String threadName = String.format("%s-worker-%d", poolName, threadNumber.getAndIncrement());
        Thread t = new Thread(group, r, threadName, 0);
        
        if (t.isDaemon()) {
            t.setDaemon(false);
        }
        if (t.getPriority() != Thread.NORM_PRIORITY) {
            t.setPriority(Thread.NORM_PRIORITY);
        }

        logger.info("[ThreadFactory] Creating new thread: {}", threadName);
        return t;
    }
} 