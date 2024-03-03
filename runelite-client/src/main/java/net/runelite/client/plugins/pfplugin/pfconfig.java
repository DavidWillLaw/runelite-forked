package net.runelite.client.plugins.pfplugin;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("pfplugin")
public interface pfconfig extends Config
{
    @ConfigItem(
            keyName = "gameObjectIds",
            name = "GameObject IDs",
            description = "Comma-separated list of GameObject IDs to click"
    )
    default String gameObjectIds()
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
}