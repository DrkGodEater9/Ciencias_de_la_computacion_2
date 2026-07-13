package Model;

import Model.Edge;
import java.util.ArrayList;
import java.util.StringJoiner;

public class AdjacencyList implements Graph {


    public static class AdjEntry {
        private final int vertex;
        private final Edge edge;

        AdjEntry(int vertex, Edge edge) {
            this.vertex = vertex;
            this.edge   = edge;
        }

        @Override
        public String toString() {
            return String.format("%d(w=%.1f)", vertex, edge.getWeight());
        }
    }


    private final int     vertexCount;
    private final boolean directed;
    private final ArrayList<ArrayList<AdjEntry>> adj;
    private final ArrayList<Edge> edgeList;
    private int nextEdgeId = 0;


    public AdjacencyList(int vertexCount, boolean directed) {
        if (vertexCount < 1)
            throw new IllegalArgumentException("El grafo debe tener al menos 1 vértice.");
        this.vertexCount = vertexCount;
        this.directed    = directed;
        this.edgeList    = new ArrayList<Edge>();
        this.adj         = new ArrayList<ArrayList<AdjEntry>>(vertexCount);
        for (int i = 0; i < vertexCount; i++) adj.add(new ArrayList<AdjEntry>());
    }


    public AdjacencyList(int vertexCount) {
        this(vertexCount, false);
    }


    @Override public int  vertexCount() { return vertexCount; }
    @Override public int  edgeCount()   { return edgeList.size(); }
    @Override public boolean isDirected(){ return directed; }

    @Override
    public Edge addEdge(int source, int destination) {
        return addEdge(source, destination, 1.0);
    }

    @Override
    public Edge addEdge(int source, int destination, double weight) {
        validate(source);
        validate(destination);
        Edge e = new Edge(nextEdgeId++, source, destination, weight);
        edgeList.add(e);
        adj.get(source).add(new AdjEntry(destination, e));
        if (!directed && source != destination)
            adj.get(destination).add(new AdjEntry(source, e));
        return e;
    }

    @Override
    public ArrayList<Edge> edges() {
        return edgeList;
    }

    @Override
    public ArrayList<Edge> edgesOf(int vertex) {
        validate(vertex);
        ArrayList<Edge> result = new ArrayList<Edge>();
        for (AdjEntry ae : adj.get(vertex)) result.add(ae.edge);
        return result;
    }

    @Override
    public ArrayList<Integer> neighbors(int vertex) {
        validate(vertex);
        ArrayList<Integer> result = new ArrayList<Integer>();
        for (AdjEntry ae : adj.get(vertex)) result.add(ae.vertex);
        return result;
    }


    public ArrayList<AdjEntry> getAdjacencyOf(int vertex) {
        validate(vertex);
        return adj.get(vertex);
    }


    public boolean hasEdge(int source, int destination) {
        validate(source); validate(destination);
        for (AdjEntry ae : adj.get(source))
            if (ae.vertex == destination) return true;
        return false;
    }


    public int degree(int vertex) {
        validate(vertex);
        return adj.get(vertex).size();
    }
    private void validate(int v) {
        if (v < 0 || v >= vertexCount)
            throw new IllegalArgumentException("Vértice fuera de rango: " + v);
    }
}
