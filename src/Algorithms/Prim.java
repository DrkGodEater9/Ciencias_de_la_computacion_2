package Algorithms;

import Model.AdjacencyMatrix;

public class Prim {

    public void primAlgorithm(AdjacencyMatrix graph) {
        int size = graph.vertexCount();

        boolean[] inMst = new boolean[size];
        double[] keyValues = new double[size];
        int[] parents = new int[size];

        for (int i = 0; i < size; i++) {
            keyValues[i] = Double.POSITIVE_INFINITY;
            parents[i] = -1;
        }
        keyValues[0] = 0;

        System.out.println("Edge \tWeight");
        for (int count = 0; count < size; count++) {
            int u = -1;
            double min = Double.POSITIVE_INFINITY;
            for (int v = 0; v < size; v++) {
                if (!inMst[v] && keyValues[v] < min) {
                    min = keyValues[v];
                    u = v;
                }
            }

            inMst[u] = true;

            if (parents[u] != -1) {
                System.out.println(parents[u] + "-" + u + " \t" + graph.getWeight(u, parents[u]));
            }

            for (int v = 0; v < size; v++) {
                double weight = graph.getWeight(u, v);
                if (weight > 0 && weight < keyValues[v] && !inMst[v]) {
                    keyValues[v] = weight;
                    parents[v] = u;
                }
            }
        }
    }
}
