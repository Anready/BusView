package com.codersanx.busview.models;

public class BusModel {
    private final String Label;
    private final double Latitude;
    private final double Longitude;
    private final float Bearing;
    private final float SpeedKmPerHour;
    private final String TripID;
    private final String RouteID;
    private final String RouteShortName;
    private final String RouteLongName;

    public BusModel(String Label, double Latitude, double Longitude, float Bearing,
               float SpeedKmPerHour, String TripID, String RouteID, String RouteShortName,
               String RouteLongName) {
        this.Label = Label;
        this.Latitude = Latitude;
        this.Longitude = Longitude;
        this.Bearing = Bearing;
        this.SpeedKmPerHour = SpeedKmPerHour;
        this.TripID = TripID;
        this.RouteID = RouteID;
        this.RouteShortName = RouteShortName;
        this.RouteLongName = RouteLongName;
    }

    // Getters and Setters
    public String getLabel() {
        return Label;
    }

    public double getLatitude() {
        return Latitude;
    }

    public double getLongitude() {
        return Longitude;
    }

    public Float getBearing() {
        return Bearing;
    }

    public Float getSpeedKmPerHour() {
        return SpeedKmPerHour;
    }

    public String getTripID() {
        return TripID;
    }

    public String getRouteID() {
        return RouteID;
    }

    public String getRouteShortName() {
        return RouteShortName;
    }

    public String getRouteLongName() {
        return RouteLongName;
    }
}

