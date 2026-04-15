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
: "${DB_NAME:?DB_NAME is required}"
: "${DB_USERNAME:?DB_USERNAME is required}"
: "${DB_PASSWORD:?DB_PASSWORD is required}"
: "${PROJECT_LIMIT:?PROJECT_LIMIT is required}"
: "${PROJECT_SIGNKEY:?PROJECT_SIGNKEY is required}"
: "${PUBLIC_DOMAIN:?PUBLIC_DOMAIN is required}"
: "${PUBLIC_HOST:?PUBLIC_HOST is required}"
: "${INGRESS_HOST:?INGRESS_HOST is required}"
: "${DB_STORAGE_SIZE:?DB_STORAGE_SIZE is required}"

if [[ -z "${DB_STORAGE_CLASS:-}" ]]; then
  unset DB_STORAGE_CLASS
fi

INIT_SQL_INDENT="$(sed 's/^/    /' "${ROOT_DIR}/deploy/init.sql")"
export INIT_SQL_INDENT

apply_template_file() {
  local file_path="$1"
  envsubst < "${file_path}" | kubectl apply -f -
}

echo "Creating namespace ${NAMESPACE} if needed"
kubectl create namespace "${NAMESPACE}" --dry-run=client -o yaml | kubectl apply -f -

echo "Applying base Kubernetes resources"
apply_template_file "${BASE_DIR}/namespace.yaml"
apply_template_file "${BASE_DIR}/configmap-init-sql.yaml"
apply_template_file "${BASE_DIR}/secret-app.yaml.tpl"
apply_template_file "${BASE_DIR}/service-db.yaml"
apply_template_file "${BASE_DIR}/statefulset-db.yaml"
apply_template_file "${BASE_DIR}/service-server.yaml"
apply_template_file "${BASE_DIR}/deployment-server.yaml"
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
echo "  kubectl logs deployment/jsw-front -n ${NAMESPACE}"
