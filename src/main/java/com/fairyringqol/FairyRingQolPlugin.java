package com.fairyringqol;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.fairyring.FairyRing;

import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.Objects;

@Slf4j
@PluginDescriptor(
    name = "Fairy Ring QoL",
    description = "Improved fairy ring code selection",
    tags = { "fairy ring", "search", "hotkey" }
)
public class FairyRingQolPlugin extends Plugin
{

    @Inject private FairyRingQolConfig config;
    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private KeyManager keyManager = null;

    private boolean isEnabled = false;
    // keypress actions are deferred until next tick to make sure chatbox and fairy ring log components are updated in time
    //  TODO: even when deferring until next tick, it's still not 100% reliable
    private boolean selectNextTick = false;
    private boolean readSearchNextTick = false;
    private boolean readClipboardNextTick = false;

    private final KeyListener hotkeyListener = new KeyListener() {
        @Override public void keyPressed(KeyEvent e) {
            if (!selectNextTick) {
                var keybind = config.selectKeybind();
                boolean isSelection = keybind != null && keybind.matches(e);
                if (isSelection) {
                    selectNextTick = true;
                    return;
                }
            }
            if (!readClipboardNextTick && config.allowClipboard() && e.isControlDown() && e.getKeyCode() == KeyEvent.VK_V) {
                readClipboardNextTick = true;
            }
        }
        @Override public void keyReleased(KeyEvent e) {
            // putting this on keyReleased to defer search a bit more. keyPressed/keyTyped are fired before searchbox is updated, even when waiting until next tick
            if (config.autoSelect()) readSearchNextTick = true;
        }
        @Override public void keyTyped(KeyEvent e) {}
    };


    private void toggle(boolean toggled) {
        if (isEnabled == toggled) return;

        isEnabled = toggled;
        if (toggled) keyManager.registerKeyListener(hotkeyListener);
        else keyManager.unregisterKeyListener(hotkeyListener);
    }

    // region fairy rings
    /**
     * @param parent container to search inside
     * @param fairyRingCodeSpaced Optional. fairy ring code in all caps separated with spaces (i.e. "A B C"). if null, first will be selected
     */
    private Widget findFairyRingWidget(Widget parent, String fairyRingCodeSpaced) {
        if (parent != null && !parent.isHidden()) {
            // if searching by code, look for the CONTENTS widget with matching text, then find the static clickable widget at the same location
            if (fairyRingCodeSpaced != null) {
                Widget[] children = parent.getChildren();
                if (children != null) {
                    // NOTE: text is always separated with spaces. fairyRingCode must be consistent (i.e. "A B C")
                    var textWidget = Arrays.stream(children).filter(w -> Objects.equals(w.getText(), fairyRingCodeSpaced)).findFirst().orElse(null);
                    if (textWidget != null) {
                        // now find the clickable widget (static with the same coordinates)
                        Widget[] staticChildren = parent.getStaticChildren();
                        var loc = textWidget.getCanvasLocation();
                        return Arrays.stream(staticChildren).filter(w -> w.getCanvasLocation().equals(loc)).findFirst().orElse(null);
                    }
                }
            }
            // if not searching by code, ignore the CONTENTS dynamic widgets, just find the first clickable one
            else {
                Widget[] children = parent.getStaticChildren();
                if (children != null) {
                    for (Widget child : children) {
                        // "HaListener" and "SpriteId" seem to be the only way to distinguish the clickable fairy ring rows from everything else in the container
                        // hasListener is true for both the row and the "fave" buttons, but the "fave" buttons have a spriteId while the rows do not
                        // there might be a better way to identify these rows? it needs to be consistent between CONTENTS and FAVES containers
                        if (child != null && !child.isHidden() && child.hasListener() && child.getSpriteId() == -1) return child;
                    }
                }
            }
        }
        return null;
    }
    // endregion


    // region events

    @Subscribe
    void onClientTick(ClientTick event) {

        String fairyRingCodeSpaced = null;
        if (selectNextTick) {
            selectNextTick = false;
            readClipboardNextTick = false;
            readSearchNextTick = false;
        }
        else {
            String searchText = null;

            if (readClipboardNextTick) {
                searchText = getClipboardText();
            }
            else if (readSearchNextTick) {
                var chatWidget = client.getWidget(InterfaceID.Chatbox.MES_TEXT2);
                if (chatWidget != null) {
                    searchText = chatWidget.getText();
                }
            }
            else return;

            selectNextTick = false;
            readSearchNextTick = false;
            readClipboardNextTick = false;

            if (searchText == null) return;

            String fairyRingCode = searchText.replaceAll("[^A-Za-z]", "").toUpperCase();
            boolean isValidFairyRing = FairyRing.forCode(fairyRingCode) != null;
            if (!isValidFairyRing) return;

            // separate the code with spaces (i.e. "ABC" -> "A B C") to be consistent with widget text
            fairyRingCodeSpaced = String.join(" ", fairyRingCode.split(""));
        }

        Widget contents = client.getWidget(InterfaceID.FairyringsLog.CONTENTS);
        if (contents == null || contents.isHidden()) return;

        // first check "FAVES" container, then check parent "CONTENTS" container
        Widget favContents = client.getWidget(InterfaceID.FairyringsLog.FAVES);
        Widget fairyRing = favContents == null || favContents.isHidden() ? null : findFairyRingWidget(favContents, fairyRingCodeSpaced);
        if (fairyRing == null) {
            fairyRing = findFairyRingWidget(contents, fairyRingCodeSpaced);
            if (fairyRing == null) return;
        }

        // click fairy ring
        client.menuAction(-1, fairyRing.getId(), net.runelite.api.MenuAction.CC_OP, 1, -1, "Use code", fairyRing.getText());

        // now click confirm
        clientThread.invokeLater(() -> {
            Widget confirmWidget = client.getWidget(398, 26);
            if (confirmWidget == null || confirmWidget.isHidden()) return false;

            client.menuAction(-1, confirmWidget.getId(), net.runelite.api.MenuAction.CC_OP, 1, -1, "Confirm", "");
            return true;
        });
    }

    @Subscribe
    void onWidgetLoaded(WidgetLoaded event) {
        if (event.getGroupId() == InterfaceID.FAIRYRINGS) toggle(true);
    }

    @Subscribe
    void onWidgetClosed(WidgetClosed event) {
        if (event.getGroupId() == InterfaceID.FAIRYRINGS) toggle(false);
    }

    //@Override protected void startUp() { toggle(true); }
    @Override protected void shutDown() { toggle(false); }

    @Subscribe
    void onConfigChanged(ConfigChanged event) {
        if (!FairyRingQolConfig.GROUP.equals(event.getGroup())) return;

        boolean wasEnabled = isEnabled;
        toggle(false);
        if (wasEnabled) toggle(true);
    }
    // endregion


    @Provides
    FairyRingQolConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(FairyRingQolConfig.class);
    }

    private String getClipboardText() {
        try {
            Transferable transferable = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
            if (transferable != null && transferable.isDataFlavorSupported(DataFlavor.stringFlavor))
                return (String) transferable.getTransferData(DataFlavor.stringFlavor);
        }
        catch (Exception ignored) { }
        return null;
    }

}
