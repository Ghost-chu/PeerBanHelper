package com.ghostchu.peerbanhelper;

import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.downloader.impl.qbittorrent.QBittorrent;
import com.ghostchu.peerbanhelper.downloader.impl.transmission.Transmission;
import com.ghostchu.peerbanhelper.gui.GuiManager;
import com.ghostchu.peerbanhelper.text.Lang;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.configuration.InvalidConfigurationException;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.LogManager;

@Slf4j
public class Main {
    @Getter
    private static final File dataDirectory = new File("data");
    @Getter
    private static final File logsDirectory = new File(dataDirectory, "logs");
    @Getter
    private static final File configDirectory = new File(dataDirectory, "config");
    private static final File pluginDirectory = new File(dataDirectory, "plugins");
    @Getter
    private static BuildMeta meta = new BuildMeta();
    private static final AtomicInteger shutdown = new AtomicInteger(0);

    public static void main(String[] args) throws InterruptedException, IOException {
        GuiManager guiManager = new GuiManager(args);
        guiManager.onColorThemeChanged();
        initLogger();
        workaroundGraalVM();
        initBuildMeta();
        List<Downloader> downloaderList = new ArrayList<>();
        log.info(Lang.LOADING_CONFIG);
        try {
            if (!initConfiguration()) {
                log.warn(Lang.CONFIG_PEERBANHELPER);
                Scanner scanner = new Scanner(System.in);
                scanner.nextLine();
                return;
            }
        } catch (IOException e) {
            log.error(Lang.ERR_SETUP_CONFIGURATION, e);
            return;
        }

        YamlConfiguration mainConfig = YamlConfiguration.loadConfiguration(new File(configDirectory, "config.yml"));
        String pbhServerAddress = mainConfig.getString("server.prefix", "http://127.0.0.1:" + mainConfig.getInt("server.http"));
        ConfigurationSection clientSection = mainConfig.getConfigurationSection("client");
        for (String client : clientSection.getKeys(false)) {
            ConfigurationSection downloaderSection = clientSection.getConfigurationSection(client);
            String endpoint = downloaderSection.getString("endpoint");
            String username = downloaderSection.getString("username");
            String password = downloaderSection.getString("password");
            String baUser = downloaderSection.getString("basic-auth.user");
            String baPass = downloaderSection.getString("basic-auth.pass");
            String httpVersion = downloaderSection.getString("http-version", "HTTP_1_1");
            HttpClient.Version httpVersionEnum;
            try {
                httpVersionEnum = HttpClient.Version.valueOf(httpVersion);
            } catch (IllegalArgumentException e) {
                httpVersionEnum = HttpClient.Version.HTTP_1_1;
            }
            boolean verifySSL = downloaderSection.getBoolean("verify-ssl", true);
            switch (downloaderSection.getString("type").toLowerCase(Locale.ROOT)) {
                case "qbittorrent" -> {
                    downloaderList.add(new QBittorrent(client, endpoint, username, password, baUser, baPass, verifySSL, httpVersionEnum));
                    log.info(Lang.DISCOVER_NEW_CLIENT, "qBittorrent", client, endpoint);
                }
                case "transmission" -> {
                    downloaderList.add(new Transmission(client, endpoint, username, password, pbhServerAddress + "/blocklist/transmission", verifySSL, httpVersionEnum));
                    log.info(Lang.DISCOVER_NEW_CLIENT, "Transmission", client, endpoint);
                }
            }
        }
        PeerBanHelperServer server;
        try {
            server = new PeerBanHelperServer(downloaderList,
                    YamlConfiguration.loadConfiguration(new File(configDirectory, "profile.yml")), mainConfig);
        } catch (Exception e) {
            log.error(Lang.BOOTSTRAP_FAILED, e);
            throw new RuntimeException(e);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            synchronized (shutdown) {
                shutdown.set(1); // We're going to shutdown!
                shutdown.notifyAll();
                log.info(Lang.PBH_SHUTTING_DOWN);
                server.shutdown();
                shutdown.set(2); // We're completed shutdown!
                shutdown.notifyAll();
            }
        }));
        while (shutdown.get() != 2) {
            synchronized (shutdown) {
                shutdown.wait(1000 * 5);
            }
        }
    }

    private static void initLogger() throws IOException {
        if (!logsDirectory.exists()) {
            logsDirectory.mkdirs();
        }
        LogManager.getLogManager().readConfiguration(Main.class.getResourceAsStream("/logging.properties"));
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

    private static void workaroundGraalVM() throws InterruptedException, IOException {
        if (System.getProperties().getProperty("os.name").toUpperCase().contains("WINDOWS")) {
            if (System.getProperty("org.graalvm.nativeimage.imagecode") != null) {
                ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", "chcp", "65001").inheritIO();
                Process p = pb.start();
                p.waitFor();
                System.out.println("Chcp switched to UTF-8 (65001) - GraalVM Native Image");
            }
        }
        // 此方法允许 Native Image Agent 在生成本地二进制文件时正确识别缺少的类
        try {
            Class.forName("java.util.logging.FileHandler");
        } catch (ClassNotFoundException ignored) {
        }
    }

    private static void handleCommand(String input) {

    }

    private static boolean initConfiguration() throws IOException {
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
        return "PeerBanHelper/" + meta.getVersion()+" BTN-Protocol/0.0.0-dev";
    }

}