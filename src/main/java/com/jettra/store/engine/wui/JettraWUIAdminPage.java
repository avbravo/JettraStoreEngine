package com.jettra.store.engine.wui;

import io.jettra.wui.core.JettraDashboardPage;
import io.jettra.wui.core.UIComponent;
import io.jettra.wui.components.*;
import io.jettra.wui.complex.*;
import com.jettra.store.engine.core.JettraStorageEngine;
import java.util.Map;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.io.File;

public class JettraWUIAdminPage extends JettraDashboardPage {
    private JettraStorageEngine engine;
    private com.jettra.store.engine.auth.AuthManager authManager;

    public JettraWUIAdminPage() {
        super("JettraStoreEngine Admin");
        this.engine = com.jettra.store.engine.server.JettraServerOrchestrator.CURRENT_ENGINE;
        this.authManager = com.jettra.store.engine.server.JettraServerOrchestrator.CURRENT_AUTH_MANAGER;
    }

    public JettraWUIAdminPage(JettraStorageEngine engine, com.jettra.store.engine.auth.AuthManager authManager) {
        super("JettraStoreEngine Admin");
        this.engine = engine;
        this.authManager = authManager;
    }

    @Override
    public void handle(com.sun.net.httpserver.HttpExchange exchange) throws java.io.IOException {
        String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
        String token = null;
        if (cookieHeader != null) {
            String[] cookies = cookieHeader.split(";");
            for (String cookie : cookies) {
                cookie = cookie.trim();
                if (cookie.startsWith("jettra_token=")) {
                    token = cookie.substring("jettra_token=".length());
                    break;
                }
            }
        }

        if (token == null || !authManager.validateToken(token)) {
            exchange.getResponseHeaders().add("Location", "/wui/login");
            exchange.sendResponseHeaders(302, -1);
            exchange.getResponseBody().close();
            return;
        }

        super.handle(exchange);
    }

    @Override
    protected void onInit(Map<String, String> params) {
        String loggedUser = "admin"; // In a real scenario, extract from token
        this.children.clear();
        initLayout(loggedUser, params);
    }

    @Override
    protected void setupLeft(Left left, String username) {
        initMenuBuilder();
        addCategory("Administration", new String[]{"Dashboard", "Databases", "Users", "Nodes", "Rules", "Backup"}, "💠");
        finishMenuBuilder(left);
    }

    @Override
    protected void initCenter(Center center, String username) {
        Div centerContent = new Div();
        centerContent.setStyle("display", "flex");
        centerContent.setStyle("flex-direction", "column");
        centerContent.setStyle("gap", "20px");
        
        UIComponent h2 = new UIComponent("h2") {};
        h2.setContent("JettraStoreEngine Dashboard");
        centerContent.add(h2);
        
        // System Status Cards
        Div metricsGrid = new Div();
        metricsGrid.setStyle("display", "grid");
        metricsGrid.setStyle("grid-template-columns", "repeat(auto-fit, minmax(200px, 1fr))");
        metricsGrid.setStyle("gap", "15px");
        
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        long usedRam = memoryBean.getHeapMemoryUsage().getUsed() / (1024 * 1024);
        long maxRam = memoryBean.getHeapMemoryUsage().getMax() / (1024 * 1024);
        
        File dataDir = new File(engine.getStorageDir().toString());
        long totalSpace = dataDir.getTotalSpace() / (1024 * 1024);
        long freeSpace = dataDir.getFreeSpace() / (1024 * 1024);
        long usedSpace = totalSpace - freeSpace;
        
        metricsGrid.add(createMetricCard("RAM Usage", usedRam + " MB / " + maxRam + " MB"));
        metricsGrid.add(createMetricCard("Disk Usage", usedSpace + " MB / " + totalSpace + " MB"));
        metricsGrid.add(createMetricCard("Network Status", "ONLINE"));
        metricsGrid.add(createMetricCard("Active Nodes", "1 (Master)"));
        
        centerContent.add(metricsGrid);
        
        // Databases info
        UIComponent h3 = new UIComponent("h3") {};
        h3.setContent("Databases Info");
        centerContent.add(h3);
        
        UIComponent dbTable = new UIComponent("table") {};
        dbTable.setProperty("class", "j-table");
        
        String tableHtml = "<thead><tr><th>Engine Type</th><th>Status</th><th>Total Records</th></tr></thead>" +
                           "<tbody>" +
                           "<tr><td>DOCUMENT</td><td>ACTIVE</td><td>-</td></tr>" +
                           "<tr><td>VECTOR</td><td>ACTIVE</td><td>-</td></tr>" +
                           "<tr><td>GRAPH</td><td>ACTIVE</td><td>-</td></tr>" +
                           "<tr><td>TIMESERIES</td><td>ACTIVE</td><td>-</td></tr>" +
                           "<tr><td>COLUMN</td><td>ACTIVE</td><td>-</td></tr>" +
                           "<tr><td>KEYVALUE</td><td>ACTIVE</td><td>-</td></tr>" +
                           "<tr><td>GEOSPATIAL</td><td>ACTIVE</td><td>-</td></tr>" +
                           "<tr><td>OBJECT</td><td>ACTIVE</td><td>-</td></tr>" +
                           "</tbody>";
                           
        dbTable.setContent(tableHtml);
        
        centerContent.add(dbTable);
        
        center.add(centerContent);
    }
    
    private Div createMetricCard(String title, String value) {
        Div card = new Div();
        card.setProperty("class", "j-card j-3d-effect");
        card.setStyle("padding", "20px");
        UIComponent h = new UIComponent("h4") {};
        h.setContent(title);
        h.setStyle("margin-top", "0");
        h.setStyle("color", "var(--jettra-accent)");
        card.add(h);
        Paragraph p = new Paragraph(value);
        p.setStyle("font-size", "1.5rem");
        p.setStyle("font-weight", "bold");
        card.add(p);
        return card;
    }
}
