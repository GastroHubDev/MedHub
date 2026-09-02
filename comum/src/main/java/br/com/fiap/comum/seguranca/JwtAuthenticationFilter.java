package br.com.fiap.comum.seguranca;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Le o cabecalho {@code Authorization: Bearer <token>} e popula o contexto de seguranca.
 *
 * <p>Nao rejeita requisicao sem token: quem decide o que exige autenticacao e a
 * {@code SecurityFilterChain} de cada servico. Assim o mesmo filtro serve tanto para rotas
 * publicas (login, GraphiQL, health) quanto protegidas.</p>
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String CABECALHO = "Authorization";
    private static final String PREFIXO = "Bearer ";

    private final TokenService tokenService;

    public JwtAuthenticationFilter(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        extrairToken(request)
                .flatMap(tokenService::validar)
                .ifPresent(usuario -> autenticar(usuario, request));
        filterChain.doFilter(request, response);
    }

    private void autenticar(UsuarioAutenticado usuario, HttpServletRequest request) {
        final var autenticacao = new UsernamePasswordAuthenticationToken(
                usuario, null, usuario.getAuthorities());
        autenticacao.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(autenticacao);
    }

    private java.util.Optional<String> extrairToken(HttpServletRequest request) {
        final String cabecalho = request.getHeader(CABECALHO);
        if (cabecalho == null || !cabecalho.startsWith(PREFIXO)) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(cabecalho.substring(PREFIXO.length()).trim());
    }
}
