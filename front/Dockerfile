FROM nginx:1.27-alpine

ENV NGINX_ENVSUBST_FILTER=VANNA_BASE_URL
ENV VANNA_BASE_URL=http://jsw-vanna:8003

# nginx 官方 entrypoint 会在启动时把环境变量渲染到 templates 目录内的配置文件。
COPY nginx/19-jsw-defaults.envsh /docker-entrypoint.d/19-jsw-defaults.envsh
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY dist /usr/share/nginx/html

EXPOSE 80
