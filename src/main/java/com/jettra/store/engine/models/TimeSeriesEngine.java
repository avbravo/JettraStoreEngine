package com.jettra.store.engine.models;

import io.jettra.json.JettraJson;
import io.jettra.json.JsonObject;
import com.jettra.store.engine.cluster.JettraConsensusClient;
import com.jettra.store.engine.core.EngineFamily;
import com.jettra.store.engine.core.JettraStorageEngine;
import java.nio.charset.StandardCharsets;

public class TimeSeriesEngine implements EngineFamily {

    private final JettraStorageEngine engine;
    private final JettraConsensusClient raftClient;
    private final JettraJson gson;

    public TimeSeriesEngine(JettraStorageEngine engine) {
        this.engine = engine;
        this.raftClient = new JettraConsensusClient();
        this.gson = new JettraJson();
    }

    @Override
    public String getName() {
        return "TIMESERIES";
    }

    @Override
    public void init() {
        System.out.println("Initializing TimeSeries Engine...");
        raftClient.init();
    }

    @Override
    public void close() {
        System.out.println("Closing TimeSeries Engine...");
        raftClient.close();
    }
    
    /**
     * Inserts a data point. The ID is automatically generated based on timestamp to optimize for range queries.
     */
    public void insert(String measurement, long timestamp, JsonObject dataPoint) {
        String key = "ts:" + measurement + ":" + timestamp;
        
        // Add timestamp inside payload too
        dataPoint.addProperty("timestamp", timestamp);
        String jsonString = gson.toJson(dataPoint);
        
        // Send to Raft Consensus
        String command = "PUT " + key + " " + jsonString;
        boolean success = raftClient.sendCommand(command);
        if (!success) {
            System.err.println("Failed to replicate timeseries data via Raft.");
        }
    }
    
    /**
     * Fallback local get for exact timestamp. In a real time-series, range queries are preferred.
     */
    public JsonObject get(String measurement, long timestamp) {
        String key = "ts:" + measurement + ":" + timestamp;
        byte[] payload = engine.getStorageCore().get(key);
        if (payload != null) {
            String jsonString = new String(payload, StandardCharsets.UTF_8);
            return gson.fromJson(jsonString, JsonObject.class);
        }
        return null;
    }
}
