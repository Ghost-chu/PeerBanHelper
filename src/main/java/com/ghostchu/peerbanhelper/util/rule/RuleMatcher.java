package com.ghostchu.peerbanhelper.util.rule;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * 规则Matcher
 */
@Slf4j
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public abstract class RuleMatcher extends AbstractMatcher {

    private final String ruleId;

    private final RuleType ruleType;

    protected String ruleName;

    public RuleMatcher(RuleType ruleType, String ruleId, String ruleName, Object... ruleData) {
        this.ruleType = ruleType;
        this.ruleId = ruleId;
        setData(ruleName, ruleData);
    }

    public abstract void setData(String ruleName, Object... ruleData);

    public Map<String, Object> metadata() {
        return Map.of("id", ruleId, "rule", ruleName, "type", ruleType);
    }

}
