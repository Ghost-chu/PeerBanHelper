package com.ghostchu.peerbanhelper.module;

import com.alibaba.cola.statemachine.StateMachine;
import com.alibaba.cola.statemachine.builder.StateMachineBuilder;
import com.alibaba.cola.statemachine.builder.StateMachineBuilderFactory;
import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.text.Lang;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * 基于状态机模型的规则模块接口
 */
public interface RuleBlocker extends FeatureModule {

    /**
     * 规则状态机构造器
     *
     * @return 状态机构造器
     */
    default StateMachineBuilder<PeerState, MatchEvents, PeerMatchContext> ruleSmBuilder() {
        StateMachineBuilder<PeerState, MatchEvents, PeerMatchContext> fsmBuilder = StateMachineBuilderFactory.create();
        fsmBuilder.externalTransitions().fromAmong(PeerState.INIT, PeerState.ACTIVE).to(PeerState.ACTIVE).on(MatchEvents.PASS)
                .perform((from, to, event, context) -> {
                    // 活跃
                    Optional.ofNullable(context.getActiveFunc()).ifPresent(func -> func.accept(context.getRecord()));
                    // 更新过期时间
                    context.getRecord().getResult().setState(to);
                    context.getRecord().getResult().setExpireTime(System.currentTimeMillis() + getServer().getDisconnectTimeout());
                });
        fsmBuilder.externalTransitions().fromAmong(PeerState.INIT, PeerState.ACTIVE).to(PeerState.BAN).on(MatchEvents.HIT)
                .perform((from, to, event, context) -> {
                    // 封禁
                    getLogger().debug(Lang.RULE_MODULE_PEER_HIT, getName(), context.getRecord().getPeer().getAddress());
                    // 执行封禁操作
                    Optional.ofNullable(context.getBanFunc()).ifPresent(func -> func.accept(context.getRecord()));
                    // 更新匹配结果
                    context.getRecord().getResult().setModuleContext(this);
                    context.getRecord().getResult().setState(to);
                    context.getRecord().getResult().setExpireTime(System.currentTimeMillis() + getServer().getBanDuration());
                });
        fsmBuilder.externalTransitions().fromAmong(PeerState.INIT, PeerState.ACTIVE).to(PeerState.END).on(MatchEvents.DISCONNECT)
                .perform((from, to, event, context) -> {
                    // 断开连接
                    getLogger().debug(Lang.RULE_MODULE_PEER_DISCONNECT, getName(), context.getRecord().getPeer().getAddress());
                    // 执行断开操作
                    Optional.ofNullable(context.getDisconnectFunc()).ifPresent(func -> func.accept(context.getRecord()));
                    // 更新匹配结果
                    context.getRecord().setResult(new MatchResultDetail(this, to, null, null, 0));
                });
        fsmBuilder.externalTransition().from(PeerState.BAN).to(PeerState.END).on(MatchEvents.TIMEOUT)
                .perform((from, to, event, context) -> {
                    getLogger().debug(Lang.RULE_MODULE_PEER_BAN_TIMEOUT, getName(), context.getRecord().getPeer().getAddress());
                    // 执行解禁操作
                    Optional.ofNullable(context.getTimeoutFunc()).ifPresent(func -> func.accept(context.getRecord()));
                    // 更新匹配结果
                    context.getRecord().setResult(new MatchResultDetail(this, to, null, null, 0));
                });
        return fsmBuilder;
    }

    /**
     * 获取Server
     *
     * @return Server
     */
    PeerBanHelperServer getServer();

    /**
     * 获取Logger
     *
     * @return Logger
     */
    Logger getLogger();

    /**
     * 获取状态机
     *
     * @return 状态机
     */
    StateMachine<PeerState, MatchEvents, PeerMatchContext> getStateMachine();

    /**
     * 是否应该封禁Peer
     *
     * @param ctx 匹配上下文
     * @return 是否封禁
     */
    CheckResult shouldBanPeer(PeerMatchRecord ctx);

    /**
     * 运行规则检查
     */
    default void runCheck(Consumer<PeerMatchRecord> activeFunc, Consumer<PeerMatchRecord> banFunc, Consumer<PeerMatchRecord> disconnectFunc, Consumer<PeerMatchRecord> timeoutFunc) {
        // long t1 = System.currentTimeMillis();
        List<String> removeLIst = new ArrayList<>();
        Map<String, PeerMatchRecord> matchRecords = getServer().getMatchRecords();
        matchRecords.forEach((key, record) -> {
            // 构建匹配上下文
            PeerMatchContext context = new PeerMatchContext(record, activeFunc, banFunc, disconnectFunc, timeoutFunc);
            MatchResultDetail result = record.getResult();
            switch (result.getState()) {
                case INIT, ACTIVE:
                    if (result.getExpireTime() < System.currentTimeMillis()) {
                        // 已经超时的记录直接归到END状态
                        triggerEvent(MatchEvents.DISCONNECT, context);
                        break;
                    }
                    // 判断是否需要封禁
                    CheckResult banResult = shouldBanPeer(record);
                    if (banResult.hit()) {
                        // 命中
                        context.getRecord().getResult().setRule(banResult.rule());
                        context.getRecord().getResult().setReason(banResult.reason());
                        triggerEvent(MatchEvents.HIT, context);
                    } else {
                        // 通过
                        triggerEvent(MatchEvents.PASS, context);
                    }
                    break;
                case BAN:
                    if (result.getExpireTime() < System.currentTimeMillis()) {
                        // 已经超时的记录直接归到END状态
                        triggerEvent(MatchEvents.TIMEOUT, context);
                    }
                    break;
                default:
                    // END状态直接移除
                    removeLIst.add(key);
                    break;
            }
        });
        removeLIst.forEach(matchRecords::remove);
        // long t2 = System.currentTimeMillis();
        // getLogger().debug(Lang.RULE_MODULE_MATCH_TIME, getName(), t2 - t1);
    }

    /**
     * 触发事件
     *
     * @param event   事件
     * @param context 上下文
     */
    default void triggerEvent(MatchEvents event, PeerMatchContext context) {
        getStateMachine().fireEvent(context.getRecord().getResult().getState(), event, context);
    }

    record CheckResult(boolean hit, String rule, String reason) {

    }

}
