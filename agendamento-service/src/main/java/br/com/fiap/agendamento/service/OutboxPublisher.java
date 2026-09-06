package br.com.fiap.agendamento.service;

import br.com.fiap.agendamento.domain.OutboxEvento;
import br.com.fiap.agendamento.repository.OutboxEventoRepository;
import br.com.fiap.comum.evento.Topicos;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publica no Kafka os eventos ja comitados na outbox.
 *
 * <p>Roda fora da transacao de negocio: quando um evento e lido daqui, a consulta que o
 * originou ja esta gravada. A entrega e <i>at-least-once</i> - se o processo cair entre o
 * envio e a marcacao, o evento e reenviado. A idempotencia fica com os consumidores, que
 * descartam reentregas comparando a {@code versao} do agregado.</p>
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventoRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final int tamanhoDoLote;
    private final long timeoutPublicacaoMs;

    public OutboxPublisher(OutboxEventoRepository outboxRepository,
                           KafkaTemplate<String, String> kafkaTemplate,
                           @Value("${app.outbox.tamanho-lote:100}") int tamanhoDoLote,
                           @Value("${app.outbox.timeout-publicacao-ms:5000}") long timeoutPublicacaoMs) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.tamanhoDoLote = tamanhoDoLote;
        this.timeoutPublicacaoMs = timeoutPublicacaoMs;
    }

    /**
     * O {@code initialDelay} configuravel existe para os testes: la ele e empurrado para longe
     * e cada teste chama {@link #publicarPendentes()} no momento que quer, sem disputar com um
     * agendador correndo em paralelo.
     */
    @Scheduled(fixedDelayString = "${app.outbox.intervalo-ms:2000}",
            initialDelayString = "${app.outbox.atraso-inicial-ms:0}")
    @Transactional
    public void publicarPendentes() {
        final List<OutboxEvento> pendentes =
                outboxRepository.findByPublicadoEmIsNullOrderByIdAsc(Limit.of(tamanhoDoLote));
        if (pendentes.isEmpty()) {
            return;
        }

        for (OutboxEvento evento : pendentes) {
            publicar(evento);
        }
    }

    private void publicar(OutboxEvento evento) {
        try {
            // Envio sincrono de proposito: so marcamos como publicado com o ack do broker.
            // A chave e o id da consulta, o que mantem a ordem dos eventos de uma consulta
            // dentro da mesma particao. Timeout curto para nao travar o lote inteiro nem a
            // transacao caso o broker fique lento ou fora do ar.
            kafkaTemplate.send(Topicos.CONSULTAS,
                    String.valueOf(evento.getAgregadoId()), evento.getPayload())
                    .get(timeoutPublicacaoMs, TimeUnit.MILLISECONDS);

            evento.marcarPublicado();
            log.debug("Evento {} da consulta {} publicado no topico {}",
                    evento.getTipoEvento(), evento.getAgregadoId(), Topicos.CONSULTAS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            evento.registrarFalha("Publicacao interrompida");
        } catch (Exception e) {
            // Nao relanca: um evento problematico nao pode impedir a publicacao dos demais,
            // e a proxima execucao do agendador tentara de novo.
            evento.registrarFalha(e.getMessage());
            log.warn("Falha ao publicar evento {} da consulta {} (tentativa {}): {}",
                    evento.getTipoEvento(), evento.getAgregadoId(), evento.getTentativas(),
                    e.getMessage());
        }
    }
}
