package com.codersanx.busview.models;

public class Stop {
    private final String stopId;
    private final String stopName;
    private final String stopLat;
    private final String stopLon;

    public Stop(String stopId, String stopName, String stopLat, String stopLon) {
        this.stopId = stopId;
        this.stopName = stopName;
        this.stopLat = stopLat;
        this.stopLon = stopLon;
    }

    public String getStopId() {
        return stopId;
    }

    public String getStopName() {
        return stopName;
    }

    public double getStopLat() {
        return Double.parseDouble(stopLat);
    }

    public double getStopLon() {
        return Double.parseDouble(stopLon);
    }
}
