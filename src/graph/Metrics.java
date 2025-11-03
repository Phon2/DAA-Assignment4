package graph;

public interface Metrics {
    void start();
    void stop();
    void reset();
    long getTimeNanos();
    double getTimeMillis();
    String report();
}
