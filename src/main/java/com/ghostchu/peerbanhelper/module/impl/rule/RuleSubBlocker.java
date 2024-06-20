package com.ghostchu.peerbanhelper.module.impl.rule;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.database.DatabaseHelper;
import com.ghostchu.peerbanhelper.database.RuleSubInfo;
import com.ghostchu.peerbanhelper.database.RuleSubLog;
import com.ghostchu.peerbanhelper.module.AbstractRuleBlocker;
import com.ghostchu.peerbanhelper.module.PeerMatchRecord;
import com.ghostchu.peerbanhelper.module.RuleUpdateType;
import com.ghostchu.peerbanhelper.module.impl.webapi.common.SlimMsg;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.IPAddressUtil;
import com.ghostchu.peerbanhelper.util.rule.MatchResult;
import com.ghostchu.peerbanhelper.util.rule.RuleType;
import com.ghostchu.peerbanhelper.util.rule.matcher.IPMatcher;
import com.ghostchu.peerbanhelper.util.rule.matcher.PrefixMatcher;
import com.ghostchu.peerbanhelper.util.rule.matcher.SubStrMatcher;
import com.github.mizosoft.methanol.MutableRequest;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import inet.ipaddr.IPAddress;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class RuleSubBlocker extends AbstractRuleBlocker {

    final private DatabaseHelper db;

    @Getter
    private long checkInterval = 86400000; // 默认24小时检查一次
    private ScheduledExecutorService scheduledExecutorService;

    public RuleSubBlocker(PeerBanHelperServer server, YamlConfiguration profile, DatabaseHelper db) {
        super(server, profile);
        this.db = db;
    }

    @Override
    public @NotNull String getName() {
        return "Rule Sub Blocker";
    }

    @Override
    public @NotNull String getConfigName() {
        return "rule-sub-blockers";
    }

    @Override
    public Logger getLogger() {
        return log;
    }

    @Override
    public void onEnable() {
        stateMachine = ruleSmBuilder().build(getConfigName());
        ConfigurationSection config = getConfig();
        // 读取检查间隔
        checkInterval = config.getLong("check-interval", checkInterval);
        scheduledExecutorService = Executors.newScheduledThreadPool(1);
        scheduledExecutorService.scheduleAtFixedRate(this::reloadConfig, 0, checkInterval, TimeUnit.MILLISECONDS);
    }

    @Override
    public void init() {
        // 读取规则
        ConfigurationSection rules = getRuleSubsConfig();
        if (null != rules) {
            for (String ruleId : rules.getKeys(false)) {
                ConfigurationSection rule = rules.getConfigurationSection(ruleId);
                assert rule != null;
                updateRule(rule, RuleUpdateType.AUTO);
            }
            log.info(Lang.SUB_RULE_UPDATE_FINISH);
        }
    }

    /**
     * Reload the configuration for this module.
     */
    private void reloadConfig() {
        if (null == rules) {
            rules = new ArrayList<>();
        }
        ConfigurationSection config = getConfig();
        // 读取检查间隔
        checkInterval = config.getLong("check-interval", checkInterval);
        init();
    }

    @Override
    public CheckResult shouldBanPeer(PeerMatchRecord ctx) {
        AtomicReference<CheckResult> result = new AtomicReference<>(new CheckResult(false, null, null));
        try (var service = Executors.newVirtualThreadPerTaskExecutor()) {
            rules.forEach(matcher -> service.submit(() -> {
                String matchStr;
                Object type = matcher.metadata().get("type");
                if (type == RuleType.PEER_ID_STARTS_WITH || type == RuleType.PEER_ID_CONTAINS) {
                    matchStr = ctx.getPeer().getPeerId();
                } else if (type == RuleType.CLIENT_NAME_STARTS_WITH || type == RuleType.CLIENT_NAME_CONTAINS) {
                    matchStr = ctx.getPeer().getClientName();
                } else {
                    matchStr = ctx.getPeer().getAddress().getIp();
                }
                if (matcher.match(matchStr) == MatchResult.TRUE) {
                    result.set(new CheckResult(true, matcher.metadata().get("rule").toString(), String.format(Lang.MODULE_IBL_MATCH_SUB_RULE, matcher.metadata().get("rule").toString())));
                }
            }));
        }
        return result.get();
    }

    /**
     * 更新规则
     *
     * @param rule 规则
     */
    public SlimMsg updateRule(@NotNull ConfigurationSection rule, RuleUpdateType updateType) {
        AtomicReference<SlimMsg> result = new AtomicReference<>();
        String ruleId = rule.getName();
        if (!rule.getBoolean("enabled", false)) {
            // 检查ipBanMatchers是否有对应的规则，有则删除
            rules.removeIf(ele -> ele.metadata().get("id").equals(ruleId));
            // 未启用跳过更新逻辑
            return new SlimMsg(false, String.format(Lang.SUB_RULE_DISABLED, ruleId));
        }
        String name = rule.getString("name", ruleId);
        String url = rule.getString("url");
        RuleType ruleType = RuleType.valueOf(Optional.ofNullable(rule.getString("type")).orElse("IP"));
        if (null != url && url.startsWith("http")) {
            // 解析远程订阅
            String ruleFileName = ruleId + ".txt";
            File dir = new File(Main.getDataDirectory(), "/sub");
            dir.mkdirs();
            File tempFile = new File(dir, "temp_" + ruleFileName);
            File ruleFile = new File(dir, ruleFileName);
            List<IPAddress> ipAddresses = new ArrayList<>();
            List<String> fileLines = new ArrayList<>();
            HTTPUtil.retryableSend(HTTPUtil.getHttpClient(false, null), MutableRequest.GET(url), HttpResponse.BodyHandlers.ofFile(Path.of(tempFile.getPath()))).whenComplete((pathHttpResponse, throwable) -> {
                if (throwable != null) {
                    tempFile.delete();
                    // 加载远程订阅文件出错,尝试从本地缓存中加载
                    if (ruleFile.exists()) {
                        // 如果一致，但ipBanMatchers没有对应的规则内容，则加载内容
                        if (rules.stream().noneMatch(ele -> ele.metadata().get("id").equals(ruleId))) {
                            try {
                                switch (ruleType) {
                                    case PEER_ID_STARTS_WITH, CLIENT_NAME_STARTS_WITH -> {
                                        fileToLines(name, ruleFile, fileLines);
                                        rules.add(new PrefixMatcher(ruleType, ruleId, name, fileLines));
                                    }
                                    case PEER_ID_CONTAINS, CLIENT_NAME_CONTAINS -> {
                                        fileToLines(name, ruleFile, fileLines);
                                        rules.add(new SubStrMatcher(ruleType, ruleId, name, fileLines));
                                    }
                                    default -> {
                                        fileToIPList(name, ruleFile, ipAddresses);
                                        rules.add(new IPMatcher(ruleId, name, ipAddresses));
                                    }
                                }
                                log.warn(Lang.SUB_RULE_USE_CACHE, name);
                                result.set(new SlimMsg(false, Lang.SUB_RULE_USE_CACHE.replace("{}", name)));
                            } catch (IOException ex) {
                                log.error(Lang.SUB_RULE_LOAD_FAILED, name, ex);
                                result.set(new SlimMsg(false, Lang.SUB_RULE_LOAD_FAILED.replace("{}", name)));
                            }
                        } else {
                            log.error(Lang.SUB_RULE_UPDATE_FAILED, name);
                            result.set(new SlimMsg(false, Lang.SUB_RULE_UPDATE_FAILED.replace("{}", name)));
                        }
                    } else {
                        // log.error(Lang.IP_BAN_RULE_LOAD_FAILED, name, throwable);
                        result.set(new SlimMsg(false, Lang.SUB_RULE_LOAD_FAILED.replace("{}", name)));
                    }
                }
                try {
                    HashCode ruleHash = null;
                    HashCode tempHash = Files.asByteSource(tempFile).hash(Hashing.sha256());
                    if (ruleFile.exists()) {
                        ruleHash = Files.asByteSource(ruleFile).hash(Hashing.sha256());
                    }
                    int ent_count = 0;
                    if (!tempHash.equals(ruleHash)) {
                        // 规则文件不存在或者规则文件与临时文件sha256不一致则需要更新
                        ent_count = switch (ruleType) {
                            case PEER_ID_STARTS_WITH, PEER_ID_CONTAINS, CLIENT_NAME_STARTS_WITH, CLIENT_NAME_CONTAINS ->
                                    fileToLines(name, ruleFile, fileLines);
                            default -> fileToIPList(name, tempFile, ipAddresses);
                        };
                        // 更新后重命名临时文件
                        ruleFile.delete();
                        tempFile.renameTo(ruleFile);
                    } else {
                        // 如果一致，但ipBanMatchers没有对应的规则内容，则加载内容
                        if (rules.stream().noneMatch(ele -> ele.metadata().get("id").equals(ruleId))) {
                            ent_count = switch (ruleType) {
                                case PEER_ID_STARTS_WITH, PEER_ID_CONTAINS, CLIENT_NAME_STARTS_WITH,
                                     CLIENT_NAME_CONTAINS -> fileToLines(name, ruleFile, fileLines);
                                default -> fileToIPList(name, tempFile, ipAddresses);
                            };
                        } else {
                            log.info(Lang.SUB_RULE_NO_UPDATE, name);
                            result.set(new SlimMsg(true, Lang.SUB_RULE_NO_UPDATE.replace("{}", name)));
                        }
                        tempFile.delete();
                    }
                    // ip列表或者subnet列表不为空代表需要更新matcher
                    if (!ipAddresses.isEmpty() || !fileLines.isEmpty()) {
                        // 如果已经存在则更新，否则添加
                        rules.stream().filter(ele -> ele.metadata().get("id").equals(ruleId)).findFirst().ifPresentOrElse(ele -> {
                            switch (ruleType) {
                                case PEER_ID_STARTS_WITH, PEER_ID_CONTAINS, CLIENT_NAME_STARTS_WITH,
                                     CLIENT_NAME_CONTAINS -> ele.setData(name, fileLines);
                                default -> ele.setData(name, ipAddresses);
                            }
                            log.info(Lang.SUB_RULE_UPDATE_SUCCESS, name);
                            result.set(new SlimMsg(true, Lang.SUB_RULE_UPDATE_SUCCESS.replace("{}", name)));
                        }, () -> {
                            switch (ruleType) {
                                case PEER_ID_STARTS_WITH, CLIENT_NAME_STARTS_WITH ->
                                        rules.add(new PrefixMatcher(ruleType, ruleId, name, fileLines));
                                case PEER_ID_CONTAINS, CLIENT_NAME_CONTAINS ->
                                        rules.add(new SubStrMatcher(ruleType, ruleId, name, fileLines));
                                default -> rules.add(new IPMatcher(ruleId, name, ipAddresses));
                            }
                            log.info(Lang.SUB_RULE_LOAD_SUCCESS, name);
                            result.set(new SlimMsg(true, Lang.SUB_RULE_LOAD_SUCCESS.replace("{}", name)));
                        });
                    }
                    if (ent_count > 0) {
                        // 更新日志
                        try {
                            db.insertRuleSubLog(ruleId, ent_count, updateType);
                            result.set(new SlimMsg(true, String.format(Lang.SUB_RULE_UPDATED, name)));
                        } catch (SQLException e) {
                            log.error(Lang.SUB_RULE_UPDATE_LOG_ERROR, ruleId, e);
                            result.set(new SlimMsg(false, Lang.SUB_RULE_UPDATE_LOG_ERROR.replace("{}", name)));
                        }
                    } else {
                        result.set(new SlimMsg(true, Lang.SUB_RULE_NO_UPDATE.replace("{}", name)));
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }).join();
        } else {
            result.set(new SlimMsg(false, String.format(Lang.SUB_RULE_URL_WRONG, name)));
        }
        return result.get();
    }

    /**
     * 读取规则文件并转为字符串列表
     *
     * @param ruleName 规则名称
     * @param ruleFile 规则文件
     * @param lines    字符串列表
     * @return 加载的行数
     */
    private int fileToLines(String ruleName, File ruleFile, List<String> lines) throws IOException {
        AtomicInteger count = new AtomicInteger();
        Files.readLines(ruleFile, StandardCharsets.UTF_8).forEach(ele -> {
            count.getAndIncrement();
            log.debug(Lang.SUB_RULE_LOAD_CONTENT, ruleName, ele);
            lines.add(ele);
        });
        return count.get();
    }

    /**
     * 读取规则文件并转为IpList
     * 其中ipv4网段地址转为精确ip
     * 考虑到ipv6分配地址通常是/64，所以ipv6网段不转为精确ip
     *
     * @param ruleName 规则名称
     * @param ruleFile 规则文件
     * @param ips      ip列表
     * @return 加载的行数
     */
    private int fileToIPList(String ruleName, File ruleFile, List<IPAddress> ips) throws IOException {
        AtomicInteger count = new AtomicInteger();
        Files.readLines(ruleFile, StandardCharsets.UTF_8).forEach(ele -> {
            count.getAndIncrement();
            log.debug(Lang.IP_BAN_RULE_LOAD_CIDR, ruleName, ele);
            ips.add(IPAddressUtil.getIPAddress(ele));
        });
        return count.get();
    }

    /**
     * 获取规则订阅配置
     *
     * @return 规则订阅配置
     */
    public ConfigurationSection getRuleSubsConfig() {
        return getConfig().getConfigurationSection("rules");
    }

    /**
     * 获取规则订阅信息
     *
     * @param ruleId 规则ID
     * @return 规则订阅信息
     */
    public RuleSubInfo getRuleSubInfo(String ruleId) throws SQLException {
        ConfigurationSection rules = getRuleSubsConfig();
        if (rules == null) {
            return null;
        }
        ConfigurationSection rule = rules.getConfigurationSection(ruleId);
        if (rule == null) {
            return null;
        }
        Optional<RuleSubLog> first = db.queryRuleSubLogs(ruleId, 0, 1).stream().findFirst();
        long lastUpdate = first.map(RuleSubLog::updateTime).orElse(0L);
        int count = first.map(RuleSubLog::count).orElse(0);
        return new RuleSubInfo(ruleId, rule.getBoolean("enabled", false), rule.getString("name", ruleId), rule.getString("url"), lastUpdate, count);
    }

    /**
     * 保存规则订阅信息并返回保存后的信息
     *
     * @param ruleSubInfo 规则订阅信息
     * @return 保存后的规则订阅信息
     * @throws IOException 保存异常
     */
    public ConfigurationSection saveRuleSubInfo(@NotNull RuleSubInfo ruleSubInfo) throws IOException {
        ConfigurationSection rules = getRuleSubsConfig();
        String ruleId = ruleSubInfo.ruleId();
        rules.set(ruleId + ".enabled", ruleSubInfo.enabled());
        rules.set(ruleId + ".name", ruleSubInfo.ruleName());
        rules.set(ruleId + ".url", ruleSubInfo.subUrl());
        saveConfig();
        return rules.getConfigurationSection(ruleId);
    }

    /**
     * 删除规则订阅信息
     *
     * @param ruleId 规则ID
     * @throws IOException 删除异常
     */
    public void deleteRuleSubInfo(String ruleId) throws IOException {
        ConfigurationSection rules = getRuleSubsConfig();
        rules.set(ruleId, null);
        saveConfig();
    }

    /**
     * 查询规则订阅日志
     *
     * @param ruleId    规则ID
     * @param pageIndex 页码
     * @param pageSize  每页数量
     * @return 规则订阅日志
     * @throws SQLException 查询异常
     */
    public List<RuleSubLog> queryRuleSubLogs(String ruleId, int pageIndex, int pageSize) throws SQLException {
        return db.queryRuleSubLogs(ruleId, pageIndex, pageSize);
    }

    /**
     * 查询规则订阅日志数量
     *
     * @param ruleId 规则ID
     * @return 规则订阅日志数量
     * @throws SQLException 查询异常
     */
    public int countRuleSubLogs(String ruleId) throws SQLException {
        return db.countRuleSubLogs(ruleId);
    }

    /**
     * 更改检查间隔
     * 会立即触发一次更新
     *
     * @param checkInterval 检查间隔
     * @throws IOException 保存异常
     */
    public void changeCheckInterval(long checkInterval) throws IOException {
        this.checkInterval = checkInterval;
        getConfig().set("check-interval", checkInterval);
        saveConfig();
        if (null != scheduledExecutorService) {
            scheduledExecutorService.shutdown();
        }
        scheduledExecutorService = Executors.newScheduledThreadPool(1, r -> Thread.ofVirtual().name("IPBlackRuleList - Update Thread").unstarted(r));
        scheduledExecutorService.scheduleAtFixedRate(this::reloadConfig, 0, checkInterval, TimeUnit.MILLISECONDS);
    }
}
