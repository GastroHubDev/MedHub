package br.com.fiap.notificacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.fiap.comum.evento.StatusConsulta;
import br.com.fiap.notificacao.domain.ConsultaAgendada;
import br.com.fiap.notificacao.repository.ConsultaAgendadaRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LembreteSchedulerTest {

    @Mock
    private ConsultaAgendadaRepository consultaRepository;
    @Mock
    private NotificacaoService notificacaoService;

    @InjectMocks
    private LembreteScheduler scheduler;

    private static ConsultaAgendada consulta(long id, LocalDateTime dataHora) {
        return new ConsultaAgendada(id, 4L, "Maria Souza", "maria@paciente.com",
                "Dra. Ana Lima", "Cardiologia", dataHora, StatusConsulta.AGENDADA, 1L);
    }

    @Test
    void deveVarrerApenasAJanelaDeVinteEQuatroHorasDeConsultasAgendadas() {
        final var inicio = ArgumentCaptor.forClass(LocalDateTime.class);
        final var fim = ArgumentCaptor.forClass(LocalDateTime.class);
        when(consultaRepository.findByStatusAndDataHoraBetweenOrderByDataHoraAsc(
                eq(StatusConsulta.AGENDADA), inicio.capture(), fim.capture()))
                .thenReturn(List.of());

        scheduler.enviarLembretesDoProximoDia();

        assertThat(Duration.between(inicio.getValue(), fim.getValue()).toHours()).isEqualTo(24);
    }

    @Test
    void deveEnviarUmLembretePorConsultaDaJanela() {
        when(consultaRepository.findByStatusAndDataHoraBetweenOrderByDataHoraAsc(any(), any(), any()))
                .thenReturn(List.of(
                        consulta(1L, LocalDateTime.now().plusHours(5)),
                        consulta(2L, LocalDateTime.now().plusHours(20))));
        when(notificacaoService.enviarLembrete(any())).thenReturn(true);

        scheduler.enviarLembretesDoProximoDia();

        verify(notificacaoService, times(2)).enviarLembrete(any());
    }

    @Test
    void falhaEmUmaConsultaNaoDeveInterromperOLote() {
        final var primeira = consulta(1L, LocalDateTime.now().plusHours(5));
        final var segunda = consulta(2L, LocalDateTime.now().plusHours(20));
        when(consultaRepository.findByStatusAndDataHoraBetweenOrderByDataHoraAsc(any(), any(), any()))
                .thenReturn(List.of(primeira, segunda));
        when(notificacaoService.enviarLembrete(primeira))
                .thenThrow(new IllegalStateException("erro isolado"));
        when(notificacaoService.enviarLembrete(segunda)).thenReturn(true);

        scheduler.enviarLembretesDoProximoDia();

        // A segunda tem de ser processada mesmo com a primeira falhando.
        verify(notificacaoService).enviarLembrete(segunda);
    }

    @Test
    void naoDeveFalharQuandoNaoHaConsultasNaJanela() {
        when(consultaRepository.findByStatusAndDataHoraBetweenOrderByDataHoraAsc(any(), any(), any()))
                .thenReturn(List.of());

        scheduler.enviarLembretesDoProximoDia();

        verify(notificacaoService, times(0)).enviarLembrete(any());
    }
}
