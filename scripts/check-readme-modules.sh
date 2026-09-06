#!/usr/bin/env bash
#
# check-readme-modules.sh - module inventory guard.
#
# Fails when a module the build publishes has no row in the README module
# table, or when the table names a module the build does not have.
#
# Exit codes:
#   0  the table and the build agree
#   1  they disagree
#   2  the check itself could not run
#
# The README is this library's API inventory: a consumer deciding whether to
# take uxmLib reads it and nothing else. A module is added in three places that
# a build checks (settings.gradle.kts, its build file, its package docs) and one
# that nothing checks, so the README is the single place where a shipped module
# can stay invisible. uxmlib-bedrock and uxmlib-menu stayed invisible for
# exactly that reason, which is why this guard exists.
#
# It reads the two files as text on purpose. Asking Gradle for the project list
# would be more exact and would cost a configuration phase in CI, and the shape
# of both lines is fixed by the house layout.

set -uo pipefail

cd "$(dirname "$0")/.." || exit 2

settings="settings.gradle.kts"
readme="README.md"

for file in "$settings" "$readme"; do
    if [ ! -f "$file" ]; then
        echo "check-readme-modules: ${file} is missing" >&2
        exit 2
    fi
done

# An include line: include(":uxmlib-menu")
built="$(grep -o '":uxmlib-[a-z-]*"' "$settings" | tr -d '":' | sort -u)"

# A table row: | `uxmlib-menu` | what it gives you |
listed="$(grep -o '^| `uxmlib-[a-z-]*`' "$readme" | tr -d '|` ' | sort -u)"

if [ -z "$built" ]; then
    echo "check-readme-modules: no module found in ${settings}" >&2
    exit 2
fi

missing="$(comm -23 <(echo "$built") <(echo "$listed"))"
extra="$(comm -13 <(echo "$built") <(echo "$listed"))"

if [ -n "$missing" ] || [ -n "$extra" ]; then
    if [ -n "$missing" ]; then
        echo "The build publishes a module the README module table does not list:"
        echo "$missing" | sed 's/^/  /'
    fi
    if [ -n "$extra" ]; then
        echo "The README module table lists a module the build does not publish:"
        echo "$extra" | sed 's/^/  /'
    fi
    exit 1
fi

echo "check-readme-modules: clean"
exit 0
