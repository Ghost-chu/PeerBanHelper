package com.ghostchu.peerbanhelper.util.rule;

import com.ghostchu.peerbanhelper.util.rule.matcher.*;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

@Slf4j
public class RuleParser {
    public static List<Rule> parse(@NotNull List<String> string) {
        return string.stream()
                .map(JsonParser::parseString)
                .map(RuleParser::parse)
                .toList();
    }

    public static RuleMatchResult matchRule(@NotNull List<Rule> rules, @NotNull String content) {
        RuleMatchResult matchResult = new RuleMatchResult(false, null);
        for (Rule rule : rules) {
            MatchResult result = rule.match(content);
            if (result == MatchResult.FALSE) { // 规则的优先级最高
                return new RuleMatchResult(false, rule);
            }
            if (result == MatchResult.TRUE) { // 其次，可被覆盖
                matchResult = new RuleMatchResult(true, rule);
            }
        }

        return matchResult;
    }

    @NotNull
    public static Rule parse(@Nullable JsonElement element) {
        if (element == null) {
            // 虚拟规则不记录数据
            return new Rule() {
                @Override
                public @NotNull MatchResult match(@NotNull String content) {
                    return MatchResult.TRUE;
                }

                @Override
                public Map<String, Object> metadata() {
                    return Map.of();
                }

                @Override
                public String matcherIdentifier() {
                    return "dumb:null";
                }
            };
        }
        if (element.isJsonNull()) {
            return new Rule() {
                @Override
                public @NotNull MatchResult match(@NotNull String content) {
                    return MatchResult.DEFAULT;
                }

                @Override
                public Map<String, Object> metadata() {
                    return Map.of();
                }

                @Override
                public String matcherIdentifier() {
                    return "dumb:jsonnull";
                }
            };
        }
        if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                return new Rule() {
                    @Override
                    public @NotNull MatchResult match(@NotNull String content) {
                        return primitive.getAsBoolean() ? MatchResult.TRUE : MatchResult.FALSE;
                    }

                    @Override
                    public Map<String, Object> metadata() {
                        return Map.of();
                    }

                    @Override
                    public String matcherIdentifier() {
                        return "dumb:boolean";
                    }
                };
            }
            if (primitive.isNumber()) {
                return new Rule() {
                    @Override
                    public @NotNull MatchResult match(@NotNull String content) {
                        return primitive.getAsInt() != 0 ? MatchResult.TRUE : MatchResult.FALSE;
                    }

                    @Override
                    public Map<String, Object> metadata() {
                        return Map.of();
                    }

                    @Override
                    public String matcherIdentifier() {
                        return "dumb:number";
                    }
                };
            }
            if (primitive.isString()) {
                return new Rule() {
                    @Override
                    public @NotNull MatchResult match(@NotNull String content) {
                        String str = primitive.getAsString();
                        return str.equalsIgnoreCase("true") ? MatchResult.TRUE : MatchResult.FALSE;
                    }

                    @Override
                    public Map<String, Object> metadata() {
                        return Map.of();
                    }

                    @Override
                    public String matcherIdentifier() {
                        return "dumb:boolstring";
                    }
                };
            }
            throw new IllegalArgumentException("Rule condition (primitive) only accepts boolean or integer");
        }
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException("Rule condition (jsonobject) only accepts object");
        }
        JsonObject obj = element.getAsJsonObject();
        String method = obj.get("method").getAsString();
        return switch (method) {
            case "STARTS_WITH" -> new StringStartsWithMatcher(obj);
            case "ENDS_WITH" -> new StringEndsWithMatcher(obj);
            case "CONTAINS" -> new StringContainsMatcher(obj);
            case "EQUALS" -> new StringEqualsMatcher(obj);
            case "REGEX" -> new StringRegexMatcher(obj);
            case "LENGTH" -> new StringLengthMatcher(obj);
            default -> throw new IllegalStateException("Unexpected method value: " + method);
        };
    }
}
