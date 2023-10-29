package ar.edu.itba.pod.tp2.client;

import ar.edu.itba.pod.tp2.client.utils.BikeTripCSVBatchPopulator;
import ar.edu.itba.pod.tp2.client.utils.StationsCSVBatchPopulator;
import ar.edu.itba.pod.tp2.model.BikeTrip;

import ar.edu.itba.pod.tp2.model.Station;
import com.hazelcast.client.HazelcastClient;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.logging.FileHandler;
import java.util.logging.SimpleFormatter;

import static ar.edu.itba.pod.tp2.client.utils.ClientUtils.*;
import static ar.edu.itba.pod.tp2.client.AverageDistance.*;


public class Client {
    private static final String STATIONS_CSV_NAME = "stations.csv";
    private static final String BIKES_CSV_NAME = "bikes.csv";
    private static final String STATIONS_MAP_NAME = "station-map";
    private static final String BIKES_MAP_NAME = "bike-map";
    private static final String PERFORMANCE_FILE_PREFIX = "time";
    private static final String PERFORMANCE_FILE_EXT = ".txt";
    
    private static Logger logger = LoggerFactory.getLogger(Client.class);
    private static final java.util.logging.Logger performanceLogger = java.util.logging.Logger.getLogger("performanceLogger");


    public static void main(String[] args) {
        logger.info("Client Starting ...");

        // Parse Arguments
        // ./queryX -Daddresses='xx.xx.xx.xx:XXXX;yy.yy.yy.yy:YYYY' -DinPath=XX -DoutPath=YY [-Dn=4 |  -DstartDate=01/05/2021 -DendDate=31/05/2021 ]
        final Map<String, String> argMap = parseArguments(args);

//        final String query = args[0]; // TODO: hacer scripts para cada query

        //TODO: Cambiar
        String query = "query1";
        String queryNumber = query.substring(5);

        final List<String> addresses = getAddressesList(argMap.get(ADDRESSES));
        final String inPath = argMap.get(INPUT_PATH);
        final String outPath = argMap.get(OUT_PATH);

        validateNullArgument(argMap.get(ADDRESSES), "Addresses not specified");
        validateNullArgument(inPath, "Input path not specified");
        validateNullArgument(outPath, "Output path not specified");

        // Inicializamos el Logger para medir la performance con el archivo correspondiente
        FileHandler performanceFileHandler;
        try {
            performanceFileHandler = new FileHandler(outPath + PERFORMANCE_FILE_PREFIX + queryNumber + PERFORMANCE_FILE_EXT);
            performanceFileHandler.setFormatter(getPerformanceSimpleFormatter());
            performanceLogger.addHandler(performanceFileHandler);
        } catch (IOException | SecurityException e) {
            throw new RuntimeException(e);
        }


        // Inicializamos el cliente de Hazelcast
        HazelcastInstance hazelcastInstance = getHazelClientInstance(addresses);
        if(hazelcastInstance == null){
            logger.error("Error creating hazelcast client");
            System.exit(1);
        }
        logger.info("Hazelcast client started");
        
        // Obtenemos los mapas de Hazelcast
        //TODO: Si no usamos metodos de IMap, instanciar como Map
        IMap<Integer, BikeTrip> bikeTripMap = hazelcastInstance.getMap(BIKES_MAP_NAME);
        bikeTripMap.clear();

        IMap<Integer, Station> stationMap = hazelcastInstance.getMap(STATIONS_MAP_NAME);
        stationMap.clear();
        
        // Populamos los mapas con los archivos CSV
        logger.info("Populating Stations");
        StationsCSVBatchPopulator stationsPopulator = new StationsCSVBatchPopulator(inPath + STATIONS_CSV_NAME, stationMap);
        performanceLogger.info("Inicio de la lectura del archivo " + STATIONS_CSV_NAME);
        stationsPopulator.run();
        performanceLogger.info("Fin de la lectura del archivo " + STATIONS_CSV_NAME);

        logger.info("Populating Bike Trips");
        BikeTripCSVBatchPopulator bikeTripPopulator = new BikeTripCSVBatchPopulator(inPath + BIKES_CSV_NAME, bikeTripMap);
        performanceLogger.info("Inicio de la lectura del archivo " + BIKES_CSV_NAME);
        bikeTripPopulator.run();
        performanceLogger.info("Fin de la lectura del archivo " + BIKES_CSV_NAME);

        if(bikeTripMap.isEmpty() || stationMap.isEmpty()){
            logger.error("Error populating maps");
            System.exit(1);
        }
        

        switch (query) {
            case "query1" -> {
                logger.info("Query 1");
                Query1 query1Instance = new Query1("query1", hazelcastInstance, stationMap, bikeTripMap, outPath);
                performanceLogger.info("Inicio del trabajo map/reduce");
                query1Instance.run();
                performanceLogger.info("Fin del trabajo map/reduce");
            }
            case "query2" -> {
                String n = argMap.get(N_VAL);
                validateNullArgument(n, "N (result limit) not specified");
                logger.info("Query 2");

                List<Station> stations = new ArrayList<>(stationMap.values());

                averageClientSolver(hazelcastInstance,Integer.parseInt(n), bikeTripMap, stations, outPath);
            }
            case "query3" -> {
                logger.info("Query 3");
                Query3 query3Instance = new Query3("query3", hazelcastInstance, stationMap, bikeTripMap, outPath);
                performanceLogger.info("Inicio del trabajo map/reduce");
                query3Instance.run();
                performanceLogger.info("Fin del trabajo map/reduce");
            }
            case "query4" -> {
                logger.info("Query 4");

                 String startDate = argMap.get(START_DATE);
                 String endDate = argMap.get(END_DATE);
                 validateNullArgument(startDate, "Start date not specified");
                 validateNullArgument(endDate, "End date not specified");

                Query4 query4Instance = new Query4("query4", hazelcastInstance, stationMap, bikeTripMap, startDate, endDate, outPath);
                query4Instance.run();
            }
            default -> logger.error("Invalid query");
        }
        
        //TODO: Borrar tests
        /*System.out.println("StationMap size: " + stationMap.size());
        System.out.println("BikeTripMap size: " + bikeTripMap.size());

        System.out.println(stationMap.get(550).toString());
        System.out.println(stationMap.get(1055).toString());
        System.out.println(stationMap.get(776).toString());
        
        System.out.println(bikeTripMap.get(1).toString());
        System.out.println(bikeTripMap.get(100).toString());
        System.out.println(bikeTripMap.get(1000).toString());*/

        // Cerramos el performanceFileHandler
        performanceFileHandler.close();
        
        // Limpiamos los mapas antes de terminar
        bikeTripMap.clear();
        stationMap.clear();

        // Shutdown
        HazelcastClient.shutdownAll();
    }

    private static SimpleFormatter getPerformanceSimpleFormatter(){
        return new SimpleFormatter() {
                private final String format = "dd/MM/yyyy HH:mm:ss:SSSS";

                @Override
                public synchronized String format(java.util.logging.LogRecord record) {
                    String message = record.getMessage();
                    String threadName = Thread.currentThread().getName();
                    String sourceClassName = record.getSourceClassName();
                    long millis = record.getMillis();

                    return String.format(
                        " %s [%s] %s (%s) - %s%n",
                        new java.text.SimpleDateFormat(format).format(millis),
                        record.getLevel(),
                        threadName,
                        sourceClassName,
                        message
                    );
                }
            };
    }

}
