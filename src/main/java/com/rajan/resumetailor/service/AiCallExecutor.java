package com.rajan.resumetailor.service;

import com.rajan.resumetailor.config.AiExecutionProperties;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AiCallExecutor {

    private static final Logger log = LoggerFactory.getLogger(AiCallExecutor.class);

    private final AiExecutionProperties properties;
    private final AiFailureClassifier failureClassifier;
    private final java.util.concurrent.Semaphore semaphore;

    public AiCallExecutor(AiExecutionProperties properties, AiFailureClassifier failureClassifier) {
        this.properties = properties;
        this.failureClassifier = failureClassifier;
        this.semaphore = new java.util.concurrent.Semaphore(Math.max(1, properties.getMaxConcurrent()), true);
    }

    public <T> T execute(String operationName, Callable<T> operation) {
        boolean acquired = false;
        try {
            acquired = tryAcquire();
            if (!acquired) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "AI is busy right now. Please try again in a moment."
                );
            }

            int maxAttempts = Math.max(1, properties.getMaxAttempts());
            Duration base = safeDuration(properties.getBaseBackoff(), Duration.ofMillis(250));
            Duration max = safeDuration(properties.getMaxBackoff(), Duration.ofSeconds(2));

            Exception last = null;
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                try {
                    return operation.call();
                } catch (Exception exception) {
                    last = exception;

                    // Don't retry if the provider is quota exhausted; Step 1 fallback will handle it.
                    if (failureClassifier.isQuotaExhausted(exception)) {
                        throw exception;
                    }

                    boolean transientFailure = failureClassifier.isTransient(exception);
                    boolean canRetry = attempt < maxAttempts;
                    if (!transientFailure || !canRetry) {
                        throw exception;
                    }

                    AiCallWarnings.add("AI call had a transient failure and was retried.");
                    log.warn("AI call transient failure (op={}, attempt={}/{}): {}",
                            operationName,
                            attempt,
                            maxAttempts,
                            exception.getMessage());
                    sleepBackoff(base, max, attempt);
                }
            }

            if (last instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new RuntimeException(last);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI request was interrupted.", interrupted);
        } catch (ResponseStatusException response) {
            throw response;
        } catch (RuntimeException runtime) {
            throw runtime;
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        } finally {
            if (acquired) {
                semaphore.release();
            }
        }
    }

    private boolean tryAcquire() throws InterruptedException {
        Duration timeout = safeDuration(properties.getAcquireTimeout(), Duration.ofSeconds(2));
        long millis = Math.max(0, timeout.toMillis());
        return semaphore.tryAcquire(millis, TimeUnit.MILLISECONDS);
    }

    private void sleepBackoff(Duration base, Duration max, int attempt) throws InterruptedException {
        long baseMs = Math.max(0, base.toMillis());
        long maxMs = Math.max(baseMs, max.toMillis());

        // Exponential backoff: base * 2^(attempt-1), clamped to max.
        long exp = baseMs * (1L << Math.min(10, Math.max(0, attempt - 1)));
        long target = Math.min(maxMs, exp);

        // Add a small jitter to avoid thundering herd.
        long jitter = ThreadLocalRandom.current().nextLong(0, 120);
        long sleepMs = Math.min(maxMs, target + jitter);

        if (sleepMs <= 0) {
            return;
        }

        // LockSupport avoids creating checked exceptions; we still respect interrupts.
        long nanos = TimeUnit.MILLISECONDS.toNanos(sleepMs);
        LockSupport.parkNanos(nanos);
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("Interrupted during AI retry backoff.");
        }
    }

    private Duration safeDuration(Duration value, Duration fallback) {
        if (value == null || value.isNegative()) {
            return fallback;
        }
        return value;
    }
}
