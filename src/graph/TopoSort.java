package graph;

import java.util.*;

/**
 * Kahn topological sort with push/pop counters and timing.
 */
public class TopoSort implements Metrics {

    private long startNs = 0, endNs = 0;
    private long pushes = 0, pops = 0;

    public TopoSort() { reset(); }

    public List<Integer> sort(List<List<Edge>> adj) {
        reset();
        start();
        int n = adj.size();
        int[] indeg = new int[n];
        for (int u = 0; u < n; u++) for (Edge e : adj.get(u)) indeg[e.to]++;
        Deque<Integer> q = new ArrayDeque<>();
        for (int i = 0; i < n; i++) if (indeg[i] == 0) { q.add(i); pushes++; }
        List<Integer> order = new ArrayList<>();
        while (!q.isEmpty()) {
            int u = q.poll(); pops++;
            order.add(u);
            for (Edge e : adj.get(u)) {
                if (--indeg[e.to] == 0) { q.add(e.to); pushes++; }
            }
        }
        stop();
        return order;
    }

    // Metrics
    @Override public void start() { startNs = System.nanoTime(); }
    @Override public void stop() { endNs = System.nanoTime(); }
    @Override public void reset() { pushes = pops = 0; startNs = endNs = 0; }
    @Override public long getTimeNanos() { return endNs - startNs; }
    @Override public double getTimeMillis() { return (endNs - startNs) / 1_000_000.0; }

    @Override
    public String report() {
        return String.format("Topo(pushes=%d pops=%d time=%.3fms)", pushes, pops, getTimeMillis());
    }

    // getters
    public long getPushes() { return pushes; }
    public long getPops() { return pops; }
    public double getTimeMs() { return getTimeMillis(); }
}
