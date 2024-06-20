package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.database.RuleSubInfo;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.RuleUpdateType;
import com.ghostchu.peerbanhelper.module.impl.rule.RuleSubBlocker;
import com.ghostchu.peerbanhelper.module.impl.webapi.common.SlimMsg;
import com.ghostchu.peerbanhelper.module.impl.webapi.common.StdMsg;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.JsonUtil;
import com.ghostchu.peerbanhelper.web.Role;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class RuleSubController extends AbstractFeatureModule {

    RuleSubBlocker ruleSubBlocker;

    public RuleSubController(PeerBanHelperServer server, YamlConfiguration profile) {
        super(server, profile);
    }

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public @NotNull String getName() {
        return "WebAPI - Rule Subscription";
    }

    @Override
    public @NotNull String getConfigName() {
        return "webapi-rule-subscription";
    }

    @Override
    public void onEnable() {
        getServer().getModuleManager().getModules().stream().filter(ele -> ele instanceof RuleSubBlocker).findFirst().ifPresent(ele -> {
            ruleSubBlocker = (RuleSubBlocker) ele;
            getServer().getWebContainer().javalin()
                    // 查询检查间隔
                    .get("/api/sub/interval", this::getCheckInterval, Role.USER_READ)
                    // 修改检查间隔
                    .patch("/api/sub/interval", this::changeCheckInterval, Role.USER_WRITE)
                    // 新增订阅规则
                    .put("/api/sub/rule", ctx -> save(ctx, null, true), Role.USER_WRITE)
                    // 更新订阅规则
                    .post("/api/sub/rule/{ruleId}/update", ctx -> ctx.json(update(ctx.pathParam("ruleId"))), Role.USER_READ)
                    // 查询订阅规则
                    .get("/api/sub/rule/{ruleId}", ctx -> ctx.json(get(ctx.pathParam("ruleId"))), Role.USER_READ)
                    // 修改订阅规则
                    .post("/api/sub/rule/{ruleId}", ctx -> save(ctx, ctx.pathParam("ruleId"), false), Role.USER_WRITE)
                    // 删除订阅规则
                    .delete("/api/sub/rule/{ruleId}", this::delete, Role.USER_WRITE)
                    // 启用/禁用订阅规则
                    .patch("/api/sub/rule/{ruleId}", this::switcher, Role.USER_WRITE)
                    // 查询订阅规则列表
                    .get("/api/sub/rules", ctx -> ctx.json(list()), Role.USER_READ)
                    // 手动更新全部订阅规则
                    .post("/api/sub/rules/update", ctx -> ctx.json(updateAll()), Role.USER_WRITE)
                    // 查询全部订阅规则更新日志
                    .get("/api/sub/logs", ctx -> logs(ctx, null), Role.USER_READ)
                    // 查询订阅规则更新日志
                    .get("/api/sub/logs/{ruleId}", ctx -> logs(ctx, ctx.pathParam("ruleId")), Role.USER_READ);
        });
    }

    @Override
    public void onDisable() {
    }

    /**
     * 查询检查间隔
     *
     * @param ctx 上下文
     */
    private void getCheckInterval(Context ctx) {
        ctx.json(new StdMsg(true, Lang.SUB_RULE_CHECK_INTERVAL_QUERY_SUCCESS, ruleSubBlocker.getCheckInterval()));
    }

    /**
     * 修改检查间隔
     *
     * @param ctx 上下文
     */
    private void changeCheckInterval(Context ctx) {
        try {
            long interval = JsonUtil.readObject(ctx.body()).get("checkInterval").getAsLong();
            ruleSubBlocker.changeCheckInterval(interval);
            ctx.json(new SlimMsg(true, Lang.SUB_RULE_CHECK_INTERVAL_UPDATED));
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new SlimMsg(false, Lang.SUB_RULE_CHECK_INTERVAL_WRONG_PARAM));
        }
    }

    /**
     * 查询订阅规则日志
     *
     * @param ctx    上下文
     * @param ruleId 规则ID
     */
    private void logs(Context ctx, String ruleId) {
        String pageIndexStr = Objects.requireNonNullElse(ctx.queryParam("pageIndex"), "0");
        String pageSizeStr = Objects.requireNonNullElse(ctx.queryParam("pageSize"), "100");
        try {
            int pageIndex = Integer.parseInt(pageIndexStr.isEmpty() ? "0" : pageIndexStr);
            int pageSize = Integer.parseInt(pageSizeStr.isEmpty() ? "100" : pageSizeStr);
            Map<String, Object> map = new HashMap<>();
            map.put("pageIndex", pageIndex);
            map.put("pageSize", pageSize);
            map.put("results", ruleSubBlocker.queryRuleSubLogs(ruleId, pageIndex, pageSize));
            map.put("total", ruleSubBlocker.countRuleSubLogs(ruleId));
            ctx.json(new StdMsg(true, Lang.SUB_RULE_LOG_QUERY_SUCCESS, map));
        } catch (Exception e) {
            log.error(Lang.SUB_RULE_LOG_QUERY_ERROR, e);
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new SlimMsg(false, Lang.SUB_RULE_LOG_QUERY_WRONG_PARAM));
        }
    }

    /**
     * 手动更新全部订阅规则
     *
     * @return 响应
     */
    private SlimMsg updateAll() {
        AtomicReference<SlimMsg> result = new AtomicReference<>();
        ruleSubBlocker.getRuleSubsConfig().getKeys(false).stream().map(this::update).filter(ele -> !ele.success()).findFirst().ifPresentOrElse(result::set, () -> result.set(new SlimMsg(true, Lang.SUB_RULE_ALL_UPDATED)));
        return result.get();
    }

    /**
     * 手动更新订阅规则
     *
     * @param ruleId 规则ID
     * @return 响应
     */
    private SlimMsg update(String ruleId) {
        if (ruleId == null || ruleId.isEmpty()) {
            return new SlimMsg(false, Lang.SUB_RULE_NO_ID);
        }
        ConfigurationSection configurationSection = ruleSubBlocker.getRuleSubsConfig().getConfigurationSection(ruleId);
        if (null == configurationSection) {
            return new SlimMsg(false, String.format(Lang.SUB_RULE_CANT_FIND, ruleId));
        }
        return ruleSubBlocker.updateRule(configurationSection, RuleUpdateType.MANUAL);
    }

    /**
     * 启用/禁用订阅规则
     *
     * @param ctx 上下文
     */
    private void switcher(Context ctx) throws SQLException, IOException {
        String ruleId = ctx.pathParam("ruleId");
        boolean enabled;
        try {
            enabled = JsonUtil.readObject(ctx.body()).get("enabled").getAsBoolean();
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new SlimMsg(false, Lang.SUB_RULE_ENABLED_WRONG_PARAM));
            return;
        }
        RuleSubInfo ruleSubInfo = ruleSubBlocker.getRuleSubInfo(ruleId);
        if (null == ruleSubInfo) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new SlimMsg(false, String.format(Lang.SUB_RULE_CANT_FIND, ruleId)));
            return;
        }
        String msg = String.format(enabled ? Lang.SUB_RULE_ENABLED : Lang.SUB_RULE_DISABLED, ruleSubInfo.ruleName());
        if (enabled != ruleSubInfo.enabled()) {
            ConfigurationSection configurationSection = ruleSubBlocker.saveRuleSubInfo(new RuleSubInfo(ruleId, enabled, ruleSubInfo.ruleName(), ruleSubInfo.subUrl(), 0, 0));
            ruleSubBlocker.updateRule(configurationSection, RuleUpdateType.MANUAL);
            log.info(msg);
            ctx.json(new SlimMsg(true, msg));
        } else {
            // 表示已经是启用/禁用状态
            ctx.json(new SlimMsg(false, msg));
        }
    }

    /**
     * 删除订阅规则
     *
     * @param ctx 上下文
     */
    private void delete(Context ctx) throws IOException, SQLException {
        String ruleId = ctx.pathParam("ruleId");
        RuleSubInfo ruleSubInfo = ruleSubBlocker.getRuleSubInfo(ruleId);
        if (null == ruleSubInfo) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new SlimMsg(false, String.format(Lang.SUB_RULE_CANT_FIND, ruleId)));
            return;
        }
        ruleSubBlocker.deleteRuleSubInfo(ruleId);
        ruleSubBlocker.getRules().removeIf(ele -> ele.metadata().get("id").equals(ruleId));
        String msg = String.format(Lang.SUB_RULE_DELETED, ruleSubInfo.ruleName());
        log.info(msg);
        ctx.json(new SlimMsg(true, msg));
    }

    /**
     * 修改订阅规则
     *
     * @param ctx    上下文
     * @param ruleId 规则ID
     */
    private void save(Context ctx, String ruleId, boolean isAdd) throws SQLException, IOException {
        SubInfo subInfo = ctx.bodyValidator(SubInfo.class).get();
        if (isAdd) {
            // 新增时从 body 中获取ruleId
            ruleId = subInfo.ruleId();
        }
        if (ruleId == null || ruleId.isEmpty()) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new SlimMsg(false, Lang.SUB_RULE_NO_ID));
            return;
        }
        RuleSubInfo ruleSubInfo = ruleSubBlocker.getRuleSubInfo(ruleId);
        if (isAdd && ruleSubInfo != null) {
            // 新增时检查规则是否存在
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new SlimMsg(false, String.format(Lang.SUB_RULE_ID_CONFLICT, ruleId)));
            return;
        }
        if (!isAdd && ruleSubInfo == null) {
            // 更新时检查规则是否存在
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new SlimMsg(false, String.format(Lang.SUB_RULE_CANT_FIND, ruleId)));
            return;
        }
        String ruleName = subInfo.ruleName();
        String subUrl = subInfo.subUrl();
        if (isAdd) {
            if (ruleName == null || subUrl == null || ruleName.isEmpty() || subUrl.isEmpty()) {
                ctx.status(HttpStatus.BAD_REQUEST);
                ctx.json(new SlimMsg(false, Lang.SUB_RULE_PARAM_WRONG));
                return;
            }
        } else {
            if (ruleName == null) {
                ruleName = ruleSubInfo.ruleName();
            }
            if (subUrl == null) {
                subUrl = ruleSubInfo.subUrl();
            }
        }
        ConfigurationSection configurationSection = ruleSubBlocker.saveRuleSubInfo(new RuleSubInfo(ruleId, isAdd || ruleSubInfo.enabled(), ruleName, subUrl, 0, 0));
        assert configurationSection != null;
        try {
            SlimMsg msg = ruleSubBlocker.updateRule(configurationSection, RuleUpdateType.MANUAL);
            if (!msg.success()) {
                ctx.status(HttpStatus.BAD_REQUEST);
                ctx.json(msg);
                return;
            }
            ctx.json(new SlimMsg(true, Lang.SUB_RULE_SAVED));
        } catch (Exception e) {
            // 更新失败时回滚
            if (isAdd) {
                ruleSubBlocker.deleteRuleSubInfo(ruleId);
            } else {
                ruleSubBlocker.saveRuleSubInfo(ruleSubInfo);
            }
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new SlimMsg(false, String.format(Lang.SUB_RULE_URL_WRONG, ruleName)));
        }
    }

    /**
     * 查询订阅规则
     *
     * @param ruleId 规则ID
     * @return 响应
     */
    private StdMsg get(String ruleId) throws SQLException {
        return new StdMsg(true, Lang.SUB_RULE_INFO_QUERY_SUCCESS, ruleSubBlocker.getRuleSubInfo(ruleId));
    }

    /**
     * 查询订阅规则列表
     *
     * @return 响应
     */
    private StdMsg list() throws SQLException {
        List<String> list = ruleSubBlocker.getRuleSubsConfig().getKeys(false).stream().toList();
        List<RuleSubInfo> data = new ArrayList<>(list.size());
        for (String s : list) {
            data.add(ruleSubBlocker.getRuleSubInfo(s));
        }
        return new StdMsg(true, Lang.SUB_RULE_INFO_QUERY_SUCCESS, data);
    }

    /**
     * 订阅规则信息
     *
     * @param ruleId   规则ID
     * @param enabled  是否启用
     * @param ruleName 规则名称
     * @param subUrl   订阅地址
     */
    record SubInfo(String ruleId, boolean enabled, String ruleName, String subUrl) {
    }

}
