package br.com.fiap.agendamento.dto;

import br.com.fiap.comum.seguranca.Role;
import java.time.Instant;

public record LoginResponse(String token, String tipo, String nome, Role perfil, Instant expiraEm) {

    public static LoginResponse de(String token, String nome, Role perfil, Instant expiraEm) {
        return new LoginResponse(token, "Bearer", nome, perfil, expiraEm);
    }
}
