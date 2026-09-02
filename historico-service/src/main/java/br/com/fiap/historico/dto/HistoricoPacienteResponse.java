package br.com.fiap.historico.dto;

import br.com.fiap.historico.domain.ConsultaHistorico;
import java.util.List;

public record HistoricoPacienteResponse(
        Long pacienteId,
        String pacienteNome,
        int totalConsultas,
        List<ConsultaHistorico> consultas
) {

    public static HistoricoPacienteResponse de(Long pacienteId, List<ConsultaHistorico> consultas) {
        final String nome = consultas.isEmpty() ? null : consultas.get(0).getPacienteNome();
        return new HistoricoPacienteResponse(pacienteId, nome, consultas.size(), consultas);
    }
}
