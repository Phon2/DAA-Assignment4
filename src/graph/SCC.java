package graph;

import java.util.*;

/**
 * Tarjan SCC with metrics (DFS visits + edges processed + time)
 */
public class SCC implements Metrics {
    private final List<Integer>[] g;
    private final int n;
    private int idxCounter = 0;
    private final int[] idx, low;
    private final boolean[] on;
    private final Deque<Integer> stack = new ArrayDeque<>();
    private final List<List<Integer>> comps = new ArrayList<>();

    // metrics
    private long startNs = 0, endNs = 0;
    private long dfsVisits = 0, edgesProcessed = 0;

    @SuppressWarnings("unchecked")
    public SCC(int n) {
        this.n = n;
        g = new List[n];
        for (int i = 0; i < n; i++) g[i] = new ArrayList<>();
        idx = new int[n]; Arrays.fill(idx, -1);
        low = new int[n];
        on = new boolean[n];
    }

    public void addEdge(int u, int v) { g[u].add(v); }

    private void dfs(int v) {
        dfsVisits++;
        idx[v] = low[v] = idxCounter++;
        stack.push(v); on[v] = true;
        for (int w : g[v]) {
            edgesProcessed++;
            if (idx[w] == -1) {
                dfs(w);
                low[v] = Math.min(low[v], low[w]);
            } else if (on[w]) {
                low[v] = Math.min(low[v], idx[w]);
            }
        }
        if (low[v] == idx[v]) {
            List<Integer> comp = new ArrayList<>();
            int w;
            do {
                w = stack.pop();
                on[w] = false;
                comp.add(w);
            } while (w != v);
            comps.add(comp);
        }
    }

    public List<List<Integer>> run() {
        reset();
        start();
        for (int i = 0; i < n; i++) if (idx[i] == -1) dfs(i);
        stop();
        return comps;
    }

    // Metrics
    @Override public void start() { startNs = System.nanoTime(); }
    @Override public void stop() { endNs = System.nanoTime(); }
    @Override public void reset() { dfsVisits = 0; edgesProcessed = 0; startNs = endNs = 0; }
    @Override public long getTimeNanos() { return endNs - startNs; }
    @Override public double getTimeMillis() { return (endNs - startNs) / 1_000_000.0; }

    @Override
    public String report() {
        return String.format("SCC(visits=%d edges=%d time=%.3fms)", dfsVisits, edgesProcessed, getTimeMillis());
    }

    // getters for batch runner
    public long getDfsVisits() { return dfsVisits; }
    public long getEdgesProcessed() { return edgesProcessed; }
    public double getTimeMs() { return getTimeMillis(); }
}
