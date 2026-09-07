package com.uxplima.uxmlib.claim;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

/**
 * The drift guard on the provider registry. Adding a provider class and forgetting to register it is a silent
 * failure: the file compiles, the tests for it pass, and no server ever consults it. This test walks the
 * compiled package and fails when a {@code *ClaimProvider} is not reachable from
 * {@link ClaimProviders#candidateKeys()}, so the registry is the one edit and the omission cannot go quiet.
 *
 * <p>The key of a provider is its class name minus the {@code ClaimProvider} suffix, lower-cased. That is not
 * a coincidence to be relied on loosely: it is asserted here, so the operator-facing toggle key stays
 * predictable from the plugin's name.
 */
class ClaimProviderRegistryDriftTest {

    /** Neither is a claim plugin: one is the port, the other folds the members that are. */
    private static final List<String> NOT_A_CANDIDATE = List.of("ClaimProvider", "CompositeClaimProvider");

    @Test
    void everyProviderClassInThePackageIsRegistered() throws IOException, URISyntaxException {
        List<String> expected = providerClassNames().stream()
                .map(ClaimProviderRegistryDriftTest::keyOf)
                .sorted()
                .toList();

        assertThat(ClaimProviders.candidateKeys()).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void theNineteenSupportedClaimPluginsAreAllThere() throws IOException, URISyntaxException {
        assertThat(providerClassNames()).hasSize(19);
        assertThat(ClaimProviders.candidateKeys()).hasSize(19);
    }

    @Test
    void everyKeyIsLowerCaseAndUnique() {
        List<String> keys = ClaimProviders.candidateKeys();
        assertThat(keys).doesNotHaveDuplicates();
        for (String key : keys) {
            assertThat(key).isEqualTo(key.toLowerCase(Locale.ROOT));
        }
    }

    private static String keyOf(String className) {
        return className
                .substring(0, className.length() - "ClaimProvider".length())
                .toLowerCase(Locale.ROOT);
    }

    // Reads the compiled package directory rather than a hand-written list, because a hand-written list is
    // exactly the thing that drifts and this test exists to catch drift.
    //
    // The lookup goes through a main class and not through an absolute package path. The claim package exists
    // in both compiled outputs, and an absolute path resolves against the test output first, which holds only
    // *ClaimProviderTest classes: the scan then found nothing and the guard reported a registry of zero while
    // the registry was in fact complete. Asking ClaimProvider for its own class file lands in the main output
    // by construction.
    private static List<String> providerClassNames() throws IOException, URISyntaxException {
        URL packageUrl = ClaimProvider.class.getResource("ClaimProvider.class");
        assertThat(packageUrl)
                .as("the compiled claim package must be on the test classpath")
                .isNotNull();
        File directory = new File(packageUrl.toURI()).getParentFile();
        File[] files = directory.listFiles();
        assertThat(files).isNotNull();

        List<String> names = new ArrayList<>();
        for (File file : files) {
            String name = file.getName();
            // A nested class carries a '$'; only the top-level provider types are candidates.
            if (!name.endsWith("ClaimProvider.class") || name.indexOf('$') >= 0) {
                continue;
            }
            String simpleName = name.substring(0, name.length() - ".class".length());
            if (!NOT_A_CANDIDATE.contains(simpleName)) {
                names.add(simpleName);
            }
        }
        return names;
    }
}
