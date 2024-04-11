package com.ghostchu.peerbanhelper.module.impl;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.config.section.ModuleActiveProbingConfigSection;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.BanResult;
import com.ghostchu.peerbanhelper.module.PeerAction;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.*;
import java.util.function.Function;

public class ActiveProbing extends AbstractFeatureModule<ModuleActiveProbingConfigSection> {

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(ActiveProbing.class);

    private final List<Function<PeerAddress, BanResult>> rules = new ArrayList<>();
    private final Cache<PeerAddress, BanResult> cache;

    private int timeout;
    private String userAgent;

    public ActiveProbing(ModuleActiveProbingConfigSection section) {
        super(section);

        this.timeout = getConfig().getTimeout();
        this.userAgent = getConfig().getHttpProbingUserAgent();
        if (userAgent == null)
            userAgent = "PeerBanHelper-PeerActiveProbing/%s (github.com/Ghost-chu/PeerBanHelper)";


        this.cache = CacheBuilder.newBuilder()
                .maximumSize(getConfig().getMaxCachedEntry())
                .expireAfterAccess(getConfig().getExpireAfterNoAccess(), TimeUnit.SECONDS)
                .build();

        for (String rule : getConfig().getProbing()) {
            if (rule.equals("PING")) {
                rules.add(this::pingPeer);
                continue;
            }
            String[] spilt = rule.split("@");
            switch (spilt[0]) {
                case "TCP" -> rules.add((address) -> tcpTestPeer(address, spilt));
                case "HTTP", "HTTPS" -> {
                    if (spilt.length < 4) {
                        log.warn(Lang.MODULE_AP_INVALID_RULE, rule);
                        continue;
                    }
                    rules.add(address -> httpTestPeer(address, spilt));
                }
                default -> log.warn(Lang.MODULE_AP_INVALID_RULE, rule);
            }
        }
    }


    @Override
    public String getName() {
        return "Active Probing";
    }

    @Override
    public BanResult shouldBanPeer(Torrent torrent, Peer peer, ExecutorService ruleExecuteExecutor) {
        PeerAddress peerAddress = peer.getAddress();
        try {
            return cache.get(peerAddress, () -> {
                List<BanResult> finishedQueue = new ArrayList<>();
                List<CompletableFuture<?>> queue = new ArrayList<>(rules.size());

                for (Function<PeerAddress, BanResult> rule : rules) {
                    queue.add(CompletableFuture.runAsync(() -> finishedQueue.add(rule.apply(peerAddress)), ruleExecuteExecutor));
                }

                try {
                    CompletableFuture.allOf(queue.toArray(new CompletableFuture[0])).get(timeout + 5, TimeUnit.MILLISECONDS);
                } catch (InterruptedException | ExecutionException | TimeoutException ignored) {
                }

                return getBanResult(finishedQueue);
            });
        } catch (ExecutionException e) {
            log.error(Lang.MODULE_AP_EXECUTE_EXCEPTION, e);
            return new BanResult(this, PeerAction.NO_ACTION, "[Runtime Exception] " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    @NotNull
    private BanResult getBanResult(List<BanResult> finishedQueue) {
        BanResult banResult = null;

        for (BanResult result : finishedQueue) {
            if (banResult == null) {
                banResult = result;
            } else {
                if (banResult.action() == PeerAction.NO_ACTION) {
                    banResult = result;
                }
            }
        }

        if (banResult == null) {
            banResult = new BanResult(this,PeerAction.NO_ACTION, "No result provided");
        }
        return banResult;
    }

    private BanResult pingPeer(PeerAddress address) {
        try {
            InetAddress add = InetAddress.getByName(address.getIp());
            if (add.isReachable(timeout)) {
                return new BanResult(this,PeerAction.BAN, Lang.MODULE_AP_PEER_BAN_PING);
            }
        } catch (IOException e) {
            return new BanResult(this,PeerAction.NO_ACTION, "Exception: " + e.getClass().getName() + e.getMessage());
        }
        return new BanResult(this,PeerAction.NO_ACTION, "No response");
    }

    private BanResult httpTestPeer(PeerAddress address, String[] spilt) {
        String scheme = spilt[0].toLowerCase(Locale.ROOT);
        String host = address.getIp();
        String port = spilt[2];
        String subpath = spilt[1];
        String exceptedCode = spilt[3];
        String url = scheme + "://" + host;
        if (StringUtils.isNotEmpty(port)) {
            url += ":" + port;
        }
        if (StringUtils.isNotEmpty(subpath)) {
            if (subpath.startsWith("/")) {
                url += subpath;
            } else {
                url += "/" + subpath;
            }
        }
        CookieManager cm = new CookieManager();
        cm.setCookiePolicy(CookiePolicy.ACCEPT_NONE);
        HttpClient.Builder builder = HttpClient
                .newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.of(timeout, ChronoUnit.MILLIS))
                .cookieHandler(cm);
        if (scheme.equals("https") && spilt.length == 5 && HTTPUtil.getIgnoreSslContext() != null) {
            builder = builder.sslContext(HTTPUtil.getIgnoreSslContext());
        }
        HttpClient client = builder.build();
        try {
            HttpResponse<String> resp = client.send(HttpRequest.newBuilder(new URI(url))
                    .GET()
                    .header("User-Agent", String.format(userAgent, Main.getMeta().getVersion()))
                    .build(), java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            String code = String.valueOf(resp.statusCode());
            if (code.equals(exceptedCode)) {
                return new BanResult(this, PeerAction.BAN, String.format(Lang.MODULE_AP_BAN_PEER_CODE, code));
            }
            return new BanResult(this,PeerAction.NO_ACTION, String.format(Lang.MODULE_AP_PEER_CODE, code));
        } catch (IOException | InterruptedException | URISyntaxException e) {
            return new BanResult(this,PeerAction.NO_ACTION, "HTTP Exception: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    private BanResult tcpTestPeer(PeerAddress address, String[] spilt) {
        try (Socket socket = new Socket()) {
            int port = Integer.parseInt(spilt[1]);
            socket.connect(new InetSocketAddress(address.getIp(), port));
            if (socket.isConnected()) {
                return new BanResult(this,PeerAction.BAN, String.format(Lang.MODULE_AP_BAN_PEER_TCP_TEST, address.getIp() + " - " + port));
            }
            return new BanResult(this,PeerAction.NO_ACTION, String.format(Lang.MODULE_AP_TCP_TEST_PORT_FAIL, "Not connected"));
        } catch (IOException e) {
            return new BanResult(this,PeerAction.NO_ACTION, String.format(Lang.MODULE_AP_TCP_TEST_PORT_FAIL, e.getClass().getName() + ": " + e.getMessage()));
        } catch (NumberFormatException e) {
            log.warn(Lang.MODULE_AP_INCORRECT_TCP_TEST_PORT, spilt[1], e.getClass().getName() + ": " + e.getMessage());
            return new BanResult(this,PeerAction.NO_ACTION, String.format(Lang.MODULE_AP_TCP_TEST_PORT_FAIL, e.getClass().getName() + ": " + e.getMessage()));
        }
    }
}
