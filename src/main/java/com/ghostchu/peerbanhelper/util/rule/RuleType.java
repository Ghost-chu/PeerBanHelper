package com.ghostchu.peerbanhelper.util.rule;

/**
 * 订阅规则类型
 */
public enum RuleType {
    /**
     * IP/CIDR
     */
    IP,
    /**
     * peer-id子串
     */
    PEER_ID_CONTAINS,
    /**
     * peer-id前缀
     */
    PEER_ID_STARTS_WITH,
    /**
     * client-name子串
     */
    CLIENT_NAME_CONTAINS,
    /**
     * client-name前缀
     */
    CLIENT_NAME_STARTS_WITH
}
