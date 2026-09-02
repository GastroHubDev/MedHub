package br.com.fiap.historico.dto;

public record EstatisticasPacienteResponse(
        Long pacienteId,
        long total,
        long agendadas,
        long realizadas,
        long canceladas,
        long futuras
) {
}
