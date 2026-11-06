#!/usr/bin/env bash
# =============================================================================
# W49 (OD-048) — validate the GitHub Pages configuration BEFORE publishing.
#
# WHAT THIS IS
#   The Pages site is built from `docs-v2/` by Jekyll and published from the
#   default branch. Before W49 the build succeeded and published anyway, and
#   the site carried a `url` value that pointed at a host that does not serve
#   it: the deployment URL was `https://alexandernarbaev.github.io/agi/` and
#   the configuration said `https://alexandr-narbaev.github.io`. Every
#   canonical link the site generated was therefore wrong, and nothing failed.
#
#   This gate is the check that would have caught it. It is deliberately
#   narrow: it checks the facts that are cheap to check and expensive to get
#   wrong — the deployment URL, the base path, the navigation's language
#   companion, and the stale positioning strings.
#
#   The deployment URL is NOT guessed. It was read from the GitHub API:
#     gh api repos/AlexanderNarbaev/agi/pages  →  "html_url"
#   and it is recorded here as a constant. If the deployment moves, the
#   constant changes in the same commit that changes the deployment.
#
# USAGE
#   bash scripts/validate-pages-config.sh
#
# EXIT CODE
#   0 — the configuration matches the deployment
#   1 — it does not, and the site must not be published
# =============================================================================

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONFIG_FILE="${REPO_ROOT}/docs-v2/_config.yml"

# The recorded deployment URL and its base path, read from the GitHub API.
readonly EXPECTED_URL="https://alexandernarbaev.github.io"
readonly EXPECTED_BASEURL="/agi"
readonly EXPECTED_SITE_URL="${EXPECTED_URL}${EXPECTED_BASEURL}"

# The positioning strings that W49 removed. A reappearance is a regression.
readonly STALE_TITLE="MATRIX Encyclopedia"
readonly STALE_DESCRIPTION="The AI That Thinks Like a Brain"
readonly STALE_FOOTER="Hybrid Intelligence"

failures=0

fail() {
    printf '  PAGES-1 %s\n' "$1" >&2
    failures=$((failures + 1))
}

read_config_value() {
    # $1 — the key. Prints the value, or nothing when the key is absent.
    sed -n "s/^${1}:[[:space:]]*\"\{0,1\}\([^\"#]*\)\"\{0,1\}[[:space:]]*$/\1/p" \
        "${CONFIG_FILE}" | head -1 | sed 's/[[:space:]]*$//'
}

if [ ! -f "${CONFIG_FILE}" ]; then
    printf 'PAGES-1 the Pages configuration is missing: %s\n' "${CONFIG_FILE}" >&2
    exit 1
fi

actual_url="$(read_config_value url)"
actual_baseurl="$(read_config_value baseurl)"
actual_title="$(read_config_value title)"
actual_description="$(read_config_value description)"

if [ "${actual_url}" != "${EXPECTED_URL}" ]; then
    fail "the configured url is '${actual_url}', the deployment serves '${EXPECTED_URL}'"
fi
if [ "${actual_baseurl}" != "${EXPECTED_BASEURL}" ]; then
    fail "the configured baseurl is '${actual_baseurl}', the deployment serves '${EXPECTED_BASEURL}'"
fi
if [ "${actual_url}${actual_baseurl}" != "${EXPECTED_SITE_URL}" ]; then
    fail "url + baseurl is '${actual_url}${actual_baseurl}', the deployed site is '${EXPECTED_SITE_URL}'"
fi
if [ -n "${actual_title}" ] && printf '%s' "${actual_title}" | grep -qF "${STALE_TITLE}"; then
    fail "the title still carries the withdrawn positioning: ${actual_title}"
fi
if [ -n "${actual_description}" ] && printf '%s' "${actual_description}" | grep -qF "${STALE_DESCRIPTION}"; then
    fail "the description still carries the withdrawn positioning: ${actual_description}"
fi
if grep -qF "${STALE_FOOTER}" "${CONFIG_FILE}"; then
    fail "the footer still carries the withdrawn positioning: ${STALE_FOOTER}"
fi

# The navigation's language companion must exist: the site has an English and
# a Russian entry point, and a broken switch is a broken entry point.
for companion in "${REPO_ROOT}/docs-v2/index.md" "${REPO_ROOT}/docs-v2/ru/index.md"; do
    if [ ! -f "${companion}" ]; then
        fail "the language companion is missing: ${companion}"
    fi
done

if [ "${failures}" -ne 0 ]; then
    printf 'PAGES-1 FAILED: %s problem(s). The site must not be published.\n' "${failures}" >&2
    exit 1
fi

printf 'PAGES-1 the Pages configuration matches the deployment: %s\n' "${EXPECTED_SITE_URL}"
printf 'PAGES-1 the title is "%s"\n' "${actual_title}"
exit 0
