package gg.paynow.paynowlib;

import java.util.Iterator;
import java.util.LinkedHashSet;

public class CommandHistory {

    private final LinkedHashSet<String> history;
    private final int maxSize;

    public CommandHistory(int maxSize) {
        this.history = new LinkedHashSet<>();
        this.maxSize = maxSize;
    }

    public void add(String attemptId) {
        if (history.size() >= maxSize) {
            Iterator<String> iterator = history.iterator();
            if (iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }
        history.add(attemptId);
    }

    public boolean contains(String attemptId) {
        return history.contains(attemptId);
    }

    public void clear() {
        history.clear();
    }

}
