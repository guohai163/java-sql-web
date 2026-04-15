apiVersion: v1
kind: Secret
metadata:
  name: jsw-app-secret
  namespace: ${NAMESPACE}
type: Opaque
stringData:
  DB_NAME: "${DB_NAME}"
  DB_USERNAME: "${DB_USERNAME}"
  DB_PASSWORD: "${DB_PASSWORD}"
  PROJECT_LIMIT: "${PROJECT_LIMIT}"
  PROJECT_SIGNKEY: "${PROJECT_SIGNKEY}"
  PROJECT_DOMAIN: "${PUBLIC_DOMAIN}"
  PROJECT_HOST: "${PUBLIC_HOST}"
