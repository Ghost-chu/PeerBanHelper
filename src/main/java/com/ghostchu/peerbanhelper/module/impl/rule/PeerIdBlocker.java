package com.ghostchu.peerbanhelper.module.impl.rule;

import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.module.AbstractRuleBlocker;
import com.ghostchu.peerbanhelper.module.PeerMatchRecord;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.rule.Rule;
import com.ghostchu.peerbanhelper.util.rule.RuleMatchResult;
import com.ghostchu.peerbanhelper.util.rule.RuleParser;
import com.ghostchu.peerbanhelper.web.Role;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;

@Slf4j
public class PeerIdBlocker extends AbstractRuleBlocker {

    private List<Rule> bannedPeers;

    public PeerIdBlocker(PeerBanHelperServer server, YamlConfiguration profile) {
        super(server, profile);
    }

    @Override
    public Logger getLogger() {
        return log;
    }

    @Override
    public @NotNull String getName() {
        return "PeerId Blocker";
    }

    @Override
    public @NotNull String getConfigName() {
        return "peer-id-blacklist";
    }

    @Override
    public void init() {
        this.bannedPeers = RuleParser.parse(getConfig().getStringList("banned-peer-id"));
        getServer().getWebContainer().javalin()
                .get("/api/modules/" + getConfigName(), this::handleWebAPI, Role.USER_READ);
    }

    private void handleWebAPI(Context ctx) {
        ctx.status(HttpStatus.OK);
        ctx.json(Map.of("peerId", bannedPeers.stream().map(Rule::toPrintableText).toList()));
    }

    @Override
    public CheckResult shouldBanPeer(PeerMatchRecord ctx) {
        RuleMatchResult matchResult = RuleParser.matchRule(bannedPeers, ctx.getPeer().getPeerId());
        if (matchResult.hit()) {
            return new CheckResult(true, matchResult.rule().toString(), String.format(Lang.MODULE_PID_MATCH_PEER_ID, matchResult.rule()));
        }
        return new CheckResult(false, null, null);
    }
}
