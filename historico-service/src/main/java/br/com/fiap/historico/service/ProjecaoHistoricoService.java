package br.com.fiap.historico.service;

import br.com.fiap.comum.evento.ConsultaEvento;
import br.com.fiap.historico.domain.ConsultaHistorico;
import br.com.fiap.historico.domain.EventoConsulta;
import br.com.fiap.historico.exception.EventoInvalidoException;
import br.com.fiap.historico.repository.ConsultaHistoricoRepository;
import br.com.fiap.historico.repository.EventoConsultaRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Constroi o read model a partir dos eventos do Kafka.
 *
 * <p>Duas gravacoes por evento: a trilha de auditoria (append-only, sempre) e o estado atual
 * (apenas se o evento for mais novo que o ja aplicado). Separar as duas e o que permite a
 * trilha registrar tambem os eventos fora de ordem que nao alteraram o estado.</p>
 */
@Service
public class ProjecaoHistoricoService {

    private static final Logger log = LoggerFactory.getLogger(ProjecaoHistoricoService.class);
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    private final ConsultaHistoricoRepository consultaRepository;
    private final EventoConsultaRepository eventoRepository;

    public ProjecaoHistoricoService(ConsultaHistoricoRepository consultaRepository,
                                    EventoConsultaRepository eventoRepository) {
        this.consultaRepository = consultaRepository;
        this.eventoRepository = eventoRepository;
    }

    @Transactional
    public void aplicar(ConsultaEvento evento) {
        validar(evento);

        final LocalDateTime dataHora = parseDataHora(evento);
        registrarNaTrilha(evento, dataHora);
        aplicarNoEstadoAtual(evento, dataHora);
    }

    private void registrarNaTrilha(ConsultaEvento evento, LocalDateTime dataHora) {
        final boolean jaRegistrado = eventoRepository.existsByConsultaIdAndVersaoAndTipoEvento(
                evento.consultaId(), evento.versao(), evento.tipoEvento());
        if (jaRegistrado) {
            log.debug("Evento {} da consulta {} versao {} ja esta na trilha; reentrega ignorada",
                    evento.tipoEvento(), evento.consultaId(), evento.versao());
            return;
        }

        eventoRepository.save(new EventoConsulta(evento.consultaId(), evento.tipoEvento(),
                evento.status(), dataHora, evento.versao(), parseOcorridoEm(evento)));
    }

    private void aplicarNoEstadoAtual(ConsultaEvento evento, LocalDateTime dataHora) {
        final ConsultaHistorico consulta = consultaRepository.findById(evento.consultaId())
                .orElseGet(() -> new ConsultaHistorico(evento.consultaId()));

        if (consulta.getVersao() > 0 && consulta.ehMaisAntigoQue(evento.versao())) {
            log.debug("Estado da consulta {} ja esta na versao {}; evento da versao {} nao o altera",
                    evento.consultaId(), consulta.getVersao(), evento.versao());
            return;
        }

        consulta.aplicar(evento.pacienteId(), evento.pacienteNome(), evento.pacienteEmail(),
                evento.medicoId(), evento.medicoNome(), evento.medicoEspecialidade(),
                dataHora, evento.status(), evento.observacoes(), evento.versao());
        consultaRepository.save(consulta);

        log.info("Historico da consulta {} atualizado para {} (versao {})",
                evento.consultaId(), evento.status(), evento.versao());
    }

    private static void validar(ConsultaEvento evento) {
        if (evento == null) {
            throw new EventoInvalidoException("Evento nulo");
        }
        exigir(evento.consultaId() != null, "consultaId ausente");
        exigir(evento.tipoEvento() != null, "tipoEvento ausente");
        exigir(evento.status() != null, "status ausente");
        exigir(evento.pacienteId() != null, "pacienteId ausente");
        exigir(evento.pacienteNome() != null, "pacienteNome ausente");
        exigir(evento.pacienteEmail() != null, "pacienteEmail ausente");
        exigir(evento.medicoId() != null, "medicoId ausente");
        exigir(evento.medicoNome() != null, "medicoNome ausente");
        exigir(evento.dataHora() != null, "dataHora ausente");
    }

    private static void exigir(boolean condicao, String descricao) {
        if (!condicao) {
            throw new EventoInvalidoException("Evento invalido: " + descricao);
        }
    }

    private static LocalDateTime parseDataHora(ConsultaEvento evento) {
        try {
            return LocalDateTime.parse(evento.dataHora());
        } catch (DateTimeParseException e) {
            throw new EventoInvalidoException(
                    "Evento invalido: dataHora '%s' fora do formato ISO-8601"
                            .formatted(evento.dataHora()), e);
        }
    }

    /** Sem o instante do evento, o momento do consumo e a melhor aproximacao disponivel. */
    private static LocalDateTime parseOcorridoEm(ConsultaEvento evento) {
        if (evento.ocorridoEm() == null) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.ofInstant(Instant.parse(evento.ocorridoEm()), FUSO);
        } catch (DateTimeParseException e) {
            return LocalDateTime.now();
        }
    }
}
