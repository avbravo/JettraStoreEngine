package com.jettra.store.engine.models;

import io.jettra.json.JettraJson;
import io.jettra.json.JsonObject;
import com.jettra.store.engine.cluster.JettraConsensusClient;
import com.jettra.store.engine.core.EngineFamily;
import com.jettra.store.engine.core.JettraStorageEngine;
import java.nio.charset.StandardCharsets;

public class ObjectEngine implements EngineFamily {

    private final JettraStorageEngine engine;
    private final JettraConsensusClient raftClient;
    private final JettraJson gson;

    public ObjectEngine(JettraStorageEngine engine) {
        this.engine = engine;
        this.raftClient = new JettraConsensusClient();
        this.gson = new JettraJson();
    }

    @Override
    public String getName() {
        return "OBJECT";
    }

    @Override
    public void init() {
        System.out.println("Initializing Object Engine...");
        raftClient.init();
    }

    @Override
    public void close() {
        System.out.println("Closing Object Engine...");
        raftClient.close();
    }

    public void saveObject(String collection, String objId, String className, JsonObject state) {
        String internalKey = "obj:" + collection + ":" + objId;
        JsonObject wrapper = new JsonObject();
        wrapper.addProperty("_class", className);
        wrapper.add("state", state);
        
        String command = "PUT " + internalKey + " " + gson.toJson(wrapper);
        boolean success = raftClient.sendCommand(command);
        if (!success) {
            System.err.println("Failed to replicate object data via Raft.");
        }
    }

    public JsonObject getObject(String collection, String objId) {
        String internalKey = "obj:" + collection + ":" + objId;
        byte[] payload = engine.getStorageCore().get(internalKey);
        if (payload != null && payload.length > 0) {
            return gson.fromJson(new String(payload, StandardCharsets.UTF_8), JsonObject.class);
        }
        return null;
    }
}
