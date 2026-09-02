package br.com.fiap.notificacao.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import br.com.fiap.comum.evento.ConsultaEvento;
import br.com.fiap.comum.evento.StatusConsulta;
import br.com.fiap.comum.evento.TipoEvento;
import br.com.fiap.comum.evento.Topicos;
import br.com.fiap.notificacao.domain.StatusEnvio;
import br.com.fiap.notificacao.domain.TipoNotificacao;
import br.com.fiap.notificacao.repository.ConsultaAgendadaRepository;
import br.com.fiap.notificacao.repository.NotificacaoRepository;
import br.com.fiap.notificacao.service.EmailSender;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.TestPropertySource;

/**
 * Fluxo real do consumidor contra um broker embarcado: evento publicado no topico, listener
 * ativo, projecao gravada e notificacao registrada - e payload invalido indo para a DLT.
 */
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {Topicos.CONSULTAS, Topicos.CONSULTAS_DLT})
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        // Este e o unico teste que precisa do listener de fato consumindo.
        "spring.kafka.listener.auto-startup=true",
        "spring.datasource.url=jdbc:h2:mem:notificacao_kafka;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
class ConsumoKafkaIntegrationTest {

    private static final Duration ESPERA = Duration.ofSeconds(20);

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ConsultaAgendadaRepository consultaRepository;

    @Autowired
    private NotificacaoRepository notificacaoRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EmbeddedKafkaBroker broker;

    /** Evita depender de um servidor SMTP para exercitar o caminho do Kafka. */
    @MockitoBean
    private EmailSender emailSender;

    @BeforeEach
    void limparBase() {
        notificacaoRepository.deleteAll();
        consultaRepository.deleteAll();
    }

    @AfterEach
    void limparDepois() {
        notificacaoRepository.deleteAll();
        consultaRepository.deleteAll();
    }

    private void publicar(ConsultaEvento evento) throws Exception {
        kafkaTemplate.send(Topicos.CONSULTAS, String.valueOf(evento.consultaId()),
                objectMapper.writeValueAsString(evento)).get();
    }

    private static ConsultaEvento evento(long consultaId, TipoEvento tipo,
                                         StatusConsulta status, long versao) {
        return new ConsultaEvento(consultaId, tipo, status,
                4L, "Maria Souza", "maria@paciente.com",
                1L, "Dra. Ana Lima", "Cardiologia",
                LocalDateTime.now().plusDays(3).withNano(0).toString(),
                "Retorno", versao, Instant.now().toString());
    }

    @Test
    void eventoPublicadoDeveVirarProjecaoENotificacao() throws Exception {
        publicar(evento(101L, TipoEvento.CONSULTA_CRIADA, StatusConsulta.AGENDADA, 1L));

        await().atMost(ESPERA).untilAsserted(() -> {
            final var projecao = consultaRepository.findById(101L);
            assertThat(projecao).isPresent();
            assertThat(projecao.get().getPacienteEmail()).isEqualTo("maria@paciente.com");
            assertThat(projecao.get().getStatus()).isEqualTo(StatusConsulta.AGENDADA);

            assertThat(notificacaoRepository.findByConsultaIdOrderByEnviadoEmDesc(101L))
                    .singleElement()
                    .satisfies(n -> {
                        assertThat(n.getTipo()).isEqualTo(TipoNotificacao.CONFIRMACAO);
                        assertThat(n.getStatus()).isEqualTo(StatusEnvio.ENVIADA);
                    });
        });
    }

    @Test
    void cancelamentoDeveAtualizarAProjecaoParaCancelada() throws Exception {
        publicar(evento(102L, TipoEvento.CONSULTA_CRIADA, StatusConsulta.AGENDADA, 1L));
        await().atMost(ESPERA).until(() -> consultaRepository.findById(102L).isPresent());

        publicar(evento(102L, TipoEvento.CONSULTA_CANCELADA, StatusConsulta.CANCELADA, 2L));

        await().atMost(ESPERA).untilAsserted(() -> {
            final var projecao = consultaRepository.findById(102L).orElseThrow();
            assertThat(projecao.getStatus()).isEqualTo(StatusConsulta.CANCELADA);
            assertThat(projecao.getVersao()).isEqualTo(2L);
        });
    }

    @Test
    void reentregaDoMesmoEventoNaoDeveDuplicarNotificacao() throws Exception {
        final var mesmoEvento = evento(103L, TipoEvento.CONSULTA_CRIADA, StatusConsulta.AGENDADA, 1L);

        publicar(mesmoEvento);
        await().atMost(ESPERA).until(() ->
                !notificacaoRepository.findByConsultaIdOrderByEnviadoEmDesc(103L).isEmpty());

        // Reentrega: exatamente o mesmo payload, como faria um rebalanceamento do consumidor.
        publicar(mesmoEvento);

        await().during(Duration.ofSeconds(3)).atMost(ESPERA).untilAsserted(() ->
                assertThat(notificacaoRepository.findByConsultaIdOrderByEnviadoEmDesc(103L))
                        .hasSize(1));
    }

    @Test
    void payloadInvalidoDeveIrParaODeadLetterTopic() throws Exception {
        final Map<String, Object> configuracao =
                KafkaTestUtils.consumerProps("teste-dlt", "true", broker);
        try (Consumer<String, String> consumidorDlt = new DefaultKafkaConsumerFactory<>(
                configuracao, new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            broker.consumeFromAnEmbeddedTopic(consumidorDlt, Topicos.CONSULTAS_DLT);

            // Falta pacienteEmail: EventoInvalidoException, marcada como nao-retryavel.
            kafkaTemplate.send(Topicos.CONSULTAS, "104", """
                    {"consultaId":104,"tipoEvento":"CONSULTA_CRIADA","status":"AGENDADA",
                     "pacienteId":4,"pacienteNome":"Maria","pacienteEmail":null,
                     "medicoId":1,"medicoNome":"Dra. Ana","medicoEspecialidade":"Cardiologia",
                     "dataHora":"2027-01-10T14:30:00","observacoes":null,"versao":1,
                     "ocorridoEm":"2026-09-02T10:00:00Z"}""").get();

            final var registros = KafkaTestUtils.getRecords(consumidorDlt, ESPERA);

            assertThat(registros.records(Topicos.CONSULTAS_DLT)).isNotEmpty();
            assertThat(consultaRepository.findById(104L)).isEmpty();
        }
    }
}
