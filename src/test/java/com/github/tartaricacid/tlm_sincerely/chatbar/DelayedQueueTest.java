package com.github.tartaricacid.tlm_sincerely.chatbar;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DelayedQueue} 的单元测试：到点才执行、同一时刻按加入顺序、异常不影响其它任务。
 */
class DelayedQueueTest {

    @Test
    void runDue_onlyRunsTasksWhoseTimeHasCome() {
        DelayedQueue queue = new DelayedQueue();
        List<String> log = new ArrayList<>();
        queue.add(10, () -> log.add("a"));
        queue.add(30, () -> log.add("c"));
        queue.add(20, () -> log.add("b"));
        assertEquals(0, queue.runDue(9, e -> { }));
        assertEquals(1, queue.runDue(10, e -> { }));
        assertEquals(List.of("a"), log);
        assertEquals(2, queue.runDue(100, e -> { }));
        assertEquals(List.of("a", "b", "c"), log);
        assertTrue(queue.isEmpty());
    }

    @Test
    void runDue_sameTickKeepsInsertionOrder() {
        DelayedQueue queue = new DelayedQueue();
        List<Integer> log = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            int n = i;
            queue.add(40, () -> log.add(n));
        }
        queue.runDue(40, e -> { });
        assertEquals(List.of(0, 1, 2, 3, 4), log);
    }

    @Test
    void runDue_aFailingTaskDoesNotStopTheOthers() {
        DelayedQueue queue = new DelayedQueue();
        List<String> log = new ArrayList<>();
        List<RuntimeException> errors = new ArrayList<>();
        queue.add(1, () -> log.add("before"));
        queue.add(2, () -> {
            throw new IllegalStateException("boom");
        });
        queue.add(3, () -> log.add("after"));
        assertEquals(3, queue.runDue(10, errors::add));
        assertEquals(List.of("before", "after"), log);
        assertEquals(1, errors.size());
    }

    @Test
    void clear_dropsEverything() {
        DelayedQueue queue = new DelayedQueue();
        queue.add(1, () -> { });
        queue.add(2, () -> { });
        queue.clear();
        assertEquals(0, queue.size());
        assertEquals(0, queue.runDue(100, e -> { }));
    }

    @Test
    void aTaskMayScheduleAnotherOne() {
        DelayedQueue queue = new DelayedQueue();
        List<String> log = new ArrayList<>();
        queue.add(5, () -> {
            log.add("first");
            queue.add(5, () -> log.add("second"));
        });
        assertEquals(2, queue.runDue(5, e -> { }));
        assertEquals(List.of("first", "second"), log);
    }
}
