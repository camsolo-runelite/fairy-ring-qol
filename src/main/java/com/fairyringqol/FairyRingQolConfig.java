package com.fairyringqol;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Keybind;

import java.awt.event.KeyEvent;

@ConfigGroup(FairyRingQolConfig.GROUP)
public interface FairyRingQolConfig extends Config
{

    String GROUP = "fairyringqol";

    @ConfigItem(
        keyName = "selectKeyBind",
        name = "Select",
        description = "Keybind for selecting first Fairy Ring in the list"
    )
    default Keybind selectKeybind() {
        return new Keybind(KeyEvent.VK_ENTER, 0);
    }

    @ConfigItem(
        keyName = "autoSelect",
        name = "Auto-Select",
        description = "Automatically select Fairy Ring when a valid code is searched"
    )
    default boolean autoSelect() {
        return true;
    }

    @ConfigItem(
        keyName = "allowClipboard",
        name = "Clipboard",
        description = "Press Ctrl+V to read Fairy Ring code from clipboard"
    )
    default boolean allowClipboard() {
        return true;
    }
}
