package br.com.fiap.agendamento.dto;

import br.com.fiap.agendamento.domain.Consulta;
import br.com.fiap.comum.evento.StatusConsulta;
import java.time.LocalDateTime;
import java.util.List;

public record ConsultaResponse(
        Long id,
        PacienteResumo paciente,
        MedicoResumo medico,
        LocalDateTime dataHora,
        StatusConsulta status,
        String observacoes,
        long versao,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm
) {

    public record PacienteResumo(Long id, String nome, String email) {
    }

    public record MedicoResumo(Long id, String nome, String especialidade) {
    }

    public static ConsultaResponse de(Consulta consulta) {
        return new ConsultaResponse(
                consulta.getId(),
                new PacienteResumo(consulta.getPaciente().getId(),
                        consulta.getPaciente().getNome(),
                        consulta.getPaciente().getEmail()),
                new MedicoResumo(consulta.getMedico().getId(),
                        consulta.getMedico().getNome(),
                        consulta.getMedico().getEspecialidade()),
                consulta.getDataHora(),
                consulta.getStatus(),
                consulta.getObservacoes(),
                consulta.getVersao(),
                consulta.getCriadoEm(),
                consulta.getAtualizadoEm());
    }

    public static List<ConsultaResponse> deLista(List<Consulta> consultas) {
        return consultas.stream().map(ConsultaResponse::de).toList();
    }
}
