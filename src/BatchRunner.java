import graph.*;
import graph.Edge;
import java.nio.file.*;
import java.util.*;
import java.io.*;
import java.util.regex.*;

public class BatchRunner {

    public static void main(String[] args) throws Exception {
        Path dataDir = Path.of(args.length > 0 ? args[0] : "data");
        if (!Files.exists(dataDir)) {
            System.err.println("data/ directory not found. Place your JSON datasets in ./data/");
            return;
        }

        Path resultsDir = Path.of("results");
        Files.createDirectories(resultsDir);

        List<Path> datasetFiles = new ArrayList<>();
        try (var s = Files.list(dataDir)) {
            s.filter(p -> p.toString().endsWith(".json")).forEach(datasetFiles::add);
        }
        Collections.sort(datasetFiles);

        Path metricsCsv = resultsDir.resolve("metrics_summary.csv");
        try (BufferedWriter csv = Files.newBufferedWriter(metricsCsv)) {
            // 🔧 UPDATED HEADER
            csv.write("dataset,n_components,n_nodes,"
                    + "scc_time_ms,dfs_visits,scc_edges,"
                    + "topo_pushes,topo_pops,"
                    + "dags_relaxations,dags_time_ms,"
                    + "shortest_path_length,critical_path_length\n");

            for (Path ds : datasetFiles) {
                System.out.println("Processing: " + ds.getFileName());
                DatasetResult res = processDataset(ds);
                // write per-dataset JSON result
                Path outFile = resultsDir.resolve(ds.getFileName().toString().replace(".json", "") + "_result.json");
                Files.writeString(outFile, res.resultJson);

                // 🔧 UPDATED CSV line
                String line = String.format(Locale.ROOT,
                        "%s,%d,%d,%.3f,%d,%d,%d,%d,%d,%.3f,%.3f,%.3f\n",
                        ds.getFileName().toString(),
                        res.numComponents,
                        res.numNodes,
                        res.sccTimeMs,
                        res.sccVisits,
                        res.sccEdges,
                        res.topoPushes,
                        res.topoPops,
                        res.dagspRelaxations,
                        res.dagspTimeMs,
                        res.shortestLength,
                        res.criticalLength
                );
                csv.write(line);
                csv.flush();

                // 🔧 Console summary with path lengths
                System.out.printf(
                        " Result -> %s%n Metrics -> SCC %.3fms, Topo pushes/pops %d/%d, DAGSP relax=%d time=%.3fms, Shortest=%.3f, Critical=%.3f%n",
                        outFile.getFileName(), res.sccTimeMs, res.topoPushes, res.topoPops,
                        res.dagspRelaxations, res.dagspTimeMs, res.shortestLength, res.criticalLength);
                System.out.println("--------------------------------------------------------");
            }
        }

        System.out.println("All done. Metrics CSV at: " + metricsCsv.toAbsolutePath());
    }

    static class DatasetResult {
        String resultJson;
        int numComponents;
        int numNodes;
        double sccTimeMs;
        long sccVisits;
        long sccEdges;
        long topoPushes;
        long topoPops;
        long dagspRelaxations;
        double dagspTimeMs;
        // 🔧 NEW
        double shortestLength;
        double criticalLength;
    }

    static DatasetResult processDataset(Path dataset) throws Exception {
        String textRaw = Files.readString(dataset);
        String compact = textRaw.replaceAll("[\\n\\r\\t ]", "");

        int n = 0;
        Matcher mN = Pattern.compile("\"n\":(\\d+)").matcher(compact);
        if (mN.find()) n = Integer.parseInt(mN.group(1));
        boolean directed = compact.contains("\"directed\":true");
        int source = 0;
        Matcher mS = Pattern.compile("\"source\":(\\d+)").matcher(compact);
        if (mS.find()) source = Integer.parseInt(mS.group(1));

        List<double[]> edges = new ArrayList<>();
        Matcher mEdges = Pattern.compile("\\{[^}]*\"u\":(\\d+)[^}]*\"v\":(\\d+)(?:[^0-9\\.]{1,}\"w\":([0-9\\.]+))?[^}]*\\}").matcher(compact);
        while (mEdges.find()) {
            int u = Integer.parseInt(mEdges.group(1));
            int v = Integer.parseInt(mEdges.group(2));
            double w = 1.0;
            if (mEdges.group(3) != null) w = Double.parseDouble(mEdges.group(3));
            edges.add(new double[]{u, v, w});
            n = Math.max(n, Math.max(u, v) + 1);
        }

        SCC scc = new SCC(n);
        for (double[] e : edges) {
            scc.addEdge((int)e[0], (int)e[1]);
            if (!directed) scc.addEdge((int)e[1], (int)e[0]);
        }
        List<List<Integer>> comps = scc.run();

        int[] cid = new int[n];
        for (int i = 0; i < comps.size(); i++) for (int v : comps.get(i)) cid[v] = i;

        int numComponents = comps.size();
        List<List<Edge>> adj = new ArrayList<>();
        for (int i = 0; i < numComponents; i++) adj.add(new ArrayList<>());
        Map<String, Double> seen = new LinkedHashMap<>();
        for (double[] e : edges) {
            int a = cid[(int)e[0]], b = cid[(int)e[1]];
            if (a != b) {
                String key = a + "-" + b;
                double w = e[2];
                seen.put(key, seen.containsKey(key) ? Math.min(seen.get(key), w) : w);
            }
        }
        for (Map.Entry<String, Double> en : seen.entrySet()) {
            String[] parts = en.getKey().split("-");
            adj.get(Integer.parseInt(parts[0])).add(new Edge(Integer.parseInt(parts[1]), en.getValue()));
        }

        TopoSort topo = new TopoSort();
        List<Integer> topoOrder = topo.sort(adj);

        DAGSP dagsp = new DAGSP();
        Map<String,Object> shortest = dagsp.shortest(adj, cid[source], topoOrder);
        Map<String,Object> critical = dagsp.longest(adj, topoOrder);

        // 🔧 Extract numeric path lengths for CSV
        double shortestLen = extractLastDistance(shortest);
        double criticalLen = extractCriticalLength(critical);

        StringBuilder out = new StringBuilder();
        out.append("{\n");
        out.append("  \"SCC\": ").append(toJsonListOfLists(comps)).append(",\n");
        out.append("  \"ComponentMap\": ").append(toJsonCompMap(cid)).append(",\n");
        out.append("  \"CondensationGraph\": [");
        boolean first = true;
        for (int i = 0; i < adj.size(); i++) for (Edge e : adj.get(i)) {
            if (!first) out.append(", "); first = false;
            out.append("{\"from\":").append(i).append(",\"to\":").append(e.to).append(",\"w\":").append(e.w).append("}");
        }
        out.append("],\n");
        out.append("  \"TopologicalOrder\": ").append(toJsonList(topoOrder)).append(",\n");
        out.append("  \"ShortestPaths\": ").append(toJsonMapFormat(shortest)).append(",\n");
        out.append("  \"CriticalPath\": ").append(toJsonMapFormat(critical)).append("\n");
        out.append("}\n");

        DatasetResult dr = new DatasetResult();
        dr.resultJson = out.toString();
        dr.numComponents = numComponents;
        dr.numNodes = n;
        dr.sccTimeMs = scc.getTimeMs();
        dr.sccVisits = scc.getDfsVisits();
        dr.sccEdges = scc.getEdgesProcessed();
        dr.topoPushes = topo.getPushes();
        dr.topoPops = topo.getPops();
        dr.dagspRelaxations = dagsp.getRelaxations();
        dr.dagspTimeMs = dagsp.getTimeMs();
        dr.shortestLength = shortestLen;
        dr.criticalLength = criticalLen;
        return dr;
    }

    // 🔧 Helper to extract numeric lengths
    static double extractLastDistance(Map<String,Object> shortest) {
        Object distObj = shortest.get("distances");
        if (distObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<Object,Object> m = (Map<Object,Object>)distObj;
            if (m.isEmpty()) return 0.0;
            Object lastVal = m.values().stream().reduce((a,b)->b).orElse(0.0);
            return Double.parseDouble(lastVal.toString());
        }
        return 0.0;
    }
    static double extractCriticalLength(Map<String,Object> critical) {
        Object len = critical.get("length");
        if (len instanceof Number) return ((Number)len).doubleValue();
        return 0.0;
    }

    // (same JSON helper methods as before, omitted for brevity)
    // toJsonList, toJsonListOfLists, toJsonCompMap, toJsonMapFormat, escapeJson

    static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (ch < 0x20) sb.append(String.format("\\u%04x", (int) ch));
                    else sb.append(ch);
            }
        }
        return sb.toString();
    }


    static String toJsonMapFormat(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) sb.append(", ");
            first = false;
            sb.append("\"").append(escapeJson(e.getKey())).append("\": ");
            sb.append(toJsonValue(e.getValue()));
        }
        sb.append("}");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    static String toJsonValue(Object v) {
        if (v == null) return "null";
        if (v instanceof Number || v instanceof Boolean) return v.toString();
        if (v instanceof String) return "\"" + escapeJson((String) v) + "\"";
        if (v instanceof Map) {
            Map<Object, Object> m = (Map<Object, Object>) v;
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<Object, Object> me : m.entrySet()) {
                if (!first) sb.append(", ");
                first = false;
                sb.append("\"").append(escapeJson(String.valueOf(me.getKey()))).append("\": ");
                sb.append(toJsonValue(me.getValue()));
            }
            sb.append("}");
            return sb.toString();
        }
        if (v instanceof List) {
            List<?> list = (List<?>) v;
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(toJsonValue(list.get(i)));
            }
            sb.append("]");
            return sb.toString();
        }
        return "\"" + escapeJson(v.toString()) + "\"";
    }

    static String toJsonList(List<?> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            Object v = list.get(i);
            if (i > 0) sb.append(", ");
            if (v instanceof Number || v instanceof Boolean)
                sb.append(v);
            else if (v instanceof Map)
                sb.append(toJsonMap((Map<?, ?>) v));
            else if (v instanceof List)
                sb.append(toJsonList((List<?>) v));
            else
                sb.append("\"").append(v).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    static String toJsonMap(Map<?, ?> map) {
        StringBuilder sb = new StringBuilder("{");
        int i = 0;
        for (var e : map.entrySet()) {
            if (i++ > 0) sb.append(", ");
            sb.append("\"").append(e.getKey()).append("\": ");
            Object v = e.getValue();
            if (v instanceof Number || v instanceof Boolean)
                sb.append(v);
            else if (v instanceof Map)
                sb.append(toJsonMap((Map<?, ?>) v));
            else if (v instanceof List)
                sb.append(toJsonList((List<?>) v));
            else
                sb.append("\"").append(v).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }


    static String toJsonListOfLists(List<List<Integer>> lists) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < lists.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(toJsonList(lists.get(i)));
        }
        sb.append("]");
        return sb.toString();
    }

    static String toJsonCompMap(int[] cid) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < cid.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append("\"").append(i).append("\": ").append(cid[i]);
        }
        sb.append("}");
        return sb.toString();
    }


}
