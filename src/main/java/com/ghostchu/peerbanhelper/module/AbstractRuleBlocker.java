package com.ghostchu.peerbanhelper.module;

import com.alibaba.cola.statemachine.StateMachine;
import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.util.rule.Rule;
import lombok.Getter;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;

import java.util.List;

public abstract class AbstractRuleBlocker extends AbstractFeatureModule implements RuleBlocker {

    @Getter
    public List<Rule> rules;

    public StateMachine<PeerState, MatchEvents, PeerMatchRecord> stateMachine;

    public AbstractRuleBlocker(PeerBanHelperServer server, YamlConfiguration profile) {
        super(server, profile);
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public void onEnable() {
        stateMachine = ruleSmBuilder().build(getConfigName());
        init();
    }

    @Override
    public void onDisable() {
    }

    @Override
    public StateMachine<PeerState, MatchEvents, PeerMatchRecord> getStateMachine() {
        return stateMachine;
    }

    public abstract void init();
}
