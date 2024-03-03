package net.runelite.client.plugins.pfplugin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.inject.Provides;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.Tile;

import java.awt.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import javax.inject.Inject;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.google.gson.Gson; // Ensure you have Gson in your project dependencies

@PluginDescriptor(
        name = "PF Plugin",
        description = "Does some stuff."
)

public class pfplugin extends Plugin
{
    @Inject
    private pfconfig config;

    @Provides
    pfconfig getConfig(ConfigManager configManager)
    {
        return configManager.getConfig(pfconfig.class);
    }

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Gson gson = new Gson();
    private static final String SERVER_URL = "http://localhost:5000/receive_data";
    private static final Set<Integer> TREE_IDS = Set.of(1276, 1277, 1278, 1279, 1280);
    private static final Set<Integer> OAK_TREE_IDS = Set.of(10820);
    private static final Set<Integer> WILLOW_TREE_IDS = Set.of(10833, 10829, 10819, 10831);
    private static final Set<Integer> TIN_COPPER_ORE = Set.of(10943, 11161, 11360, 11361);
    private ScheduledExecutorService executorService;

    @Inject
    private Client client;

    @Override
    protected void startUp() throws Exception {
        executorService = Executors.newSingleThreadScheduledExecutor();
        executorService.scheduleAtFixedRate(this::compileAndSendData, 0, 200, TimeUnit.MILLISECONDS); // Poll twice per second

    }

    @Override
    protected void shutDown() throws Exception
    {
        System.out.println("Plugin shutting down!");
        executorService.shutdown();
    }

    // Gets the inventory count and array
    private JsonObject gatherInventoryData() {
        JsonObject inventoryData = new JsonObject();
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);

        if (inventory != null) {
            int inventoryCount = 0;
            JsonArray itemsArray = new JsonArray();

            for (Item item : inventory.getItems()) {
                JsonObject itemObject = new JsonObject();
                itemObject.addProperty("id", item.getId());
                itemObject.addProperty("quantity", item.getQuantity());
                itemsArray.add(itemObject);
                if (item.getId() != -1) { // Assuming -1 is an empty slot
                    inventoryCount++;
                }
            }

            inventoryData.addProperty("inventoryCount", inventoryCount);
            inventoryData.add("items", itemsArray);
        }

        return inventoryData;
    }

    // Gets position of the player on the screen so we can find objects closest to the player
    private JsonObject gatherPlayerPosition() {
        JsonObject playerPosition = new JsonObject();
        Player localPlayer = client.getLocalPlayer();

        if (localPlayer != null) {
            LocalPoint localPoint = localPlayer.getLocalLocation();
            Point screenPoint = Perspective.localToCanvas(client, localPoint, client.getPlane());

            if (screenPoint != null) {
                playerPosition.addProperty("playerX", screenPoint.getX());
                playerPosition.addProperty("playerY", screenPoint.getY());
            }
        }

        return playerPosition;
    }

    // Gets the bounding boxes of trees
    private JsonArray gatherTreeLocations() {
        JsonArray treeBoundingBoxes = new JsonArray();
        Tile[][][] tiles = client.getScene().getTiles();

        // Fetch user-specified GameObject IDs from the plugin configuration
        String gameObjectIdsConfig = config.gameObjectIds(); // Assuming 'config' is your @Inject'ed ExamplePluginConfig instance
        Set<Integer> gameObjectIds = Arrays.stream(gameObjectIdsConfig.split(","))
                .map(String::trim)
                .map(Integer::parseInt)
                .collect(Collectors.toSet());

        for (Tile[][] plane : tiles) {
            for (Tile[] xCoord : plane) {
                for (Tile tile : xCoord) {
                    if (tile != null) {
                        for (GameObject gameObject : tile.getGameObjects()) {
                            if (gameObject != null && gameObjectIds.contains(gameObject.getId())) {
                                Shape clickbox = gameObject.getClickbox();
                                if (clickbox != null) {
                                    Rectangle bounds = clickbox.getBounds();
                                    JsonObject json = new JsonObject();
                                    json.addProperty("topLeftX", bounds.getMinX());
                                    json.addProperty("topLeftY", bounds.getMinY());
                                    json.addProperty("bottomRightX", bounds.getMaxX());
                                    json.addProperty("bottomRightY", bounds.getMaxY());
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
        int walkAnimation = localPlayer.getWalkAnimation();

        playerStates.addProperty("isAnimating", isAnimating);
        playerStates.addProperty("isInteracting", isInteracting);

        System.out.println("isAnimating : " + isAnimating);
        System.out.println("isInteracting : " + isInteracting);
        System.out.println("walkAnimation : " + walkAnimation);

        return playerStates;
    }

    // Combines various bits of data into a single JSON, then sends this to the sendToPythonServer method that sends the JSON
    public void compileAndSendData() {
        try {
            JsonObject compiledData = new JsonObject();

            // Gather player states
            JsonObject playerStates = gatherPlayerStates();
            compiledData.add("playerStates", playerStates);

            // Gather the player position
            JsonObject playerPosition = gatherPlayerPosition();
            compiledData.add("playerPosition", playerPosition);

            // Gather inventory data
            JsonObject inventoryData = gatherInventoryData();
            compiledData.add("inventoryData", inventoryData);

            System.out.println("Compiled data before trees : " + compiledData);

            // Gather tree locations
            JsonArray treeLocations = gatherTreeLocations();
            compiledData.add("trees", treeLocations);

            System.out.println("Full compiled data : " + compiledData);

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
            System.out.println("Failure in sendToPythonServer || Is Server On?");
        }
    }
}
