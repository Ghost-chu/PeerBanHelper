package com.ghostchu.peerbanhelper;

import com.alessiodp.libby.LibraryManager;
import com.alessiodp.libby.logging.LogLevel;
import com.ghostchu.peerbanhelper.config.MainConfigUpdateScript;
import com.ghostchu.peerbanhelper.config.PBHConfigUpdater;
import com.ghostchu.peerbanhelper.config.ProfileUpdateScript;
import com.ghostchu.peerbanhelper.event.PBHShutdownEvent;
import com.ghostchu.peerbanhelper.gui.PBHGuiManager;
import com.ghostchu.peerbanhelper.gui.impl.console.ConsoleGuiImpl;
import com.ghostchu.peerbanhelper.gui.impl.javafx.JavaFxImpl;
import com.ghostchu.peerbanhelper.gui.impl.swing.SwingGuiImpl;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.PBHLibrariesLoader;
import com.ghostchu.peerbanhelper.util.Slf4jLogAppender;
import com.google.common.eventbus.EventBus;
import com.google.common.io.ByteStreams;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.core.config.plugins.util.PluginManager;
import org.bspfsystems.yamlconfiguration.configuration.InvalidConfigurationException;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.logging.Level;

@Slf4j
public class Main {
    @Getter
    private static File dataDirectory;
    @Getter
    private static File logsDirectory;
    @Getter
    private static File configDirectory;
    private static File pluginDirectory;
    @Getter
    private static BuildMeta meta = new BuildMeta();
    @Getter
    private static PeerBanHelperServer server;
    @Getter
    private static PBHGuiManager guiManager;
    @Getter
    private static final EventBus eventBus = new EventBus();
    @Getter
    private static File mainConfigFile;
    @Getter
    private static File profileConfigFile;
    @Getter
    private static LibraryManager libraryManager;
    @Getter
    private static PBHLibrariesLoader librariesLoader;

    public static void main(String[] args) {
        setupConfDirectory(args);
        setupLog4j2();
        setupProxySettings();
        libraryManager = new PBHLibraryManager(
                new Slf4jLogAppender(),
                dataDirectory.toPath(), "libraries"
        );
        Path librariesPath = dataDirectory.toPath().toAbsolutePath().resolve("libraries");
        libraryManager.setLogLevel(LogLevel.ERROR);
        librariesLoader = new PBHLibrariesLoader(libraryManager, librariesPath);
        initBuildMeta();
        initGUI(args);
        setupConfiguration();
        guiManager.createMainWindow();

        mainConfigFile = new File(configDirectory, "config.yml");
        YamlConfiguration mainConfig = loadConfiguration(mainConfigFile);
        new PBHConfigUpdater(mainConfigFile, mainConfig).update(new MainConfigUpdateScript(mainConfig));
        profileConfigFile = new File(configDirectory, "profile.yml");
        YamlConfiguration profileConfig = loadConfiguration(profileConfigFile);
        new PBHConfigUpdater(profileConfigFile, profileConfig).update(new ProfileUpdateScript(profileConfig));
        String pbhServerAddress = mainConfig.getString("server.prefix", "http://127.0.0.1:" + mainConfig.getInt("server.http"));
        try {
            server = new PeerBanHelperServer(pbhServerAddress, profileConfig, mainConfig);
        } catch (Exception e) {
            log.error(Lang.BOOTSTRAP_FAILED, e);
            throw new RuntimeException(e);
        }
        guiManager.onPBHFullyStarted(server);
        setupShutdownHook();
        guiManager.sync();
    }

    private static void setupProxySettings() {
        if (System.getenv("http_proxy") != null || System.getenv("HTTP_PROXY") != null) {
            log.warn(Lang.ALERT_INCORRECT_PROXY_SETTING);
        }
    }

    private static void setupConfDirectory(String[] args) {
        String osName = System.getProperty("os.name");
        String root = "data";
        if ("true".equalsIgnoreCase(System.getProperty("pbh.usePlatformConfigLocation"))) {
            if (osName.contains("Windows")) {
                root = new File(System.getenv("LOCALAPPDATA"), "PeerBanHelper").getAbsolutePath();
            } else {
                root = new File(System.getProperty("user.home"), ".config/PeerBanHelper").getAbsolutePath();
            }
        }
        if (System.getProperty("pbh.datadir") != null) {
            root = System.getProperty("pbh.datadir");
        }
        dataDirectory = new File(root);
        logsDirectory = new File(dataDirectory, "logs");
        configDirectory = new File(dataDirectory, "config");
        pluginDirectory = new File(dataDirectory, "plugins");
    }

    private static void setupLog4j2() {
        PluginManager.addPackage("com.ghostchu.peerbanhelper.log4j2");
    }

    private static YamlConfiguration loadConfiguration(File file) {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.getOptions()
                .setParseComments(true)
                .setWidth(1000);
        try {
            configuration.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            log.error(Lang.CONFIGURATION_INVALID, file);
            guiManager.createDialog(Level.SEVERE, Lang.CONFIGURATION_INVALID_TITLE, String.format(Lang.CONFIGURATION_INVALID_DESCRIPTION, file));
            System.exit(1);
        }
        return configuration;
    }

    private static void setupConfiguration() {
        log.info(Lang.LOADING_CONFIG);
        try {
            if (!initConfiguration()) {
                guiManager.showConfigurationSetupDialog();
                System.exit(0);
            }
        } catch (IOException e) {
            log.error(Lang.ERR_SETUP_CONFIGURATION, e);
            System.exit(0);
        }
    }

    private static void setupShutdownHook() {
        Thread shutdownThread = new Thread(() -> {
            try {
                log.info(Lang.PBH_SHUTTING_DOWN);
                eventBus.post(new PBHShutdownEvent());
                server.shutdown();
                guiManager.close();
            } catch (Throwable th) {
                th.printStackTrace();
            }
        });
        shutdownThread.setDaemon(false);
        shutdownThread.setName("ShutdownThread");
        Runtime.getRuntime().addShutdownHook(shutdownThread);
    }

    private static void initGUI(String[] args) {
        String guiType = "javafx";
        if (!Desktop.isDesktopSupported() || System.getProperty("pbh.nogui") != null || Arrays.stream(args).anyMatch(arg -> arg.equalsIgnoreCase("nogui"))) {
            guiType = "console";
        } else if (Arrays.stream(args).anyMatch(arg -> arg.equalsIgnoreCase("swing"))) {
            guiType = "swing";
        }
        if ("javafx".equals(guiType)) {
            try {
                if (!loadJavaFxDependencies()) {
                    guiType = "swing";
                }
            } catch (IOException e) {
                log.warn("Failed to load JavaFx dependencies", e);
                guiType = "swing";
            }
        }
        switch (guiType) {
            case "javafx" -> guiManager = new PBHGuiManager(new JavaFxImpl(args));
            case "swing" -> guiManager = new PBHGuiManager(new SwingGuiImpl(args));
            case "console" -> guiManager = new PBHGuiManager(new ConsoleGuiImpl(args));
        }
        guiManager.setup();
    }

    private static boolean loadJavaFxDependencies() throws IOException {
        try (var is = Main.class.getResourceAsStream("/libraries/javafx.maven")) {
            String str = new String(ByteStreams.toByteArray(is), StandardCharsets.UTF_8);
            String[] libraries = str.split("\n");
            String osName = System.getProperty("os.name").toLowerCase();
            String sysArch = "win";
            if (osName.contains("linux")) {
                sysArch = "linux";
            } else if (osName.contains("mac")) {
                sysArch = "mac";
            }
            try {
                librariesLoader.loadLibraries(Arrays.stream(libraries).toList(), Map.of("system.platform", sysArch, "javafx.version", meta.getJavafx()));
                return true;
            } catch (Exception e) {
                log.warn("Unable to load JavaFx dependencies", e);
                return false;
            }
        }

    }

    private static void initBuildMeta() {
        meta = new BuildMeta();
        try (InputStream stream = Main.class.getResourceAsStream("/build-info.yml")) {
            if (stream == null) {
                log.error(Lang.ERR_BUILD_NO_INFO_FILE);
            } else {
                String str = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                YamlConfiguration configuration = new YamlConfiguration();
                configuration.loadFromString(str);
                meta.loadBuildMeta(configuration);
            }
        } catch (IOException | InvalidConfigurationException e) {
            log.error(Lang.ERR_CANNOT_LOAD_BUILD_INFO, e);
        }
        log.info(Lang.MOTD, meta.getVersion());
    }

    private static void handleCommand(String input) {

    }

    private static boolean initConfiguration() throws IOException {
        log.info("PeerBanHelper data directory: {}", dataDirectory.getAbsolutePath());
        if (!dataDirectory.exists()) {
            configDirectory.mkdirs();
        }
        if (!configDirectory.exists()) {
            configDirectory.mkdirs();
        }
        if (!configDirectory.isDirectory()) {
            throw new IllegalStateException(Lang.ERR_CONFIG_DIRECTORY_INCORRECT);
        }
        if (!pluginDirectory.exists()) {
            pluginDirectory.mkdirs();
        }
        boolean exists = true;
        File config = new File(configDirectory, "config.yml");
        File profile = new File(configDirectory, "profile.yml");
        if (!config.exists()) {
            exists = false;
            Files.copy(Main.class.getResourceAsStream("/config.yml"), config.toPath());
        }
        if (!profile.exists()) {
            exists = false;
            Files.copy(Main.class.getResourceAsStream("/profile.yml"), profile.toPath());
        }
        return exists;
    }


    public static String getUserAgent() {
        return "PeerBanHelper/" + meta.getVersion() + " BTN-Protocol/0.0.0-dev";
    }

}