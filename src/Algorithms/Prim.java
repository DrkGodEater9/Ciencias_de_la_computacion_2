package Algorithms;

import Model.Edge;
import Model.Graph;
import Model.Node;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;

public class Prim {

    /**
     * Calcula el Árbol de Recubrimiento Mínimo (o bosque, si el grafo
     * está desconectado) sobre cualquier implementación de Graph.
     * Reemplaza la versión anterior que dependía de AdjacencyMatrix.
     */
    public List<Edge> primAlgorithm(Graph graph) {
        int size = graph.vertexCount();
        List<Edge> result = new ArrayList<Edge>();
        if (size == 0) return result;

        boolean[] inMst = new boolean[size];
        double[]  key   = new double[size];
        Edge[]    edgeToMst = new Edge[size]; // arista con la que v entró al árbol

        Arrays.fill(key, Double.POSITIVE_INFINITY);

        PriorityQueue<Node> pq = new PriorityQueue<Node>();

        // Recorremos todos los vértices como posibles semillas para cubrir
        // también grafos desconectados (islas de calles sin conexión).
        for (int start = 0; start < size; start++) {
            if (inMst[start]) continue;

            key[start] = 0.0;
            pq.add(new Node(start, 0.0));

            while (!pq.isEmpty()) {
                Node current = pq.poll();
                int u = current.getVertex();

                if (inMst[u]) continue;   // ya lo procesamos (entrada obsoleta)
                inMst[u] = true;

                if (edgeToMst[u] != null) result.add(edgeToMst[u]);

                for (Edge e : graph.edgesOf(u)) {
                    int v = e.getOther(u);
                    if (v == -1 || inMst[v]) continue;

                    double w = e.getWeight();
                    if (w < key[v]) {
                        key[v] = w;
                        edgeToMst[v] = e;
                        pq.add(new Node(v, w));
                    }
                }
            }
        }

        System.out.println("Edge \tWeight");
        for (Edge e : result) {
            System.out.println(e.getSource() + "-" + e.getDestination() + " \t" + e.getWeight());
        }
        return result;
    }
}