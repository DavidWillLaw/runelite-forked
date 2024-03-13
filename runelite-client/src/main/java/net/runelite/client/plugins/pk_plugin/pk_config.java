package net.runelite.client.plugins.pk_plugin;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("pk_config")
public interface pk_config extends Config {
    @ConfigItem(
            keyName = "itemIdsToPickup",
            name = "itemIdsToPickup IDs",
            description = "Comma-separated list of itemIdsToPickup IDs to pickup"
    )
    default String itemIdsToPickup()
    {
        return "";
    }

    @ConfigItem(
            keyName = "npcsToKill",
            name = "npcsToKill",
            description = "Comma-separated list of npcs to kill"
    )
    default String npcsToKill()
    {
        return "";
    }

    @ConfigItem(
            keyName = "foodToConsume",
            name = "foodToConsume",
            description = "Comma-separated list of Item IDs to eat"
    )
    default String foodToConsume()
    {
        return "";
    }

    @ConfigItem(
            keyName = "foodHealAmount",
            name = "foodHealAmount",
            description = "foodHealAmount int"
    )
    default int foodHealAmount()
    {
        return 0;
    }
}
