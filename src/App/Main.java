package App;

import Algorithms.AStar;
import Algorithms.Kruskal;
import Algorithms.Prim;
import IO.MapExporter;
import IO.OSMParser;
import Model.Edge;
import Model.Graph;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Paths;
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
    static final String HIST_FILE = DATA_DIR + "/historial_accidentes.csv"; // historial de accidentes (opcion 6B)

    // Velocidad que asumimos para la ambulancia (con sirena) en el casco urbano
    // de Yopal. Es el maximo realista que puede sostener por avenidas urbanas.
    // Con esto convertimos la distancia de la ruta en tiempo de respuesta.
    static final double VELOCIDAD_AMBULANCIA_KMH = 60.0;

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
    static List<Edge> mstActual = null;   // el MST del mapa cargado, para reusarlo en la simulacion

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
            } else if (op == 5) {
                compararRutasVsMST();
            } else if (op == 6) {
                sugerirUbicaciones();
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
        System.out.println("1. Cargar mapa y exportar el mapa con el MST en rojo");
        System.out.println("2. Registrar puntos criticos");
        System.out.println("3. Simular accidente");
        System.out.println("4. Ver metricas");
        System.out.println("5. Comparar rutas individuales vs MST (para el informe)");
        System.out.println("6. Sugerir ubicaciones de estaciones");
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
            System.out.println("  vias descartadas (peatonales, ciclorrutas, etc): " + parser.getDiscardedWayCount());

            // calculamos el MST para dibujarlo en rojo sobre el mapa
            // (y lo guardamos para reusarlo despues en la simulacion)
            List<Edge> mst = calcularMST();
            mstActual = mst;

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
            exp.exportar(salida, mst);   // siempre con el MST en rojo

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
            // cualquier vertice del grafo al azar (puede o no tener ruta a los criticos)
            vAcc = (int) (Math.random() * parser.getVertexCount());
            System.out.println("Accidente aleatorio en el vertice " + vAcc
                    + " (" + parser.getLat(vAcc) + ", " + parser.getLon(vAcc) + ")");
        } else {
            System.out.println("Opcion no valida.\n");
            return;
        }

        double accLat = parser.getLat(vAcc);
        double accLon = parser.getLon(vAcc);
        System.out.println("\nAccidente en el vertice " + vAcc + " (" + accLat + ", " + accLon + ")");

        // guardamos el accidente en el historial (lo usa la opcion 6B)
        registrarEnHistorial(accLat, accLon);

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
            System.out.printf("  %s: %.1f m, ~%.1f min (%d nodos)%n", pc.nombre, dist, tiempoMin(dist), ruta.size());

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
        System.out.printf(">>> Tiempo estimado de respuesta: ~%.1f min (a %.0f km/h)%n",
                tiempoMin(mejorDist), VELOCIDAD_AMBULANCIA_KMH);
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

        // exportamos un mapa con el resultado grafico de la simulacion:
        // criticos en verde, accidente en rojo y la ruta mas corta en azul
        try {
            File carpetaMapas = new File(MAPS_DIR);
            if (!carpetaMapas.exists()) {
                carpetaMapas.mkdirs();
            }
            // armo las listas de coordenadas y nombres de los criticos
            ArrayList<double[]> coordsCriticos = new ArrayList<double[]>();
            ArrayList<String> nombresCriticos = new ArrayList<String>();
            for (PuntoCritico pc : criticos) {
                coordsCriticos.add(new double[]{pc.lat, pc.lon});
                nombresCriticos.add(pc.nombre);
            }

            // nombre automatico: mapas/simulacion_1.html, _2.html, ... (no repite)
            String salida = siguienteNombreSimulacion();

            MapExporter exp = new MapExporter(parser);
            exp.exportarSimulacion(salida, mstActual, coordsCriticos, nombresCriticos, accLat, accLon, mejorRuta);
        } catch (IOException e) {
            System.out.println("No se pudo exportar el mapa de la simulacion: " + e.getMessage());
        }
        System.out.println();
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
            System.out.printf("Distancia: %.1f m (%.2f km) | tiempo: ~%.1f min | %d nodos%n",
                    r.distancia, r.distancia / 1000.0, tiempoMin(r.distancia), r.nodos);
        } else {
            System.out.println("Todavia no has simulado ningun accidente.");
        }
        System.out.println("==============================\n");
    }

    // calcula el MST (arbol de recubrimiento minimo) y devuelve sus aristas
    // para poder dibujarlas en rojo sobre el mapa
    static List<Edge> calcularMST() {
        System.out.println("Con que algoritmo quieres el MST?");
        System.out.println("  1. Prim");
        System.out.println("  2. Kruskal");
        int alg = leerEntero("Opcion: ");

        // OJO: el MST se calcula sobre el grafo NO dirigido. El arbol de
        // recubrimiento minimo solo tiene sentido en grafos no dirigidos, asi
        // que aca no importa si la calle es de uno o de doble sentido.
        Graph noDirigido = parser.getUndirectedGraph();

        // Prim y Kruskal imprimen todas las aristas por consola (miles de lineas),
        // asi que apago la salida un momento mientras corren para no llenar todo.
        PrintStream consolaReal = System.out;
        System.setOut(new PrintStream(PrintStream.nullOutputStream()));

        List<Edge> mst;
        String nombreAlg;
        if (alg == 2) {
            mst = new Kruskal().kruskalAlgorithm(noDirigido);
            nombreAlg = "Kruskal";
        } else {
            mst = new Prim().primAlgorithm(noDirigido);
            nombreAlg = "Prim";
        }

        System.setOut(consolaReal); // vuelvo a prender la consola

        // longitud total del arbol
        double total = 0;
        for (Edge e : mst) {
            total += e.getWeight();
        }

        System.out.println("MST calculado con " + nombreAlg);
        System.out.println("  aristas del arbol: " + mst.size());
        System.out.printf("  longitud total del MST: %.2f km%n", total / 1000.0);

        return mst;
    }

    // ---------- OPCION 5: comparar rutas individuales vs MST (para el informe) ----------
    // NO toca la simulacion; solo calcula numeros para el informe.
    static void compararRutasVsMST() {
        if (!hayMapa()) {
            return;
        }
        if (criticos.size() < 2) {
            System.out.println("Necesitas al menos 2 puntos criticos para comparar (opcion 2).\n");
            return;
        }

        int k = criticos.size();
        System.out.println("Calculando rutas mas cortas entre los " + k + " puntos de interes con A*...");

        // A* sobre el grafo NO dirigido (la red de patrullaje ignora el sentido, igual que el MST)
        AStar astar = new AStar(parser.getUndirectedGraph());

        // matriz de distancias entre cada par de puntos de interes
        double[][] d = new double[k][k];
        boolean hayInalcanzable = false;
        for (int i = 0; i < k; i++) {
            for (int j = i + 1; j < k; j++) {
                List<Integer> ruta = astar.search(criticos.get(i).vertice, criticos.get(j).vertice,
                        parser.heuristicTo(criticos.get(j).vertice));
                double dist = (ruta == null) ? Double.POSITIVE_INFINITY : longitudRuta(ruta);
                d[i][j] = dist;
                d[j][i] = dist;
                if (Double.isInfinite(dist)) hayInalcanzable = true;
            }
        }
        if (hayInalcanzable) {
            System.out.println("Aviso: hay puntos que no se pueden conectar entre si (red desconectada).");
        }

        // ESTRELLA (rutas individuales): el mejor "hub" es el que minimiza la suma
        // de distancias a los demas (1-mediana entre los puntos de interes)
        int mejorHub = 0;
        double mejorStar = Double.POSITIVE_INFINITY;
        for (int h = 0; h < k; h++) {
            double suma = 0;
            for (int j = 0; j < k; j++) {
                if (j != h) suma += d[h][j];
            }
            if (suma < mejorStar) {
                mejorStar = suma;
                mejorHub = h;
            }
        }

        // MST sobre el grafo completo de puntos de interes (Prim sobre la matriz)
        double mstTotal = mstSobreMatriz(d, k);

        System.out.println("\n===== COMPARACION: rutas individuales vs MST =====");
        System.out.println("Puntos de interes: " + k);
        System.out.println("(A) INDIVIDUAL - estrella desde \"" + criticos.get(mejorHub).nombre + "\":");
        System.out.printf ("    costo total: %.2f km  |  ~%.1f min%n", mejorStar / 1000.0, tiempoMin(mejorStar));
        System.out.println("(B) MST - un solo recorrido que conecta todos:");
        System.out.printf ("    costo total: %.2f km  |  ~%.1f min%n", mstTotal / 1000.0, tiempoMin(mstTotal));
        if (mejorStar > 0 && !Double.isInfinite(mstTotal) && !Double.isInfinite(mejorStar)) {
            double ahorro = (mejorStar - mstTotal) / mejorStar * 100.0;
            System.out.printf ("Ahorro del MST frente a la estrella: %.1f%%%n", ahorro);
        }
        System.out.println("Cobertura: ambas estrategias conectan los " + k + " puntos.");
        System.out.println("==================================================\n");
    }

    // Prim sobre una matriz de distancias (grafo completo de k puntos). Devuelve el costo total.
    static double mstSobreMatriz(double[][] d, int k) {
        boolean[] inMst = new boolean[k];
        double[] key = new double[k];
        java.util.Arrays.fill(key, Double.POSITIVE_INFINITY);
        key[0] = 0;
        double total = 0;
        for (int it = 0; it < k; it++) {
            int u = -1;
            for (int v = 0; v < k; v++) {
                if (!inMst[v] && (u == -1 || key[v] < key[u])) u = v;
            }
            if (u == -1 || Double.isInfinite(key[u])) break; // desconectado
            inMst[u] = true;
            total += key[u];
            for (int v = 0; v < k; v++) {
                if (!inMst[v] && d[u][v] < key[v]) key[v] = d[u][v];
            }
        }
        return total;
    }

    // ---------- OPCION 6: sugerir ubicaciones de estaciones ----------
    static void sugerirUbicaciones() {
        if (!hayMapa()) {
            return;
        }
        System.out.println("Como quieres sugerir las ubicaciones?");
        System.out.println("  1. Por cobertura (k-centro: reparte k estaciones por toda la ciudad)");
        System.out.println("  2. Por historial de accidentes (donde mas ocurren)");
        int m = leerEntero("Opcion: ");
        if (m == 1) {
            sugerirPorCobertura();
        } else if (m == 2) {
            sugerirPorHistorial();
        } else {
            System.out.println("Opcion no valida.\n");
        }
    }

    // 6A: corta el MST en k zonas (quitando las k-1 aristas mas largas) y propone
    // una estacion en el centro de cada zona.
    static void sugerirPorCobertura() {
        int k = leerEntero("Cuantas estaciones quieres proponer (ej. 4): ");
        if (k < 1) {
            System.out.println("Numero invalido.\n");
            return;
        }
        int n = parser.getVertexCount();
        if (k > n) k = n;

        // La primera estacion va en el centro geografico de la ciudad.
        double sumLat = 0, sumLon = 0;
        for (int i = 0; i < n; i++) {
            sumLat += parser.getLat(i);
            sumLon += parser.getLon(i);
        }
        int primera = parser.nearestVertex(sumLat / n, sumLon / n);

        int[] estaciones = new int[k];
        estaciones[0] = primera;

        // minDist[v] = distancia (en linea recta) de v a la estacion mas cercana ya elegida
        double[] minDist = new double[n];
        for (int v = 0; v < n; v++) {
            minDist[v] = OSMParser.haversine(parser.getLat(v), parser.getLon(v),
                    parser.getLat(primera), parser.getLon(primera));
        }

        // farthest-first (k-centro): cada nueva estacion es el vertice MAS LEJANO
        // a todas las que ya estan puestas. Asi quedan repartidas por toda la ciudad.
        for (int s = 1; s < k; s++) {
            int lejano = 0;
            for (int v = 1; v < n; v++) {
                if (minDist[v] > minDist[lejano]) lejano = v;
            }
            estaciones[s] = lejano;
            for (int v = 0; v < n; v++) {
                double dd = OSMParser.haversine(parser.getLat(v), parser.getLon(v),
                        parser.getLat(lejano), parser.getLon(lejano));
                if (dd < minDist[v]) minDist[v] = dd;
            }
        }

        // Para el "por que": asignamos cada interseccion a su estacion mas cercana
        // y medimos cuantas cubre y su radio maximo.
        int[] cuenta = new int[k];
        double[] radio = new double[k];
        for (int v = 0; v < n; v++) {
            int mejor = 0;
            double md = Double.POSITIVE_INFINITY;
            for (int s = 0; s < k; s++) {
                double dd = OSMParser.haversine(parser.getLat(v), parser.getLon(v),
                        parser.getLat(estaciones[s]), parser.getLon(estaciones[s]));
                if (dd < md) { md = dd; mejor = s; }
            }
            cuenta[mejor]++;
            if (md > radio[mejor]) radio[mejor] = md;
        }

        ArrayList<double[]> coords = new ArrayList<double[]>();
        ArrayList<String> etiquetas = new ArrayList<String>();

        System.out.println("\n===== SUGERENCIA POR COBERTURA (k-centro) =====");
        for (int s = 0; s < k; s++) {
            int e = estaciones[s];
            System.out.printf("Estacion %d: vertice %d (%.6f, %.6f)%n",
                    s + 1, e, parser.getLat(e), parser.getLon(e));
            System.out.printf("   cubre %d intersecciones, radio maximo ~%.0f m (~%.1f min)%n",
                    cuenta[s], radio[s], tiempoMin(radio[s]));
            coords.add(new double[]{parser.getLat(e), parser.getLon(e)});
            etiquetas.add("Estacion " + (s + 1));
        }
        System.out.println("Idea: cada estacion queda lo mas lejos posible de las otras, para");
        System.out.println("cubrir la ciudad de forma pareja (triangulacion).");
        System.out.println("===============================================\n");

        exportarSugerencia("mapas/sugerencia_cobertura.html", coords, etiquetas, null);
    }

    // 6B: usa el historial de accidentes para proponer la estacion donde mas ocurren.
    static void sugerirPorHistorial() {
        ArrayList<double[]> accidentes = leerHistorial();

        if (accidentes.isEmpty()) {
            System.out.println("No hay historial de accidentes todavia.");
            boolean gen = leerSiNo("Quieres generar 30 accidentes aleatorios de ejemplo? (s/n): ");
            if (!gen) {
                System.out.println("Simula accidentes con la opcion 3 y vuelve.\n");
                return;
            }
            for (int i = 0; i < 30; i++) {
                int v = (int) (Math.random() * parser.getVertexCount());
                registrarEnHistorial(parser.getLat(v), parser.getLon(v));
            }
            accidentes = leerHistorial();
        }

        // bounding box del mapa
        int n = parser.getVertexCount();
        double latMin = Double.POSITIVE_INFINITY, latMax = -Double.POSITIVE_INFINITY;
        double lonMin = Double.POSITIVE_INFINITY, lonMax = -Double.POSITIVE_INFINITY;
        for (int i = 0; i < n; i++) {
            double la = parser.getLat(i), lo = parser.getLon(i);
            if (la < latMin) latMin = la;
            if (la > latMax) latMax = la;
            if (lo < lonMin) lonMin = lo;
            if (lo > lonMax) lonMax = lo;
        }

        // rejilla de 6x6: contamos accidentes por celda y buscamos la mas cargada
        int G = 6;
        int[][] conteo = new int[G][G];
        double dLat = (latMax - latMin) / G;
        double dLon = (lonMax - lonMin) / G;
        for (double[] a : accidentes) {
            int fi = (int) ((a[0] - latMin) / dLat);
            int co = (int) ((a[1] - lonMin) / dLon);
            if (fi < 0) fi = 0; if (fi >= G) fi = G - 1;
            if (co < 0) co = 0; if (co >= G) co = G - 1;
            conteo[fi][co]++;
        }
        int maxFi = 0, maxCo = 0, maxCant = -1;
        for (int i = 0; i < G; i++) {
            for (int j = 0; j < G; j++) {
                if (conteo[i][j] > maxCant) { maxCant = conteo[i][j]; maxFi = i; maxCo = j; }
            }
        }

        // centro de los accidentes que cayeron en la celda mas cargada
        double sumLat = 0, sumLon = 0;
        int c = 0;
        for (double[] a : accidentes) {
            int fi = (int) ((a[0] - latMin) / dLat);
            int co = (int) ((a[1] - lonMin) / dLon);
            if (fi < 0) fi = 0; if (fi >= G) fi = G - 1;
            if (co < 0) co = 0; if (co >= G) co = G - 1;
            if (fi == maxFi && co == maxCo) { sumLat += a[0]; sumLon += a[1]; c++; }
        }
        int estacion = parser.nearestVertex(sumLat / c, sumLon / c);
        double pct = 100.0 * maxCant / accidentes.size();

        System.out.println("\n===== SUGERENCIA POR HISTORIAL DE ACCIDENTES =====");
        System.out.println("Accidentes en el historial: " + accidentes.size());
        System.out.printf ("La zona mas critica concentra %d accidentes (%.0f%% del total).%n", maxCant, pct);
        System.out.printf ("Estacion sugerida: vertice %d (%.6f, %.6f)%n",
                estacion, parser.getLat(estacion), parser.getLon(estacion));
        System.out.println("Por que: es el centro de la zona donde mas accidentes ocurren, asi");
        System.out.println("baja el tiempo de respuesta justo donde mas se necesita.");
        System.out.println("==================================================\n");

        ArrayList<double[]> est = new ArrayList<double[]>();
        ArrayList<String> etq = new ArrayList<String>();
        est.add(new double[]{parser.getLat(estacion), parser.getLon(estacion)});
        etq.add("Estacion sugerida");
        exportarSugerencia("mapas/sugerencia_historial.html", est, etq, accidentes);
    }

    // exporta un html con las estaciones sugeridas (y opcionalmente los accidentes)
    static void exportarSugerencia(String salida, ArrayList<double[]> estaciones,
                                   ArrayList<String> etiquetas, ArrayList<double[]> accidentes) {
        try {
            File carpeta = new File(MAPS_DIR);
            if (!carpeta.exists()) carpeta.mkdirs();
            MapExporter exp = new MapExporter(parser);
            exp.exportarUbicaciones(salida, estaciones, etiquetas, accidentes);
        } catch (IOException e) {
            System.out.println("No se pudo exportar el mapa: " + e.getMessage());
        }
    }

    // agrega un accidente (lat,lon) al archivo de historial
    static void registrarEnHistorial(double lat, double lon) {
        try {
            FileWriter fw = new FileWriter(HIST_FILE, true); // true = agregar al final
            fw.write(lat + "," + lon + "\n");
            fw.close();
        } catch (IOException e) {
            // no es grave, seguimos sin historial
        }
    }

    // lee el archivo de historial y devuelve la lista de {lat, lon}
    static ArrayList<double[]> leerHistorial() {
        ArrayList<double[]> lista = new ArrayList<double[]>();
        File f = new File(HIST_FILE);
        if (!f.exists()) return lista;
        try {
            for (String linea : Files.readAllLines(Paths.get(HIST_FILE))) {
                linea = linea.trim();
                if (linea.isEmpty()) continue;
                String[] p = linea.split(",");
                if (p.length >= 2) {
                    lista.add(new double[]{Double.parseDouble(p[0]), Double.parseDouble(p[1])});
                }
            }
        } catch (IOException | NumberFormatException e) {
            System.out.println("Aviso: no se pudo leer bien el historial: " + e.getMessage());
        }
        return lista;
    }

    // ---------- metodos de apoyo ----------

    // busca el siguiente nombre libre tipo mapas/simulacion_1.html, _2.html, ...
    static String siguienteNombreSimulacion() {
        int n = 1;
        File f = new File(MAPS_DIR, "simulacion_" + n + ".html");
        while (f.exists()) {
            n++;
            f = new File(MAPS_DIR, "simulacion_" + n + ".html");
        }
        return f.getPath();
    }

    // convierte una distancia en metros al tiempo (en minutos) que tardaria la
    // ambulancia a VELOCIDAD_AMBULANCIA_KMH. tiempo = distancia / velocidad
    static double tiempoMin(double metros) {
        double km = metros / 1000.0;
        double horas = km / VELOCIDAD_AMBULANCIA_KMH;
        return horas * 60.0;
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
