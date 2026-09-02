package br.com.fiap.historico.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.fiap.comum.evento.ConsultaEvento;
import br.com.fiap.comum.evento.StatusConsulta;
import br.com.fiap.comum.evento.TipoEvento;
import br.com.fiap.historico.exception.EventoInvalidoException;
import br.com.fiap.historico.repository.ConsultaHistoricoRepository;
import br.com.fiap.historico.repository.EventoConsultaRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras da projecao contra o banco real (H2 com as migracoes Flyway): ordenacao por versao,
 * idempotencia e a separacao entre trilha de auditoria e estado atual.
 */
@SpringBootTest
@Transactional
class ProjecaoHistoricoServiceTest {

    private static final Long ID_CONSULTA = 77L;
    private static final LocalDateTime DATA_HORA = LocalDateTime.now().plusDays(4).withNano(0);

    @Autowired
    private ProjecaoHistoricoService projecaoService;

    @Autowired
    private ConsultaHistoricoRepository consultaRepository;

    @Autowired
    private EventoConsultaRepository eventoRepository;

    @BeforeEach
    void limpar() {
        eventoRepository.deleteAll();
        consultaRepository.deleteAll();
    }

    private static ConsultaEvento evento(TipoEvento tipo, StatusConsulta status, long versao) {
        return new ConsultaEvento(ID_CONSULTA, tipo, status, 4L, "Maria Souza",
                "maria@paciente.com", 1L, "Dra. Ana Lima", "Cardiologia",
                DATA_HORA.toString(), "Retorno anual", versao, Instant.now().toString());
    }

    @Test
    void deveCriarEstadoAtualETrilhaAoReceberOPrimeiroEvento() {
        projecaoService.aplicar(evento(TipoEvento.CONSULTA_CRIADA, StatusConsulta.AGENDADA, 1L));

        final var consulta = consultaRepository.findById(ID_CONSULTA).orElseThrow();
        assertThat(consulta.getStatus()).isEqualTo(StatusConsulta.AGENDADA);
        assertThat(consulta.getPacienteNome()).isEqualTo("Maria Souza");
        assertThat(consulta.getVersao()).isEqualTo(1L);

        assertThat(eventoRepository.findByConsultaIdOrderByVersaoAscIdAsc(ID_CONSULTA))
                .singleElement()
                .satisfies(e -> assertThat(e.getTipoEvento()).isEqualTo(TipoEvento.CONSULTA_CRIADA));
    }

    @Test
    void deveAcumularATrilhaMantendoApenasUmEstadoAtual() {
        projecaoService.aplicar(evento(TipoEvento.CONSULTA_CRIADA, StatusConsulta.AGENDADA, 1L));
        projecaoService.aplicar(evento(TipoEvento.CONSULTA_ATUALIZADA, StatusConsulta.REALIZADA, 2L));
        projecaoService.aplicar(evento(TipoEvento.CONSULTA_CANCELADA, StatusConsulta.CANCELADA, 3L));

        assertThat(consultaRepository.count()).isEqualTo(1);
        assertThat(consultaRepository.findById(ID_CONSULTA).orElseThrow().getStatus())
                .isEqualTo(StatusConsulta.CANCELADA);
        assertThat(eventoRepository.findByConsultaIdOrderByVersaoAscIdAsc(ID_CONSULTA)).hasSize(3);
    }

    @Test
    void reentregaDoMesmoEventoNaoDeveDuplicarATrilha() {
        final var mesmoEvento = evento(TipoEvento.CONSULTA_CRIADA, StatusConsulta.AGENDADA, 1L);

        projecaoService.aplicar(mesmoEvento);
        projecaoService.aplicar(mesmoEvento);

        assertThat(eventoRepository.findByConsultaIdOrderByVersaoAscIdAsc(ID_CONSULTA)).hasSize(1);
        assertThat(consultaRepository.count()).isEqualTo(1);
    }

    @Test
    void eventoForaDeOrdemNaoDeveSobrescreverEstadoMaisNovo() {
        projecaoService.aplicar(evento(TipoEvento.CONSULTA_CRIADA, StatusConsulta.AGENDADA, 1L));
        projecaoService.aplicar(evento(TipoEvento.CONSULTA_CANCELADA, StatusConsulta.CANCELADA, 3L));

        // Chega atrasada uma atualizacao da versao 2, ja superada pela 3.
        projecaoService.aplicar(evento(TipoEvento.CONSULTA_ATUALIZADA, StatusConsulta.REALIZADA, 2L));

        final var consulta = consultaRepository.findById(ID_CONSULTA).orElseThrow();
        assertThat(consulta.getStatus()).isEqualTo(StatusConsulta.CANCELADA);
        assertThat(consulta.getVersao()).isEqualTo(3L);

        // A trilha, porem, registra que o evento atrasado chegou.
        assertThat(eventoRepository.findByConsultaIdOrderByVersaoAscIdAsc(ID_CONSULTA)).hasSize(3);
    }

    @Test
    void trilhaDeveVirOrdenadaPorVersao() {
        projecaoService.aplicar(evento(TipoEvento.CONSULTA_CRIADA, StatusConsulta.AGENDADA, 1L));
        projecaoService.aplicar(evento(TipoEvento.CONSULTA_CANCELADA, StatusConsulta.CANCELADA, 3L));
        projecaoService.aplicar(evento(TipoEvento.CONSULTA_ATUALIZADA, StatusConsulta.REALIZADA, 2L));

        assertThat(eventoRepository.findByConsultaIdOrderByVersaoAscIdAsc(ID_CONSULTA))
                .extracting(e -> e.getVersao())
                .containsExactly(1L, 2L, 3L);
    }

    @Test
    void deveRecusarEventoSemCamposObrigatorios() {
        final var semMedico = new ConsultaEvento(ID_CONSULTA, TipoEvento.CONSULTA_CRIADA,
                StatusConsulta.AGENDADA, 4L, "Maria", "maria@paciente.com",
                null, null, null, DATA_HORA.toString(), null, 1L, null);

        assertThatThrownBy(() -> projecaoService.aplicar(semMedico))
                .isInstanceOf(EventoInvalidoException.class)
                .hasMessageContaining("medicoId");
    }

    @Test
    void deveRecusarDataForaDoFormatoIso() {
        final var dataRuim = new ConsultaEvento(ID_CONSULTA, TipoEvento.CONSULTA_CRIADA,
                StatusConsulta.AGENDADA, 4L, "Maria", "maria@paciente.com", 1L, "Dra. Ana",
                "Cardiologia", "04/09/2026", null, 1L, null);

        assertThatThrownBy(() -> projecaoService.aplicar(dataRuim))
                .isInstanceOf(EventoInvalidoException.class)
                .hasMessageContaining("ISO-8601");
    }

    @Test
    void eventoSemInstanteDeOcorrenciaDeveUsarOMomentoDoConsumo() {
        final var semOcorridoEm = new ConsultaEvento(ID_CONSULTA, TipoEvento.CONSULTA_CRIADA,
                StatusConsulta.AGENDADA, 4L, "Maria", "maria@paciente.com", 1L, "Dra. Ana",
                "Cardiologia", DATA_HORA.toString(), null, 1L, null);

        projecaoService.aplicar(semOcorridoEm);

        assertThat(eventoRepository.findByConsultaIdOrderByVersaoAscIdAsc(ID_CONSULTA))
                .singleElement()
                .satisfies(e -> assertThat(e.getOcorridoEm()).isNotNull());
    }
}
