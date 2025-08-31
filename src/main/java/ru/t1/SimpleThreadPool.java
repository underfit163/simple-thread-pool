package ru.t1;

import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class SimpleThreadPool {
    private final Queue<Runnable> taskQueue;
    private final Thread[] workers;

    private final ReentrantLock taskLock;
    private final Condition notEmpty;

    private final ReentrantLock terminationLock;
    private final Condition terminationCondition;
    private final AtomicInteger activeThreads;

    private final AtomicBoolean isShutdown;
    private final AtomicLong submittedCount;
    private final AtomicLong completedCount;

    public SimpleThreadPool(int poolSize) {
        if (poolSize <= 0) throw new IllegalArgumentException("poolSize must be greater than 0");
        this.workers = new Thread[poolSize];
        taskQueue = new LinkedList<>();
        taskLock = new ReentrantLock();
        notEmpty = taskLock.newCondition();
        terminationLock = new ReentrantLock();
        terminationCondition = terminationLock.newCondition();
        activeThreads = new AtomicInteger(0);
        isShutdown = new AtomicBoolean(false);
        submittedCount = new AtomicLong(0);
        completedCount = new AtomicLong(0);

        for (int i = 0; i < poolSize; i++) {
            workers[i] = new Thread(new Worker(), "SimpleThreadPool-worker-" + i);
            workers[i].start();
        }
    }

    public void execute(Runnable task) {
        Objects.requireNonNull(task, "task must not be null");
        if (isShutdown.get()) {
            throw new IllegalStateException("ThreadPool is shutdown, cannot accept new tasks");
        }

        taskLock.lock();
        try {
            taskQueue.offer(task);
            notEmpty.signal();
            submittedCount.incrementAndGet();
        } finally {
            taskLock.unlock();
        }
    }

    public void shutdown() {
        if (!isShutdown.getAndSet(true)) {
            taskLock.lock();
            try {
                notEmpty.signalAll();
            } finally {
                taskLock.unlock();
            }
        }
    }

    public void awaitTermination() throws InterruptedException {
        terminationLock.lock();
        try {
            while (activeThreads.get() > 0) {
                terminationCondition.await();
            }
        } finally {
            terminationLock.unlock();
        }
    }

    public long getSubmittedCount() {
        return submittedCount.get();
    }

    public long getCompletedCount() {
        return completedCount.get();
    }

    private class Worker implements Runnable {
        @Override
        public void run() {
            activeThreads.incrementAndGet();
            try {
                while (true) {
                    Runnable task;
                    taskLock.lock();
                    try {
                        while (taskQueue.isEmpty() && !isShutdown.get()) {
                            try {
                                notEmpty.await();
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                return;
                            }
                        }
                        if (taskQueue.isEmpty() && isShutdown.get()) {
                            return;
                        }
                        task = taskQueue.poll();
                        if (task == null) {
                            continue;
                        }
                    } finally {
                        taskLock.unlock();
                    }

                    try {
                        task.run();
                        completedCount.incrementAndGet();
                    } catch (RuntimeException t) {
                        System.err.println("Task threw exception: " + t.getMessage());
                    }
                }
            } finally {
                if (activeThreads.decrementAndGet() == 0) {
                    terminationLock.lock();
                    try {
                        terminationCondition.signalAll();
                    } finally {
                        terminationLock.unlock();
                    }
                }
            }
        }
    }
}
