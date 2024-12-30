package com.codersanx.busview.models;

import java.util.HashMap;
import java.util.Map;

public class BusData {
    private int BusCount;
    private final Map<String, BusModel> Buses = new HashMap<>();

    public int getBusCount() {
        return BusCount;
    }

    public void setBusCount(int busCount) {
        BusCount = busCount;
    }

    public Map<String, BusModel> getBuses() {
        return Buses;
    }

    public void addBus(String busId, BusModel bus) {
        this.Buses.put(busId, bus);
    }
}
