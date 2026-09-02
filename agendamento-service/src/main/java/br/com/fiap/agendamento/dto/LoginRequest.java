package br.com.fiap.agendamento.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "E-mail e obrigatorio.")
        @Email(message = "E-mail em formato invalido.")
        String email,

        @NotBlank(message = "Senha e obrigatoria.")
        String senha
) {
}
