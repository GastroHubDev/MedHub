package br.com.fiap.comum.seguranca;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TokenServiceTest {

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
}
