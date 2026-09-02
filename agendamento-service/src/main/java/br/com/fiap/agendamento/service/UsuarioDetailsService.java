package br.com.fiap.agendamento.service;

import br.com.fiap.agendamento.repository.UsuarioRepository;
import br.com.fiap.comum.seguranca.UsuarioAutenticado;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    public UsuarioDetailsService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        return usuarioRepository.findByEmail(email)
                .map(usuario -> new UsuarioAutenticado(
                        usuario.getId(), usuario.getEmail(), usuario.getSenha(), usuario.getRole()))
                .orElseThrow(() -> new UsernameNotFoundException("Credenciais invalidas"));
    }
}
