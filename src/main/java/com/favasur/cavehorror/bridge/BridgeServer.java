package com.favasur.cavehorror.bridge;

import com.favasur.cavehorror.CaveNoisePlugin;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * BridgeServer — optional HTTP bridge for remote monitoring.
 * 
 * Provides a lightweight HTTP endpoint exposing plugin state JSON.
 * Reserved for future integration with a web-based admin dashboard.
 * 
 * The primary external companion tool is the C# AssetProcessor:
 *   tools/csharp/AssetProcessor/ — converts audio, resizes textures, validates assets
 * 
 * For AI behavior design using Hytale's visual scripting node system:
 *   docs/visual-scripting-guide.md — maps Minecraft AI goals to Hytale node graphs
 *   src/main/resources/enderman_stalk.json — proposed node behavior schema
 * 
 * HYTALE API: Replace com.sun.net.httpserver with the Hytale server's
 * networking API (WebSocket-based) when available.
 */
public class BridgeServer {

    private final CaveNoisePlugin plugin;
    private final int port;
    
    private com.sun.net.httpserver.HttpServer httpServer;
    private boolean running = false;
    
    public BridgeServer(CaveNoisePlugin plugin, int port) {
        this.plugin = plugin;
        this.port = port;
    }
    
    public void start() {
        if (running) return;
        
        try {
            httpServer = com.sun.net.httpserver.HttpServer.create(
                new InetSocketAddress(port), 0);
            
            httpServer.createContext("/state", exchange -> {
                String response = getStateJson();
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            });
            
            httpServer.createContext("/event", exchange -> {
                if (!"POST".equals(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(405, -1);
                    return;
                }
                byte[] body = exchange.getRequestBody().readAllBytes();
                CaveNoisePlugin.getLogger().debug("Bridge event: {}", new String(body, StandardCharsets.UTF_8));
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.sendResponseHeaders(200, 2);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write("OK".getBytes());
                }
            });
            
            httpServer.setExecutor(Executors.newSingleThreadExecutor());
            httpServer.start();
            this.running = true;
            CaveNoisePlugin.getLogger().info("Bridge server started on port {}", port);
        } catch (IOException e) {
            CaveNoisePlugin.getLogger().error("Failed to start bridge server: {}", e.getMessage());
        }
    }
    
    public void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            running = false;
            CaveNoisePlugin.getLogger().info("Bridge server stopped.");
        }
    }
    
    public void pushEvent(String type, String data) {
        if (!running) return;
        CaveNoisePlugin.getLogger().debug("Bridge push: {} — {}", type, data);
    }
    
    private String getStateJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"plugin_state\",\"data\":{");
        sb.append("\"calmTimer\":").append(plugin.getCalmTimer()).append(",");
        sb.append("\"activeEntities\":").append(
            plugin.getEndermanRegistry().getActiveEntities().size()).append(",");
        sb.append("\"uptime\":0");
        sb.append("}}");
        return sb.toString();
    }
    
    public boolean isRunning() { return running; }
    public int getPort() { return port; }
}
