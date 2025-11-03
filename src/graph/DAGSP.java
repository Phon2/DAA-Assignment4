package graph;

import java.util.*;
import graph.Edge;

/**
 * Shortest and Longest (critical) paths on DAG.
 * Expects a topological order to be provided (to avoid re-running TopoSort which counts separately).
 * Counts relaxations and measures time.
 */
public class DAGSP implements Metrics {

    private long startNs = 0, endNs = 0;
    private long relaxations = 0;

    public DAGSP() { reset(); }

    // shortest: takes topo order (List<Integer>) to avoid re-counting topological pushes/pops
    public Map<String,Object> shortest(List<List<Edge>> adj, int src, List<Integer> topoOrder) {
        reset();
        start();
        int n = adj.size();
        double[] d = new double[n]; Arrays.fill(d, Double.POSITIVE_INFINITY);
        Integer[] prev = new Integer[n];
        d[src] = 0;
        for (int u : topoOrder) {
            if (Double.isInfinite(d[u])) continue;
            for (Edge e : adj.get(u)) {
                if (d[e.to] > d[u] + e.w) {
                    d[e.to] = d[u] + e.w;
                    prev[e.to] = u;
                    relaxations++;
                }
            }
        }
        stop();

        Map<Integer, Double> reachable = new LinkedHashMap<>();
        for (int i = 0; i < n; i++)
            if (!Double.isInfinite(d[i])) reachable.put(i, d[i]);

        List<Integer> path = new ArrayList<>();
        if (!reachable.isEmpty()) {
            int t = -1;
            // choose last reachable by topo order (preserve ordering) — find last element in topoOrder that is reachable
            for (int k = topoOrder.size()-1; k >= 0; k--) {
                int node = topoOrder.get(k);
                if (reachable.containsKey(node)) { t = node; break; }
            }
            if (t != -1) {
                for (Integer cur = t; cur != null; cur = prev[cur]) path.add(cur);
                Collections.reverse(path);
            }
        }

        Map<String,Object> r = new LinkedHashMap<>();
        r.put("distances", reachable);
        r.put("path", path);
        return r;
    }

    // longest (critical) path: uses topoOrder as well
    public Map<String,Object> longest(List<List<Edge>> adj, List<Integer> topoOrder) {
        reset();
        start();
        int n = adj.size();
        double[] d = new double[n]; Arrays.fill(d, Double.NEGATIVE_INFINITY);
        Integer[] prev = new Integer[n];
        int[] indeg = new int[n];
        for (int u = 0; u < n; u++) for (Edge e : adj.get(u)) indeg[e.to]++;
        for (int i = 0; i < n; i++) if (indeg[i] == 0) d[i] = 0;

        for (int u : topoOrder) {
            if (d[u] == Double.NEGATIVE_INFINITY) continue;
            for (Edge e : adj.get(u)) {
                if (d[e.to] < d[u] + e.w) {
                    d[e.to] = d[u] + e.w;
                    prev[e.to] = u;
                    relaxations++;
                }
            }
        }
        stop();

        Map<Integer, Double> reachable = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) if (d[i] != Double.NEGATIVE_INFINITY) reachable.put(i, d[i]);

        double max = Double.NEGATIVE_INFINITY;
        int end = -1;
        for (int i = 0; i < n; i++) if (d[i] > max) { max = d[i]; end = i; }

        List<Integer> path = new ArrayList<>();
        if (end != -1 && max != Double.NEGATIVE_INFINITY) {
            for (Integer cur = end; cur != null; cur = prev[cur]) path.add(cur);
            Collections.reverse(path);
        }

        Map<String,Object> r = new LinkedHashMap<>();
        r.put("distances", reachable);
        r.put("length", max == Double.NEGATIVE_INFINITY ? null : max);
        r.put("path", path);
        return r;
    }

    // Metrics interface
    @Override public void start() { startNs = System.nanoTime(); }
    @Override public void stop() { endNs = System.nanoTime(); }
    @Override public void reset() { relaxations = 0; startNs = endNs = 0; }
    @Override public long getTimeNanos() { return endNs - startNs; }
    @Override public double getTimeMillis() { return (endNs - startNs) / 1_000_000.0; }

    @Override
    public String report() {
        return String.format("DAGSP(relax=%d time=%.3fms)", relaxations, getTimeMillis());
    }

    // getters
    public long getRelaxations() { return relaxations; }
    public double getTimeMs() { return getTimeMillis(); }
}
