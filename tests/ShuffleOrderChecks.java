package com.mygo.music;

import java.util.*;

public final class ShuffleOrderChecks {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        for (int seed = 0; seed < 200; seed++) {
            List<Object> queue = new ArrayList<>();
            for (int i = 0; i < 12; i++) queue.add(new Object());
            ShuffleOrder<Object> order = new ShuffleOrder<>(new Random(seed));
            Object current = queue.get(4);
            order.reset(queue, current);
            Set<Object> heard = new HashSet<>();
            heard.add(current);
            for (int i = 1; i < queue.size(); i++) {
                Object next = order.next(queue);
                check(next != current && heard.add(next), "repeated an entry before finishing first cycle");
                current = next;
                order.select(queue, current); // The service resolves after choosing each entry.
            }
            for (int cycle = 0; cycle < 30; cycle++) {
                heard.clear();
                for (int i = 0; i < queue.size(); i++) {
                    Object next = order.next(queue);
                    check(next != current && heard.add(next), "cycle repeated or skipped an entry");
                    current = next;
                    order.select(queue, current);
                }
            }
            Object first = current, second = order.next(queue), third = order.next(queue);
            check(order.previous(queue) == second, "previous did not follow listening history");
            check(order.previous(queue) == first, "second previous lost listening history");
            check(order.next(queue) == second && order.next(queue) == third, "next after back lost forward history");
            Collections.reverse(queue);
            check(order.previous(queue) == second, "queue reorder changed history identity");
            queue.remove(first);
            check(order.previous(queue) != first, "deleted history entry returned");
            Object manual = queue.get(0);
            order.select(queue, manual);
            Object added = new Object();
            queue.add(added);
            Set<Object> edited = new HashSet<>();
            for (int i = 0; i < queue.size() * 2; i++) {
                Object next = order.next(queue);
                check(queue.contains(next), "removed entry escaped edited queue");
                edited.add(next);
            }
            check(edited.contains(added), "newly enqueued entry was never played");
            queue.clear();
            check(order.next(queue) == null, "empty queue retained a next entry");
            Object single = new Object();
            queue.add(single);
            order.reset(queue, single);
            check(order.previous(queue) == single && order.next(queue) == single, "single-entry queue failed");
            Object duplicateA = new Object(), duplicateB = new Object();
            queue = new ArrayList<>(Arrays.asList(duplicateA, duplicateB));
            order.reset(queue, duplicateA);
            check(order.next(queue) == duplicateB && order.next(queue) == duplicateA, "distinct same-song queue entries collapsed");
            order.reset(queue, duplicateB);
            check(order.previous(queue) == duplicateB && order.next(queue) == duplicateA, "new queue retained stale history");
        }
        System.out.println("MYGO_SHUFFLE_ORDER_OK: 200 deterministic seeds; complete cycles, boundaries, history, forward replay, edits, replacement, duplicates, empty and single queues");
    }
}
