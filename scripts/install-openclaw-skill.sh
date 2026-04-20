#!/usr/bin/env bash
set -euo pipefail

REGISTRY_URL="${CLAWHUB_REGISTRY:-https://skills.gydev.cn}"
SKILL_SLUG="${SKILL_SLUG:-jsw-db-query}"
VERSION="${VERSION:-${1:-latest}}"

require_command() {
  local command_name="$1"
  if ! command -v "${command_name}" >/dev/null 2>&1; then
    echo "Missing required command: ${command_name}" >&2
    exit 1
  fi
}

require_command npx

export CLAWHUB_REGISTRY="${REGISTRY_URL}"

if [[ -z "${VERSION}" || "${VERSION}" == "latest" ]]; then
  SKILL_SPEC="${SKILL_SLUG}"
else
  SKILL_SPEC="${SKILL_SLUG}@${VERSION}"
fi

echo "SkillHub registry: ${CLAWHUB_REGISTRY}"
echo "Installing skill: ${SKILL_SPEC}"
npx clawhub install "${SKILL_SPEC}" --registry "${CLAWHUB_REGISTRY}"

cat <<EOF

Installed ${SKILL_SPEC} through SkillHub.

If the skill is not public or does not belong to @global, log in first:
  npx clawhub login --token sk_your_api_token_here --registry ${CLAWHUB_REGISTRY}

OpenClaw configuration example:
{
  "skills": {
    "entries": {
      "${SKILL_SLUG}": {
        "enabled": true,
        "env": {
          "JSW_BASE_URL": "https://your-jsw.example.com",
          "JSW_ACCESS_TOKEN": "jsw_xxx"
        }
      }
    }
  }
}

After updating openclaw.json, refresh or restart OpenClaw so it reloads the skill entry.
Use \$${SKILL_SLUG} to verify the skill is available.
EOF
