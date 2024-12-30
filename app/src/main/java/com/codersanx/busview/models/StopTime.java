package com.codersanx.busview.models;

public class StopTime {
    private final String tripId;
    private final String departureTime;
    private final String stopId;
    private final String stopNumber;

    public StopTime(String tripId, String departureTime, String stopId, String stopNumber) {
        this.tripId = tripId;
        this.departureTime = departureTime;
        this.stopId = stopId;
        this.stopNumber = stopNumber;
    }

    public String getTripId() {
        return tripId;
    }

    public String getDepartureTime() {
        return departureTime;
    }

    public String getStopId() {
        return stopId;
    }

    public String getStopNumber() {
        return stopNumber;
    }
}
