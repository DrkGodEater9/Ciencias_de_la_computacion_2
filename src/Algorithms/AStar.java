package Algorithms;

import Model.Edge;
import Model.Graph;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AStar {
    private Graph graph;

    public AStar(Graph graph) {
        if (graph == null)
            throw new IllegalArgumentException("El grafo no puede ser null");
        this.graph = graph;
    }

    private void validate(int v) {
        if (v < 0 || v >= graph.vertexCount())
            throw new IllegalArgumentException("Vértice fuera de rango: " + v);
    }

    public List<Integer> search(int start, int goal, double[] heuristic) {
        int size = graph.vertexCount();
        validate(start);
        validate(goal);

        if (heuristic == null)
            heuristic = new double[size];

        double[] gScore = new double[size];
        double[] fScore = new double[size];
        int[] cameFrom = new int[size];
        boolean[] closedSet = new boolean[size];

        Arrays.fill(gScore, Double.POSITIVE_INFINITY);
        Arrays.fill(fScore, Double.POSITIVE_INFINITY);
        Arrays.fill(cameFrom, -1);

        gScore[start] = 0;
        fScore[start] = heuristic[start];

        Map<Integer, Double> openSet = new HashMap<Integer, Double>();
        openSet.put(start, fScore[start]);

        while (!openSet.isEmpty()) {
            Map.Entry<Integer, Double> best = null;
            for (Map.Entry<Integer, Double> entry : openSet.entrySet()) {
                if (best == null || entry.getValue() < best.getValue()) {
                    best = entry;
                }
            }

            int current = best.getKey();

            if (current == goal)
                return reconstructPath(cameFrom, current);

            openSet.remove(current);
            closedSet[current] = true;

            for (Edge e : graph.edgesOf(current)) {
                int neighbor = e.getOther(current);
                if (neighbor == -1 || closedSet[neighbor])
                    continue;

                double tentativeG = gScore[current] + e.getWeight();
                if (tentativeG < gScore[neighbor]) {
                    cameFrom[neighbor] = current;
                    gScore[neighbor] = tentativeG;
                    fScore[neighbor] = tentativeG + heuristic[neighbor];
                    openSet.put(neighbor, fScore[neighbor]);
                }
            }
        }

        return null;
    }

    private List<Integer> reconstructPath(int[] cameFrom, int current) {
        List<Integer> path = new ArrayList<Integer>();
        path.add(current);
        while (cameFrom[current] != -1) {
            current = cameFrom[current];
            path.add(current);
        }
        Collections.reverse(path);
        return path;
    }
}
