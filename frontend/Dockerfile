# Front estatico: nao ha etapa de build porque nao ha framework nem bundler.
# O que esta em app/ e exatamente o que o browser recebe.
FROM nginx:1.27-alpine

COPY nginx.conf       /etc/nginx/conf.d/default.conf
COPY proxy-comum.inc  /etc/nginx/conf.d/proxy-comum.inc
COPY app/             /usr/share/nginx/html/

EXPOSE 80
