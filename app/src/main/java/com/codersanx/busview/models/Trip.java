package com.codersanx.busview.models;

public class Trip {
    private final String routeId;
    private final String date;

    public Trip(String routeId, String date) {
        this.routeId = routeId;
        this.date = date;
    }

    public String getRouteId() {
        return routeId;
    }

    public String getDate() {
        return date;
    }

}
