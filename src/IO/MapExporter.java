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

    /**
     * Exporta un mapa con el RESULTADO de una simulación de accidente:
     *   - el grafo vial de fondo (gris),
     *   - el MST resaltado en rojo (si se pasa),
     *   - los puntos críticos como círculos verdes (con su nombre),
     *   - el accidente como un círculo rojo grande (bien diferenciado),
     *   - y la ruta más corta accidente -> punto crítico más cercano en AZUL.
     *
     * El orden de dibujo deja el rojo del MST abajo y la ruta azul + los
     * marcadores encima, para que siempre se vean.
     *
     * @param htmlPath ruta de salida, ej. "mapas/simulacion_1.html"
     * @param mstEdges aristas del MST a resaltar en rojo (puede ser null)
     * @param criticos lista de coordenadas {lat, lon} de cada punto crítico
     * @param nombres  nombre de cada punto crítico (mismo orden que 'criticos')
     * @param accLat   latitud del accidente
     * @param accLon   longitud del accidente
     * @param ruta     lista de índices de vértice de la ruta más corta (en azul)
     */
    public void exportarSimulacion(String htmlPath, List<Edge> mstEdges,
                                   List<double[]> criticos, List<String> nombres,
                                   double accLat, double accLon,
                                   List<Integer> ruta) throws IOException {
        Graph g = parser.getGraph();

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html>\n<head>\n");
        sb.append("<meta charset='utf-8'>\n");
        sb.append("<title>Simulacion de accidente - Grupo 4</title>\n");
        sb.append("<link rel='stylesheet' href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'/>\n");
        sb.append("<script src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'></script>\n");
        sb.append("<style>#map{height:100vh;width:100%;} ")
          .append("#leyenda{position:absolute;top:10px;right:10px;z-index:1000;background:white;")
          .append("padding:8px 12px;border-radius:6px;box-shadow:0 1px 4px rgba(0,0,0,.3);")
          .append("font-family:sans-serif;font-size:13px;}</style>\n");
        sb.append("</head>\n<body>\n");
        sb.append("<div id='map'></div>\n");
        sb.append("<div id='leyenda'><b>Simulacion</b><br>")
          .append("<span style='color:red'>&#9679;</span> Accidente<br>")
          .append("<span style='color:green'>&#9679;</span> Punto critico<br>")
          .append("<span style='color:blue'>&#9644;</span> Ruta mas corta<br>")
          .append("<span style='color:red'>&#9644;</span> MST (arbol de recubrimiento minimo)</div>\n");
        sb.append("<script>\n");

        // Centramos el mapa en el accidente.
        sb.append("var map = L.map('map').setView([")
          .append(accLat).append(",").append(accLon).append("], 15);\n");
        sb.append("L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',")
          .append("{maxZoom:19, attribution:'© OpenStreetMap'}).addTo(map);\n");

        // --- Grafo de fondo (gris claro) ---
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
        sb.append("calles.forEach(function(c){L.polyline(c,{color:'#888',weight:1,opacity:0.4}).addTo(map);});\n");

        // --- MST (rojo, debajo de la ruta y los marcadores) ---
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

        // --- Ruta más corta (AZUL, gruesa) ---
        if (ruta != null && ruta.size() > 1) {
            sb.append("var ruta = [\n");
            first = true;
            for (int idx : ruta) {
                if (!first) sb.append(",\n");
                first = false;
                sb.append("[").append(parser.getLat(idx)).append(",").append(parser.getLon(idx)).append("]");
            }
            sb.append("\n];\n");
            sb.append("L.polyline(ruta,{color:'blue',weight:5,opacity:0.9}).addTo(map);\n");
        }

        // --- Puntos críticos (círculos verdes) ---
        for (int i = 0; i < criticos.size(); i++) {
            double clat = criticos.get(i)[0], clon = criticos.get(i)[1];
            String nombre = (nombres != null && i < nombres.size()) ? nombres.get(i) : ("Critico " + (i + 1));
            sb.append("L.circleMarker([").append(clat).append(",").append(clon)
              .append("],{radius:8,color:'darkgreen',fillColor:'green',fillOpacity:0.9})")
              .append(".addTo(map).bindPopup('").append(escapar(nombre)).append("');\n");
        }

        // --- Accidente (círculo rojo grande, bien diferenciado) ---
        sb.append("L.circleMarker([").append(accLat).append(",").append(accLon)
          .append("],{radius:12,color:'black',weight:2,fillColor:'red',fillOpacity:1})")
          .append(".addTo(map).bindPopup('ACCIDENTE').openPopup();\n");

        sb.append("</script>\n</body>\n</html>\n");

        FileWriter fw = new FileWriter(htmlPath);
        fw.write(sb.toString());
        fw.close();

        System.out.println("Mapa de la simulacion exportado a: " + htmlPath + " (ábrelo en tu navegador)");
    }

    /**
     * Exporta un mapa con ESTACIONES sugeridas (marcadores azules grandes) y,
     * opcionalmente, los accidentes del historial (puntitos naranjas). Se usa
     * para las recomendaciones de ubicación (opción 6 del menú).
     *
     * @param htmlPath   ruta de salida
     * @param estaciones coordenadas {lat, lon} de cada estación sugerida
     * @param etiquetas  nombre de cada estación (mismo orden)
     * @param accidentes coordenadas {lat, lon} de accidentes a mostrar (puede ser null)
     */
    public void exportarUbicaciones(String htmlPath, List<double[]> estaciones,
                                    List<String> etiquetas, List<double[]> accidentes) throws IOException {
        Graph g = parser.getGraph();

        // centro del mapa: promedio de las estaciones sugeridas
        double centerLat = 0, centerLon = 0;
        if (!estaciones.isEmpty()) {
            for (double[] e : estaciones) { centerLat += e[0]; centerLon += e[1]; }
            centerLat /= estaciones.size();
            centerLon /= estaciones.size();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html>\n<head>\n");
        sb.append("<meta charset='utf-8'>\n");
        sb.append("<title>Ubicaciones sugeridas - Grupo 4</title>\n");
        sb.append("<link rel='stylesheet' href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'/>\n");
        sb.append("<script src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'></script>\n");
        sb.append("<style>#map{height:100vh;width:100%;} ")
          .append("#leyenda{position:absolute;top:10px;right:10px;z-index:1000;background:white;")
          .append("padding:8px 12px;border-radius:6px;box-shadow:0 1px 4px rgba(0,0,0,.3);")
          .append("font-family:sans-serif;font-size:13px;}</style>\n");
        sb.append("</head>\n<body>\n");
        sb.append("<div id='map'></div>\n");
        sb.append("<div id='leyenda'><b>Ubicaciones sugeridas</b><br>")
          .append("<span style='color:blue'>&#9679;</span> Estacion sugerida<br>")
          .append("<span style='color:orange'>&#9679;</span> Accidente del historial</div>\n");
        sb.append("<script>\n");

        sb.append("var map = L.map('map').setView([")
          .append(centerLat).append(",").append(centerLon).append("], 14);\n");
        sb.append("L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',")
          .append("{maxZoom:19, attribution:'© OpenStreetMap'}).addTo(map);\n");

        // --- Grafo de fondo (gris) ---
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
        sb.append("calles.forEach(function(c){L.polyline(c,{color:'#888',weight:1,opacity:0.4}).addTo(map);});\n");

        // --- Accidentes del historial (puntitos naranjas) ---
        if (accidentes != null) {
            for (double[] a : accidentes) {
                sb.append("L.circleMarker([").append(a[0]).append(",").append(a[1])
                  .append("],{radius:4,color:'orange',fillColor:'orange',fillOpacity:0.7}).addTo(map);\n");
            }
        }

        // --- Estaciones sugeridas (marcadores azules grandes) ---
        for (int i = 0; i < estaciones.size(); i++) {
            double elat = estaciones.get(i)[0], elon = estaciones.get(i)[1];
            String nombre = (etiquetas != null && i < etiquetas.size()) ? etiquetas.get(i) : ("Estacion " + (i + 1));
            sb.append("L.circleMarker([").append(elat).append(",").append(elon)
              .append("],{radius:11,color:'navy',weight:2,fillColor:'blue',fillOpacity:0.9})")
              .append(".addTo(map).bindPopup('").append(escapar(nombre)).append("');\n");
        }

        sb.append("</script>\n</body>\n</html>\n");

        FileWriter fw = new FileWriter(htmlPath);
        fw.write(sb.toString());
        fw.close();

        System.out.println("Mapa de ubicaciones sugeridas exportado a: " + htmlPath + " (ábrelo en tu navegador)");
    }

    /** Escapa comillas simples para no romper el string de JavaScript. */
    private static String escapar(String s) {
        return s.replace("\\", "\\\\").replace("'", "\\'");
    }
}
