package br.com.fiap.notificacao.messaging;

import br.com.fiap.comum.evento.ConsultaEvento;
import br.com.fiap.comum.evento.Topicos;
import br.com.fiap.notificacao.service.NotificacaoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Consumidor do topico de consultas no grupo {@code notificacao-service}.
 *
 * <p>Usar um grupo proprio - distinto do grupo do historico - e o que faz os dois servicos
 * receberem <b>todos</b> os eventos, cada um com seus offsets, em vez de disputarem as mesmas
 * mensagens.</p>
 *
 * <p>O grupo vem da configuracao ({@code spring.kafka.consumer.group-id}) e nao de uma
 * constante fixada aqui, para que seja possivel subir o servico com um grupo novo e
 * reprocessar o topico desde o inicio.</p>
 */
@Component
public class ConsultaEventoListener {

    private static final Logger log = LoggerFactory.getLogger(ConsultaEventoListener.class);

    private final NotificacaoService notificacaoService;

    public ConsultaEventoListener(NotificacaoService notificacaoService) {
        this.notificacaoService = notificacaoService;
    }

    @KafkaListener(topics = Topicos.CONSULTAS, groupId = "${spring.kafka.consumer.group-id}")
    public void aoReceber(@Payload ConsultaEvento evento,
                          @Header(name = KafkaHeaders.RECEIVED_PARTITION, required = false) Integer particao,
                          @Header(name = KafkaHeaders.OFFSET, required = false) Long offset) {
        log.debug("Evento {} da consulta {} recebido (particao {}, offset {})",
                evento.tipoEvento(), evento.consultaId(), particao, offset);
        notificacaoService.processarEvento(evento);
    }
}
