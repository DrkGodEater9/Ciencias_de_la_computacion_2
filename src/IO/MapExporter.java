package IO;

import Model.Edge;
import Model.Graph;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/**
 * Exporta el grafo vial a un archivo HTML interactivo (usando Leaflet + mapa
 * real de OpenStreetMap de fondo) para poder VER la zona y decidir a ojo
 * dónde ubicar el hospital, los bomberos o la base de ambulancias.
 *
 * Cómo se usa:
 *   MapExporter exp = new MapExporter(parser);
 *   exp.exportar("mapa.html");           // solo el grafo
 *   exp.exportar("mapa.html", mstEdges); // grafo + MST resaltado (opcional)
 *
 * Luego abres "mapa.html" en cualquier navegador. Al hacer CLIC en el mapa,
 * te muestra la latitud/longitud exacta de ese punto: esa coordenada la
 * pasas a parser.nearestVertex(lat, lon) para fijar la ubicación de un recurso.
 *
 * @author Grupo 4
 */
public class MapExporter {

    private final OSMParser parser;

    public MapExporter(OSMParser parser) {
        this.parser = parser;
    }

    /** Exporta solo el grafo vial. */
    public void exportar(String htmlPath) throws IOException {
        exportar(htmlPath, null);
    }

    /**
     * Exporta el grafo vial y, si se le pasa una lista de aristas del MST,
     * las dibuja encima en otro color para verlas resaltadas.
     *
     * @param htmlPath ruta de salida, ej. "mapa.html"
     * @param mstEdges aristas del MST a resaltar (puede ser null)
     */
    public void exportar(String htmlPath, List<Edge> mstEdges) throws IOException {
        Graph g = parser.getGraph();

        // Calculamos el centro del mapa (promedio de coordenadas) para centrar la vista.
        double sumLat = 0, sumLon = 0;
        int n = parser.getVertexCount();
        for (int i = 0; i < n; i++) {
            sumLat += parser.getLat(i);
            sumLon += parser.getLon(i);
        }
        double centerLat = sumLat / n;
        double centerLon = sumLon / n;

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html>\n<head>\n");
        sb.append("<meta charset='utf-8'>\n");
        sb.append("<title>Grafo vial - Grupo 4</title>\n");
        sb.append("<link rel='stylesheet' href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'/>\n");
        sb.append("<script src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'></script>\n");
        sb.append("<style>#map{height:100vh;width:100%;} ")
          .append("#info{position:absolute;top:10px;right:10px;z-index:1000;background:white;")
          .append("padding:8px 12px;border-radius:6px;box-shadow:0 1px 4px rgba(0,0,0,.3);")
          .append("font-family:sans-serif;font-size:13px;}</style>\n");
        sb.append("</head>\n<body>\n");
        sb.append("<div id='map'></div>\n");
        sb.append("<div id='info'>Haz clic en el mapa para ver la coordenada</div>\n");
        sb.append("<script>\n");

        sb.append("var map = L.map('map').setView([")
          .append(centerLat).append(",").append(centerLon).append("], 15);\n");
        sb.append("L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',")
          .append("{maxZoom:19, attribution:'© OpenStreetMap'}).addTo(map);\n");

        // --- Aristas del grafo completo (gris claro) ---
        sb.append("var calles = [\n");
        boolean first = true;
        for (Edge e : g.edges()) {
            int u = e.getSource(), v = e.getDestination();
            if (!first) sb.append(",\n");
            first = false;
            sb.append("[[").append(parser.getLat(u)).append(",").append(parser.getLon(u))
              .append("],[").append(parser.getLat(v)).append(",").append(parser.getLon(v)).append("]]");
        }
        sb.append("\n];\n");
        sb.append("calles.forEach(function(c){L.polyline(c,{color:'#888',weight:1,opacity:0.5}).addTo(map);});\n");

        // --- Aristas del MST (rojo, si se pasaron) ---
        if (mstEdges != null && !mstEdges.isEmpty()) {
            sb.append("var mst = [\n");
            first = true;
            for (Edge e : mstEdges) {
                int u = e.getSource(), v = e.getDestination();
                if (!first) sb.append(",\n");
                first = false;
                sb.append("[[").append(parser.getLat(u)).append(",").append(parser.getLon(u))
                  .append("],[").append(parser.getLat(v)).append(",").append(parser.getLon(v)).append("]]");
            }
            sb.append("\n];\n");
            sb.append("mst.forEach(function(c){L.polyline(c,{color:'red',weight:2}).addTo(map);});\n");
        }

        // --- Clic para leer coordenada (para ubicar recursos) ---
        sb.append("map.on('click', function(ev){\n");
        sb.append("  var lat = ev.latlng.lat.toFixed(7), lon = ev.latlng.lng.toFixed(7);\n");
        sb.append("  document.getElementById('info').innerHTML =\n");
        sb.append("    'Coordenada: <b>' + lat + ', ' + lon + '</b><br>' +\n");
        sb.append("    'Usa esto en parser.nearestVertex(' + lat + ', ' + lon + ')';\n");
        sb.append("  L.marker([lat, lon]).addTo(map);\n");
        sb.append("});\n");

        sb.append("</script>\n</body>\n</html>\n");

        FileWriter fw = new FileWriter(htmlPath);
        fw.write(sb.toString());
        fw.close();

        System.out.println("Mapa exportado a: " + htmlPath + " (ábrelo en tu navegador)");
    }
}
