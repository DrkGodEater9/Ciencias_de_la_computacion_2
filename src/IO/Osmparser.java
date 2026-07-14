package IO;

import Model.AdjacencyList;
import Model.Edge;
import Model.Graph;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Lee un archivo GeoJSON exportado desde Overpass Turbo (calles con
 * geometría LineString) y construye un Graph (AdjacencyList) donde:
 *
 *   - cada coordenada distinta (lon,lat) se convierte en un vértice entero 0..n-1
 *   - cada par consecutivo de coordenadas dentro de una calle se convierte
 *     en una arista, con peso = distancia real en metros (fórmula de Haversine)
 *
 * Las intersecciones aparecen solas: cuando dos calles comparten exactamente
 * la misma coordenada, ambas apuntan al mismo vértice.
 *
 * Como el Graph solo maneja vértices enteros, esta clase guarda además la
 * traducción índice -> (lat, lon), necesaria para el heurístico de A*.
 *
 * @author Grupo 4
 */
public class OSMParser {

    private Graph graph;
    private double[] lat;   // lat[i] = latitud del vértice i
    private double[] lon;   // lon[i] = longitud del vértice i
    private int vertexCount;

    /** Traducción "lon,lat" (redondeado) -> índice entero del vértice. */
    private final Map<String, Integer> coordToIndex = new HashMap<String, Integer>();
    /** Guardamos las coordenadas en orden de aparición para luadar lat/lon. */
    private final ArrayList<double[]> coordList = new ArrayList<double[]>(); // {lat, lon}

    /**
     * Carga y parsea el archivo. Después de llamar a este constructor el
     * grafo ya está construido y disponible con getGraph().
     *
     * @param geojsonPath ruta al archivo, ej. "src/Data/yopal_urbano.geojson"
     */
    /** Contadores globales, útiles para reportar cuántas calles son de cada tipo. */
    private int onewayStreetCount = 0;
    private int twoWayStreetCount = 0;

    /** Un tramo de calle ya resuelto a índices de vértice, con su sentido real. */
    private static class RoadSegment {
        final int from, to;   // dirección REAL de circulación (from -> to)
        final boolean oneway; // true = solo se puede ir de from a to
        RoadSegment(int from, int to, boolean oneway) {
            this.from = from; this.to = to; this.oneway = oneway;
        }
    }

    public OSMParser(String geojsonPath) throws IOException {
        String content = new String(Files.readAllBytes(Paths.get(geojsonPath)));
        JSONObject root = new JSONObject(content);
        JSONArray features = root.getJSONArray("features");

        // Primera pasada: recolectar vértices y tramos de calle (con su sentido).
        ArrayList<RoadSegment> segments = new ArrayList<RoadSegment>();

        for (int f = 0; f < features.length(); f++) {
            JSONObject feature = features.getJSONObject(f);
            JSONObject geometry = feature.optJSONObject("geometry");
            if (geometry == null) continue;

            String type = geometry.optString("type", "");
            if (!type.equals("LineString") && !type.equals("MultiLineString")) {
                continue; // ignoramos Points/Polygons: solo nos interesan las calles
            }

            // Leemos el tag "oneway" de OSM: "yes"/"1" = sentido único en el
            // orden de las coordenadas; "-1" = sentido único pero invertido;
            // cualquier otro valor (o ausente) = doble sentido.
            JSONObject props = feature.optJSONObject("properties");
            String onewayTag = (props != null) ? props.optString("oneway", "no") : "no";
            boolean oneway  = onewayTag.equals("yes") || onewayTag.equals("1") || onewayTag.equals("true");
            boolean reversed = onewayTag.equals("-1");

            if (type.equals("LineString")) {
                JSONArray coords = geometry.getJSONArray("coordinates");
                addLine(coords, segments, oneway, reversed);
            } else {
                JSONArray lines = geometry.getJSONArray("coordinates");
                for (int i = 0; i < lines.length(); i++) {
                    addLine(lines.getJSONArray(i), segments, oneway, reversed);
                }
            }
        }

        // Ya conocemos cuántos vértices hay: construimos el grafo.
        // Se construye DIRIGIDO para poder respetar las calles de sentido único;
        // las de doble sentido simplemente agregan la arista en los dos sentidos.
        vertexCount = coordList.size();
        lat = new double[vertexCount];
        lon = new double[vertexCount];
        for (int i = 0; i < vertexCount; i++) {
            lat[i] = coordList.get(i)[0];
            lon[i] = coordList.get(i)[1];
        }

        graph = new AdjacencyList(vertexCount, true); // dirigido: respeta el sentido real

        // Segunda pasada: agregar las aristas con su peso Haversine.
        for (RoadSegment seg : segments) {
            int u = seg.from, v = seg.to;
            if (u == v) continue; // evita bucles por coordenadas repetidas
            double w = haversine(lat[u], lon[u], lat[v], lon[v]);

            if (seg.oneway) {
                onewayStreetCount++;
                Edge e = graph.addEdge(u, v, w);
                e.setOneway(true);
            } else {
                twoWayStreetCount++;
                graph.addEdge(u, v, w).setOneway(false); // sentido de las coordenadas
                graph.addEdge(v, u, w).setOneway(false); // sentido contrario
            }
        }
    }

    /**
     * Descompone una calle (lista de coordenadas) en tramos consecutivos,
     * respetando si es de sentido único (oneway) y en qué dirección.
     */
    private void addLine(JSONArray coords, ArrayList<RoadSegment> segments,
                          boolean oneway, boolean reversed) {
        int prev = -1;
        for (int i = 0; i < coords.length(); i++) {
            JSONArray point = coords.getJSONArray(i);
            double plon = point.getDouble(0); // GeoJSON = [lon, lat]
            double plat = point.getDouble(1);
            int idx = indexFor(plat, plon);
            if (prev != -1) {
                if (reversed) {
                    segments.add(new RoadSegment(idx, prev, true)); // circula al revés de las coords
                } else {
                    segments.add(new RoadSegment(prev, idx, oneway));
                }
            }
            prev = idx;
        }
    }

    /** Cuántos tramos de calle son de sentido único ("unidireccional"). */
    public int getOnewayStreetCount() { return onewayStreetCount; }

    /** Cuántos tramos de calle son de doble sentido ("bidireccional"). */
    public int getTwoWayStreetCount() { return twoWayStreetCount; }

    /** Devuelve el índice del vértice para una coordenada, creándolo si es nuevo. */
    private int indexFor(double plat, double plon) {
        // Redondeamos a 7 decimales (~1 cm) para que nodos compartidos coincidan.
        String key = String.format("%.7f,%.7f", plon, plat);
        Integer existing = coordToIndex.get(key);
        if (existing != null) return existing;

        int newIndex = coordList.size();
        coordToIndex.put(key, newIndex);
        coordList.add(new double[]{plat, plon});
        return newIndex;
    }

    // ---------------------------------------------------------------
    //  Utilidades públicas
    // ---------------------------------------------------------------

    /**
     * Construye una versión NO DIRIGIDA del mismo grafo (mismos vértices,
     * mismos tramos de calle, mismos pesos), ignorando el sentido único.
     *
     * Úsala para Prim/Kruskal (MST): el árbol de recubrimiento mínimo es un
     * concepto de grafos no dirigidos, así que para decidir DÓNDE ubicar
     * hospital/bomberos/ambulancias no importa el sentido de las calles,
     * solo qué tan conectada está la zona.
     *
     * Para A* (simulación de accidentes) usa en cambio getGraph(), que sí
     * respeta el sentido real de circulación.
     */
    public Graph getUndirectedGraph() {
        Graph ug = new AdjacencyList(vertexCount, false);
        java.util.Set<String> seen = new java.util.HashSet<String>();
        for (Edge e : graph.edges()) {
            int a = Math.min(e.getSource(), e.getDestination());
            int b = Math.max(e.getSource(), e.getDestination());
            String key = a + "," + b;
            if (seen.contains(key)) continue; // ya se agregó (calle de doble sentido)
            seen.add(key);
            ug.addEdge(e.getSource(), e.getDestination(), e.getWeight());
        }
        return ug;
    }

    public Graph getGraph()      { return graph; }
    public int   getVertexCount(){ return vertexCount; }
    public double getLat(int i)  { return lat[i]; }
    public double getLon(int i)  { return lon[i]; }

    /**
     * Construye el arreglo de heurística que espera AStar.search(start, goal, h):
     * para cada vértice, la distancia en línea recta (Haversine) hasta 'goal'.
     * Es admisible, así que A* devuelve la ruta óptima.
     */
    public double[] heuristicTo(int goal) {
        double[] h = new double[vertexCount];
        for (int i = 0; i < vertexCount; i++) {
            h[i] = haversine(lat[i], lon[i], lat[goal], lon[goal]);
        }
        return h;
    }

    /**
     * Devuelve el vértice más cercano a una coordenada dada.
     * Útil para ubicar estaciones o el punto de un accidente por lat/lon.
     */
    public int nearestVertex(double queryLat, double queryLon) {
        int best = -1;
        double bestDist = Double.POSITIVE_INFINITY;
        for (int i = 0; i < vertexCount; i++) {
            double d = haversine(queryLat, queryLon, lat[i], lon[i]);
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        return best;
    }

    // ---------------------------------------------------------------
    //  Haversine: distancia en metros entre dos coordenadas geográficas
    // ---------------------------------------------------------------
    public static double haversine(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371000.0; // radio de la Tierra en metros
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}