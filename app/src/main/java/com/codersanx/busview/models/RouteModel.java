package com.codersanx.busview.models;

public class RouteModel {
    private final String routeId;
    private final String routeShortName;
    private final String routeLongName;

    public RouteModel(String routeId, String routeShortName, String routeLongName) {
        this.routeId = routeId;
        this.routeShortName = routeShortName;
        this.routeLongName = routeLongName;
    }

    public String getRouteId() {
        return routeId;
    }

    public String getRouteShortName() {
        return routeShortName;
    }

    public String getRouteLongName() {
        return routeLongName;
    }
}

