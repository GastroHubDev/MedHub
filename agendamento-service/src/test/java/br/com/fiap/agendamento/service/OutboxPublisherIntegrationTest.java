package br.com.fiap.agendamento.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.fiap.agendamento.domain.OutboxEvento;
import br.com.fiap.agendamento.dto.AtualizarConsultaRequest;
import br.com.fiap.agendamento.dto.CriarConsultaRequest;
import br.com.fiap.agendamento.repository.OutboxEventoRepository;
import br.com.fiap.comum.evento.ConsultaEvento;
import br.com.fiap.comum.evento.StatusConsulta;
import br.com.fiap.comum.evento.TipoEvento;
import br.com.fiap.comum.evento.Topicos;
import br.com.fiap.comum.seguranca.Role;
import br.com.fiap.comum.seguranca.UsuarioAutenticado;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;

/**
 * Fecha o ciclo do padrao outbox contra um broker real (embarcado): a consulta e gravada, o
 * evento fica pendente na tabela, o publicador o envia ao Kafka e so entao o marca publicado.
 */
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {Topicos.CONSULTAS})
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        // Banco proprio: estes testes nao podem ser transacionais (o publicador precisa de
        // dados comitados), entao as consultas que eles criam vazariam para os demais testes
        // se compartilhassem a instancia H2 em memoria.
        "spring.datasource.url=jdbc:h2:mem:agendamento_outbox;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
class OutboxPublisherIntegrationTest {

    @Autowired
    private ConsultaService consultaService;

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Autowired
    private OutboxEventoRepository outboxRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EmbeddedKafkaBroker broker;

    private Consumer<String, String> consumidor;

    @BeforeEach
    void prepararConsumidorEContexto() {
        final Map<String, Object> configuracao =
                KafkaTestUtils.consumerProps("teste-outbox", "true", broker);
        consumidor = new DefaultKafkaConsumerFactory<>(configuracao,
                new StringDeserializer(), new StringDeserializer()).createConsumer();
        broker.consumeFromAnEmbeddedTopic(consumidor, Topicos.CONSULTAS);

        // O ConsultaService consulta o contexto de seguranca; aqui autenticamos um medico.
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        UsuarioAutenticado.doToken(1L, "medico@hospital.com", Role.MEDICO),
                        null, List.of()));
    }

    @AfterEach
    void limpar() {
        consumidor.close();
        SecurityContextHolder.clearContext();
        outboxRepository.deleteAll();
    }

    /**
     * Le tudo que estiver disponivel e devolve o ultimo registro. Os testes desta classe nao sao
     * transacionais (o publicador precisa enxergar dados comitados), entao o topico acumula os
     * eventos dos testes anteriores - o que interessa e sempre o mais recente. Com uma unica
     * particao, a ordem do topico e total.
     */
    private ConsumerRecord<String, String> ultimoRegistroDoTopico() {
        final var registros = KafkaTestUtils.getRecords(consumidor, Duration.ofSeconds(10));
        assertThat(registros.count()).isPositive();

        ConsumerRecord<String, String> ultimo = null;
        for (ConsumerRecord<String, String> registro : registros.records(Topicos.CONSULTAS)) {
            ultimo = registro;
        }
        return ultimo;
    }

    private ConsultaEvento ultimoEventoDoTopico() throws Exception {
        return objectMapper.readValue(ultimoRegistroDoTopico().value(), ConsultaEvento.class);
    }

    @Test
    void deveGravarEventoPendenteEPublicarSomenteQuandoOPublicadorRoda() throws Exception {
        final LocalDateTime dataHora = LocalDateTime.now().plusDays(40).withNano(0);

        consultaService.criar(new CriarConsultaRequest(4L, 1L, dataHora, "Consulta via outbox"));

        // Antes do publicador: gravado no banco, ainda nao no topico.
        assertThat(outboxRepository.countByPublicadoEmIsNull()).isEqualTo(1);

        outboxPublisher.publicarPendentes();

        final ConsultaEvento evento = ultimoEventoDoTopico();
        assertThat(evento.tipoEvento()).isEqualTo(TipoEvento.CONSULTA_CRIADA);
        assertThat(evento.status()).isEqualTo(StatusConsulta.AGENDADA);
        assertThat(evento.pacienteEmail()).isEqualTo("paciente@hospital.com");
        assertThat(evento.medicoEspecialidade()).isEqualTo("Cardiologia");
        assertThat(evento.dataHora()).isEqualTo(dataHora.toString());
        assertThat(evento.versao()).isEqualTo(1L);

        assertThat(outboxRepository.countByPublicadoEmIsNull()).isZero();
    }

    @Test
    void deveUsarOIdDaConsultaComoChaveDaMensagem() throws Exception {
        final LocalDateTime dataHora = LocalDateTime.now().plusDays(41).withNano(0);
        final var consulta = consultaService.criar(
                new CriarConsultaRequest(4L, 1L, dataHora, "Chave da mensagem"));

        outboxPublisher.publicarPendentes();

        // A chave e o que garante que todos os eventos de uma consulta caiam na mesma particao
        // e sejam consumidos em ordem.
        assertThat(ultimoRegistroDoTopico().key()).isEqualTo(String.valueOf(consulta.getId()));
    }

    @Test
    void atualizacaoDeveGerarEventoComVersaoIncrementada() throws Exception {
        final LocalDateTime dataHora = LocalDateTime.now().plusDays(42).withNano(0);
        final var consulta = consultaService.criar(
                new CriarConsultaRequest(4L, 1L, dataHora, "Original"));
        outboxPublisher.publicarPendentes();
        ultimoEventoDoTopico();

        consultaService.atualizar(consulta.getId(),
                new AtualizarConsultaRequest(null, StatusConsulta.REALIZADA, "Compareceu"));
        outboxPublisher.publicarPendentes();

        final ConsultaEvento evento = ultimoEventoDoTopico();
        assertThat(evento.tipoEvento()).isEqualTo(TipoEvento.CONSULTA_ATUALIZADA);
        assertThat(evento.status()).isEqualTo(StatusConsulta.REALIZADA);
        assertThat(evento.versao()).isEqualTo(2L);
    }

    @Test
    void cancelamentoDeveGerarEventoDeCancelamento() throws Exception {
        final LocalDateTime dataHora = LocalDateTime.now().plusDays(43).withNano(0);
        final var consulta = consultaService.criar(
                new CriarConsultaRequest(4L, 1L, dataHora, "Sera cancelada"));
        outboxPublisher.publicarPendentes();
        ultimoEventoDoTopico();

        consultaService.cancelar(consulta.getId());
        outboxPublisher.publicarPendentes();

        final ConsultaEvento evento = ultimoEventoDoTopico();
        assertThat(evento.tipoEvento()).isEqualTo(TipoEvento.CONSULTA_CANCELADA);
        assertThat(evento.status()).isEqualTo(StatusConsulta.CANCELADA);
    }

    @Test
    void publicadorDeveSerIdempotenteQuandoNaoHaPendentes() {
        outboxPublisher.publicarPendentes();
        outboxPublisher.publicarPendentes();

        assertThat(outboxRepository.countByPublicadoEmIsNull()).isZero();
    }

    @Test
    void falhaDePublicacaoDeveContarTentativaSemMarcarComoPublicado() {
        final OutboxEvento evento =
                new OutboxEvento(999L, TipoEvento.CONSULTA_CRIADA, "{\"consultaId\":999}");

        evento.registrarFalha("broker indisponivel");

        // Continua pendente: a proxima execucao do agendador tentara de novo, e nenhum evento
        // e perdido por uma falha transitoria de rede.
        assertThat(evento.getPublicadoEm()).isNull();
        assertThat(evento.getTentativas()).isEqualTo(1);
        assertThat(evento.getUltimoErro()).isEqualTo("broker indisponivel");
    }

    @Test
    void mensagemDeErroMuitoLongaDeveSerTruncadaParaCaberNaColuna() {
        final OutboxEvento evento =
                new OutboxEvento(999L, TipoEvento.CONSULTA_CRIADA, "{}");

        evento.registrarFalha("x".repeat(900));

        assertThat(evento.getUltimoErro()).hasSize(500);
    }
}
