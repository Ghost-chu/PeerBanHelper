package com.ghostchu.peerbanhelper.util.rule.matcher;

import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.rule.MatchResult;
import com.ghostchu.peerbanhelper.util.rule.RuleMatcher;
import com.ghostchu.peerbanhelper.util.rule.RuleType;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class PrefixMatcher extends RuleMatcher<String> {

    private List<String> prefixes;

    public PrefixMatcher(RuleType ruleType, String ruleId, String ruleName, List<String> ruleData) {
        super(ruleType, ruleId, ruleName, ruleData);
    }

    @Override
    public void setData(String ruleName, List<String> ruleData) {
        this.ruleName = ruleName;
        Optional.ofNullable(ruleData).ifPresentOrElse(list -> this.prefixes = list, () -> this.prefixes = new ArrayList<>());
    }

    @Override
    public @NotNull MatchResult match0(@NotNull String content) {
        for (String prefix : prefixes) {
            if (content.startsWith(prefix)) {
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
        return "peerbanhelper:prefixmatcher";
    }
}
