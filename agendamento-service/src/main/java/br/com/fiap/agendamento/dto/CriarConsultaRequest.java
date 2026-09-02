package br.com.fiap.agendamento.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record CriarConsultaRequest(
        @NotNull(message = "Id do paciente e obrigatorio.")
        Long pacienteId,

        @NotNull(message = "Id do medico e obrigatorio.")
        Long medicoId,

        @NotNull(message = "Data e hora da consulta sao obrigatorias.")
        LocalDateTime dataHora,

        @Size(max = 1000, message = "Observacoes devem ter no maximo 1000 caracteres.")
        String observacoes
) {
}
