package br.com.fiap.historico.messaging;

import br.com.fiap.comum.evento.ConsultaEvento;
import br.com.fiap.comum.evento.Topicos;
import br.com.fiap.historico.service.ProjecaoHistoricoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Consumidor do topico de consultas no grupo {@code historico-service}.
 *
 * <p>Grupo proprio, offsets proprios: o historico le o mesmo topico que o servico de
 * notificacao sem competir com ele. E o que permite reconstruir o read model do zero
 * reprocessando o log desde o inicio, sem afetar as notificacoes.</p>
 *
 * <p>O grupo vem da configuracao ({@code spring.kafka.consumer.group-id}), e nao de uma
 * constante fixada aqui: e assim que a reconstrucao e disparada, subindo o servico com
 * SPRING_KAFKA_CONSUMER_GROUP_ID diferente e auto-offset-reset=earliest.</p>
 */
@Component
public class ConsultaEventoListener {

    private static final Logger log = LoggerFactory.getLogger(ConsultaEventoListener.class);

    private final ProjecaoHistoricoService projecaoService;

    public ConsultaEventoListener(ProjecaoHistoricoService projecaoService) {
        this.projecaoService = projecaoService;
    }

    @KafkaListener(topics = Topicos.CONSULTAS, groupId = "${spring.kafka.consumer.group-id}")
    public void aoReceber(@Payload ConsultaEvento evento,
                          @Header(name = KafkaHeaders.RECEIVED_PARTITION, required = false) Integer particao,
                          @Header(name = KafkaHeaders.OFFSET, required = false) Long offset) {
        log.debug("Evento {} da consulta {} recebido (particao {}, offset {})",
                evento.tipoEvento(), evento.consultaId(), particao, offset);
        projecaoService.aplicar(evento);
    }
}
