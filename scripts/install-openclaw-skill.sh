#!/usr/bin/env bash
set -euo pipefail

REPO_OWNER="guohai163"
REPO_NAME="java-sql-web"
SKILL_NAME="java-sql-web-query"
OPENCLAW_HOME="${OPENCLAW_HOME:-${HOME}/.openclaw}"
TARGET_ROOT="${OPENCLAW_HOME}/skills"
TARGET_DIR="${TARGET_ROOT}/${SKILL_NAME}"
OPENCLAW_CONFIG_FILE="${OPENCLAW_HOME}/openclaw.json"
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

cleanup() {
  rm -rf "${TEMP_DIR}"
}
trap cleanup EXIT

echo "Downloading ${ARCHIVE_URL}"
curl -fsSL "${ARCHIVE_URL}" -o "${ARCHIVE_FILE}"

mkdir -p "${EXTRACT_DIR}"
tar -xzf "${ARCHIVE_FILE}" -C "${EXTRACT_DIR}"

mapfile -t ARCHIVE_ROOTS < <(find "${EXTRACT_DIR}" -mindepth 1 -maxdepth 1 -type d | sort)
if [[ "${#ARCHIVE_ROOTS[@]}" -ne 1 ]]; then
  echo "Expected exactly one archive root directory after extraction, found ${#ARCHIVE_ROOTS[@]}:" >&2
  printf '  %s\n' "${ARCHIVE_ROOTS[@]}" >&2
  exit 1
fi

ARCHIVE_ROOT_DIR="${ARCHIVE_ROOTS[0]}"
SKILL_SOURCE_DIR="${ARCHIVE_ROOT_DIR}/skills/${SKILL_NAME}"

echo "Resolved archive root: ${ARCHIVE_ROOT_DIR}"

if [[ ! -d "${SKILL_SOURCE_DIR}" ]]; then
  echo "Skill directory not found in archive: ${SKILL_SOURCE_DIR}" >&2
  if [[ -d "${ARCHIVE_ROOT_DIR}/skills" ]]; then
    echo "Available skills in archive:" >&2
    find "${ARCHIVE_ROOT_DIR}/skills" -mindepth 1 -maxdepth 1 -type d | sort >&2
  fi
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
echo "OpenClaw home: ${OPENCLAW_HOME}"
echo "Skill path: ${TARGET_DIR}"

if [[ -f "${OPENCLAW_CONFIG_FILE}" ]]; then
  if grep -Fq "\"${SKILL_NAME}\"" "${OPENCLAW_CONFIG_FILE}"; then
    echo "Detected an existing ${SKILL_NAME} entry in ${OPENCLAW_CONFIG_FILE}"
  else
    echo "Warning: ${OPENCLAW_CONFIG_FILE} exists, but no ${SKILL_NAME} entry was found."
  fi
else
  echo "Warning: ${OPENCLAW_CONFIG_FILE} does not exist yet."
fi

cat <<EOF

To make this skill available in OpenClaw, ensure ${OPENCLAW_CONFIG_FILE} contains:

{
  "skills": {
    "entries": {
      "${SKILL_NAME}": {
        "enabled": true,
        "env": {
          "JSW_BASE_URL": "https://your-jsw.example.com",
          "JSW_ACCESS_TOKEN": "jsw_xxx"
        }
      }
    }
  }
}

Notes:
- If OpenClaw runs under a different home directory, reinstall with OPENCLAW_HOME=/actual/openclaw/home
- After updating openclaw.json, refresh or restart OpenClaw so it reloads skills and entry configuration
- A generic question like "你有什么 skill" may not reliably reflect newly installed skills; verify with: \$${SKILL_NAME}
EOF
