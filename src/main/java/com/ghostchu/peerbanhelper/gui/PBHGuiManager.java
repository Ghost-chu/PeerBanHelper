package com.ghostchu.peerbanhelper.gui;

import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.gui.impl.GuiImpl;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.logging.Level;

@Slf4j
public class PBHGuiManager implements GuiManager {
    private final GuiImpl gui;

    public PBHGuiManager(GuiImpl gui) {
        this.gui = gui;
    }

    @Override
    public void setup() {
        gui.setup();
    }


    @Override
    public boolean isGuiAvailable() {
        return Desktop.isDesktopSupported();
    }

    @Override
    public void showConfigurationSetupDialog() {
        gui.showConfigurationSetupDialog();
    }

    @Override
    public void createMainWindow() {
        gui.createMainWindow();
    }

    @Override
    public void sync() {
        gui.sync();
    }

    @Override
    public void close() {
        gui.close();
    }

    @Override
    public void onPBHFullyStarted(PeerBanHelperServer server) {
        gui.onPBHFullyStarted(server);
    }


    @Override
    public void createNotification(@NotNull Level level, @NotNull String title, @NotNull String description) {
        gui.createNotification(level, title, description);
    }

    @Override
    public void createDialog(@NotNull Level level, @NotNull String title, @NotNull String description) {
        gui.createDialog(level, title, description);
    }
}
