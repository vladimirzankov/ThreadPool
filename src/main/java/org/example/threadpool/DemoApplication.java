package org.example.threadpool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class DemoApplication {
    private static final Logger logger = LoggerFactory.getLogger(DemoApplication.class);
    private static final AtomicInteger taskCounter = new AtomicInteger(0);

    public static void main(String[] args) {
        CustomThreadPool threadPool = new CustomThreadPool(
            2,
            4,
            5,
            TimeUnit.SECONDS,
            5,
            1,
            "CustomPool"
        );

        for (int i = 0; i < 10; i++) {
            final int taskId = taskCounter.incrementAndGet();
            try {
                threadPool.execute(() -> {
                    logger.info("Task {} started", taskId);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    logger.info("Task {} completed", taskId);
                });
            } catch (RejectedExecutionException e) {
                logger.error("Task {} was rejected", taskId);
            }
        }

        try {
            threadPool.submit(() -> {
                logger.info("Callable task started");
                Thread.sleep(2000);
                logger.info("Callable task completed");
                return "Callable task result";
            });
        } catch (RejectedExecutionException e) {
            logger.error("Callable task was rejected");
        }

        try {
            Thread.sleep(15000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        logger.info("Initiating shutdown...");
        threadPool.shutdown();

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        logger.info("Initiating force shutdown...");
        threadPool.shutdownNow();
    }
} 