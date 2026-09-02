package br.com.fiap.agendamento.service;

import br.com.fiap.agendamento.domain.Consulta;
import br.com.fiap.agendamento.domain.OutboxEvento;
import br.com.fiap.agendamento.repository.OutboxEventoRepository;
import br.com.fiap.comum.evento.ConsultaEvento;
import br.com.fiap.comum.evento.TipoEvento;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Traduz uma consulta em evento e o grava na outbox.
 *
 * <p>O {@code Propagation.MANDATORY} e proposital: registrar um evento so faz sentido dentro
 * da transacao que alterou a consulta. Se alguem chamar este componente fora de uma
 * transacao, o build falha em runtime na hora - em vez de gravar um evento orfao.</p>
 */
@Component
public class RegistradorDeEventos {

    private final OutboxEventoRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public RegistradorDeEventos(OutboxEventoRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(Consulta consulta, TipoEvento tipoEvento) {
        final ConsultaEvento evento = montar(consulta, tipoEvento);
        outboxRepository.save(new OutboxEvento(consulta.getId(), tipoEvento, serializar(evento)));
    }

    private static ConsultaEvento montar(Consulta consulta, TipoEvento tipoEvento) {
        return new ConsultaEvento(
                consulta.getId(),
                tipoEvento,
                consulta.getStatus(),
                consulta.getPaciente().getId(),
                consulta.getPaciente().getNome(),
                consulta.getPaciente().getEmail(),
                consulta.getMedico().getId(),
                consulta.getMedico().getNome(),
                consulta.getMedico().getEspecialidade(),
                consulta.getDataHora().toString(),
                consulta.getObservacoes(),
                consulta.getVersao(),
                Instant.now().toString());
    }

    private String serializar(ConsultaEvento evento) {
        try {
            return objectMapper.writeValueAsString(evento);
        } catch (JsonProcessingException e) {
            // Falha aqui significa contrato quebrado em tempo de compilacao/deploy, nao um erro
            // recuperavel: derruba a transacao em vez de gravar um payload invalido na outbox.
            throw new IllegalStateException("Falha ao serializar evento da consulta "
                    + evento.consultaId(), e);
        }
    }
}
