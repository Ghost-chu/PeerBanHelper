package com.ghostchu.peerbanhelper.gui.impl.console;

import com.ghostchu.peerbanhelper.gui.impl.GuiImpl;
import com.ghostchu.peerbanhelper.text.Lang;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

@Slf4j
public class ConsoleGuiImpl implements GuiImpl {
    private final AtomicBoolean wakeLock = new AtomicBoolean(false);

    public ConsoleGuiImpl(String[] args) {
    }

    @Override
    public void showConfigurationSetupDialog() {
        log.info(Lang.CONFIG_PEERBANHELPER);
        Scanner scanner = new Scanner(System.in);
        scanner.nextLine();
    }

    @Override
    public void setup() {
        // do nothing
    }

    @Override
    public void createMainWindow() {

    }

    @SneakyThrows
    @Override
    public void sync() {
        while (!wakeLock.get()) {
            synchronized (wakeLock) {
                wakeLock.wait(1000 * 5);
            }
        }
    }

    @Override
    public void close() {
        synchronized (wakeLock) {
            wakeLock.set(true);
            wakeLock.notifyAll();
        }
    }

    @Override
    public void createNotification(@NotNull Level level, @NotNull String title, @NotNull String description) {
        if (level.equals(Level.INFO)) {
            log.info("{}: {}", title, description);
        }
        if (level.equals(Level.WARNING)) {
            log.warn("{}: {}", title, description);
        }
        if (level.equals(Level.SEVERE)) {
            log.error("{}: {}", title, description);
        }
    }

    @Override
    public void createDialog(@NotNull Level level, @NotNull String title, @NotNull String description) {
        if (level.equals(Level.INFO)) {
            log.info("{}: {}", title, description);
        }
        if (level.equals(Level.WARNING)) {
            log.warn("{}: {}", title, description);
        }
        if (level.equals(Level.SEVERE)) {
            log.error("{}: {}", title, description);
        }
    }


}
