package com.ghostchu.peerbanhelper.database;

import com.ghostchu.peerbanhelper.module.RuleUpdateType;

public record RuleSubLog(
        String ruleId,
        long updateTime,
        int count,
        RuleUpdateType updateType
) {
}
