/*
 * Copyright 2023-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package me.champeau.a4j.jsolex.processing.util;

import me.champeau.a4j.jsolex.processing.sun.tasks.AbstractTask;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class ParallelExecutor implements AutoCloseable {
    private final ExecutorService executorService;
    private final Semaphore semaphore;

    private Consumer<? super Throwable> exceptionHandler = (Consumer<Throwable>) LoggingSupport::logError;

    private ParallelExecutor(int parallelism) {
        executorService = Executors.newWorkStealingPool(parallelism);
        semaphore = new Semaphore(parallelism);
    }

    public static ParallelExecutor newExecutor() {
        return new ParallelExecutor(Runtime.getRuntime().availableProcessors());
    }

    public static ParallelExecutor newExecutor(int parallelism) {
        if (parallelism < 1) {
            throw new IllegalArgumentException("Parallelism cannot be lower than 1");
        }
        return new ParallelExecutor(parallelism);
    }

    public void setExceptionHandler(Consumer<? super Throwable> exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
    }

    public void submit(Runnable task) {
        try {
            semaphore.acquire();
            executorService.submit(() -> {
                Thread.currentThread().setUncaughtExceptionHandler((t, e) -> exceptionHandler.accept(e));
                try {
                    task.run();
                } catch (Throwable ex) {
                    exceptionHandler.accept(ex);
                } finally {
                    notifyTaskFinished();
                    Thread.currentThread().setUncaughtExceptionHandler(null);
                }
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProcessingException(e);
        }
    }

    public <T> Future<T> submit(Callable<T> task) {
        try {
            semaphore.acquire();
            return executorService.submit(() -> {
                Thread.currentThread().setUncaughtExceptionHandler((t, e) -> exceptionHandler.accept(e));
                try {
                    return task.call();
                } catch (Throwable ex) {
                    exceptionHandler.accept(ex);
                } finally {
                    notifyTaskFinished();
                    Thread.currentThread().setUncaughtExceptionHandler(null);
                }
                return null;
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProcessingException(e);
        }
    }

    private void notifyTaskFinished() {
        semaphore.release();
    }

    public <T> CompletableFuture<T> submit(AbstractTask<T> task) {
        try {
            semaphore.acquire();
            return CompletableFuture.supplyAsync(() -> {
                Thread.currentThread().setUncaughtExceptionHandler((t, e) -> exceptionHandler.accept(e));
                try {
                    return task.get();
                } catch (Throwable ex) {
                    exceptionHandler.accept(ex);
                    return null;
                } finally {
                    Thread.currentThread().setUncaughtExceptionHandler(null);
                    notifyTaskFinished();
                }
            }, executorService);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProcessingException(e);
        }
    }

    @Override
    public void close() throws Exception {
        executorService.shutdown();
        if (!executorService.awaitTermination(1, TimeUnit.HOURS)) {
            throw new ProcessingException("Processing timed out after 1 hour");
        }
    }
}
