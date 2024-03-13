package net.runelite.client.plugins.pk_plugin;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.inject.Provides;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import javax.inject.Inject;
import java.awt.*;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@PluginDescriptor(
        name = "Pk Plugin",
        description = "Provides data to Python client.",
        enabledByDefault = false
)
public class pk_plugin extends Plugin {
    private HttpServer server;
    private static final int SERVER_PORT = 8080;
    private final Gson gson = new Gson();
    // List to store item on the ground data
    private final Map<WorldPoint, ConcurrentHashMap<Integer, JsonObject>> spawnedItemsData = new ConcurrentHashMap<>();

    @Inject
    private Client client;

    @Inject
    private pk_config config;

    @Provides
    pk_config getConfig(ConfigManager configManager) {
        return configManager.getConfig(pk_config.class);
    }

    @Override
    protected void startUp() throws Exception {
        startServer();
    }

    @Override
    protected void shutDown() throws Exception {
        if (server != null) {
            server.stop(0);
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

    private void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(SERVER_PORT), 0);
        server.createContext("/inventory", this::handleInventoryRequest);
        server.createContext("/config", this::handleConfigRequest);
        server.createContext("/player-states", this::handlePlayerStatesRequest);
        server.createContext("/bank", this::handleBankDataRequest);
        server.createContext("/bank-booth-locations", this::handleBankBoothLocationsRequest);
        server.createContext("/npc-locations", this::handleNpcLocationsRequest);
        server.createContext("/npc-attacking-player", this::handleAttackingPlayerRequest);
        server.createContext("/ground-item-data", this::handleSpawnedItemsRequest);
        server.setExecutor(null); // creates a default executor
        server.start();
    }

    private JsonObject gatherNpcToKillData() {
        JsonArray npcDataArray = new JsonArray();
        List<NPC> npcs = client.getNpcs();
        Set<Integer> npcIdsSet = Arrays.stream(config.npcsToKill().split(","))
                .map(String::trim)
                .map(Integer::valueOf)
                .collect(Collectors.toSet());

        int boxWidth = 50;
        int boxHeight = 50;

        for (NPC npc : npcs) {
            if (npc != null && npcIdsSet.contains(npc.getId()) && npc.getHealthRatio() == -1 && npc.getHealthScale() == -1) {
                WorldPoint worldLocation = npc.getWorldLocation();
                if (worldLocation != null) {
                    LocalPoint localPoint = LocalPoint.fromWorld(client, worldLocation);
                    if (localPoint != null) {
                        Point screenPoint = Perspective.localToCanvas(client, localPoint, client.getPlane());
                        if (screenPoint != null) {
                            int topLeftX = screenPoint.getX() - boxWidth / 2;
                            int topLeftY = screenPoint.getY() - boxHeight / 2;
                            int bottomRightX = screenPoint.getX() + boxWidth / 2;
                            int bottomRightY = screenPoint.getY() + boxHeight / 2;

                            JsonObject npcJson = new JsonObject();
                            npcJson.addProperty("topLeftX", topLeftX);
                            npcJson.addProperty("topLeftY", topLeftY);
                            npcJson.addProperty("bottomRightX", bottomRightX);
                            npcJson.addProperty("bottomRightY", bottomRightY);
                            npcJson.addProperty("worldX", worldLocation.getX());
                            npcJson.addProperty("worldY", worldLocation.getY());
                            npcJson.addProperty("plane", worldLocation.getPlane());
                            npcJson.addProperty("healthRatio", npc.getHealthRatio());
                            npcJson.addProperty("healthScale", npc.getHealthScale());

                            npcDataArray.add(npcJson);
                        }
                    }
                }
            }
        }

        JsonObject npcData = new JsonObject();
        npcData.add("npcs", npcDataArray);
        return npcData;
    }


    private JsonObject gatherBankData() {
        JsonObject bankData = new JsonObject();
        JsonArray itemsArray = new JsonArray();

        ItemContainer bank = client.getItemContainer(InventoryID.BANK);

        if (bank != null) {
            for (Item item : bank.getItems()) {
                JsonObject itemObject = new JsonObject();
                itemObject.addProperty("id", item.getId());
                itemObject.addProperty("quantity", item.getQuantity());
                itemsArray.add(itemObject);
            }
        }

        bankData.add("items", itemsArray);
        return bankData;
    }

    private JsonArray gatherBankBoothLocations() {
        JsonArray bankBoothBoundingBoxes = new JsonArray();
        Tile[][][] tiles = client.getScene().getTiles();
        final Set<Integer> BankBooth = Set.of(10583);

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

    private void handleBankDataRequest(HttpExchange exchange) throws IOException {
        JsonObject response = gatherBankData();

        String jsonResponse = gson.toJson(response);
        exchange.sendResponseHeaders(200, jsonResponse.getBytes().length);
        OutputStream os = exchange.getResponseBody();
        os.write(jsonResponse.getBytes());
        os.close();
    }

    private void handleBankBoothLocationsRequest(HttpExchange exchange) throws IOException {
        try {
            JsonArray bankBoothLocations = gatherBankBoothLocations();
            JsonObject responseJson = new JsonObject();
            responseJson.add("bankBoothLocations", bankBoothLocations);

            if (bankBoothLocations.size() == 0) {
                System.out.println("No bank booths found. Ensure you are near a bank.");
                // You might want to send a response indicating no bank booths were found
                responseJson.addProperty("message", "No bank booths found.");
            }

            String response = gson.toJson(responseJson);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.getBytes().length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        } catch (Exception e) {
            e.printStackTrace();
            // Send an error response if something goes wrong
            String errorResponse = "Internal server error";
            exchange.sendResponseHeaders(500, errorResponse.getBytes().length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(errorResponse.getBytes());
            }
        }
    }

    public boolean isAnyNpcTargetingPlayer() {
        List<NPC> npcs = client.getNpcs();
        Player localPlayer = client.getLocalPlayer();

        for (NPC npc : npcs) {
            if (npc.getInteracting() != null && npc.getInteracting().equals(localPlayer)) {
                return true;
            }
        }

        return false;
    }

    private void handleAttackingPlayerRequest(HttpExchange exchange) throws IOException {
        boolean isTargeted = isAnyNpcTargetingPlayer();

        JsonObject responseObj = new JsonObject();
        responseObj.addProperty("isTargeted", isTargeted);

        // Prepare the JSON response with the proper content type
        String response = gson.toJson(responseObj);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.getBytes().length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }

    private void handleInventoryRequest(HttpExchange exchange) throws IOException {
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        JsonObject jsonResponse = new JsonObject();
        JsonArray itemsArray = new JsonArray();

        if (inventory != null) {
            for (Item item : inventory.getItems()) {
                JsonObject itemObject = new JsonObject();
                itemObject.addProperty("id", item.getId());
                itemObject.addProperty("quantity", item.getQuantity());
                itemsArray.add(itemObject);
            }
        }
        jsonResponse.add("items", itemsArray);

        String response = gson.toJson(jsonResponse);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.getBytes().length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }

    private void handleNpcLocationsRequest(HttpExchange exchange) throws IOException {
        try {
            JsonObject npcLocationsData = gatherNpcToKillData();
            JsonObject responseJson = new JsonObject();
            responseJson.add("npcLocations", npcLocationsData.get("npcs")); // Assuming gatherNpcToKillData() returns a JsonObject

            if (npcLocationsData.get("npcs").getAsJsonArray().size() == 0) {
                System.out.println("No NPCs found. Ensure you are in the correct location.");
                // You might want to send a response indicating no NPCs were found
                responseJson.addProperty("message", "No NPCs found.");
            }

            String response = gson.toJson(responseJson);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.getBytes().length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        } catch (Exception e) {
            e.printStackTrace();
            // Send an error response if something goes wrong
            String errorResponse = "Internal server error";
            exchange.sendResponseHeaders(500, errorResponse.getBytes().length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(errorResponse.getBytes());
            }
        }
    }

    @Subscribe
    public void onItemSpawned(ItemSpawned event) {
        TileItem item = event.getItem();
        WorldPoint worldLocation = event.getTile().getWorldLocation();
        Set<Integer> itemIdsToPickup = Arrays.stream(config.itemIdsToPickup().split(","))
                .map(String::trim)
                .map(Integer::parseInt)
                .collect(Collectors.toSet());

        if (itemIdsToPickup.contains(item.getId())) {
            Point screenLocation = Perspective.localToCanvas(client,
                    event.getTile().getLocalLocation(), client.getPlane());

            if (screenLocation != null) {
                // Create and store item data as JSON object
                JsonObject itemData = new JsonObject();
                itemData.addProperty("id", item.getId());
                itemData.addProperty("worldX", worldLocation.getX());
                itemData.addProperty("worldY", worldLocation.getY());
                itemData.addProperty("worldPlane", worldLocation.getPlane());
                itemData.addProperty("screenX", screenLocation.getX());
                itemData.addProperty("screenY", screenLocation.getY());

                spawnedItemsData.computeIfAbsent(worldLocation, k -> new ConcurrentHashMap<>()).put(item.getId(), itemData);
            }
        }
    }

    @Subscribe
    public void onItemDespawned(ItemDespawned event) {
        TileItem item = event.getItem();
        WorldPoint worldLocation = event.getTile().getWorldLocation();

        Map<Integer, JsonObject> itemsAtLocation = spawnedItemsData.get(worldLocation);
        if (itemsAtLocation != null) {
            itemsAtLocation.remove(item.getId());
            if (itemsAtLocation.isEmpty()) {
                spawnedItemsData.remove(worldLocation);
            }
        }
    }

    private void handleSpawnedItemsRequest(HttpExchange exchange) throws IOException {
        JsonArray itemsArray = new JsonArray();

        spawnedItemsData.values().forEach(map -> map.values().forEach(itemsArray::add));

        JsonObject response = new JsonObject();
        response.add("items", itemsArray);

        String jsonResponse = gson.toJson(response);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, jsonResponse.getBytes(StandardCharsets.UTF_8).length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(jsonResponse.getBytes(StandardCharsets.UTF_8));
        }
    }

    private void handleConfigRequest(HttpExchange exchange) throws IOException {
        // Build a response object with the current config settings
        ConfigData configData = new ConfigData(
                config.itemIdsToPickup(),
                config.npcsToKill(),
                config.foodToConsume(),
                config.foodHealAmount()
        );

        // Convert the response object to JSON
        String response = gson.toJson(configData);
        exchange.sendResponseHeaders(200, response.getBytes().length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }

    private void handlePlayerStatesRequest(HttpExchange exchange) throws IOException {
        Player localPlayer = client.getLocalPlayer();
        boolean isAnimating = localPlayer != null && localPlayer.getAnimation() != -1;
        boolean isInteracting = localPlayer != null && localPlayer.isInteracting();
        boolean isLevelUpOpen = isLevelUpOpen();
        int boostedHitpoints = client.getBoostedSkillLevel(Skill.HITPOINTS); // Get boosted hitpoints
        WorldPoint worldLocation = localPlayer != null ? localPlayer.getWorldLocation() : null;

        JsonObject playerStates = new JsonObject();
        playerStates.addProperty("isAnimating", isAnimating);
        playerStates.addProperty("isInteracting", isInteracting);
        playerStates.addProperty("isLevelUpOpen", isLevelUpOpen);
        playerStates.addProperty("boostedHitpoints", boostedHitpoints); // Add boosted hitpoints to JSON

        // Add world location if available
        if (worldLocation != null) {
            playerStates.addProperty("worldX", worldLocation.getX());
            playerStates.addProperty("worldY", worldLocation.getY());
            playerStates.addProperty("worldPlane", worldLocation.getPlane());
        } else {
            // Handle the case where player or location is null
            playerStates.addProperty("worldX", "unknown");
            playerStates.addProperty("worldY", "unknown");
            playerStates.addProperty("worldPlane", "unknown");
        }

        String response = gson.toJson(playerStates);
        exchange.getResponseHeaders().set("Content-Type", "application/json"); // Set the content type
        exchange.sendResponseHeaders(200, response.getBytes().length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        } finally {
            exchange.close(); // Ensure to close the exchange
        }
    }

    public static class ConfigData {
        private String itemIdsToPickup;
        private String npcsToKill;
        private String foodToConsume;
        private int foodHealAmount;

        public ConfigData(String itemIdToCastOn, String itemIdToBank, String s, int spellSlotToCast) {
            this.itemIdsToPickup = itemIdsToPickup;
            this.npcsToKill = npcsToKill;
            this.foodToConsume = foodToConsume;
            this.foodHealAmount = foodHealAmount;
        }

        // Getters
        public String getItemIdsToPickup() { return itemIdsToPickup; }
        public String getNpcsToKill() { return npcsToKill; }
        public String getFoodToConsume() { return foodToConsume; }
        public int getFoodHealAmount() { return foodHealAmount; }
    }
}
