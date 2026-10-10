package com.github.tartaricacid.tlm_sincerely.chatbar;

import java.util.PriorityQueue;

/**
 * 按时间到点才执行的任务队列（纯逻辑，时间由调用方传入，不读时钟，便于单元测试）。
 * 同一时刻到点的任务按加入的先后顺序执行。只在服务端主线程使用。
 */
public final class DelayedQueue {
    private record Entry(long dueTick, long sequence, Runnable task) implements Comparable<Entry> {
        @Override
        public int compareTo(Entry other) {
            int byTime = Long.compare(dueTick, other.dueTick);
            return byTime != 0 ? byTime : Long.compare(sequence, other.sequence);
        }
    }

    private final PriorityQueue<Entry> queue = new PriorityQueue<>();
    private long nextSequence;

    /** 加入一个任务，{@code dueTick} 到点（含）之后的第一次 {@link #runDue} 执行它。 */
    public void add(long dueTick, Runnable task) {
        queue.add(new Entry(dueTick, nextSequence++, task));
    }

    /**
     * 执行所有已经到点的任务。某个任务抛出异常不影响其它任务（异常交给 {@code onError}）。
     *
     * @return 执行了几个
     */
    public int runDue(long nowTick, java.util.function.Consumer<RuntimeException> onError) {
        int ran = 0;
        while (!queue.isEmpty() && queue.peek().dueTick() <= nowTick) {
            Entry entry = queue.poll();
            ran++;
            try {
                entry.task().run();
            } catch (RuntimeException e) {
                onError.accept(e);
            }
        }
        return ran;
    }

    public int size() {
        return queue.size();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public void clear() {
        queue.clear();
    }
}
