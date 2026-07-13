/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package Model;

import java.util.ArrayList;

/**
 *
 * @author carlosmamut1
 */
public interface Graph {
    int vertexCount();
    int edgeCount();
    boolean isDirected();
    Edge addEdge(int source, int destination);
    Edge addEdge(int source, int destination, double weight);
    ArrayList<Edge> edges();
    ArrayList<Edge> edgesOf(int vertex);
    ArrayList<Integer> neighbors (int vertex);
}
