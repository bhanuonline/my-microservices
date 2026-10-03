package com.example.common.featureflag;

import java.util.List;

/**
 * Parsed representation of {@link FeatureFlag#getRulesJson()}.
 * Defaults: percentage=0 (nobody), empty whitelists.
 */
public record FlagRules(
        int percentage,
        List<String> tenantWhitelist,
        List<String> userWhitelist
) {
    public static FlagRules empty() {
        return new FlagRules(0, List.of(), List.of());
    }
}
