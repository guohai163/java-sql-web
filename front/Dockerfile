FROM nginx:1.27-alpine

ENV NGINX_ENVSUBST_FILTER=VANNA_BASE_URL

# nginx 官方 entrypoint 会在启动时把环境变量渲染到 templates 目录内的配置文件。
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY dist /usr/share/nginx/html

EXPOSE 80
