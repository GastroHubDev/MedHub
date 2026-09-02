package br.com.fiap.comum.seguranca;

/**
 * Perfis de acesso do enunciado. O prefixo {@code ROLE_} exigido pelo Spring Security
 * fica concentrado em {@link #authority()}, para que ele nunca seja escrito a mao.
 */
public enum Role {
    MEDICO,
    ENFERMEIRO,
    PACIENTE;

    public String authority() {
        return "ROLE_" + name();
    }
}
