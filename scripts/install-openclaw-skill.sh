#!/usr/bin/env bash
set -euo pipefail

REPO_OWNER="guohai163"
REPO_NAME="java-sql-web"
SKILL_NAME="java-sql-web-query"
TARGET_ROOT="${HOME}/.openclaw/skills"
TARGET_DIR="${TARGET_ROOT}/${SKILL_NAME}"
VERSION="${VERSION:-${1:-}}"

require_command() {
  local command_name="$1"
  if ! command -v "${command_name}" >/dev/null 2>&1; then
    echo "Missing required command: ${command_name}" >&2
    exit 1
  fi
}

if [[ -z "${VERSION}" ]]; then
  echo "Usage: VERSION=v2.1.0 bash install-openclaw-skill.sh" >&2
  echo "Or: bash install-openclaw-skill.sh v2.1.0" >&2
  exit 1
fi

require_command curl
require_command tar
require_command mktemp

ARCHIVE_URL="https://github.com/${REPO_OWNER}/${REPO_NAME}/archive/refs/tags/${VERSION}.tar.gz"
TEMP_DIR="$(mktemp -d)"
ARCHIVE_FILE="${TEMP_DIR}/${REPO_NAME}-${VERSION}.tar.gz"
EXTRACT_DIR="${TEMP_DIR}/extract"
ARCHIVE_ROOT="${REPO_NAME}-${VERSION}"
SKILL_SOURCE_DIR="${EXTRACT_DIR}/${ARCHIVE_ROOT}/skills/${SKILL_NAME}"

cleanup() {
  rm -rf "${TEMP_DIR}"
}
trap cleanup EXIT

echo "Downloading ${ARCHIVE_URL}"
curl -fsSL "${ARCHIVE_URL}" -o "${ARCHIVE_FILE}"

mkdir -p "${EXTRACT_DIR}"
tar -xzf "${ARCHIVE_FILE}" -C "${EXTRACT_DIR}"

if [[ ! -d "${SKILL_SOURCE_DIR}" ]]; then
  echo "Skill directory not found in archive: ${SKILL_SOURCE_DIR}" >&2
  exit 1
fi

mkdir -p "${TARGET_ROOT}"

if [[ -d "${TARGET_DIR}" ]]; then
  BACKUP_DIR="${TARGET_DIR}.backup.$(date +%Y%m%d%H%M%S)"
  echo "Backing up existing skill to ${BACKUP_DIR}"
  mv "${TARGET_DIR}" "${BACKUP_DIR}"
fi

cp -R "${SKILL_SOURCE_DIR}" "${TARGET_DIR}"

for required_file in \
  "${TARGET_DIR}/SKILL.md" \
  "${TARGET_DIR}/references/api-query.md" \
  "${TARGET_DIR}/references/query-guardrails.md" \
  "${TARGET_DIR}/agents/openai.yaml"; do
  if [[ ! -f "${required_file}" ]]; then
    echo "Install verification failed, missing file: ${required_file}" >&2
    exit 1
  fi
done

echo "Installed ${SKILL_NAME} from ${VERSION}"
echo "Skill path: ${TARGET_DIR}"
echo "Next step: refresh or restart OpenClaw so it reloads skills."
echo "Then invoke it with: \$${SKILL_NAME}"
