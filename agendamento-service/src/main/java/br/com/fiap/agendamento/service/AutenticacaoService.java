package br.com.fiap.agendamento.service;

import br.com.fiap.agendamento.domain.Usuario;
import br.com.fiap.agendamento.dto.LoginRequest;
import br.com.fiap.agendamento.dto.LoginResponse;
import br.com.fiap.agendamento.repository.UsuarioRepository;
import br.com.fiap.comum.seguranca.TokenService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AutenticacaoService {

    private final AuthenticationManager authenticationManager;
    private final UsuarioRepository usuarioRepository;
    private final TokenService tokenService;

    public AutenticacaoService(AuthenticationManager authenticationManager,
                               UsuarioRepository usuarioRepository,
                               TokenService tokenService) {
        this.authenticationManager = authenticationManager;
        this.usuarioRepository = usuarioRepository;
        this.tokenService = tokenService;
    }

    @Transactional(readOnly = true)
    public LoginResponse autenticar(LoginRequest requisicao) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(requisicao.email(), requisicao.senha()));

        final Usuario usuario = usuarioRepository.findByEmail(requisicao.email())
                .orElseThrow(() -> new BadCredentialsException("Credenciais invalidas"));

        final String token = tokenService.gerar(usuario.getId(), usuario.getEmail(), usuario.getRole());
        return LoginResponse.de(token, usuario.getNome(), usuario.getRole(), tokenService.expiracaoDe(token));
    }
}
