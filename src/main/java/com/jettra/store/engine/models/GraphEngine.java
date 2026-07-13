package com.jettra.store.engine.models;

import io.jettra.json.JettraJson;
import io.jettra.json.JsonObject;
import com.jettra.store.engine.cluster.JettraConsensusClient;
import com.jettra.store.engine.core.EngineFamily;
import com.jettra.store.engine.core.JettraStorageEngine;
import java.nio.charset.StandardCharsets;

public class GraphEngine implements EngineFamily {

    private final JettraStorageEngine engine;
    private final JettraConsensusClient raftClient;
    private final JettraJson gson;

    public GraphEngine(JettraStorageEngine engine) {
        this.engine = engine;
        this.raftClient = new JettraConsensusClient();
        this.gson = new JettraJson();
    }

    @Override
    public String getName() {
        return "GRAPH";
    }

    @Override
    public void init() {
        System.out.println("Initializing Graph Engine...");
        raftClient.init();
    }

    @Override
    public void close() {
        System.out.println("Closing Graph Engine...");
        raftClient.close();
    }
    
    public void addNode(String graphId, String nodeId, JsonObject data) {
        String key = "graph:" + graphId + ":node:" + nodeId;
        String jsonString = gson.toJson(data);
        
        String command = "PUT " + key + " " + jsonString;
        boolean success = raftClient.sendCommand(command);
        if (!success) {
            System.err.println("Failed to replicate graph node via Raft.");
        }
    }
    
    public JsonObject getNode(String graphId, String nodeId) {
        String key = "graph:" + graphId + ":node:" + nodeId;
        byte[] payload = engine.getStorageCore().get(key);
        if (payload != null && payload.length > 0) {
            return gson.fromJson(new String(payload, StandardCharsets.UTF_8), JsonObject.class);
        }
        return null;
    }
    
    public void addEdge(String graphId, String fromNode, String toNode, String label, JsonObject properties) {
        String key = "graph:" + graphId + ":edge:" + fromNode + ":" + toNode + ":" + label;
        String jsonString = properties != null ? gson.toJson(properties) : "{}";
        
        String command = "PUT " + key + " " + jsonString;
        boolean success = raftClient.sendCommand(command);
        if (!success) {
            System.err.println("Failed to replicate graph edge via Raft.");
        }
    }
}
