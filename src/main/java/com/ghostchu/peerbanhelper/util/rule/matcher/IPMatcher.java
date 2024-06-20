package com.ghostchu.peerbanhelper.util.rule.matcher;

import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.IPAddressUtil;
import com.ghostchu.peerbanhelper.util.rule.MatchResult;
import com.ghostchu.peerbanhelper.util.rule.RuleMatcher;
import com.ghostchu.peerbanhelper.util.rule.RuleType;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import inet.ipaddr.IPAddress;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class IPMatcher extends RuleMatcher<IPAddress> {

    private List<IPAddress> subnets;
    private List<IPAddress> ips;
    private BloomFilter<String> bloomFilter;

    public IPMatcher(String ruleId, String ruleName, List<IPAddress> ruleData) {
        super(RuleType.IP, ruleId, ruleName, ruleData);
    }

    @Override
    public void setData(String ruleName, List<IPAddress> ruleData) {
        this.ruleName = ruleName;
        this.ips = new ArrayList<>();
        this.subnets = new ArrayList<>();
        Optional.ofNullable(ruleData).ifPresent(list -> list.forEach(ipAddress -> {
            // 判断是否是网段
            if (null != ipAddress.getNetworkPrefixLength()) {
                if (ipAddress.isIPv4Convertible() && ipAddress.getNetworkPrefixLength() >= 20) {
                    // 前缀长度 >= 20 的ipv4网段地址转为精确ip
                    ipAddress.nonZeroHostIterator().forEachRemaining(ips::add);
                } else {
                    subnets.add(ipAddress);
                    log.debug(Lang.IP_BAN_RULE_LOAD_CIDR, ruleName, ipAddress);
                }
            } else {
                ips.add(ipAddress);
            }
        }));
        bloomFilter = BloomFilter.create(Funnels.stringFunnel(StandardCharsets.UTF_8), ips.size(), 0.01);
        ips.forEach(ip -> bloomFilter.put(ip.toString()));
    }

    @Override
    public @NotNull MatchResult match0(@NotNull String content) {
        IPAddress pa = IPAddressUtil.getIPAddress(content);
        if (pa.isIPv4Convertible()) {
            pa = pa.toIPv4();
        }
        final IPAddress ip = pa;
        // 先用bloom过滤器查一下
        if (bloomFilter.mightContain(content)) {
            // 如果查到了，那么进一步验证到底是不是在黑名单中(bloom filter存在误报的可能性)
            for (IPAddress ele : ips) {
                if (ele.isIPv4Convertible() == ip.isIPv4Convertible() && ele.equals(ip)) {
                    return MatchResult.TRUE;
                }
            }
        }
        // 最后subnet表查一下
        for (IPAddress subnet : subnets) {
            if (subnet.contains(ip)) {
                return MatchResult.TRUE;
            }
        }
        return MatchResult.DEFAULT;
    }

    @Override
    public @NotNull String matcherName() {
        return Lang.RULE_MATCHER_SUB_RULE;
    }

    @Override
    public String matcherIdentifier() {
        return "peerbanhelper:ipmatcher";
    }
}
