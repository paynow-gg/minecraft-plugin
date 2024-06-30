package gg.paynow.paynowlib;

import java.util.PriorityQueue;
import java.util.Queue;

public class CommandHistory {

    private final Queue<String> queue;
    private final int maxSize;

    public CommandHistory(int maxSize) {
        this.queue = new PriorityQueue<>();
        this.maxSize = maxSize;
    }

    public void add(String attemptId) {
        if (queue.size() == maxSize) {
            queue.poll();
        }
        queue.add(attemptId);
    }

    public boolean contains(String attemptId) {
        return queue.contains(attemptId);
    }

    public void clear() {
        queue.clear();
    }

}
