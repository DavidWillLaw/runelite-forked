package net.runelite.client.plugins.test_plugin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.StatChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.eventbus.Subscribe;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.URI;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import javax.inject.Inject;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.Tile;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson; // Ensure you have Gson in your project dependencies

@PluginDescriptor(
        name = "Example Plugin",
        description = "An example plugin."
)

public class test_plugin extends Plugin
{
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Gson gson = new Gson();
    private static final String SERVER_URL = "http://localhost:5000/receive_data";
    private static final Set<Integer> TREE_IDS = Set.of(1276, 1277, 1278, 1279, 1280);
    private ScheduledExecutorService executorService;

    @Inject
    private Client client;

    @Override
    protected void startUp() throws Exception {
        executorService = Executors.newSingleThreadScheduledExecutor();
        executorService.scheduleAtFixedRate(this::compileAndSendData, 0, 1, TimeUnit.SECONDS);
    }

    @Override
    protected void shutDown() throws Exception
    {
        System.out.println("Plugin shutting down!");
        executorService.shutdown();
    }

    @Subscribe
    public void onStatChanged(StatChanged event) {
        if (event.getSkill() == Skill.HITPOINTS) {
            int hitpoints = event.getLevel();
            sendHitpoints(hitpoints);
        }
    }

    public void sendHitpoints(int hitpoints) {
        String jsonPayload = "{\"hitpoints\":" + hitpoints + "}";
        System.out.println("Sending payload: " + jsonPayload); // Debug print

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:5000/receive_data"))
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(jsonPayload))
                .build();

        httpClient.sendAsync(request, BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenAccept(responseBody -> System.out.println("Response: " + responseBody)) // More detailed print
                .exceptionally(e -> {
                    e.printStackTrace();
                    return null;
                });
    }

    // Gets the bounding boxes of trees
    private JsonArray gatherTreeLocations() {
        JsonArray treeBoundingBoxes = new JsonArray();
        Tile[][][] tiles = client.getScene().getTiles();

        for (Tile[][] plane : tiles) {
            for (Tile[] xCoord : plane) {
                for (Tile tile : xCoord) {
                    if (tile != null) {
                        for (GameObject gameObject : tile.getGameObjects()) {
                            if (gameObject != null && TREE_IDS.contains(gameObject.getId())) {
                                LocalPoint lp = gameObject.getLocalLocation();
                                Point centerScreenPoint = Perspective.localToCanvas(client, lp, gameObject.getPlane());
                                if (centerScreenPoint != null) {
                                    int size = 32;
                                    int titleBarHeight = 50; // Estimate this value
                                    int borderLeftWidth = 10; // Estimate this value
                                    Point topLeft = new Point(centerScreenPoint.getX() - size / 2, centerScreenPoint.getY() - size / 2);
                                    Point bottomRight = new Point(centerScreenPoint.getX() + size / 2, centerScreenPoint.getY() + size / 2);

                                    JsonObject json = new JsonObject();
                                    json.addProperty("topLeftX", (topLeft.getX() + borderLeftWidth));
                                    json.addProperty("topLeftY", (topLeft.getY() + titleBarHeight));
                                    json.addProperty("bottomRightX", (bottomRight.getX() + borderLeftWidth));
                                    json.addProperty("bottomRightY", (bottomRight.getY() + titleBarHeight));
                                    treeBoundingBoxes.add(json);
                                }
                            }
                        }
                    }
                }
            }
        }
        return treeBoundingBoxes;
    }

    // Gets the state of the player, animating and ismoving
    private JsonObject gatherPlayerStates() {
        JsonObject playerStates = new JsonObject();
        Player localPlayer = client.getLocalPlayer();
        boolean isAnimating = localPlayer != null && localPlayer.getAnimation() != -1;
        boolean isInteracting = localPlayer != null && localPlayer.isInteracting();

        playerStates.addProperty("isAnimating", isAnimating);
        playerStates.addProperty("isInteracting", isInteracting);

        return playerStates;
    }

    // Combines various bits of data into a single JSON, then sends this to the sendToPythonServer method that sends the JSON
    public void compileAndSendData() {
        try {
            JsonObject compiledData = new JsonObject();

            // Gather tree locations
            JsonArray treeLocations = gatherTreeLocations();
            compiledData.add("trees", treeLocations);

            // Gather player states
            JsonObject playerStates = gatherPlayerStates();
            compiledData.add("playerStates", playerStates);

            // Send the compiled JSON
            sendToPythonServer(gson.toJson(compiledData));
        } catch (Exception e) {
            System.out.println("An error occurred while compiling or sending data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void sendToPythonServer(String jsonPayload) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(SERVER_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("Response from server: " + response.body());
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}