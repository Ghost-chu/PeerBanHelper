package com.ghostchu.peerbanhelper.downloader.impl.transmission;

import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.downloader.DownloaderBasicAuth;
import com.ghostchu.peerbanhelper.downloader.DownloaderLastStatus;
import com.ghostchu.peerbanhelper.downloader.WebViewScriptCallback;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.util.JsonUtil;
import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import com.ghostchu.peerbanhelper.wrapper.TorrentWrapper;
import com.google.gson.JsonObject;
import cordelia.client.TrClient;
import cordelia.client.TypedResponse;
import cordelia.rpc.*;
import cordelia.rpc.types.Fields;
import cordelia.rpc.types.Status;
import cordelia.rpc.types.TorrentAction;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class Transmission implements Downloader {
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(Transmission.class);
    private final String name;
    private final TrClient client;
    private final String blocklistUrl;
    private final Config config;
    private DownloaderLastStatus lastStatus = DownloaderLastStatus.UNKNOWN;
    private String statusMessage;

    /*
            API 受限，实际实现起来意义不大

            */
    public Transmission(@NotNull String name, @NotNull String blocklistUrl, @NotNull Config config) {
        this.name = name;
        this.config = config;
        this.client = new TrClient(config.getEndpoint() + config.getRpcUrl(), config.getUsername(), config.getPassword(), config.isVerifySsl(), HttpClient.Version.valueOf(config.getHttpVersion()));
        this.blocklistUrl = blocklistUrl;
        log.warn(Lang.DOWNLOADER_TR_MOTD_WARNING);
    }

    @NotNull
    private static String generateBlocklistUrl(@NotNull String pbhServerAddress) {
        return pbhServerAddress + "/blocklist/transmission";
    }

    public static Transmission loadFromConfig(@NotNull String name, @NotNull String pbhServerAddress, @NotNull ConfigurationSection section) {
        Config config = Config.readFromYaml(section);
        return new Transmission(name, generateBlocklistUrl(pbhServerAddress), config);
    }

    public static Transmission loadFromConfig(@NotNull String name, @NotNull String pbhServerAddress, @NotNull JsonObject section) {
        Transmission.Config config = JsonUtil.getGson().fromJson(section.toString(), Transmission.Config.class);
        return new Transmission(name, generateBlocklistUrl(pbhServerAddress), config);
    }

    @Override
    public @NotNull JsonObject saveDownloaderJson() {
        return JsonUtil.getGson().toJsonTree(config).getAsJsonObject();
    }

    @Override
    public @NotNull YamlConfiguration saveDownloader() {
        return config.saveToYaml();
    }

    @Override
    public @NotNull String getEndpoint() {
        return config.getEndpoint();
    }

    @Override
    public String getWebUIEndpoint() {
        return config.getEndpoint();
    }

    @Override
    public @Nullable DownloaderBasicAuth getDownloaderBasicAuth() {
        return new DownloaderBasicAuth(config.getEndpoint(), config.getUsername(), config.getPassword());
    }

    @Override
    public @Nullable WebViewScriptCallback getWebViewJavaScript() {
        return null;
    }

    @Override
    public boolean isSupportWebview() {
        return true;
    }


    @Override
    public @NotNull String getName() {
        return name;
    }

    @Override
    public @NotNull String getType() {
        return "Transmission";
    }

    @Override
    public boolean login() {
        RqSessionGet get = new RqSessionGet();
        TypedResponse<RsSessionGet> resp = client.execute(get); // 执行任意 RPC 操作以刷新 session
        String version = resp.getArgs().getVersion();
        if (version.startsWith("0.") || version.startsWith("1.") || version.startsWith("2.")) {
            throw new IllegalStateException(String.format(Lang.DOWNLOADER_TR_KNOWN_INCOMPATIBILITY, Lang.DOWNLOADER_TR_INCOMPATIBILITY_BANAPI));
        }
        return true;
    }

    @Override
    public @NotNull List<Torrent> getTorrents() {
        RqTorrentGet torrent = new RqTorrentGet(Fields.ID, Fields.HASH_STRING, Fields.NAME, Fields.PEERS_CONNECTED, Fields.STATUS, Fields.TOTAL_SIZE, Fields.PEERS, Fields.RATE_DOWNLOAD, Fields.RATE_UPLOAD, Fields.PEER_LIMIT, Fields.PERCENT_DONE);
        TypedResponse<RsTorrentGet> rsp = client.execute(torrent);
        return rsp.getArgs().getTorrents().stream()
                .filter(t -> t.getStatus() == Status.DOWNLOADING || t.getStatus() == Status.SEEDING)
                .map(TRTorrent::new).collect(Collectors.toList());
    }

    @Override
    public @NotNull List<Peer> getPeers(@NotNull Torrent torrent) {
        TRTorrent trTorrent = (TRTorrent) torrent;
        return trTorrent.getPeers();
    }


    @SneakyThrows
    @Override
    public void setBanList(@NotNull Collection<PeerAddress> fullList, @Nullable Collection<BanMetadata> added, @Nullable Collection<BanMetadata> removed) {
        RqSessionSet set = RqSessionSet.builder()
                .blocklistUrl(blocklistUrl + "?t=" + System.currentTimeMillis()) // 更改 URL 来确保更改生效
                .blocklistEnabled(true)
                .build();
        TypedResponse<RsSessionGet> sessionSetResp = client.execute(set);
        if (!sessionSetResp.isSuccess()) {
            log.warn(Lang.DOWNLOADER_TR_INCORRECT_BANLIST_API_RESP, sessionSetResp.getResult());
        }
        Thread.sleep(3000); // Transmission 在这里疑似有崩溃问题？
        RqBlockList updateBlockList = new RqBlockList();
        TypedResponse<RsBlockList> updateBlockListResp = client.execute(updateBlockList);
        if (!updateBlockListResp.isSuccess()) {
            log.warn(Lang.DOWNLOADER_TR_INCORRECT_SET_BANLIST_API_RESP);
        } else {
            log.info(Lang.DOWNLOADER_TR_UPDATED_BLOCKLIST, updateBlockListResp.getArgs().getBlockListSize());
        }
    }


    @Override
    public void relaunchTorrentIfNeeded(@NotNull Collection<Torrent> torrents) {
        relaunchTorrents(torrents.stream().map(t -> Long.parseLong(t.getId())).toList());
    }

    private void relaunchTorrents(@NotNull Collection<Long> ids) {
        if (ids.isEmpty()) return;
        log.info(Lang.DOWNLOADER_TR_DISCONNECT_PEERS, ids.size());
        RqTorrent stop = new RqTorrent(TorrentAction.STOP, new ArrayList<>());
        for (long torrent : ids) {
            stop.add(torrent);
        }
        client.execute(stop);
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        RqTorrent resume = new RqTorrent(TorrentAction.START, new ArrayList<>());
        for (long torrent : ids) {
            resume.add(torrent);
        }
        client.execute(resume);
    }

    @Override
    public void relaunchTorrentIfNeededByTorrentWrapper(@NotNull Collection<TorrentWrapper> torrents) {
        relaunchTorrents(torrents.stream().map(t -> Long.parseLong(t.getId())).toList());
    }

    @Override
    public @NotNull DownloaderLastStatus getLastStatus() {
        return lastStatus;
    }

    @Override
    public void setLastStatus(@NotNull DownloaderLastStatus lastStatus, @NotNull String statusMessage) {
        this.lastStatus = lastStatus;
        this.statusMessage = statusMessage;
    }

    @Override
    public String getLastStatusMessage() {
        return statusMessage;
    }


    @Override
    public void close() {
        client.shutdown();
    }

    @NoArgsConstructor
    @Data
    public static class Config {

        private String type;
        private String endpoint;
        private String username;
        private String password;
        private String httpVersion;
        private boolean verifySsl;
        private String rpcUrl;

        @NotNull
        public static Transmission.Config readFromYaml(@NotNull ConfigurationSection section) {
            Transmission.Config config = new Transmission.Config();
            config.setType("transmission");
            config.setEndpoint(section.getString("endpoint"));
            if (config.getEndpoint().endsWith("/")) { // 浏览器复制党 workaround 一下， 避免连不上的情况
                config.setEndpoint(config.getEndpoint().substring(0, config.getEndpoint().length() - 1));
            }
            config.setUsername(section.getString("username"));
            config.setPassword(section.getString("password"));
            config.setRpcUrl(section.getString("rpc-url", "transmission/rpc"));
            config.setHttpVersion(section.getString("http-version", "HTTP_1_1"));
            config.setVerifySsl(section.getBoolean("verify-ssl", true));
            return config;
        }

        @NotNull
        public YamlConfiguration saveToYaml() {
            YamlConfiguration section = new YamlConfiguration();
            section.set("type", "qbittorrent");
            section.set("endpoint", endpoint);
            section.set("username", username);
            section.set("password", password);
            section.set("rpc-url", rpcUrl);
            section.set("http-version", httpVersion);
            section.set("verify-ssl", verifySsl);
            return section;
        }
    }
}
