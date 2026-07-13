package Algorithms;

import Model.Edge;
import Model.Graph;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class Kruskal {

    public int find(int[] parent, int i) {
        if (parent[i] == i)
            return i;
        return find(parent, parent[i]);
    }

    public void union(int[] parent, int[] rank, int x, int y) {
        int xroot = find(parent, x);
        int yroot = find(parent, y);
        if (rank[xroot] < rank[yroot]) {
            parent[xroot] = yroot;
        } else if (rank[xroot] > rank[yroot]) {
            parent[yroot] = xroot;
        } else {
            parent[yroot] = xroot;
            rank[xroot]++;
        }
    }

    public List<Edge> kruskalAlgorithm(Graph graph) {
        List<Edge> result = new ArrayList<Edge>();
        int i = 0;

        List<Edge> edges = new ArrayList<Edge>(graph.edges());
        Collections.sort(edges, new Comparator<Edge>() {
            @Override
            public int compare(Edge a, Edge b) {
                return Double.compare(a.getWeight(), b.getWeight());
            }
        });

        int size = graph.vertexCount();
        int[] parent = new int[size];
        int[] rank = new int[size];

        for (int node = 0; node < size; node++) {
            parent[node] = node;
            rank[node] = 0;
        }

        while (i < edges.size()) {
            Edge e = edges.get(i);
            int u = e.getSource();
            int v = e.getDestination();
            i++;

            int x = find(parent, u);
            int y = find(parent, v);
            if (x != y) {
                result.add(e);
                union(parent, rank, x, y);
            }
        }

        System.out.println("Edge \tWeight");
        for (Edge e : result) {
            System.out.println(e.getSource() + "-" + e.getDestination() + " \t" + e.getWeight());
        }

        return result;
    }
}
