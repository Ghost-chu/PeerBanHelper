package com.ghostchu.peerbanhelper.util.time;

import lombok.Getter;

public enum ExceptedTime {
    RECOVER_PERSISTENT_BAN_LIST(30 * 1000),
    COLLECT_PEERS(30 * 1000),
    UPDATE_LIVE_PEERS(30 * 1000),
    CHECK_BANS(30 * 1000),
    ADD_BAN_ENTRY(30 * 1000),
    APPLY_BANLIST(30 * 1000),
    STAGE_BAN_WAVE(COLLECT_PEERS.timeout + CHECK_BANS.timeout + ADD_BAN_ENTRY.timeout + APPLY_BANLIST.timeout + 10 * 1000),
    RUN_BLOCKER(30 * 1000),
    BAN_PEER(3 * 1000);

    @Getter
    private final long timeout;

    ExceptedTime(long timeout) {
        this.timeout = timeout;
    }
}
