package com.codersanx.busview.models;

public class ShapeRoute {
    private final String lat;
    private final String lon;

    public ShapeRoute(String lat, String lon) {
        this.lat = lat;
        this.lon = lon;
    }

    public double getLat() {
        return Double.parseDouble(lat);
    }

    public double getLon() {
        return Double.parseDouble(lon);
    }
}
