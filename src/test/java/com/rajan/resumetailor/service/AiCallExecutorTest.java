package com.rajan.resumetailor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.rajan.resumetailor.config.AiExecutionProperties;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class AiCallExecutorTest {

    @Test
    void retriesOnTransientFailureAndEventuallySucceeds() {
        AiExecutionProperties props = new AiExecutionProperties();
        props.setMaxAttempts(3);
        props.setBaseBackoff(Duration.ZERO);
        props.setMaxBackoff(Duration.ZERO);
        props.setAcquireTimeout(Duration.ofSeconds(1));
        props.setMaxConcurrent(1);

        AiCallExecutor exec = new AiCallExecutor(props, new AiFailureClassifier());
        AtomicInteger calls = new AtomicInteger();

        String value = exec.execute("test", () -> {
            int c = calls.incrementAndGet();
            if (c < 3) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "temporary");
            }
            return "ok";
        });

        assertEquals("ok", value);
        assertEquals(3, calls.get());
    }

    @Test
    void doesNotRetryOnQuotaExhausted() {
        AiExecutionProperties props = new AiExecutionProperties();
        props.setMaxAttempts(5);
        props.setBaseBackoff(Duration.ZERO);
        props.setMaxBackoff(Duration.ZERO);
        props.setAcquireTimeout(Duration.ofSeconds(1));
        props.setMaxConcurrent(1);

        AiCallExecutor exec = new AiCallExecutor(props, new AiFailureClassifier());
        AtomicInteger calls = new AtomicInteger();

        assertThrows(ResponseStatusException.class, () -> exec.execute("test", () -> {
            calls.incrementAndGet();
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "quota exceeded");
        }));

        assertEquals(1, calls.get());
    }
}

