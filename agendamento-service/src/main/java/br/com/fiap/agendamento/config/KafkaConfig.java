package br.com.fiap.agendamento.config;

import br.com.fiap.comum.evento.Topicos;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Topologia do Kafka declarada pelo produtor.
 *
 * <p>Criar os topicos por {@code NewTopic} (o {@code KafkaAdmin} do Boot aplica na subida)
 * evita um passo manual de {@code kafka-topics.sh} antes de rodar o projeto.</p>
 */
@Configuration
public class KafkaConfig {

    private static final int PARTICOES = 3;
    private static final short REPLICAS = 1;

    /**
     * Tres particoes com a chave sendo o id da consulta: eventos da mesma consulta caem sempre
     * na mesma particao e sao consumidos em ordem, enquanto consultas diferentes escalam em
     * paralelo. Uma replica so porque o compose sobe um unico broker.
     */
    @Bean
    public NewTopic topicoConsultas() {
        return TopicBuilder.name(Topicos.CONSULTAS)
                .partitions(PARTICOES)
                .replicas(REPLICAS)
                .build();
    }

    /** Dead letter topic: uma particao basta, o volume esperado e residual. */
    @Bean
    public NewTopic topicoConsultasDlt() {
        return TopicBuilder.name(Topicos.CONSULTAS_DLT)
                .partitions(1)
                .replicas(REPLICAS)
                .build();
    }
}
