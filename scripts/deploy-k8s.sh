#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ROOT_DIR}/deploy/k8s/env/prod.env"
BASE_DIR="${ROOT_DIR}/deploy/k8s/base"

require_command() {
  local command_name="$1"
  if ! command -v "${command_name}" >/dev/null 2>&1; then
    echo "Missing required command: ${command_name}" >&2
    exit 1
  fi
}

require_command kubectl
require_command envsubst

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "Missing environment file: ${ENV_FILE}" >&2
  echo "Please copy deploy/k8s/env/prod.env.example to deploy/k8s/env/prod.env and fill in values." >&2
  exit 1
fi

set -a
source "${ENV_FILE}"
set +a

: "${NAMESPACE:?NAMESPACE is required}"
: "${TAG:?TAG is required}"
: "${JSW_SERVER_IMAGE:?JSW_SERVER_IMAGE is required}"
: "${JSW_FRONT_IMAGE:?JSW_FRONT_IMAGE is required}"
: "${JSW_VANNA_IMAGE:?JSW_VANNA_IMAGE is required}"
: "${DB_NAME:?DB_NAME is required}"
: "${DB_USERNAME:?DB_USERNAME is required}"
: "${DB_PASSWORD:?DB_PASSWORD is required}"
: "${DB_DIALECT:?DB_DIALECT is required}"
: "${DB_PORT:?DB_PORT is required}"
: "${PROJECT_LIMIT:?PROJECT_LIMIT is required}"
: "${PROJECT_SIGNKEY:?PROJECT_SIGNKEY is required}"
: "${PUBLIC_DOMAIN:?PUBLIC_DOMAIN is required}"
: "${PUBLIC_HOST:?PUBLIC_HOST is required}"
: "${INGRESS_HOST:?INGRESS_HOST is required}"
: "${DB_STORAGE_SIZE:?DB_STORAGE_SIZE is required}"
: "${VANNA_PORT:?VANNA_PORT is required}"
: "${JSW_SERVER_BASE_URL:?JSW_SERVER_BASE_URL is required}"
: "${VANNA_INTERNAL_TOKEN:?VANNA_INTERNAL_TOKEN is required}"
: "${VANNA_DB_URL:?VANNA_DB_URL is required}"
: "${VANNA_CHAT_MODEL:?VANNA_CHAT_MODEL is required}"
: "${VANNA_EMBEDDING_MODEL:?VANNA_EMBEDDING_MODEL is required}"
: "${VANNA_LLM_BASE_URL:?VANNA_LLM_BASE_URL is required}"
: "${VANNA_LLM_API_KEY:?VANNA_LLM_API_KEY is required}"

PROJECT_LEGACY_TLS_ENABLED="${PROJECT_LEGACY_TLS_ENABLED:-false}"
JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-}"

if [[ -z "${DB_STORAGE_CLASS:-}" ]]; then
  unset DB_STORAGE_CLASS
fi

case "${DB_DIALECT}" in
  postgresql|postgres|pgsql)
    DB_DIALECT="postgresql"
    INIT_SQL_FILE="${ROOT_DIR}/deploy/init.postgresql.sql"
    DB_JDBC_URL="jdbc:postgresql://jsw-db:${DB_PORT}/${DB_NAME}"
    DB_CONTAINER_NAME="postgres"
    DB_IMAGE="${DB_IMAGE:-postgres:18-alpine}"
    DB_SERVICE_PORT_NAME="postgres"
    DB_VOLUME_MOUNT_PATH="/var/lib/postgresql"
    DB_PROBE_COMMAND='pg_isready -h 127.0.0.1 -U "${POSTGRES_USER}" -d "${POSTGRES_DB}"'
    DB_ENV_INDENT='            - name: POSTGRES_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: jsw-app-secret
                  key: DB_PASSWORD
            - name: POSTGRES_DB
              valueFrom:
                secretKeyRef:
                  name: jsw-app-secret
                  key: DB_NAME
            - name: POSTGRES_USER
              valueFrom:
                secretKeyRef:
                  name: jsw-app-secret
                  key: DB_USERNAME'
    ;;
  mysql|mariadb)
    DB_DIALECT="mysql"
    INIT_SQL_FILE="${ROOT_DIR}/deploy/init.sql"
    DB_JDBC_URL="jdbc:mysql://jsw-db:${DB_PORT}/${DB_NAME}?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Hongkong"
    DB_CONTAINER_NAME="mariadb"
    DB_IMAGE="${DB_IMAGE:-mariadb:10.11}"
    DB_SERVICE_PORT_NAME="mysql"
    DB_VOLUME_MOUNT_PATH="/var/lib/mysql"
    DB_PROBE_COMMAND='mariadb-admin ping -h 127.0.0.1 -uroot -p"${MARIADB_ROOT_PASSWORD}"'
    DB_ENV_INDENT='            - name: MARIADB_ROOT_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: jsw-app-secret
                  key: DB_PASSWORD
            - name: MARIADB_DATABASE
              valueFrom:
                secretKeyRef:
                  name: jsw-app-secret
                  key: DB_NAME
            - name: DB_USERNAME
              valueFrom:
                secretKeyRef:
                  name: jsw-app-secret
                  key: DB_USERNAME'
    ;;
  *)
    echo "Unsupported DB_DIALECT: ${DB_DIALECT}. Expected postgresql, mysql, or mariadb." >&2
    exit 1
    ;;
esac

INIT_SQL_INDENT="$(sed 's/^/    /' "${INIT_SQL_FILE}")"
LEGACY_TLS_SECURITY_INDENT="$(sed 's/^/    /' "${ROOT_DIR}/deploy/java-security/legacy-tls.security")"
export INIT_SQL_INDENT
export LEGACY_TLS_SECURITY_INDENT
export PROJECT_LEGACY_TLS_ENABLED
export JAVA_TOOL_OPTIONS
export DB_DIALECT
export DB_PORT
export DB_JDBC_URL
export DB_CONTAINER_NAME
export DB_IMAGE
export DB_SERVICE_PORT_NAME
export DB_VOLUME_MOUNT_PATH
export DB_PROBE_COMMAND
export DB_ENV_INDENT

apply_template_file() {
  local file_path="$1"
  envsubst < "${file_path}" | kubectl apply -f -
}

echo "Creating namespace ${NAMESPACE} if needed"
kubectl create namespace "${NAMESPACE}" --dry-run=client -o yaml | kubectl apply -f -

echo "Applying base Kubernetes resources"
apply_template_file "${BASE_DIR}/namespace.yaml"
apply_template_file "${BASE_DIR}/configmap-init-sql.yaml"
apply_template_file "${BASE_DIR}/configmap-legacy-tls-security.yaml"
apply_template_file "${BASE_DIR}/secret-app.yaml.tpl"
apply_template_file "${BASE_DIR}/service-db.yaml"
apply_template_file "${BASE_DIR}/statefulset-db.yaml"
apply_template_file "${BASE_DIR}/service-server.yaml"
apply_template_file "${BASE_DIR}/deployment-server.yaml"
apply_template_file "${BASE_DIR}/service-vanna.yaml"
apply_template_file "${BASE_DIR}/deployment-vanna.yaml"
apply_template_file "${BASE_DIR}/service-front.yaml"
apply_template_file "${BASE_DIR}/deployment-front.yaml"
apply_template_file "${BASE_DIR}/ingress-front.yaml"

echo
echo "Deployment submitted."
echo "Namespace: ${NAMESPACE}"
echo "Ingress host: ${INGRESS_HOST}"
echo
echo "Useful commands:"
echo "  kubectl get pods -n ${NAMESPACE}"
echo "  kubectl get ingress -n ${NAMESPACE}"
echo "  kubectl logs deployment/jsw-server -n ${NAMESPACE}"
echo "  kubectl logs deployment/jsw-vanna -n ${NAMESPACE}"
echo "  kubectl logs deployment/jsw-front -n ${NAMESPACE}"
