package com.codersanx.busview.buses;

import static com.codersanx.busview.buses.DownloadAndUnzip.downloadFile;
import static com.codersanx.busview.buses.DownloadAndUnzip.unzipFile;

import com.codersanx.busview.models.RouteModel;
import com.codersanx.busview.models.ShapeRoute;
import com.codersanx.busview.models.Stop;
import com.codersanx.busview.models.StopTime;
import com.codersanx.busview.models.Trip;

import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import android.content.Context;

public class Emulation {
    private final Map<String, RouteModel> allRoutes = new HashMap<>();
    private final Map<String, Stop> allStops = new HashMap<>();
    private final Map<String, List<StopTime>> allStopTimes = new HashMap<>();
    private final Map<String, List<ShapeRoute>> allShapes = new HashMap<>();
    private final Map<String, Trip> routeTrip = new HashMap<>();
    private final Map<String, String> dayId = new HashMap<>();
    private final String path = "/output_folder/";
    private final Context context;
    
    public Emulation(Context context) {
        this.context = context;

        File file = new File(context.getFilesDir() + path + "calendar_dates.txt");
        if (!file.exists()) {
            return;
        }

        getTodayDateID();
        addRoutes();
        addStops();
        matchIds();
        addStopTimes();
        addShapes();
    }

    public String getBuses() {
        File file = new File(context.getFilesDir() + path + "calendar_dates.txt");
        if (!file.exists()) {
            update(true);
        }

        boolean updateNeeded = true;
        for (String date : dayId.values()) {
            if (isAfter(date + "_23:59:59")) {
                updateNeeded = false;
            }
        }

        if (updateNeeded) {
            update(true);
            System.out.println("UPDATE");
        }

        List<StopTime> trip = new ArrayList<>();

        int q = 0;

        outer: for (List<StopTime> item : allStopTimes.values()) {
            for (StopTime i : item) {
                if (isPast(routeTrip.get(i.getTripId()).getDate() + "_" + i.getDepartureTime())) {
                    continue;
                }

                if (isAfter(routeTrip.get(i.getTripId()).getDate() + "_" + i.getDepartureTime()) && i.getStopNumber().equals("0")) {
                    continue outer;
                }

                q++;

                trip.add(i);
                continue outer;
            }
        }

        BusData busData = new BusData();
        busData.setBusCount(0);

        for (StopTime item : trip) {
            Trip id = routeTrip.get(item.getTripId());
            RouteModel thisRoute = allRoutes.get(id.getRouteId());
            if (thisRoute == null){
                continue;
            }

            String s = routeTrip.get(item.getTripId()).getDate() + "_" + item.getDepartureTime().substring(0, 5);

            List<ShapeRoute> currentRoute = allShapes.get(id.getRouteId());
            Stop current = allStops.get(item.getStopId());


            SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd_HH:mm");
            Date specifiedDate;
            try {
                specifiedDate = formatter.parse(s);
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }
            long minutesDifference = (new Date().getTime() - specifiedDate.getTime()) / (1000 * 60);

            ShapeRoute busPosition = calculateRouteDistance(current, currentRoute, 25 * minutesDifference);
            busData.addBus(item.getTripId(), new Bus(item.getTripId(), busPosition.getLat(), busPosition.getLon(), 90, 25, item.getTripId(), thisRoute.getRouteId(), thisRoute.getRouteShortName(), thisRoute.getRouteLongName()));
        }

        System.out.println(q);
        busData.setBusCount(q);

        return response(busData);
    }

    private void addShapes() {
        allShapes.clear();

        List<String> lines = getInfoInFile("shapes.txt");
        lines.remove(0);

        for (String line : lines) {
            String[] splitLine = line.split(",");
            List<ShapeRoute> a = allShapes.get(splitLine[0]);

            if (a == null) {
                a = new ArrayList<>();
            }

            a.add(new ShapeRoute(splitLine[1], splitLine[2]));
            allShapes.put(splitLine[0], a);
        }
    }

    private boolean isAfter(String date) {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd_HH:mm:ss");

        try {
            Date inputDate = format.parse(date);
            Date currentDate = new Date();

            return inputDate.after(currentDate);
        } catch (ParseException e) {
            System.out.println("Ошибка в формате даты.");
        }

        return true;
    }

    private void getTodayDateID() {
        dayId.clear();

        List<String> lines = getInfoInFile("calendar_dates.txt");
        lines.remove(0);

        for (String line : lines) {
            String[] splitLine = line.split(",");
            dayId.put(splitLine[0], splitLine[1]);
        }
    }

    private void addStopTimes() {
        allStopTimes.clear();

        List<String> lines = getInfoInFile("stop_times.txt");
        lines.remove(0);

        for (String line : lines) {
            String[] splitLine = line.split(",");
            List<StopTime> a = allStopTimes.get(splitLine[0]);

            if (a == null) {
                a = new ArrayList<>();
            }

            a.add(new StopTime(splitLine[0], splitLine[2], splitLine[3], splitLine[4]));
            allStopTimes.put(splitLine[0], a);
        }
    }

    private void addStops() {
        allStops.clear();

        List<String> lines = getInfoInFile("stops.txt");
        lines.remove(0);

        for (String line : lines) {
            String[] splitLine = line.split(",");
            allStops.put(splitLine[0], new Stop(splitLine[0], splitLine[2], splitLine[4], splitLine[5]));
        }
    }

    private void addRoutes() {
        allRoutes.clear();

        List<String> lines = getInfoInFile("routes.txt");
        lines.remove(0);

        for (String line : lines) {
            String[] splitLine = line.split(",");
            allRoutes.put(splitLine[0], new RouteModel(splitLine[0], splitLine[2], splitLine[3]));
        }
    }

    private void matchIds() {
        routeTrip.clear();

        List<String> lines = getInfoInFile("trips.txt");

        lines.remove(0);

        for (String line : lines) {
            String[] splitLine = line.split(",");
            routeTrip.put(splitLine[2], new Trip(splitLine[0], dayId.get(splitLine[1])));
        }
    }

    private boolean isPast(String date) {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd_HH:mm:ss");

        try {
            Date inputDate = format.parse(date);
            Date currentDate = new Date();

            return inputDate.before(currentDate);
        } catch (ParseException e) {
            System.out.println("Ошибка в формате даты.");
        }

        return true;
    }


    private int findNearestPointIndex(Stop point, List<ShapeRoute> routeCoordinates) {
        int nearestIndex = 0;
        double minDistance = Double.MAX_VALUE;

        for (int i = 0; i < routeCoordinates.size(); i++) {
            double distance = calculateDistance(
                    point.getStopLat(),
                    point.getStopLon(),
                    routeCoordinates.get(i).getLat(),
                    routeCoordinates.get(i).getLon()
            );

            if (distance < minDistance) {
                minDistance = distance;
                nearestIndex = i;
            }
        }

        return nearestIndex;
    }


    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371.0; // Радиус Земли в километрах
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return r * c;
    }

    private ShapeRoute calculateRouteDistance(Stop current, List<ShapeRoute> route, double neededDistance) {
        int nearestEndIndex = findNearestPointIndex(current, route);
        int nearestStartIndex = nearestEndIndex - 1;

        if (nearestStartIndex == -1) {
            return new ShapeRoute(String.valueOf(route.get(nearestEndIndex).getLat()), String.valueOf(route.get(nearestEndIndex).getLon()));
        }

        int startIndex = Math.min(nearestStartIndex, nearestEndIndex);
        int endIndex = Math.max(nearestStartIndex, nearestEndIndex);

        double totalDistance = totalDistance(startIndex, endIndex, route);

        int i = 1;
        while (totalDistance < neededDistance && startIndex - i > 1) {
            totalDistance = totalDistance(startIndex - i, endIndex, route);
            i++;
        }

        return new ShapeRoute(String.valueOf(route.get(startIndex - i + 1).getLat()), String.valueOf(route.get(startIndex - i + 1).getLon()));
    }

    private double totalDistance(int startIndex, int endIndex, List<ShapeRoute> route) {
        double totalDistance = 0.0;
        for (int i = startIndex; i < endIndex; i++) {
            totalDistance += calculateDistance(
                    route.get(i).getLat(),
                    route.get(i).getLon(),
                    route.get(i + 1).getLat(),
                    route.get(i + 1).getLon()
            );
        }
        return totalDistance;
    }
    private String response(BusData busData) {
        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("{\n");
        jsonBuilder.append("  \"BusCount\": ").append(busData.getBusCount()).append(",\n");
        jsonBuilder.append("  \"Buses\": {\n");

        for (Map.Entry<String, Bus> entry : busData.getBuses().entrySet()) {
            Bus bus = entry.getValue();
            jsonBuilder.append("    \"").append(entry.getKey()).append("\": {\n");
            jsonBuilder.append("      \"Label\": \"").append(bus.getLabel()).append("\",\n");
            jsonBuilder.append("      \"Latitude\": ").append(bus.getLatitude()).append(",\n");
            jsonBuilder.append("      \"Longitude\": ").append(bus.getLongitude()).append(",\n");
            jsonBuilder.append("      \"Bearing\": ").append(bus.getBearing()).append(",\n");
            jsonBuilder.append("      \"SpeedKmPerHour\": ").append(bus.getSpeedKmPerHour()).append(",\n");
            jsonBuilder.append("      \"TripID\": \"").append(bus.getTripID()).append("\",\n");
            jsonBuilder.append("      \"RouteID\": \"").append(bus.getRouteID()).append("\",\n");
            jsonBuilder.append("      \"RouteShortName\": \"").append(bus.getRouteShortName()).append("\",\n");
            jsonBuilder.append("      \"RouteLongName\": \"").append(bus.getRouteLongName()).append("\"\n");
            jsonBuilder.append("    },\n");
        }

        int lastCommaIndex = jsonBuilder.lastIndexOf(",");
        if (lastCommaIndex != -1) {
            jsonBuilder.deleteCharAt(lastCommaIndex);
        }

        jsonBuilder.append("  }\n");
        jsonBuilder.append("}");

        return jsonBuilder.toString();
    }

    private List<String> getInfoInFile(String fileName) {
        List<String> lines = new ArrayList<>();

        try (InputStream inputStream = new FileInputStream(context.getFilesDir() + path + fileName);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {

            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return lines;
    }

    public void update(boolean isReimportNeeded) {
        String fileURL = "https://www.motionbuscard.org.cy/opendata/downloadfile?file=GTFS%5C6_google_transit.zip&rel=True";
        String saveFilePath = context.getFilesDir() + "/6google_transit.zip";
        String unzipLocation = context.getFilesDir() + "/output_folder/";

        File outputFolder = new File(unzipLocation); 
        if (!outputFolder.exists()) {
            outputFolder.mkdirs();
        }

        try {
            downloadFile(fileURL, saveFilePath);
            unzipFile(saveFilePath, unzipLocation);
            System.out.println("Файл успешно загружен и распакован.");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (isReimportNeeded) {
            getTodayDateID();
            addRoutes();
            addStops();
            matchIds();
            addStopTimes();
            addShapes();
        }
    }
}