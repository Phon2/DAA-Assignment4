Name: Alikhan Nurzhan
Group: SE-2429

The SCC detection uses Tarjan’s algorithm (single DFS-based low-link method), chosen for its in-place performance and minimal memory overhead compared to Kosaraju’s two-pass approach.

1. Dataset Documentation
Dataset	Vertices (n)	Edges	Type	Notes
small_1.json	6	5	DAG	Simple linear dependency chain (no cycles)
small_2.json	6	6	Cyclic	Single small SCC (one cycle)
small_3.json	6	7	Mixed	Two SCCs and DAG structure between them
medium_1.json	10	10	Mixed	Few SCCs; partially cyclic
medium_2.json	12	11	Mixed	2–3 SCCs; sparse connections
medium_3.json	15	15	DAG	Fully acyclic, medium density
large_1.json	20	20	Mixed	18 components, light cycles
large_2.json	25	26	Mixed	19 components, moderate density
large_3.json	30	33	DAG	Fully acyclic; large DAG for performance test

2. Experimental Results Overview
Dataset	#Components	#Nodes SCCTime (ms)	Topo Pushes/Pops	DAG-SP Relaxations	DAG-SP Time (ms)	Critical Path
large_1.json	18	   20	   0.088	      18  /  18	              17	                      0.018	  38.0
large_2.json	19	   25	   0.036	      19  /  19	              17	                      0.012	  32.0
large_3.json	30	   30	   0.034	      30  /  30	              33	                      0.045	  28.0
medium_1.json	8	     10	   0.045	      8   /  8	              7	                        0.007   12.0
medium_2.json	8	     12	   0.015	      8   /  8	              5	                      0.005	  8.0

3. Analysis
3.1 SCC Analysis
Bottlenecks:
SCC time grows very slowly with the number of nodes (≈0.03–0.09 ms across all datasets).
Tarjan’s algorithm scales nearly linearly with edges and vertices (O(V + E)), so performance remains excellent even for 30 nodes.
Effect of Structure:
Datasets with many small SCCs (like large_1.json) have slightly higher overhead due to repeated DFS calls.
Dense cyclic graphs cause more recursive DFS exploration, slightly increasing dfs_visits and edges_processed.
3.2 Topological Sorting
Method Used: Kahn’s algorithm (BFS-based).
Observation:
The number of pushes/pops equals the number of components — confirming correct DAG condensation.
Topological sorting time was negligible (<0.05 ms for 30 nodes).
Effect of Structure:
Cyclic input graphs are first reduced via SCC condensation.
After compression, the condensation graph (DAG) ensures topological order exists for scheduling.
3.3 Shortest & Longest Paths (DAG-SP)
Shortest Paths:
Many nodes are disconnected from the given source (hence 0.0 values).
For connected nodes, the relaxation counts (5–33) show dynamic programming efficiency (O(V+E) over topo order).
Critical Path (Longest Path):
Critical path length increases with node count and edge density:
Medium graphs: 8–12
Large graphs: 28–38
Represents longest dependency chain — crucial for Smart City “critical service time”.

4. Conclusion
This project effectively integrates theoretical graph algorithms with practical Smart City applications:
SCC detection identifies interdependent urban maintenance clusters.
Topological sorting sequences city operations without conflicts.
DAG shortest/longest path planning provides optimized schedules and identifies critical bottlenecks.
Even for large, dense graphs, performance remained excellent, demonstrating the real-world feasibility of these methods for scalable smart infrastructure scheduling.
