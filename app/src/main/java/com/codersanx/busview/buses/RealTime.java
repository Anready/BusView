package com.codersanx.busview.buses;

import android.content.Context;

import com.codersanx.busview.R;
import com.codersanx.busview.models.BusData;
import com.codersanx.busview.models.BusModel;
import com.codersanx.busview.models.RouteModel;
import com.google.transit.realtime.GtfsRealtime;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class RealTime {
    private final Map<String, RouteModel> allRoutes = new HashMap<>();
    private final Context context;

    public RealTime(Context context) {
        addRoutes(context);
        this.context = context;
    }

    public String getBuses() {
        String url = "http://20.19.98.194:8328/Api/api/gtfs-realtime";
        String filePath = context.getFilesDir() + "/gtfs-realtime";

        try {
            byte[] data = downloadFileToVariable(url);
            if (data != null) {
                saveToFile(data, filePath);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        BusData busData = new BusData();
        busData.setBusCount(0);

        try (FileInputStream input = new FileInputStream(filePath)) {
            GtfsRealtime.FeedMessage feed = GtfsRealtime.FeedMessage.parseFrom(input);

            int busesTotal = 0;
            for (GtfsRealtime.FeedEntity entity : feed.getEntityList()) {
                if (entity.hasVehicle()) {

                    GtfsRealtime.VehiclePosition position = entity.getVehicle();
                    String i = position.getTrip().getRouteId();
                    RouteModel a = allRoutes.get(i);
                    if (a == null) {
                        System.out.println(position.getTrip().toString());
                        continue;
                    }
                    busData.addBus(position.getVehicle().getId(), new BusModel(position.getVehicle().getLabel(), position.getPosition().getLatitude(), position.getPosition().getLongitude(), position.getPosition().getBearing(), position.getPosition().getSpeed(), position.getTrip().getTripId(), i, a.getRouteShortName(), a.getRouteLongName()));
                    busesTotal++;
                }
            }
            busData.setBusCount(busesTotal);
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
        } finally {
            deleteFile(filePath);
        }

        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("{\n");
        jsonBuilder.append("  \"BusCount\": ").append(busData.getBusCount()).append(",\n");
        jsonBuilder.append("  \"Buses\": {\n");

        for (Map.Entry<String, BusModel> entry : busData.getBuses().entrySet()) {
            BusModel bus = entry.getValue();
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

    private void addRoutes(Context context) {
        allRoutes.clear();

        try {
            List<String> lines = new ArrayList<>();

            try (InputStream inputStream = context.getResources().openRawResource(R.raw.routes);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
            }

            lines.remove(0);

            for (String line : lines) {
                String[] splitLine = line.split(",");
                allRoutes.put(splitLine[0], new RouteModel(splitLine[0], splitLine[2], splitLine[3]));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] downloadFileToVariable(String url) throws IOException {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(url).build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }
            return response.body() != null ? response.body().bytes() : null;
        }
    }

    public static void saveToFile(byte[] data, String filePath) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            fos.write(data);
        }
    }

    public static void deleteFile(String filePath) {
        File file = new File(filePath);
        if (file.exists()) {
            if (file.delete()) {
                System.out.println("File deleted successfully: " + filePath);
            } else {
                System.out.println("Failed to delete the file: " + filePath);
            }
        } else {
            System.out.println("File not found: " + filePath);
        }
    }
}
