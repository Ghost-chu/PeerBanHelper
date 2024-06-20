package com.ghostchu.peerbanhelper.module;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MatchResultDetail {
    private FeatureModule moduleContext;
    private PeerState state;
    private String rule;
    private String reason;
    private long expireTime;
}
