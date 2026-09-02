package br.com.fiap.historico.graphql;

import br.com.fiap.historico.domain.ConsultaHistorico;
import br.com.fiap.historico.domain.EventoConsulta;
import br.com.fiap.historico.dto.EstatisticasPacienteResponse;
import br.com.fiap.historico.dto.FiltroHistorico;
import br.com.fiap.historico.dto.HistoricoPacienteResponse;
import br.com.fiap.historico.repository.EventoConsultaRepository;
import br.com.fiap.historico.service.HistoricoService;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolvers do historico.
 *
 * <p>Os {@code @PreAuthorize} cobrem o nivel de papel; a restricao de que um paciente so
 * enxerga o proprio historico vive no {@code HistoricoService}, porque depende do dado pedido.</p>
 */
@Controller
public class HistoricoController {

    private final HistoricoService historicoService;
    private final EventoConsultaRepository eventoRepository;

    public HistoricoController(HistoricoService historicoService,
                               EventoConsultaRepository eventoRepository) {
        this.historicoService = historicoService;
        this.eventoRepository = eventoRepository;
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('MEDICO','ENFERMEIRO','PACIENTE')")
    public HistoricoPacienteResponse historicoPaciente(@Argument Long pacienteId,
                                                       @Argument FiltroHistorico filtro) {
        return historicoService.historicoPaciente(pacienteId, filtro);
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('MEDICO','ENFERMEIRO','PACIENTE')")
    public List<ConsultaHistorico> minhasConsultas(@Argument FiltroHistorico filtro) {
        return historicoService.minhasConsultas(filtro);
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('MEDICO','ENFERMEIRO','PACIENTE')")
    public List<ConsultaHistorico> consultasFuturas(@Argument Long pacienteId) {
        return historicoService.consultasFuturas(pacienteId);
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('MEDICO','ENFERMEIRO','PACIENTE')")
    public ConsultaHistorico consultaHistorico(@Argument Long consultaId) {
        return historicoService.consulta(consultaId);
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('MEDICO','ENFERMEIRO','PACIENTE')")
    public EstatisticasPacienteResponse estatisticasPaciente(@Argument Long pacienteId) {
        return historicoService.estatisticas(pacienteId);
    }

    // ---------- Resolvers de campo ----------

    @SchemaMapping(typeName = "ConsultaHistorico", field = "paciente")
    public Map<String, Object> paciente(ConsultaHistorico consulta) {
        return Map.of("id", consulta.getPacienteId(),
                "nome", consulta.getPacienteNome(),
                "email", consulta.getPacienteEmail());
    }

    @SchemaMapping(typeName = "ConsultaHistorico", field = "medico")
    public Map<String, Object> medico(ConsultaHistorico consulta) {
        final var medico = new java.util.HashMap<String, Object>();
        medico.put("id", consulta.getMedicoId());
        medico.put("nome", consulta.getMedicoNome());
        medico.put("especialidade", consulta.getMedicoEspecialidade());
        return medico;
    }

    @SchemaMapping(typeName = "ConsultaHistorico", field = "dataHora")
    public String dataHora(ConsultaHistorico consulta) {
        return consulta.getDataHora().toString();
    }

    @SchemaMapping(typeName = "ConsultaHistorico", field = "atualizadoEm")
    public String atualizadoEm(ConsultaHistorico consulta) {
        return consulta.getAtualizadoEm().toString();
    }

    /**
     * {@code @BatchMapping} em vez de {@code @SchemaMapping}: pedir a trilha de eventos numa
     * lista de N consultas faria N consultas ao banco (o N+1 classico do GraphQL). Aqui todas
     * as consultas da resposta sao resolvidas com uma unica ida ao banco.
     */
    @BatchMapping(typeName = "ConsultaHistorico", field = "eventos")
    @Transactional(readOnly = true)
    public Map<ConsultaHistorico, List<EventoConsulta>> eventos(List<ConsultaHistorico> consultas) {
        final var ids = consultas.stream().map(ConsultaHistorico::getConsultaId).toList();
        final Map<Long, List<EventoConsulta>> porConsulta =
                eventoRepository.findByConsultaIdInOrderByVersaoAscIdAsc(ids).stream()
                        .collect(Collectors.groupingBy(EventoConsulta::getConsultaId));

        return consultas.stream().collect(Collectors.toMap(
                consulta -> consulta,
                consulta -> porConsulta.getOrDefault(consulta.getConsultaId(), List.of())));
    }

    @SchemaMapping(typeName = "EventoConsulta", field = "tipo")
    public String tipo(EventoConsulta evento) {
        return evento.getTipoEvento().name();
    }

    @SchemaMapping(typeName = "EventoConsulta", field = "ocorridoEm")
    public String ocorridoEm(EventoConsulta evento) {
        return evento.getOcorridoEm().toString();
    }

    @SchemaMapping(typeName = "EventoConsulta", field = "registradoEm")
    public String registradoEm(EventoConsulta evento) {
        return evento.getRegistradoEm().toString();
    }
}
