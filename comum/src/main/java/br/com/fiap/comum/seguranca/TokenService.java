package br.com.fiap.comum.seguranca;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;

/**
 * Emissao e validacao do JWT (HS256).
 *
 * <p>
 * Vive no modulo comum porque o agendamento emite e os demais servicos validam
 * o mesmo
 * token: manter uma unica implementacao impede que as claims esperadas divirjam
 * entre eles.
 * </p>
 */
public class TokenService {

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_USUARIO_ID = "usuarioId";

    private final SecretKey chave;
    private final long expiracaoMinutos;
    private final String emissor;

    public TokenService(JwtProperties propriedades) {
        this.chave = Keys.hmacShaKeyFor(Decoders.BASE64.decode(propriedades.secret()));
        this.expiracaoMinutos = propriedades.expiracaoMinutos();
        this.emissor = propriedades.emissor();
    }

    public String gerar(Long usuarioId, String email, Role role) {
        final Instant agora = Instant.now();
        final Instant expiracao = agora.plus(expiracaoMinutos, ChronoUnit.MINUTES);
        return Jwts.builder()
                .subject(email)
                .issuer(emissor)
                .claim(CLAIM_ROLE, role.name())
                .claim(CLAIM_USUARIO_ID, usuarioId)
                .issuedAt(Date.from(agora))
                .expiration(Date.from(expiracao))
                .signWith(chave)
                .compact();
    }

    public Instant expiracaoDe(String token) {
        return parse(token).map(claims -> claims.getExpiration().toInstant()).orElse(null);
    }

    /**
     * Valida assinatura, emissor e expiracao. Devolve vazio em qualquer falha - o
     * chamador
     * nao deve distinguir "token expirado" de "assinatura invalida" na resposta ao
     * cliente.
     */
    public Optional<UsuarioAutenticado> validar(String token) {
        return parse(token).flatMap(this::extrairUsuario);
    }

    private Optional<UsuarioAutenticado> extrairUsuario(Claims claims) {
        try {
            return Optional.of(UsuarioAutenticado.doToken(
                    claims.get(CLAIM_USUARIO_ID, Number.class).longValue(),
                    claims.getSubject(),
                    Role.valueOf(claims.get(CLAIM_ROLE, String.class))));
        } catch (IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }

    private Optional<Claims> parse(String token) {
        try {
            return Optional.of(Jwts.parser()
                    .verifyWith(chave)
                    .requireIssuer(emissor)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload());
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }
}
