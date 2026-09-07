package com.uxplima.uxmlib.claim;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import com.uxplima.uxmlib.common.Log;
import org.jspecify.annotations.NullMarked;

/**
 * The operator's claim-provider choices, read once from a {@code claims} block at wiring time. Which claim
 * plugins to consult and how to fold their answers is a server-wide decision rather than a per-feature one, so
 * a consumer reads it once and hands the same value to every part of itself that asks about land.
 *
 * <p>A present claim plugin is consulted unless the operator turns it off, so a provider is enabled by
 * default and only the keys set to {@code false} under {@code claims.providers} are recorded here as disabled.
 *
 * @param disabledProviders the provider keys the operator switched off (lower-cased); an unlisted provider is on
 * @param combine how {@link CompositeClaimProvider} folds several overlapping claims' answers into one
 */
@NullMarked
public record ClaimProvidersConfig(Set<String> disabledProviders, CombineMode combine) {

    private static final String PROVIDERS_PATH = "claims.providers";
    private static final String COMBINE_PATH = "claims.combine";

    public ClaimProvidersConfig {
        Objects.requireNonNull(disabledProviders, "disabledProviders");
        Objects.requireNonNull(combine, "combine");
        disabledProviders = Set.copyOf(disabledProviders);
    }

    /** Every provider on, folded with {@link CombineMode#ANY_LAND}: the behaviour when no {@code claims} block is set. */
    public static ClaimProvidersConfig defaults() {
        return new ClaimProvidersConfig(Set.of(), CombineMode.ANY_LAND);
    }

    /**
     * Read the {@code claims} block from {@code config}, defaulting each provider on and the combine to any-land.
     *
     * <p>A key an operator writes under {@code claims.providers} that matches no registered provider (a typo like
     * {@code lnads}) disables nothing, the safe direction, but is silent, so the intended disable never takes
     * without any signal. Each unrecognised key is warned about here, naming it and the known set, so the operator
     * learns their edit had no effect. The valid keys are {@link ClaimProviders#candidateKeys()}, the same registry
     * {@link ClaimProviders#detectAll} folds, so this validation cannot drift from the provider set.
     */
    public static ClaimProvidersConfig from(ClaimSettings config, Log log) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(log, "log");
        List<String> known = ClaimProviders.candidateKeys();
        Set<String> disabled = new HashSet<>();
        for (String key : config.keys(PROVIDERS_PATH)) {
            String normalized = normalize(key);
            if (!known.contains(normalized)) {
                log.warn(
                        "event=claim_provider_unknown_key key={} known={}: no claim provider is registered under this "
                                + "claims.providers key, so it disables nothing; check the spelling",
                        key,
                        known);
                continue;
            }
            if (!config.getBoolean(PROVIDERS_PATH + "." + key, true)) {
                disabled.add(normalized);
            }
        }
        String combineToken = config.getString(COMBINE_PATH, CombineMode.ANY_LAND.configName());
        warnOnUnknownCombine(combineToken, log);
        return new ClaimProvidersConfig(disabled, CombineMode.fromConfig(combineToken));
    }

    /**
     * Warn when {@code claims.combine} is set to something neither {@code any-land} nor {@code all-land}. Left
     * unwarned, a typo like {@code all_land} silently resolves to the more permissive {@code any-land}, quietly
     * loosening a security-relevant knob: the same trap the unknown-provider-key warning guards against.
     */
    private static void warnOnUnknownCombine(String token, Log log) {
        String normalized = token.trim().toLowerCase(Locale.ROOT);
        for (CombineMode mode : CombineMode.values()) {
            if (mode.configName().equals(normalized)) {
                return;
            }
        }
        log.warn(
                "event=claim_combine_unknown value={} known=[any-land, all-land]: unrecognised claims.combine, "
                        + "falling back to any-land; check the spelling",
                token);
    }

    /** Whether the provider registered under {@code key} may be consulted; one absent from config is on by default. */
    public boolean enabled(String key) {
        Objects.requireNonNull(key, "key");
        return !disabledProviders.contains(normalize(key));
    }

    private static String normalize(String key) {
        return key.toLowerCase(Locale.ROOT);
    }

    /** How overlapping claims from several providers are folded into a single trust/ownership answer. */
    public enum CombineMode {

        /** Trusted or owner if <em>any</em> covering claim says so: the most permissive reading. */
        ANY_LAND("any-land"),

        /** Trusted or owner only if <em>every</em> covering claim says so: the strictest reading. */
        ALL_LAND("all-land");

        private final String configName;

        CombineMode(String configName) {
            this.configName = configName;
        }

        /** The lower-cased token an operator writes under {@code claims.combine}. */
        public String configName() {
            return configName;
        }

        /** Parse a {@code claims.combine} token, falling back to {@link #ANY_LAND} for anything unrecognised. */
        static CombineMode fromConfig(String raw) {
            Objects.requireNonNull(raw, "raw");
            String normalized = raw.trim().toLowerCase(Locale.ROOT);
            return ALL_LAND.configName.equals(normalized) ? ALL_LAND : ANY_LAND;
        }
    }
}
