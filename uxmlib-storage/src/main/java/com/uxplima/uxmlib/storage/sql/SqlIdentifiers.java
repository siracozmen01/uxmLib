package com.uxplima.uxmlib.storage.sql;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Shared validation for the SQL fragments the builders inline literally — table/column identifiers and
 * comparison operators. Values always flow through bound {@code ?} placeholders, but identifiers and
 * operators cannot be parameters, so they are checked against a strict allowlist (a bare name or one
 * dotted qualifier, and a fixed operator set); anything else is rejected, so a name threaded from
 * untrusted input can never inject SQL. Keeping this in one place means every builder in the package
 * shares one allowlist instead of carrying its own copy.
 */
final class SqlIdentifiers {

    // A SQL identifier we are willing to inline: a bare name or a single dotted qualifier, nothing that
    // could carry an injection. Anything else must go through a hand-written statement instead.
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?");

    // The only comparison operators a builder will inline; everything else is rejected.
    private static final Set<String> OPERATORS = Set.of("=", "!=", "<>", "<", "<=", ">", ">=", "LIKE", "IS", "IS NOT");

    private SqlIdentifiers() {}

    /** Return {@code value} if it is a simple SQL identifier, else throw {@link IllegalArgumentException}. */
    static String identifier(String value, String what) {
        Objects.requireNonNull(value, what);
        if (!IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(what + " must be a simple SQL identifier (got '" + value
                    + "'); write the statement by hand for anything else");
        }
        return value;
    }

    /** Return the normalised (upper-cased) operator if it is on the allowlist, else throw. */
    static String operator(String value) {
        Objects.requireNonNull(value, "operator");
        String normalised = value.strip().toUpperCase(Locale.ROOT);
        if (!OPERATORS.contains(normalised)) {
            throw new IllegalArgumentException("unsupported operator '" + value + "'; allowed: " + OPERATORS);
        }
        return normalised;
    }
}
