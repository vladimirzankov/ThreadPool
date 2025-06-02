package org.example.threadpool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class CustomThreadPool implements CustomExecutor {
    private static final Logger logger = LoggerFactory.getLogger(CustomThreadPool.class);

    private final int corePoolSize;
    private final int maxPoolSize;
    private final long keepAliveTime;
    private final TimeUnit timeUnit;
    private final int queueSize;
    private final int minSpareThreads;
    private final CustomThreadFactory threadFactory;
    private final List<BlockingQueue<Runnable>> taskQueues;
    private final List<Worker> workers;
    private final AtomicInteger activeThreads = new AtomicInteger(0);
    private final ReentrantLock mainLock = new ReentrantLock();
    private volatile boolean isShutdown = false;
    private volatile boolean isTerminated = false;

    public CustomThreadPool(int corePoolSize, int maxPoolSize, long keepAliveTime, TimeUnit timeUnit,
                          int queueSize, int minSpareThreads, String poolName) {
        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maxPoolSize;
        this.keepAliveTime = keepAliveTime;
        this.timeUnit = timeUnit;
        this.queueSize = queueSize;
        this.minSpareThreads = minSpareThreads;
        this.threadFactory = new CustomThreadFactory(poolName);
        this.taskQueues = new ArrayList<>();
        this.workers = new ArrayList<>();

        for (int i = 0; i < corePoolSize; i++) {
            addWorker();
        }
    }

    private void addWorker() {
        mainLock.lock();
        try {
            if (workers.size() >= maxPoolSize) {
                return;
            }

            BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(queueSize);
            taskQueues.add(queue);
            Worker worker = new Worker(queue);
            workers.add(worker);
            worker.thread.start();
            activeThreads.incrementAndGet();
        } finally {
            mainLock.unlock();
        }
    }

    @Override
    public void execute(Runnable command) {
        if (isShutdown) {
            throw new RejectedExecutionException("ThreadPool is shutdown");
        }

        if (command == null) {
            throw new NullPointerException();
        }

        BlockingQueue<Runnable> selectedQueue = null;
        int minSize = Integer.MAX_VALUE;

        for (BlockingQueue<Runnable> queue : taskQueues) {
            if (queue.size() < minSize) {
                minSize = queue.size();
                selectedQueue = queue;
            }
        }

        if (selectedQueue != null && selectedQueue.offer(command)) {
            logger.info("[Pool] Task accepted into queue #{}", taskQueues.indexOf(selectedQueue));
            return;
        }

        if (workers.size() < maxPoolSize) {
            addWorker();
            execute(command);
            return;
        }

        logger.warn("[Rejected] Task was rejected due to overload!");
        throw new RejectedExecutionException("Task rejected due to overload");
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        if (task == null) throw new NullPointerException();
        FutureTask<T> futureTask = new FutureTask<>(task);
        execute(futureTask);
        return futureTask;
    }

    @Override
    public void shutdown() {
        mainLock.lock();
        try {
            isShutdown = true;
            logger.info("[Pool] Shutdown initiated");
        } finally {
            mainLock.unlock();
        }
    }

    @Override
    public void shutdownNow() {
        mainLock.lock();
        try {
            isShutdown = true;
            isTerminated = true;
            for (Worker worker : workers) {
                worker.thread.interrupt();
            }
            logger.info("[Pool] Shutdown now initiated");
        } finally {
            mainLock.unlock();
        }
    }

    private class Worker implements Runnable {
        private final BlockingQueue<Runnable> queue;
        private final Thread thread;
        private volatile boolean running = true;

        Worker(BlockingQueue<Runnable> queue) {
            this.queue = queue;
            this.thread = threadFactory.newThread(this);
        }

        @Override
        public void run() {
            try {
                while (running && !isTerminated) {
                    Runnable task = queue.poll(keepAliveTime, timeUnit);
                    if (task != null) {
                        logger.info("[Worker] {} executes task", thread.getName());
                        task.run();
                    } else if (workers.size() > corePoolSize) {
                        logger.info("[Worker] {} idle timeout, stopping", thread.getName());
                        break;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                logger.info("[Worker] {} terminated", thread.getName());
                mainLock.lock();
                try {
                    workers.remove(this);
                    taskQueues.remove(queue);
                    activeThreads.decrementAndGet();
                } finally {
                    mainLock.unlock();
                }
            }
        }
    }
} 