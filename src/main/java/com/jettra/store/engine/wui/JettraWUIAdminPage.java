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

    public JettraWUIAdminPage(JettraStorageEngine engine) {
        super("JettraStoreEngine Admin");
        this.engine = engine;
    }

    @Override
    protected void onInit(Map<String, String> params) {
        // Bypass auth temporarily since there is no Login UI implemented for this dashboard yet
        String loggedUser = "admin";
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
