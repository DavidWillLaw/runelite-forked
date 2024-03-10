package net.runelite.client.plugins.pmage;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("pmageconfig")
public interface pmageconfig extends Config {
    @ConfigItem(
            keyName = "itemIdToCastOn",
            name = "Item IDs to Cast on",
            description = "Comma-separated list of Item IDs to cast on"
    )
    default String itemIdToCastOn()
    {
        return "";
    }

    @ConfigItem(
            keyName = "ItemIdToBank",
            name = "ItemIdToBank",
            description = "Comma-separated list of Item IDs to bank"
    )
    default String ItemIdToBank()
    {
        return "";
    }

    @ConfigItem(
            keyName = "spellSlotToCast",
            name = "spellSlotToCast",
            description = "spellSlotToCast positional int"
    )
    default int spellSlotToCast()
    {
        return 1;
    }
}
