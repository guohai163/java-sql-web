#!/usr/bin/env bash
set -euo pipefail

REGISTRY_URL="${CLAWHUB_REGISTRY:-https://skills.gydev.cn}"
SKILL_DIR="${1:-skills/jsw-db-query}"

require_command() {
  local command_name="$1"
  if ! command -v "${command_name}" >/dev/null 2>&1; then
    echo "Missing required command: ${command_name}" >&2
    exit 1
  fi
}

require_command npx

if [[ ! -d "${SKILL_DIR}" ]]; then
  echo "Skill directory not found: ${SKILL_DIR}" >&2
  exit 1
fi

export CLAWHUB_REGISTRY="${REGISTRY_URL}"

echo "SkillHub registry: ${CLAWHUB_REGISTRY}"
echo "Publishing skill package: ${SKILL_DIR}"
npx clawhub publish "${SKILL_DIR}" --registry "${CLAWHUB_REGISTRY}"
