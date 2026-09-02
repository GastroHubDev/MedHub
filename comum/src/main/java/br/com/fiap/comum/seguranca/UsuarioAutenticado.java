package br.com.fiap.comum.seguranca;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Principal autenticado. Nos servicos de leitura ele e montado direto das claims do token,
 * sem ida ao banco; no agendamento ele tambem serve de {@link UserDetails} para o
 * {@code DaoAuthenticationProvider} no login.
 */
public class UsuarioAutenticado implements UserDetails {

    private final Long id;
    private final String email;
    private final String senha;
    private final Role role;

    public UsuarioAutenticado(Long id, String email, String senha, Role role) {
        this.id = id;
        this.email = email;
        this.senha = senha;
        this.role = role;
    }

    /** Usuario reconstruido a partir do token: nao ha senha envolvida. */
    public static UsuarioAutenticado doToken(Long id, String email, Role role) {
        return new UsuarioAutenticado(id, email, "", role);
    }

    public Long getId() {
        return id;
    }

    public Role getRole() {
        return role;
    }

    public boolean isPaciente() {
        return role == Role.PACIENTE;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.authority()));
    }

    @Override
    public String getPassword() {
        return senha;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
