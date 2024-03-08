package net.runelite.client.plugins.ptplugin;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("ptconfig")
public interface ptconfig extends Config
{
    @ConfigItem(
            keyName = "NPCObjectIds",
            name = "NPC IDs",
            description = "Comma-separated list of NPCObject IDs to click"
    )
    default String npcObjectIds()
    {
        return "";
    }

    @ConfigItem(
            keyName = "gameObjectIds",
            name = "gameObjectIds IDs",
            description = "Comma-separated list of NPCObject IDs to click"
    )
    default String gameObjectIds()
    {
        return "";
    }

    @ConfigItem(
            keyName = "itemIdsToHeal",
            name = "Item IDs to Heal",
            description = "Comma-separated list of Item IDs to heal"
    )
    default String itemIdsToHeal()
    {
        return "";
    }

    @ConfigItem(
            keyName = "itemIdsToDrop",
            name = "Item IDs to Drop",
            description = "Comma-separated list of Item IDs to drop"
    )
    default String itemIdsToDrop()
    {
        return "";
    }

    @ConfigItem(
            position = 0,
            keyName = "dropInventory",
            name = "dropInventory",
            description = "Bool for whether we need to highlight inventory for dropping"
    )
    default boolean dropInventory(){ return false; }

    @ConfigItem(
            position = 0,
            keyName = "typeFocusBool",
            name = "Focus on NPC",
            description = "Bool for whether we need to highlight gameObjects or NPCs for acquiring goods from"
    )
    default boolean typeFocusBool(){ return true; }
}