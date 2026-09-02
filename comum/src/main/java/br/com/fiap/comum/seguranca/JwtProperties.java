package br.com.fiap.comum.seguranca;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracao do JWT. O mesmo segredo e emissor sao usados pelo servico que emite o token
 * (agendamento) e pelos que apenas o validam (notificacao e historico).
 *
 * @param secret            chave HMAC em Base64, injetada por variavel de ambiente
 * @param expiracaoMinutos  validade do token emitido
 * @param emissor           claim {@code iss}, exigida na validacao
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        long expiracaoMinutos,
        String emissor
) {
}
