/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package Model;

/**
 *
 * @author Familia Guerrero
 */
public class Node implements Comparable<Node> {
    private int vertex;
    private double distance;

    @Override
    public int compareTo(Node o) {
     return Double.compare(this.distance, o.distance); 
    }

    public int getVertex() {
        return vertex;
    }

    public void setVertex(int vertex) {
        this.vertex = vertex;
    }

    public double getDistance() {
        return distance;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }

    public Node(int vertex, double distance) {
        this.vertex = vertex;
        this.distance = distance;
    }
    
    
}
