package IO;

import Model.AdjacencyList;
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
     * @param geojsonPath ruta al archivo, ej. "src/Data/export.geojson"
     */
    public OSMParser(String geojsonPath) throws IOException {
        String content = new String(Files.readAllBytes(Paths.get(geojsonPath)));
        JSONObject root = new JSONObject(content);
        JSONArray features = root.getJSONArray("features");

        // Primera pasada: recolectar vértices y aristas (por índice).
        ArrayList<int[]> edgePairs = new ArrayList<int[]>(); // {u, v}

        for (int f = 0; f < features.length(); f++) {
            JSONObject feature = features.getJSONObject(f);
            JSONObject geometry = feature.optJSONObject("geometry");
            if (geometry == null) continue;

            String type = geometry.optString("type", "");
            if (type.equals("LineString")) {
                JSONArray coords = geometry.getJSONArray("coordinates");
                addLine(coords, edgePairs);
            } else if (type.equals("MultiLineString")) {
                JSONArray lines = geometry.getJSONArray("coordinates");
                for (int i = 0; i < lines.length(); i++) {
                    addLine(lines.getJSONArray(i), edgePairs);
                }
            }
            // Ignoramos Points/Polygons: solo nos interesan las calles.
        }

        // Ya conocemos cuántos vértices hay: construimos el grafo.
        vertexCount = coordList.size();
        lat = new double[vertexCount];
        lon = new double[vertexCount];
        for (int i = 0; i < vertexCount; i++) {
            lat[i] = coordList.get(i)[0];
            lon[i] = coordList.get(i)[1];
        }

        graph = new AdjacencyList(vertexCount, false); // no dirigido

        // Segunda pasada: agregar las aristas con su peso Haversine.
        for (int[] pair : edgePairs) {
            int u = pair[0];
            int v = pair[1];
            if (u == v) continue; // evita bucles por coordenadas repetidas
            double w = haversine(lat[u], lon[u], lat[v], lon[v]);
            graph.addEdge(u, v, w);
        }
    }

    /** Descompone una calle (lista de coordenadas) en aristas consecutivas. */
    private void addLine(JSONArray coords, ArrayList<int[]> edgePairs) {
        int prev = -1;
        for (int i = 0; i < coords.length(); i++) {
            JSONArray point = coords.getJSONArray(i);
            double plon = point.getDouble(0); // GeoJSON = [lon, lat]
            double plat = point.getDouble(1);
            int idx = indexFor(plat, plon);
            if (prev != -1) {
                edgePairs.add(new int[]{prev, idx});
            }
            prev = idx;
        }
    }

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