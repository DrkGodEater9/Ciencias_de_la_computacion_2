/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package Model;

/**
 *
 * @author carlosmamut1
 */
public class Edge {
    private int id;
    private int source;
    private int destination;
    private double weight;

    public Edge(int id, int source, int destination) {
        this.id = id;
        this.source = source;
        this.destination = destination;
    }

    public Edge(int id, int source, int destination, double weight) {
        this.id = id;
        this.source = source;
        this.destination = destination;
        this.weight = weight;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getSource() {
        return source;
    }

    public void setSource(int source) {
        this.source = source;
    }

    public int getDestination() {
        return destination;
    }

    public void setDestination(int destination) {
        this.destination = destination;
    }

    public double getWeight() {
        return weight;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }
    
    public int getOther(int vertex) {
        if(vertex == source) return destination;
        if(vertex == destination) return source;
        return -1;
    }
        
}
