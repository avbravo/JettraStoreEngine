package com.jettra.store.engine.models;

import io.jettra.json.JettraJson;
import io.jettra.json.JsonObject;
import com.jettra.store.engine.cluster.JettraConsensusClient;
import com.jettra.store.engine.core.EngineFamily;
import com.jettra.store.engine.core.JettraStorageEngine;
import java.nio.charset.StandardCharsets;

public class GeospatialEngine implements EngineFamily {

    private final JettraStorageEngine engine;
    private final JettraConsensusClient raftClient;
    private final JettraJson gson;

    public GeospatialEngine(JettraStorageEngine engine) {
        this.engine = engine;
        this.raftClient = new JettraConsensusClient();
        this.gson = new JettraJson();
    }

    @Override
    public String getName() {
        return "GEOSPATIAL";
    }

    @Override
    public void init() {
        System.out.println("Initializing Geospatial Engine...");
        raftClient.init();
    }

    @Override
    public void close() {
        System.out.println("Closing Geospatial Engine...");
        raftClient.close();
    }

    public void insertLocation(String collection, String locId, double lat, double lon, JsonObject metadata) {
        String internalKey = "geo:" + collection + ":" + locId;
        JsonObject doc = new JsonObject();
        JsonObject coords = new JsonObject();
        coords.addProperty("lat", lat);
        coords.addProperty("lon", lon);
        doc.add("coordinates", coords);
        if (metadata != null) {
            doc.add("metadata", metadata);
        }
        
        String command = "PUT " + internalKey + " " + gson.toJson(doc);
        boolean success = raftClient.sendCommand(command);
        if (!success) {
            System.err.println("Failed to replicate geo data via Raft.");
        }
    }

    public JsonObject getLocation(String collection, String locId) {
        String internalKey = "geo:" + collection + ":" + locId;
        byte[] payload = engine.getStorageCore().get(internalKey);
        if (payload != null && payload.length > 0) {
            return gson.fromJson(new String(payload, StandardCharsets.UTF_8), JsonObject.class);
        }
        return null;
    }
}
