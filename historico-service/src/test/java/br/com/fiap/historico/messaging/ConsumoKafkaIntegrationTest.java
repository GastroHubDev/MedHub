package br.com.fiap.historico.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import br.com.fiap.comum.evento.ConsultaEvento;
import br.com.fiap.comum.evento.StatusConsulta;
import br.com.fiap.comum.evento.TipoEvento;
import br.com.fiap.comum.evento.Topicos;
import br.com.fiap.comum.seguranca.Role;
import br.com.fiap.comum.seguranca.UsuarioAutenticado;
import br.com.fiap.historico.repository.ConsultaHistoricoRepository;
import br.com.fiap.historico.repository.EventoConsultaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.ExecutionGraphQlService;
import org.springframework.graphql.test.tester.ExecutionGraphQlServiceTester;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;

/**
 * A cadeia completa do CQRS: evento no topico Kafka, consumido pelo grupo do historico,
 * projetado no read model e respondido por uma query GraphQL - incluindo a trilha de eventos.
 */
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {Topicos.CONSULTAS, Topicos.CONSULTAS_DLT})
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.listener.auto-startup=true",
        "spring.datasource.url=jdbc:h2:mem:historico_kafka;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
class ConsumoKafkaIntegrationTest {

    private static final Duration ESPERA = Duration.ofSeconds(20);

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ConsultaHistoricoRepository consultaRepository;

    @Autowired
    private EventoConsultaRepository eventoRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ExecutionGraphQlService graphQlService;

    @Autowired
    private EmbeddedKafkaBroker broker;

    private GraphQlTester tester;

    @BeforeEach
    void prepararContexto() {
        tester = ExecutionGraphQlServiceTester.create(graphQlService);
        eventoRepository.deleteAll();
        consultaRepository.deleteAll();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        UsuarioAutenticado.doToken(1L, "medico@hospital.com", Role.MEDICO), null,
                        List.of(new SimpleGrantedAuthority(Role.MEDICO.authority()))));
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
        eventoRepository.deleteAll();
        consultaRepository.deleteAll();
    }

    private void publicar(ConsultaEvento evento) throws Exception {
        kafkaTemplate.send(Topicos.CONSULTAS, String.valueOf(evento.consultaId()),
                objectMapper.writeValueAsString(evento)).get();
    }

    private static ConsultaEvento evento(long consultaId, TipoEvento tipo,
                                         StatusConsulta status, long versao, String observacoes) {
        return new ConsultaEvento(consultaId, tipo, status, 4L, "Maria Souza",
                "maria@paciente.com", 1L, "Dra. Ana Lima", "Cardiologia",
                LocalDateTime.now().plusDays(6).withNano(0).toString(),
                observacoes, versao, Instant.now().toString());
    }

    @Test
    void eventoNoTopicoDeveAparecerNaQueryGraphQl() throws Exception {
        publicar(evento(201L, TipoEvento.CONSULTA_CRIADA, StatusConsulta.AGENDADA, 1L, "Retorno"));

        await().atMost(ESPERA).untilAsserted(() ->
                assertThat(consultaRepository.findById(201L)).isPresent());

        tester.document("""
                        query { consultaHistorico(consultaId: 201) {
                            status observacoes
                            medico { nome especialidade }
                            eventos { tipo statusResultante }
                        } }""")
                .execute()
                .path("consultaHistorico.status").entity(String.class).isEqualTo("AGENDADA")
                .path("consultaHistorico.observacoes").entity(String.class).isEqualTo("Retorno")
                .path("consultaHistorico.medico.especialidade").entity(String.class)
                .isEqualTo("Cardiologia")
                .path("consultaHistorico.eventos").entityList(Object.class).hasSize(1);
    }

    @Test
    void edicaoFeitaNoAgendamentoDeveChegarAoHistoricoComATrilha() throws Exception {
        publicar(evento(202L, TipoEvento.CONSULTA_CRIADA, StatusConsulta.AGENDADA, 1L, "Original"));
        await().atMost(ESPERA).until(() -> consultaRepository.findById(202L).isPresent());

        // Equivale ao medico chamando PUT /api/consultas/202 no servico de agendamento.
        publicar(evento(202L, TipoEvento.CONSULTA_ATUALIZADA, StatusConsulta.REALIZADA, 2L,
                "Paciente compareceu"));

        await().atMost(ESPERA).untilAsserted(() ->
                assertThat(consultaRepository.findById(202L).orElseThrow().getVersao())
                        .isEqualTo(2L));

        tester.document("""
                        query { consultaHistorico(consultaId: 202) {
                            status observacoes versao
                            eventos { tipo statusResultante versao }
                        } }""")
                .execute()
                .path("consultaHistorico.status").entity(String.class).isEqualTo("REALIZADA")
                .path("consultaHistorico.observacoes").entity(String.class)
                .isEqualTo("Paciente compareceu")
                .path("consultaHistorico.eventos").entityList(Object.class).hasSize(2)
                .path("consultaHistorico.eventos[1].tipo").entity(String.class)
                .isEqualTo("CONSULTA_ATUALIZADA");
    }

    @Test
    void payloadInvalidoDeveIrParaODeadLetterTopicSemSujarOReadModel() throws Exception {
        final Map<String, Object> configuracao =
                KafkaTestUtils.consumerProps("teste-dlt-historico", "true", broker);
        try (Consumer<String, String> consumidorDlt = new DefaultKafkaConsumerFactory<>(
                configuracao, new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            broker.consumeFromAnEmbeddedTopic(consumidorDlt, Topicos.CONSULTAS_DLT);

            kafkaTemplate.send(Topicos.CONSULTAS, "203", """
                    {"consultaId":203,"tipoEvento":"CONSULTA_CRIADA","status":"AGENDADA",
                     "pacienteId":4,"pacienteNome":"Maria","pacienteEmail":"maria@paciente.com",
                     "medicoId":null,"medicoNome":null,"medicoEspecialidade":null,
                     "dataHora":"2027-01-10T14:30:00","observacoes":null,"versao":1,
                     "ocorridoEm":"2026-09-02T10:00:00Z"}""").get();

            assertThat(KafkaTestUtils.getRecords(consumidorDlt, ESPERA)
                    .records(Topicos.CONSULTAS_DLT)).isNotEmpty();
            assertThat(consultaRepository.findById(203L)).isEmpty();
        }
    }

    @Test
    void readModelDeveSerReconstruivelReprocessandoOLog() throws Exception {
        publicar(evento(204L, TipoEvento.CONSULTA_CRIADA, StatusConsulta.AGENDADA, 1L, "Inicial"));
        publicar(evento(204L, TipoEvento.CONSULTA_ATUALIZADA, StatusConsulta.REALIZADA, 2L, "Final"));

        await().atMost(ESPERA).untilAsserted(() ->
                assertThat(consultaRepository.findById(204L).orElseThrow().getVersao())
                        .isEqualTo(2L));

        // Apaga o read model, como quem descarta o banco do servico de historico...
        consultaRepository.deleteAll();
        eventoRepository.deleteAll();
        assertThat(consultaRepository.findById(204L)).isEmpty();

        // ...e reprocessa o log desde o inicio com um group.id novo. E este o argumento
        // pro-Kafka: os eventos continuam no topico, entao o historico se reconstroi sozinho.
        final Map<String, Object> configuracao =
                KafkaTestUtils.consumerProps("historico-reconstrucao", "true", broker);
        try (Consumer<String, String> reprocessador = new DefaultKafkaConsumerFactory<>(
                configuracao, new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            broker.consumeFromAnEmbeddedTopic(reprocessador, Topicos.CONSULTAS);

            final var registros = KafkaTestUtils.getRecords(reprocessador, ESPERA);
            assertThat(registros.records(Topicos.CONSULTAS)).hasSizeGreaterThanOrEqualTo(2);
        }
    }
}
