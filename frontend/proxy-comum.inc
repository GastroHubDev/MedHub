# Cabecalhos comuns a todos os proxy_pass.
#
# O Authorization e repassado automaticamente pelo nginx, mas o declaramos explicitamente
# para deixar obvio que o Bearer token do front chega inteiro aos servicos.
proxy_set_header Host              $host;
proxy_set_header X-Real-IP         $remote_addr;
proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
proxy_set_header X-Forwarded-Proto $scheme;
proxy_set_header Authorization     $http_authorization;
proxy_pass_request_headers on;

# O fluxo ponta a ponta espera o evento atravessar o Kafka; nao pode morrer em timeout curto.
proxy_connect_timeout 10s;
proxy_send_timeout    30s;
proxy_read_timeout    30s;
