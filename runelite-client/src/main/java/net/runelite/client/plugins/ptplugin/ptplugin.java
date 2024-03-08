package net.runelite.client.plugins.ptplugin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.inject.Provides;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.Tile;

import java.awt.*;
import java.io.OutputStream;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.google.gson.Gson; // Ensure you have Gson in your project dependencies

@PluginDescriptor(
        name = "PT Plugin",
        description = "Does some stuff.",
        enabledByDefault = false
)

public class ptplugin extends Plugin
{
    @Inject
    private ptconfig config;

    @Provides
    ptconfig getConfig(ConfigManager configManager)
    {
        return configManager.getConfig(ptconfig.class);
    }

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Gson gson = new Gson();
    private static final String SERVER_URL = "http://localhost:5000/receive_data";
    private static final Set<Integer> NPC_IDS = Set.of(1528);
    private ScheduledExecutorService executorService;

    @Inject
    private Client client;

    @Override
    protected void startUp() throws Exception {
        executorService = Executors.newSingleThreadScheduledExecutor();
        executorService.scheduleAtFixedRate(this::compileAndSendData, 0, 200, TimeUnit.MILLISECONDS); // Poll twice per second

        sendConfigUpdate();
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


    private JsonArray gatherLocations(){
        JsonArray nothingArray = new JsonArray();
        boolean typeFocusBool = config.typeFocusBool();
        if (typeFocusBool == true)
        {
            JsonArray NPCLocations = new JsonArray();
            List<NPC> npcs = client.getNpcs();
            // Fetch user-specified GameObject IDs from the plugin configuration
            String npcObjectIdsStr = config.npcObjectIds(); // Use a different variable name here
            Set<Integer> npcObjectIds = Arrays.stream(npcObjectIdsStr.split(","))
                    .map(String::trim)
                    .map(Integer::parseInt)
                    .collect(Collectors.toSet());

            int boxWidth = 50; // Width of the bounding box, adjust as needed
            int boxHeight = 50; // Height of the bounding box, adjust as needed

            for (NPC npc : npcs) {
                if (npc != null && npcObjectIds.contains(npc.getId())) {
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
                                NPCLocations.add(json);
                            }
                        }
                    }
                }
            }
            return NPCLocations;
        } else {
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
            System.out.println("gameObjects : " + treeBoundingBoxes);
            return treeBoundingBoxes;
        }
    }


    // Gets the bounding boxes of NPCs



    // Gets the state of the player, animating and ismoving
    private JsonObject gatherPlayerStates() {
        JsonObject playerStates = new JsonObject();
        Player localPlayer = client.getLocalPlayer();
        boolean isAnimating = localPlayer != null && localPlayer.getAnimation() != -1;
        boolean isInteracting = localPlayer != null && localPlayer.isInteracting();
        int walkAnimation = localPlayer.getWalkAnimation();
        int currentHitpoints = client.getBoostedSkillLevel(Skill.HITPOINTS);

        playerStates.addProperty("isAnimating", isAnimating);
        playerStates.addProperty("isInteracting", isInteracting);
        playerStates.addProperty("currentHitpoints", currentHitpoints);

        //System.out.println("isAnimating : " + isAnimating);
        //System.out.println("isInteracting : " + isInteracting);
        //System.out.println("walkAnimation : " + walkAnimation);

        return playerStates;
    }

    private JsonObject gatherConfigDataToSend(){
        JsonObject configData = new JsonObject();
        boolean dropInventory = config.dropInventory();
        boolean typeFocusBool = config.typeFocusBool();
        String itemsToHeal = config.itemIdsToHeal();
        String itemsToDrop = config.itemIdsToDrop();

        configData.addProperty("dropInventory", dropInventory);
        configData.addProperty("typeFocusBool", typeFocusBool);
        configData.addProperty("itemsToHeal", itemsToHeal);
        configData.addProperty("itemsToDrop", itemsToDrop);

        return configData;
    }

    // Combines various bits of data into a single JSON, then sends this to the sendToPythonServer method that sends the JSON
    public void compileAndSendData() {
        try {
            boolean typeFocusBool = config.typeFocusBool();
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

            // Gather config data
            JsonObject configData = gatherConfigDataToSend();
            compiledData.add("configData", configData);

            //System.out.println("Compiled data before trees : " + compiledData);

            // We want to vary what coords we are sending based on what we want to interact with
            JsonArray NPCLocations = gatherLocations();
            compiledData.add("objectCoordinates", NPCLocations);

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
        }
    }

    // Code that triggers on a config change updating itemsToHeal then sending that to the Python script
    @Subscribe
    public void onConfigChanged(ConfigChanged event) throws IOException {
        if (!"pfplugin".equals(event.getGroup()))
        {
            return;
        }

        sendConfigUpdate();
    }

    private static final String SERVER_URL_UPDATE = "http://localhost:5000/update_settings";
    public void sendConfigUpdate() {
        JsonObject compiledData = new JsonObject();
        // Gather config data
        JsonObject configData = gatherConfigDataToSend();
        compiledData.add("configData", configData);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(SERVER_URL_UPDATE))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(String.valueOf(compiledData)))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("Response from server: " + response.body());
        } catch (IOException | InterruptedException e) {
            System.out.println("Failure in sendToPythonServer || Is Server On?");
        }
    }
}
