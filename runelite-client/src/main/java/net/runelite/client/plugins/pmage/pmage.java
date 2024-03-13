package net.runelite.client.plugins.pmage;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.inject.Provides;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.runelite.api.*;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.pmage.pmageconfig;

import javax.inject.Inject;
import java.awt.*;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Set;

@PluginDescriptor(
        name = "Pmage Plugin",
        description = "Provides data to Python client.",
        enabledByDefault = false
)
public class pmage extends Plugin {
    private HttpServer server;
    private static final int SERVER_PORT = 8080;
    private final Gson gson = new Gson();

    @Inject
    private Client client;

    @Inject
    private net.runelite.client.plugins.pmage.pmageconfig config;

    @Provides
    net.runelite.client.plugins.pmage.pmageconfig getConfig(ConfigManager configManager) {
        return configManager.getConfig(pmageconfig.class);
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
        server.setExecutor(null); // creates a default executor
        server.start();
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

    private void handleBankDataRequest(HttpExchange exchange) throws IOException {
        JsonObject response = gatherBankData();

        String jsonResponse = gson.toJson(response);
        exchange.sendResponseHeaders(200, jsonResponse.getBytes().length);
        OutputStream os = exchange.getResponseBody();
        os.write(jsonResponse.getBytes());
        os.close();
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

    private void handleConfigRequest(HttpExchange exchange) throws IOException {
        // Build a response object with the current config settings
        ConfigData configData = new ConfigData(
                config.itemIdToCastOn(),
                config.ItemIdToBank(),
                config.spellSlotToCast()
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

        JsonObject playerStates = new JsonObject();
        playerStates.addProperty("isAnimating", isAnimating);
        playerStates.addProperty("isInteracting", isInteracting);
        playerStates.addProperty("isLevelUpOpen", isLevelUpOpen);

        String response = gson.toJson(playerStates);
        exchange.sendResponseHeaders(200, response.getBytes().length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }

    public static class ConfigData {
        private String itemIdToCastOn;
        private String itemIdToBank;
        private int spellSlotToCast;

        public ConfigData(String itemIdToCastOn, String itemIdToBank, int spellSlotToCast) {
            this.itemIdToCastOn = itemIdToCastOn;
            this.itemIdToBank = itemIdToBank;
            this.spellSlotToCast = spellSlotToCast;
        }

        // Getters
        public String getItemIdToCastOn() { return itemIdToCastOn; }
        public String getItemIdToBank() { return itemIdToBank; }
        public int getSpellSlotToCast() { return spellSlotToCast; }
    }





    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if ("pmage".equals(event.getGroup())) {
            // Optional: Do something when config changes, if needed
            // Since Python client will request data, no need to push updates
        }
    }

}
