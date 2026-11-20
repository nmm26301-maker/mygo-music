package com.mygo.music;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Queue entry identity preserves duplicates, cached qualities and edits during shuffle. */
final class ShuffleOrder<T> {
    private final Random random;
    private final ArrayList<T> remaining = new ArrayList<>();
    private final ArrayList<T> back = new ArrayList<>();
    private final ArrayList<T> forward = new ArrayList<>();
    private final Set<T> known = new HashSet<>();
    private T current;

    ShuffleOrder() { this(new Random()); }
    ShuffleOrder(Random random) { this.random = random; }

    void reset(List<T> queue, T selected) {
        remaining.clear();
        back.clear();
        forward.clear();
        known.clear();
        current = selected;
        sync(queue);
        remaining.remove(selected);
    }

    void select(List<T> queue, T selected) {
        sync(queue);
        if (selected != current) {
            if (queue.contains(current)) remember(current);
            forward.clear();
            current = selected;
        }
        remaining.remove(selected);
    }

    T next(List<T> queue) {
        sync(queue);
        if (queue.isEmpty()) return null;
        T selected;
        if (!forward.isEmpty()) {
            selected = forward.remove(forward.size() - 1);
        } else {
            if (remaining.isEmpty()) {
                remaining.addAll(queue);
            }
            int excluded = remaining.indexOf(current);
            int choices = remaining.size() - (excluded >= 0 && remaining.size() > 1 ? 1 : 0);
            int choice = random.nextInt(choices);
            if (excluded >= 0 && remaining.size() > 1 && choice >= excluded) choice++;
            selected = remaining.remove(choice);
        }
        if (queue.contains(current)) remember(current);
        current = selected;
        remaining.remove(selected);
        return selected;
    }

    T previous(List<T> queue) {
        sync(queue);
        if (back.isEmpty()) {
            if (!queue.contains(current)) current = queue.isEmpty() ? null : queue.get(0);
            remaining.remove(current);
            return current;
        }
        if (queue.contains(current)) forward.add(current);
        current = back.remove(back.size() - 1);
        remaining.remove(current);
        return current;
    }

    private void remember(T value) {
        back.add(value);
        if (back.size() > 500) back.remove(0);
    }

    private void sync(List<T> queue) {
        Set<T> live = new HashSet<>(queue);
        remaining.retainAll(live);
        back.retainAll(live);
        forward.retainAll(live);
        known.retainAll(live);
        for (T item : queue) if (known.add(item) && item != current) remaining.add(item);
    }
}
