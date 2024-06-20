package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.downloader.DownloaderLastStatus;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.wrapper.PeerMetadata;
import com.ghostchu.peerbanhelper.wrapper.TorrentWrapper;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class PBHDownloaderController extends AbstractFeatureModule {
    public PBHDownloaderController(PeerBanHelperServer server, YamlConfiguration profile) {
        super(server, profile);
    }

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public @NotNull String getName() {
        return "WebAPI - Downloader API";
    }

    @Override
    public @NotNull String getConfigName() {
        return "webapi-downloader";
    }

    @Override
    public void onEnable() {
        getServer().getWebContainer().javalin()
                .get("/api/downloaders", this::handleDownloaderList, Role.USER_READ)
                .put("/api/downloaders", this::handleDownloaderPut, Role.USER_WRITE)
                .patch("/api/downloaders/{downloaderName}", ctx -> handleDownloaderPatch(ctx, ctx.pathParam("downloaderName")), Role.USER_WRITE)
                .post("/api/downloaders/test", this::handleDownloaderTest, Role.USER_WRITE)
                .delete("/api/downloaders/{downloaderName}", ctx -> handleDownloaderDelete(ctx, ctx.pathParam("downloaderName")), Role.USER_WRITE)
                .get("/api/downloaders/{downloaderName}/status", ctx -> handleDownloaderStatus(ctx, ctx.pathParam("downloaderName")), Role.USER_READ)
                .get("/api/downloaders/{downloaderName}/torrents", ctx -> handleDownloaderTorrents(ctx, ctx.pathParam("downloaderName")), Role.USER_READ)
                .get("/api/downloaders/{downloaderName}/torrent/{torrentId}/peers", ctx -> handlePeersInTorrentOnDownloader(ctx, ctx.pathParam("downloaderName"), ctx.pathParam("torrentId")), Role.USER_READ);
    }

    private void handleDownloaderPut(Context ctx) {
        JsonObject draftDownloader = JsonParser.parseString(ctx.body()).getAsJsonObject();
        String name = draftDownloader.get("name").getAsString();
        JsonObject config = draftDownloader.get("config").getAsJsonObject();
        Downloader downloader = getServer().createDownloader(name, config);
        if (downloader == null) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(Map.of("message", "Unable to create downloader, unsupported downloader type?"));
            return;
        }
        if (getServer().registerDownloader(downloader)) {
            ctx.status(HttpStatus.OK);
            ctx.json(Map.of("message", "Download created!"));
        } else {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(Map.of("message", "Unable to create downloader, same name downloader already registered!"));
        }
        try {
            getServer().saveDownloaders();
        } catch (IOException e) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
            ctx.json(Map.of("message", "Unable to create downloader, I/O error: " + e.getMessage()));
        }
    }

    private void handleDownloaderPatch(Context ctx, String downloaderName) {
        JsonObject draftDownloader = JsonParser.parseString(ctx.body()).getAsJsonObject();
        String name = draftDownloader.get("name").getAsString();
        JsonObject config = draftDownloader.get("config").getAsJsonObject();
        Downloader downloader = getServer().createDownloader(name, config);
        if (downloader == null) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(Map.of("message", "Unable to update downloader, unsupported downloader type?"));
            return;
        }
        // 可能重命名了？
        getServer().getDownloaders().stream()
                .filter(d -> d.getName().equals(downloaderName))
                .forEach(d -> getServer().unregisterDownloader(d));
        if (getServer().registerDownloader(downloader)) {
            ctx.status(HttpStatus.OK);
            ctx.json(Map.of("message", "Downloader updated!"));
        } else {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(Map.of("message", "Unable to update downloader, same name downloader already registered (and failed to remove)!"));
        }
        try {
            getServer().saveDownloaders();
        } catch (IOException e) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
            ctx.json(Map.of("message", "Unable to update downloader, I/O error: " + e.getMessage()));
        }
    }

    private void handleDownloaderTest(Context ctx) {
        JsonObject draftDownloader = JsonParser.parseString(ctx.body()).getAsJsonObject();
        String name = draftDownloader.get("name").getAsString();
        JsonObject config = draftDownloader.get("config").getAsJsonObject();
        if (getServer().getDownloaders().stream().anyMatch(d -> d.getName().equals(name))) {
            ctx.status(HttpStatus.CONFLICT);
            ctx.json(Map.of("message", "The downloader name conflict with exists downloader"));
            return;
        }
        Downloader downloader = getServer().createDownloader(name, config);
        if (downloader == null) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(Map.of("message", "Unable to create downloader, unsupported downloader type?"));
            return;
        }
        boolean testResult = downloader.login();
        ctx.status(HttpStatus.OK);
        ctx.json(Map.of("message", "Successfully to test the downloader", "test", testResult));
    }

    private void handleDownloaderDelete(Context ctx, String downloaderName) {
        Optional<Downloader> selected = getServer().getDownloaders().stream().filter(d -> d.getName().equals(downloaderName)).findFirst();
        if (selected.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND);
            ctx.json(Map.of("message", "The requested downloader not registered in PeerBanHelper configuration"));
            return;
        }
        Downloader downloader = selected.get();
        getServer().unregisterDownloader(downloader);
        try {
            getServer().saveDownloaders();
            ctx.status(HttpStatus.OK);
            ctx.json(Map.of("message", "Configuration saved!"));
        } catch (IOException e) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
            ctx.json(Map.of("message", e.getClass().getName() + ": " + e.getMessage()));
        }

    }

    private void handlePeersInTorrentOnDownloader(Context ctx, String downloaderName, String torrentId) {
        Optional<Downloader> selected = getServer().getDownloaders().stream().filter(d -> d.getName().equals(downloaderName)).findFirst();
        if (selected.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND);
            ctx.json(Map.of("message", "The requested downloader not registered in PeerBanHelper configuration"));
            return;
        }
        Downloader downloader = selected.get();
        List<PeerMetadata> peerWrappers = getServer().getLivePeersSnapshot().values()
                .stream()
                .filter(p -> p.getDownloader().equals(downloader.getName()))
                .filter(p -> p.getTorrent().getHash().equals(torrentId))
                .toList();
        ctx.status(HttpStatus.OK);
        ctx.json(peerWrappers);
    }

    private void handleDownloaderTorrents(@NotNull Context ctx, String downloaderName) {
        Optional<Downloader> selected = getServer().getDownloaders().stream()
                .filter(d -> d.getName().equals(downloaderName))
                .findFirst();
        if (selected.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND);
            ctx.json(Map.of("message", "The requested downloader not registered in PeerBanHelper configuration"));
            return;
        }
        Downloader downloader = selected.get();
        List<TorrentWrapper> torrentWrappers = getServer().getLivePeersSnapshot().values().stream().filter(p -> p.getDownloader().equals(downloader.getName()))
                .map(PeerMetadata::getTorrent)
                .distinct()
                .toList();
        ctx.status(HttpStatus.OK);
        ctx.json(torrentWrappers);
    }

    private void handleDownloaderStatus(@NotNull Context ctx, String downloaderName) {
        Optional<Downloader> selected = getServer().getDownloaders().stream()
                .filter(d -> d.getName().equals(downloaderName))
                .findFirst();
        if (selected.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND);
            ctx.json(Map.of("message", "The requested downloader not registered in PeerBanHelper configuration"));
            return;
        }
        Downloader downloader = selected.get();
        DownloaderLastStatus lastStatus = downloader.getLastStatus();
        long activeTorrents = getServer().getLivePeersSnapshot().values()
                .stream()
                .filter(p -> p.getDownloader().equals(downloader.getName()))
                .map(p -> p.getTorrent().getHash())
                .distinct().count();
        long activePeers = getServer().getLivePeersSnapshot().values()
                .stream()
                .filter(p -> p.getDownloader().equals(downloader.getName()))
                .count();

        JsonObject config = downloader.saveDownloaderJson();
        ctx.status(HttpStatus.OK);
        ctx.json(new DownloaderStatus(lastStatus, downloader.getLastStatusMessage(), activeTorrents, activePeers, config));
    }

    private void handleDownloaderList(@NotNull Context ctx) {
        List<DownloaderWrapper> downloaders = getServer().getDownloaders().stream().map(d -> new DownloaderWrapper(d.getName(), d.getEndpoint(), d.getType())).toList();
        ctx.status(HttpStatus.OK);
        ctx.json(downloaders);
    }


    @Override
    public void onDisable() {

    }

    record DraftDownloader(String name, JsonObject config) {
    }

    record DownloaderStatus(DownloaderLastStatus lastStatus, String lastStatusMessage, long activeTorrents,
                            long activePeers, JsonObject config) {

    }

    record DownloaderWrapper(String name, String endpoint, String type) {
    }
}
