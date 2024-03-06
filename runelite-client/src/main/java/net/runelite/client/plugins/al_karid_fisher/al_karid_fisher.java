package net.runelite.client.plugins.al_karid_fisher;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.inject.Provides;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.Tile;
import net.runelite.api.widgets.InterfaceID;

import java.awt.*;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.Set;
import javax.inject.Inject;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.List;

import com.google.gson.Gson; // Ensure you have Gson in your project dependencies

@PluginDescriptor(
        name = "Al Karid Fishing",
        description = "Does some stuff.",
        enabledByDefault = false
)

public class al_karid_fisher extends Plugin
{
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Gson gson = new Gson();
    private static final String SERVER_URL = "http://localhost:5000/receive_data";
    private static final Set<Integer> SMALL_NET_FISHING_SPOT = Set.of(1528);
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

    final int BANK_GROUP_ID = 12; // Placeholder value, replace with the actual ID
    final int BANK_CONTAINER_ID = 1; // Placeholder value, replace with the actual ID

    // Returns whether the bank is open or not, returns True/False
    private boolean isBankOpen() {
        Widget w = client.getWidget(
                ComponentID.BANK_CONTAINER);
        if (w != null){
            return true;
        } else {
            return false;
        }
    }

    public boolean isLevelUpOpen() {
        Widget levelUpWidget = client.getWidget(WidgetID.LEVEL_UP_GROUP_ID);

        if (levelUpWidget != null && !levelUpWidget.isHidden()) {
            return true;
        } else {
            return false;
        }
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
    private JsonArray gatherFishLocations() {
        JsonArray fishingSpotLocations = new JsonArray();
        List<NPC> npcs = client.getNpcs();
        Set<Integer> npcIds = SMALL_NET_FISHING_SPOT; // Make sure this is defined correctly

        int boxWidth = 50; // Width of the bounding box, adjust as needed
        int boxHeight = 50; // Height of the bounding box, adjust as needed

        for (NPC npc : npcs) {
            if (npc != null && npcIds.contains(npc.getId())) {
                WorldPoint worldLocation = npc.getWorldLocation();
                if (worldLocation != null) {
                    LocalPoint localPoint = LocalPoint.fromWorld(client, worldLocation);
                    if (localPoint != null) {
                        Point screenPoint = Perspective.localToCanvas(client, localPoint, client.getPlane());

                        if (screenPoint != null) {
                            // Calculate the top-left and bottom-right points of the bounding box
                            int topLeftX = screenPoint.getX() - boxWidth / 2;
                            int topLeftY = screenPoint.getY() - boxHeight / 2;
                            int bottomRightX = screenPoint.getX() + boxWidth / 2;
                            int bottomRightY = screenPoint.getY() + boxHeight / 2;

                            JsonObject json = new JsonObject();
                            json.addProperty("topLeftX", topLeftX);
                            json.addProperty("topLeftY", topLeftY);
                            json.addProperty("bottomRightX", bottomRightX);
                            json.addProperty("bottomRightY", bottomRightY);
                            fishingSpotLocations.add(json);
                        }
                    }
                }
            }
        }
        return fishingSpotLocations;
    }


    private JsonArray gatherBankBoothLocations() {
        JsonArray bankBoothBoundingBoxes = new JsonArray();
        Tile[][][] tiles = client.getScene().getTiles();

        // Fetch user-specified GameObject IDs from the plugin configuration
        final Set<Integer> BankBooth = Set.of(10355);

        for (Tile[][] plane : tiles) {
            for (Tile[] xCoord : plane) {
                for (Tile tile : xCoord) {
                    if (tile != null) {
                        for (GameObject gameObject : tile.getGameObjects()) {
                            if (gameObject != null && BankBooth.contains(gameObject.getId())) {
                                Shape clickbox = gameObject.getClickbox();
                                if (clickbox != null) {
                                    Rectangle bounds = clickbox.getBounds();
                                    JsonObject json = new JsonObject();
                                    json.addProperty("topLeftX", bounds.getMinX());
                                    json.addProperty("topLeftY", bounds.getMinY());
                                    json.addProperty("bottomRightX", bounds.getMaxX());
                                    json.addProperty("bottomRightY", bounds.getMaxY());
                                    bankBoothBoundingBoxes.add(json);
                                }
                            }
                        }
                    }
                }
            }
        }
        return bankBoothBoundingBoxes;
    }

    // Gets the state of the player, animating and ismoving
    private JsonObject gatherPlayerStates() {
        JsonObject playerStates = new JsonObject();
        Player localPlayer = client.getLocalPlayer();
        boolean isAnimating = localPlayer != null && localPlayer.getAnimation() != -1;
        boolean isInteracting = localPlayer != null && localPlayer.isInteracting();
        System.out.println("Checking if bank is open...");
        boolean isBankOpen = isBankOpen();
        boolean isLevelUpOpen = isLevelUpOpen();
        System.out.println("Bank open: " + isBankOpen);
        int walkAnimation = localPlayer.getWalkAnimation();
        // Get the player world location
        Player player = client.getLocalPlayer();
        WorldPoint playerWorldLocation = player.getWorldLocation();

        playerStates.addProperty("isAnimating", isAnimating);
        playerStates.addProperty("isInteracting", isInteracting);
        playerStates.addProperty("x", playerWorldLocation.getX());
        playerStates.addProperty("y", playerWorldLocation.getY());
        playerStates.addProperty("isBankOpen", isBankOpen);
        playerStates.addProperty("isLevelUpOpen", isLevelUpOpen);

        //System.out.println("isAnimating : " + isAnimating);
        //System.out.println("isInteracting : " + isInteracting);
        //System.out.println("walkAnimation : " + walkAnimation);

        return playerStates;
    }

    // Gets the tiles around the player
    private JsonArray gatherSurroundingTiles() {
        JsonArray tileArray = new JsonArray();
        final int MAX_DISTANCE = 25; // Adjust the distance as needed
        Player player = client.getLocalPlayer();

        if (player == null) {
            return tileArray;
        }

        WorldPoint playerWorldLocation = player.getWorldLocation();
        if (playerWorldLocation == null) {
            return tileArray;
        }

        int baseX = playerWorldLocation.getX() - MAX_DISTANCE;
        int baseY = playerWorldLocation.getY() - MAX_DISTANCE;

        for (int dx = 0; dx <= MAX_DISTANCE * 2; dx++) {
            for (int dy = 0; dy <= MAX_DISTANCE * 2; dy++) {
                int worldX = baseX + dx;
                int worldY = baseY + dy;
                LocalPoint localPoint = LocalPoint.fromWorld(client, worldX, worldY);

                if (localPoint != null) {
                    Point screenPoint = Perspective.localToCanvas(client, localPoint, client.getPlane());
                    if (screenPoint != null) {
                        JsonObject tileObject = new JsonObject();
                        tileObject.addProperty("worldX", worldX);
                        tileObject.addProperty("worldY", worldY);
                        tileObject.addProperty("screenX", screenPoint.getX());
                        tileObject.addProperty("screenY", screenPoint.getY());
                        // Add your logic to determine if the tile is walkable
                        tileObject.addProperty("walkable", true); // Placeholder
                        tileArray.add(tileObject);
                    }
                }
            }
        }

        return tileArray;
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

            //System.out.println("Compiled data before trees : " + compiledData);

            // Gather fishing locations
            JsonArray fishLocations = gatherFishLocations();
            compiledData.add("fishing_spots", fishLocations);

            // Gather fishing locations
            JsonArray bankBoothLocations = gatherBankBoothLocations();
            compiledData.add("bank_booths", bankBoothLocations);

            JsonArray tileData = gatherSurroundingTiles();
            compiledData.add("tiles", tileData);
            //System.out.println("Full compiled data : " + compiledData);

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
            e.printStackTrace();
            // Restore interrupted state...
            Thread.currentThread().interrupt();
        }
    }

}
