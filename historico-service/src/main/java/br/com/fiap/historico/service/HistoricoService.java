package br.com.fiap.historico.service;

import br.com.fiap.comum.evento.StatusConsulta;
import br.com.fiap.comum.seguranca.ContextoSeguranca;
import br.com.fiap.historico.domain.ConsultaHistorico;
import br.com.fiap.historico.dto.EstatisticasPacienteResponse;
import br.com.fiap.historico.dto.FiltroHistorico;
import br.com.fiap.historico.dto.HistoricoPacienteResponse;
import br.com.fiap.historico.exception.FiltroInvalidoException;
import br.com.fiap.historico.exception.RecursoNaoEncontradoException;
import br.com.fiap.historico.repository.ConsultaHistoricoRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Leitura do historico.
 *
 * <p>Autorizacao em dois niveis, como no agendamento: o resolver barra por perfil e este
 * servico barra por posse, via {@link ContextoSeguranca} - decisao que depende do dado
 * solicitado e nao apenas do papel de quem pede.</p>
 */
@Service
@Transactional(readOnly = true)
public class HistoricoService {

    private final ConsultaHistoricoRepository consultaRepository;
    private final ContextoSeguranca contextoSeguranca;

    public HistoricoService(ConsultaHistoricoRepository consultaRepository,
                            ContextoSeguranca contextoSeguranca) {
        this.consultaRepository = consultaRepository;
        this.contextoSeguranca = contextoSeguranca;
    }

    public HistoricoPacienteResponse historicoPaciente(Long pacienteIdSolicitado,
                                                       FiltroHistorico filtro) {
        final Long pacienteAlvo = contextoSeguranca.resolverPacienteAlvo(pacienteIdSolicitado);
        return HistoricoPacienteResponse.de(pacienteAlvo, buscar(pacienteAlvo, filtro));
    }

    /** Sempre o historico de quem esta autenticado, qualquer que seja o perfil. */
    public List<ConsultaHistorico> minhasConsultas(FiltroHistorico filtro) {
        return buscar(contextoSeguranca.usuarioLogado().getId(), filtro);
    }

    public List<ConsultaHistorico> consultasFuturas(Long pacienteIdSolicitado) {
        final Long pacienteAlvo = contextoSeguranca.resolverPacienteAlvo(pacienteIdSolicitado);
        // So AGENDADA: uma consulta cancelada nao e mais um compromisso futuro de verdade,
        // mesmo que a data dela ainda nao tenha passado.
        return buscar(pacienteAlvo,
                new FiltroHistorico(true, List.of(StatusConsulta.AGENDADA), null, null, null, null));
    }

    public ConsultaHistorico consulta(Long consultaId) {
        final ConsultaHistorico consulta = consultaRepository.findById(consultaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Consulta %d nao encontrada no historico".formatted(consultaId)));

        contextoSeguranca.exigirPosse(consulta.getPacienteId());
        return consulta;
    }

    public EstatisticasPacienteResponse estatisticas(Long pacienteIdSolicitado) {
        final Long pacienteAlvo = contextoSeguranca.resolverPacienteAlvo(pacienteIdSolicitado);
        if (pacienteAlvo == null) {
            throw new FiltroInvalidoException(
                    "Informe o pacienteId para calcular as estatisticas");
        }

        return new EstatisticasPacienteResponse(
                pacienteAlvo,
                consultaRepository.countByPacienteId(pacienteAlvo),
                consultaRepository.countByPacienteIdAndStatus(pacienteAlvo, StatusConsulta.AGENDADA),
                consultaRepository.countByPacienteIdAndStatus(pacienteAlvo, StatusConsulta.REALIZADA),
                consultaRepository.countByPacienteIdAndStatus(pacienteAlvo, StatusConsulta.CANCELADA),
                consultaRepository.countByPacienteIdAndStatusAndDataHoraGreaterThanEqual(
                        pacienteAlvo, StatusConsulta.AGENDADA, LocalDateTime.now()));
    }

    private List<ConsultaHistorico> buscar(Long pacienteId, FiltroHistorico filtro) {
        final FiltroHistorico efetivo = filtro == null ? FiltroHistorico.vazio() : filtro;

        LocalDateTime de = parseData(efetivo.de(), "de");
        final LocalDateTime ate = parseData(efetivo.ate(), "ate");

        // apenasFuturas e um atalho para "de = agora"; se ambos vierem, vale o mais restritivo.
        if (efetivo.querApenasFuturas()) {
            final LocalDateTime agora = LocalDateTime.now();
            de = (de == null || de.isBefore(agora)) ? agora : de;
        }
        if (de != null && ate != null && de.isAfter(ate)) {
            throw new FiltroInvalidoException("O inicio do periodo e posterior ao fim");
        }

        final var status = (efetivo.status() == null || efetivo.status().isEmpty())
                ? null : efetivo.status();

        return consultaRepository.buscarComFiltros(
                pacienteId, efetivo.medicoId(), de, ate, efetivo.especialidade(), status);
    }

    private static LocalDateTime parseData(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(valor);
        } catch (DateTimeParseException e) {
            throw new FiltroInvalidoException(
                    "Filtro '%s' deve estar em ISO-8601 (ex.: 2026-09-10T00:00:00), recebido '%s'"
                            .formatted(campo, valor));
        }
    }
}
