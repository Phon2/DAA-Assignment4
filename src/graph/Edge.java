package graph;

public class Edge {
    public final int to;
    public final double w;
    public Edge(int to, double w) { this.to = to; this.w = w; }
    @Override public String toString() { return "{\"to\": " + to + ", \"w\": " + w + "}"; }
}
