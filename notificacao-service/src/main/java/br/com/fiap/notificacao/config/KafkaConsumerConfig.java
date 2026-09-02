package br.com.fiap.notificacao.config;

import br.com.fiap.comum.evento.Topicos;
import br.com.fiap.notificacao.exception.EventoInvalidoException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.converter.JsonMessageConverter;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Tratamento de erro do consumidor.
 *
 * <p>Falha transitoria (banco fora do ar, por exemplo) merece retentativa; payload malformado
 * nao vai melhorar tentando de novo e so ocuparia o consumidor. Por isso
 * {@link EventoInvalidoException} e marcada como nao-retryavel e vai direto ao dead letter
 * topic, enquanto o restante tenta tres vezes com um segundo de intervalo.</p>
 */
@Configuration
public class KafkaConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    private static final long INTERVALO_MS = 1_000L;
    private static final long MAXIMO_TENTATIVAS = 3L;

    /**
     * Converte o JSON do topico para {@code ConsultaEvento} pelo tipo declarado no metodo do
     * listener, e nao por um header de tipo - assim o produtor nao precisa enviar metadados
     * de classe Java, mantendo o contrato interoperavel.
     */
    @Bean
    public JsonMessageConverter jsonMessageConverter() {
        return new JsonMessageConverter();
    }

    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {
        final var recuperador = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (registro, excecao) -> destinoDlt(registro, excecao));

        final var tratador = new DefaultErrorHandler(recuperador,
                new FixedBackOff(INTERVALO_MS, MAXIMO_TENTATIVAS));
        tratador.addNotRetryableExceptions(EventoInvalidoException.class);
        return tratador;
    }

    private static TopicPartition destinoDlt(ConsumerRecord<?, ?> registro, Exception excecao) {
        log.error("Enviando ao dead letter topic {} a mensagem do offset {}: {}",
                Topicos.CONSULTAS_DLT, registro.offset(), excecao.getMessage());
        // Particao -1 deixa o proprio Kafka escolher: a DLT tem uma particao so, e fixar o
        // numero da particao de origem quebraria se o topico principal tivesse mais que ela.
        return new TopicPartition(Topicos.CONSULTAS_DLT, -1);
    }
}
