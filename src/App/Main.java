package App;

import Algorithms.AStar;
import IO.MapExporter;
import IO.OSMParser;
import Model.Edge;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/*
 * Menu por consola del proyecto de rutas de emergencia.
 *
 * La idea es:
 *  1. Cargar un archivo geojson de la carpeta src/Data y sacar el mapa con MapExporter.
 *  2. Meter los puntos criticos (hospital, bomberos, etc) por coordenada.
 *  3. Simular un accidente (coords a mano o aleatorio) y con A* buscar el
 *     punto critico mas cercano y el camino mas corto hacia el.
 *  4. Mostrar unas metricas.
 *
 * Grupo 4
 */
public class Main {

    static final String DATA_DIR = "src/Data";   // aqui estan los geojson
    static final String MAPS_DIR = "mapas";       // aqui se guardan los html que exporta MapExporter

    // un punto critico ya "pegado" a un vertice del grafo
    static class PuntoCritico {
        String nombre;
        int vertice;
        double lat, lon;
        PuntoCritico(String nombre, int vertice, double lat, double lon) {
            this.nombre = nombre;
            this.vertice = vertice;
            this.lat = lat;
            this.lon = lon;
        }
    }

    // guardamos el resultado del ultimo accidente para las metricas
    static class ResultadoAccidente {
        int verticeAccidente;
        double latAcc, lonAcc;
        PuntoCritico mejor;
        double distancia;
        int nodos;
    }

    // variables globales del programa
    static OSMParser parser = null;
    static String archivoCargado = null;
    static ArrayList<PuntoCritico> criticos = new ArrayList<PuntoCritico>();
    static ResultadoAccidente ultimoAccidente = null;

    static Scanner sc = new Scanner(System.in);

    public static void main(String[] args) {
        System.out.println("=== SISTEMA DE RUTAS DE EMERGENCIA - Yopal, Casanare ===");

        boolean salir = false;
        while (!salir) {
            mostrarMenu();
            int op = leerEntero("Elige una opcion: ");
            System.out.println();

            if (op == 1) {
                cargarMapa();
            } else if (op == 2) {
                registrarCriticos();
            } else if (op == 3) {
                simularAccidente();
            } else if (op == 4) {
                mostrarMetricas();
            } else if (op == 0) {
                salir = true;
            } else {
                System.out.println("Esa opcion no existe.\n");
            }
        }
        System.out.println("Listo, chao.");
    }

    static void mostrarMenu() {
        System.out.println();
        System.out.println("--------------- MENU ---------------");
        if (parser == null) {
            System.out.println("(todavia no hay mapa cargado)");
        } else {
            System.out.println("Mapa: " + archivoCargado + "  |  vertices: " + parser.getVertexCount()
                    + "  |  criticos: " + criticos.size());
        }
        System.out.println("1. Cargar mapa y exportar el mapa interactivo");
        System.out.println("2. Registrar puntos criticos");
        System.out.println("3. Simular accidente");
        System.out.println("4. Ver metricas");
        System.out.println("0. Salir");
    }

    // ---------- OPCION 1: cargar geojson y exportar mapa ----------
    static void cargarMapa() {
        File dir = new File(DATA_DIR);
        File[] archivos = dir.listFiles();

        // me quedo solo con los .geojson
        ArrayList<File> geojsons = new ArrayList<File>();
        if (archivos != null) {
            for (File f : archivos) {
                if (f.getName().toLowerCase().endsWith(".geojson")) {
                    geojsons.add(f);
                }
            }
        }

        if (geojsons.isEmpty()) {
            System.out.println("No hay archivos .geojson en " + DATA_DIR + "\n");
            return;
        }

        System.out.println("Archivos que hay en " + DATA_DIR + ":");
        for (int i = 0; i < geojsons.size(); i++) {
            System.out.println("  " + (i + 1) + ") " + geojsons.get(i).getName());
        }

        int sel = leerEntero("Cual quieres usar (numero): ");
        if (sel < 1 || sel > geojsons.size()) {
            System.out.println("Ese numero no esta en la lista.\n");
            return;
        }

        File elegido = geojsons.get(sel - 1);
        try {
            System.out.println("Cargando " + elegido.getName() + "...");
            parser = new OSMParser(elegido.getPath());
            archivoCargado = elegido.getName();

            // si cargo un mapa nuevo los criticos viejos ya no sirven (cambian los indices)
            criticos.clear();
            ultimoAccidente = null;

            System.out.println("Mapa cargado:");
            System.out.println("  vertices: " + parser.getVertexCount());
            System.out.println("  aristas: " + parser.getGraph().edgeCount());
            System.out.println("  calles de un solo sentido: " + parser.getOnewayStreetCount());
            System.out.println("  calles de doble sentido: " + parser.getTwoWayStreetCount());

            // exportar el html con MapExporter, en la carpeta mapas/
            File carpetaMapas = new File(MAPS_DIR);
            if (!carpetaMapas.exists()) {
                carpetaMapas.mkdirs();
            }
            String base = elegido.getName().replace(".geojson", "");
            String salidaDefault = MAPS_DIR + "/" + base + "_mapa.html";
            String salida = leerLinea("Nombre del html (enter para " + salidaDefault + "): ").trim();
            if (salida.isEmpty()) {
                salida = salidaDefault;
            }

            MapExporter exp = new MapExporter(parser);
            exp.exportar(salida);

        } catch (IOException e) {
            System.out.println("No se pudo leer el archivo: " + e.getMessage());
            parser = null;
            archivoCargado = null;
        } catch (Exception e) {
            System.out.println("Algo salio mal procesando el archivo: " + e.getMessage());
            parser = null;
            archivoCargado = null;
        }
        System.out.println();
    }

    // ---------- OPCION 2: registrar puntos criticos ----------
    static void registrarCriticos() {
        if (!hayMapa()) {
            return;
        }

        if (!criticos.isEmpty()) {
            System.out.println("Criticos que ya tienes:");
            listarCriticos();
        }

        boolean seguir = true;
        while (seguir) {
            String nombre = leerLinea("Nombre del punto critico (hospital, bomberos, etc): ").trim();
            if (nombre.isEmpty()) {
                nombre = "Punto " + (criticos.size() + 1);
            }
            double lat = leerDouble("Latitud: ");
            double lon = leerDouble("Longitud: ");

            // lo pegamos al vertice mas cercano del grafo
            int v = parser.nearestVertex(lat, lon);
            double vlat = parser.getLat(v);
            double vlon = parser.getLon(v);
            double dist = OSMParser.haversine(lat, lon, vlat, vlon);

            criticos.add(new PuntoCritico(nombre, v, vlat, vlon));
            System.out.println("  Guardado en el vertice " + v + " (" + vlat + ", " + vlon + ")");
            System.out.printf("  Quedo a %.1f m de la coordenada que pusiste%n", dist);

            seguir = leerSiNo("Quieres agregar otro? (s/n): ");
        }
        System.out.println("En total tienes " + criticos.size() + " punto(s) critico(s).\n");
    }

    // ---------- OPCION 3: simular accidente y buscar con A* ----------
    static void simularAccidente() {
        if (!hayMapa()) {
            return;
        }
        if (criticos.isEmpty()) {
            System.out.println("Primero registra al menos un punto critico (opcion 2).\n");
            return;
        }

        System.out.println("Como quieres poner el accidente?");
        System.out.println("  1. Escribir las coordenadas");
        System.out.println("  2. Que salgan aleatorias");
        int modo = leerEntero("Opcion: ");

        int vAcc;

        if (modo == 1) {
            double lat = leerDouble("Latitud del accidente: ");
            double lon = leerDouble("Longitud del accidente: ");
            vAcc = parser.nearestVertex(lat, lon);
        } else if (modo == 2) {
            // vertice al azar. Como el grafo es dirigido, a veces un vertice no
            // alcanza a ningun critico por el sentido de las calles, asi que
            // reintento unas cuantas veces hasta encontrar uno que si sirva.
            vAcc = -1;
            for (int intento = 0; intento < 50; intento++) {
                int v = (int) (Math.random() * parser.getVertexCount());
                if (alcanzaAlgunCritico(v)) {
                    vAcc = v;
                    break;
                }
            }
            if (vAcc == -1) {
                System.out.println("No encontre un punto aleatorio con ruta a los criticos. Intenta de nuevo.\n");
                return;
            }
            System.out.println("Accidente aleatorio en el vertice " + vAcc
                    + " (" + parser.getLat(vAcc) + ", " + parser.getLon(vAcc) + ")");
        } else {
            System.out.println("Opcion no valida.\n");
            return;
        }

        double accLat = parser.getLat(vAcc);
        double accLon = parser.getLon(vAcc);
        System.out.println("\nAccidente en el vertice " + vAcc + " (" + accLat + ", " + accLon + ")");
        System.out.println("Buscando la ruta mas corta a cada punto critico con A*...\n");

        AStar astar = new AStar(parser.getGraph());

        PuntoCritico mejor = null;
        List<Integer> mejorRuta = null;
        double mejorDist = Double.POSITIVE_INFINITY;

        for (PuntoCritico pc : criticos) {
            // A* desde el accidente HACIA el punto critico (respeta los sentidos unicos)
            double[] h = parser.heuristicTo(pc.vertice);
            List<Integer> ruta = astar.search(vAcc, pc.vertice, h);

            if (ruta == null) {
                System.out.println("  " + pc.nombre + ": no hay ruta");
                continue;
            }
            double dist = longitudRuta(ruta);
            System.out.printf("  %s: %.1f m (%d nodos)%n", pc.nombre, dist, ruta.size());

            if (dist < mejorDist) {
                mejorDist = dist;
                mejor = pc;
                mejorRuta = ruta;
            }
        }

        System.out.println();
        if (mejor == null) {
            System.out.println("Ningun punto critico es alcanzable desde el accidente.\n");
            ultimoAccidente = null;
            return;
        }

        System.out.println(">>> El mas cercano es: " + mejor.nombre);
        System.out.printf(">>> Distancia por las calles: %.1f m (%.2f km)%n", mejorDist, mejorDist / 1000.0);
        System.out.println(">>> La ruta tiene " + mejorRuta.size() + " nodos");
        System.out.println();

        // lo guardo para las metricas
        ultimoAccidente = new ResultadoAccidente();
        ultimoAccidente.verticeAccidente = vAcc;
        ultimoAccidente.latAcc = accLat;
        ultimoAccidente.lonAcc = accLon;
        ultimoAccidente.mejor = mejor;
        ultimoAccidente.distancia = mejorDist;
        ultimoAccidente.nodos = mejorRuta.size();
    }

    // ---------- OPCION 4: metricas ----------
    static void mostrarMetricas() {
        if (!hayMapa()) {
            return;
        }

        int n = parser.getVertexCount();

        // recorro los vertices para sacar el rango de coordenadas
        double latMin = Double.POSITIVE_INFINITY, latMax = -Double.POSITIVE_INFINITY;
        double lonMin = Double.POSITIVE_INFINITY, lonMax = -Double.POSITIVE_INFINITY;
        for (int i = 0; i < n; i++) {
            double la = parser.getLat(i);
            double lo = parser.getLon(i);
            if (la < latMin) latMin = la;
            if (la > latMax) latMax = la;
            if (lo < lonMin) lonMin = lo;
            if (lo > lonMax) lonMax = lo;
        }

        // longitud total de calles: uso el grafo no dirigido para no contar dos
        // veces las calles de doble sentido
        double totalMetros = 0;
        for (Edge e : parser.getUndirectedGraph().edges()) {
            totalMetros += e.getWeight();
        }

        System.out.println("========== METRICAS ==========");
        System.out.println("Archivo: " + archivoCargado);
        System.out.println("Vertices: " + n);
        System.out.println("Aristas: " + parser.getGraph().edgeCount());
        System.out.println("Calles de un solo sentido: " + parser.getOnewayStreetCount());
        System.out.println("Calles de doble sentido: " + parser.getTwoWayStreetCount());
        System.out.printf("Longitud total de la red: %.2f km%n", totalMetros / 1000.0);
        System.out.printf("Latitud entre %.6f y %.6f%n", latMin, latMax);
        System.out.printf("Longitud entre %.6f y %.6f%n", lonMin, lonMax);
        System.out.println("Puntos criticos: " + criticos.size());
        if (!criticos.isEmpty()) {
            listarCriticos();
        }

        if (ultimoAccidente != null) {
            ResultadoAccidente r = ultimoAccidente;
            System.out.println("--- Ultimo accidente simulado ---");
            System.out.println("Accidente en vertice " + r.verticeAccidente
                    + " (" + r.latAcc + ", " + r.lonAcc + ")");
            System.out.println("Critico mas cercano: " + r.mejor.nombre);
            System.out.printf("Distancia: %.1f m (%.2f km), %d nodos%n",
                    r.distancia, r.distancia / 1000.0, r.nodos);
        } else {
            System.out.println("Todavia no has simulado ningun accidente.");
        }
        System.out.println("==============================\n");
    }

    // ---------- metodos de apoyo ----------

    // devuelve true si desde el vertice v se puede llegar a algun critico
    static boolean alcanzaAlgunCritico(int v) {
        AStar astar = new AStar(parser.getGraph());
        for (PuntoCritico pc : criticos) {
            double[] h = parser.heuristicTo(pc.vertice);
            if (astar.search(v, pc.vertice, h) != null) {
                return true;
            }
        }
        return false;
    }

    // suma la distancia real (haversine) entre los nodos de la ruta
    static double longitudRuta(List<Integer> ruta) {
        double d = 0;
        for (int i = 1; i < ruta.size(); i++) {
            int a = ruta.get(i - 1);
            int b = ruta.get(i);
            d += OSMParser.haversine(parser.getLat(a), parser.getLon(a),
                    parser.getLat(b), parser.getLon(b));
        }
        return d;
    }

    static void listarCriticos() {
        for (int i = 0; i < criticos.size(); i++) {
            PuntoCritico pc = criticos.get(i);
            System.out.println("  " + (i + 1) + ") " + pc.nombre
                    + " -> vertice " + pc.vertice + " (" + pc.lat + ", " + pc.lon + ")");
        }
    }

    static boolean hayMapa() {
        if (parser == null) {
            System.out.println("Primero carga un mapa con la opcion 1.\n");
            return false;
        }
        return true;
    }

    // ---------- lectura de datos por teclado ----------

    static int leerEntero(String mensaje) {
        while (true) {
            System.out.print(mensaje);
            String linea = sc.nextLine().trim();
            try {
                return Integer.parseInt(linea);
            } catch (NumberFormatException e) {
                System.out.println("  Escribe un numero entero.");
            }
        }
    }

    static double leerDouble(String mensaje) {
        while (true) {
            System.out.print(mensaje);
            String linea = sc.nextLine().trim().replace(',', '.');
            try {
                return Double.parseDouble(linea);
            } catch (NumberFormatException e) {
                System.out.println("  Escribe un numero (ejemplo 5.3389 o -72.395).");
            }
        }
    }

    static String leerLinea(String mensaje) {
        System.out.print(mensaje);
        return sc.nextLine();
    }

    static boolean leerSiNo(String mensaje) {
        String r = leerLinea(mensaje).trim().toLowerCase();
        return r.startsWith("s");
    }
}
