package br.com.fiap.comum.seguranca;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

class TokenServiceTest {

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_USUARIO_ID = "usuarioId";

    private static final String SEGREDO = Base64.getEncoder()
            .encodeToString("segredo-de-teste-com-tamanho-suficiente-para-hs256".getBytes());

    private final TokenService tokenService =
            new TokenService(new JwtProperties(SEGREDO, 120, "agendamento-service"));

    @Test
    void deveGerarTokenQueSeValidaComAsClaimsEsperadas() {
        final String token = tokenService.gerar(7L, "maria@paciente.com", Role.PACIENTE);

        final Optional<UsuarioAutenticado> usuario = tokenService.validar(token);

        assertThat(usuario).isPresent();
        assertThat(usuario.get().getId()).isEqualTo(7L);
        assertThat(usuario.get().getUsername()).isEqualTo("maria@paciente.com");
        assertThat(usuario.get().getRole()).isEqualTo(Role.PACIENTE);
        assertThat(usuario.get().getAuthorities())
                .extracting(a -> a.getAuthority())
                .containsExactly("ROLE_PACIENTE");
    }

    @Test
    void deveRecusarTokenComAssinaturaAdulterada() {
        final String token = tokenService.gerar(1L, "medico@hospital.com", Role.MEDICO);

        assertThat(tokenService.validar(token + "adulterado")).isEmpty();
    }

    @Test
    void deveRecusarTokenAssinadoComOutroSegredo() {
        final String outroSegredo = Base64.getEncoder()
                .encodeToString("outro-segredo-completamente-diferente-hs256!!".getBytes());
        final TokenService intruso =
                new TokenService(new JwtProperties(outroSegredo, 120, "agendamento-service"));

        final String token = intruso.gerar(1L, "medico@hospital.com", Role.MEDICO);

        assertThat(tokenService.validar(token)).isEmpty();
    }

    @Test
    void deveRecusarTokenDeOutroEmissor() {
        final TokenService outroEmissor =
                new TokenService(new JwtProperties(SEGREDO, 120, "servico-desconhecido"));

        final String token = outroEmissor.gerar(1L, "medico@hospital.com", Role.MEDICO);

        assertThat(tokenService.validar(token)).isEmpty();
    }

    @Test
    void deveRecusarTokenJaExpirado() {
        final TokenService expirado =
                new TokenService(new JwtProperties(SEGREDO, -1, "agendamento-service"));

        final String token = expirado.gerar(1L, "medico@hospital.com", Role.MEDICO);

        assertThat(tokenService.validar(token)).isEmpty();
    }

    @Test
    void deveRecusarEntradaNulaOuVazia() {
        assertThat(tokenService.validar(null)).isEmpty();
        assertThat(tokenService.validar("")).isEmpty();
        assertThat(tokenService.validar("nao-e-um-jwt")).isEmpty();
    }

    /**
     * Assinatura valida, mas sem a claim "role" - simula um token forjado a mao ou emitido por
     * uma versao antiga do servico. Nao pode derrubar a validacao com NPE.
     */
    @Test
    void deveRecusarTokenComRoleAusente() {
        final String token = tokenComClaims(builder -> builder.claim(CLAIM_USUARIO_ID, 1L));

        assertThat(tokenService.validar(token)).isEmpty();
    }

    @Test
    void deveRecusarTokenComRoleForaDoEnum() {
        final String token = tokenComClaims(builder -> builder
                .claim(CLAIM_USUARIO_ID, 1L)
                .claim(CLAIM_ROLE, "SUPERADMIN"));

        assertThat(tokenService.validar(token)).isEmpty();
    }

    @Test
    void deveRecusarTokenComUsuarioIdAusente() {
        final String token = tokenComClaims(builder -> builder.claim(CLAIM_ROLE, Role.MEDICO.name()));

        assertThat(tokenService.validar(token)).isEmpty();
    }

    private static String tokenComClaims(UnaryOperator<JwtBuilder> personalizar) {
        final var builder = Jwts.builder()
                .subject("medico@hospital.com")
                .issuer("agendamento-service")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SEGREDO)));
        return personalizar.apply(builder).compact();
    }
}
