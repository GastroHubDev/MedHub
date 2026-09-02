package br.com.fiap.notificacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.fiap.comum.evento.ConsultaEvento;
import br.com.fiap.comum.evento.StatusConsulta;
import br.com.fiap.comum.evento.TipoEvento;
import br.com.fiap.notificacao.domain.ConsultaAgendada;
import br.com.fiap.notificacao.domain.Notificacao;
import br.com.fiap.notificacao.domain.StatusEnvio;
import br.com.fiap.notificacao.domain.TipoNotificacao;
import br.com.fiap.notificacao.exception.EventoInvalidoException;
import br.com.fiap.notificacao.repository.ConsultaAgendadaRepository;
import br.com.fiap.notificacao.repository.NotificacaoRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mail.MailSendException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificacaoServiceTest {

    private static final Long ID_CONSULTA = 42L;
    private static final LocalDateTime DATA_HORA = LocalDateTime.now().plusDays(3).withNano(0);

    @Mock
    private ConsultaAgendadaRepository consultaRepository;
    @Mock
    private NotificacaoRepository notificacaoRepository;
    @Mock
    private EmailSender emailSender;

    private NotificacaoService notificacaoService;

    private NotificacaoService servico() {
        if (notificacaoService == null) {
            notificacaoService = new NotificacaoService(consultaRepository, notificacaoRepository,
                    new RedatorDeMensagens(), emailSender);
        }
        return notificacaoService;
    }

    private static ConsultaEvento evento(TipoEvento tipo, StatusConsulta status, long versao) {
        return new ConsultaEvento(ID_CONSULTA, tipo, status,
                4L, "Maria Souza", "maria@paciente.com",
                1L, "Dra. Ana Lima", "Cardiologia",
                DATA_HORA.toString(), "Retorno anual", versao,
                java.time.Instant.now().toString());
    }

    private static ConsultaAgendada projecao(long versao) {
        return new ConsultaAgendada(ID_CONSULTA, 4L, "Maria Souza", "maria@paciente.com",
                "Dra. Ana Lima", "Cardiologia", DATA_HORA, StatusConsulta.AGENDADA, versao);
    }

    @Nested
    class AoProcessarEvento {

        @Test
        void deveCriarProjecaoEEnviarConfirmacao() {
            when(consultaRepository.findById(ID_CONSULTA)).thenReturn(Optional.empty());
            when(consultaRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            servico().processarEvento(evento(TipoEvento.CONSULTA_CRIADA, StatusConsulta.AGENDADA, 1L));

            verify(emailSender).enviar(eq("maria@paciente.com"),
                    eq("Consulta agendada"), anyString());

            final var salva = ArgumentCaptor.forClass(Notificacao.class);
            verify(notificacaoRepository).save(salva.capture());
            assertThat(salva.getValue().getTipo()).isEqualTo(TipoNotificacao.CONFIRMACAO);
            assertThat(salva.getValue().getStatus()).isEqualTo(StatusEnvio.ENVIADA);
            assertThat(salva.getValue().getVersaoConsulta()).isEqualTo(1L);
        }

        @Test
        void reentregaDoMesmoEventoNaoDeveGerarSegundoEmail() {
            when(consultaRepository.findById(ID_CONSULTA)).thenReturn(Optional.of(projecao(1L)));
            when(notificacaoRepository.existsByConsultaIdAndTipoAndVersaoConsultaAndStatus(
                    ID_CONSULTA, TipoNotificacao.CONFIRMACAO, 1L, StatusEnvio.ENVIADA))
                    .thenReturn(true);

            servico().processarEvento(evento(TipoEvento.CONSULTA_CRIADA, StatusConsulta.AGENDADA, 1L));

            verify(emailSender, never()).enviar(anyString(), anyString(), anyString());
            verify(notificacaoRepository, never()).save(any());
        }

        @Test
        void eventoComVersaoAntigaNaoDeveSobrescreverProjecaoMaisNova() {
            final ConsultaAgendada atual = projecao(5L);
            when(consultaRepository.findById(ID_CONSULTA)).thenReturn(Optional.of(atual));

            servico().processarEvento(evento(TipoEvento.CONSULTA_ATUALIZADA, StatusConsulta.REALIZADA, 3L));

            assertThat(atual.getVersao()).isEqualTo(5L);
            assertThat(atual.getStatus()).isEqualTo(StatusConsulta.AGENDADA);
            verify(emailSender, never()).enviar(anyString(), anyString(), anyString());
        }

        @Test
        void deveAtualizarProjecaoQuandoAVersaoEMaisNova() {
            final ConsultaAgendada atual = projecao(1L);
            when(consultaRepository.findById(ID_CONSULTA)).thenReturn(Optional.of(atual));

            servico().processarEvento(evento(TipoEvento.CONSULTA_ATUALIZADA, StatusConsulta.REALIZADA, 2L));

            assertThat(atual.getVersao()).isEqualTo(2L);
            assertThat(atual.getStatus()).isEqualTo(StatusConsulta.REALIZADA);
            verify(emailSender).enviar(anyString(), eq("Sua consulta foi atualizada"), anyString());
        }

        @Test
        void cancelamentoDeveAtualizarStatusEAvisarOPaciente() {
            final ConsultaAgendada atual = projecao(1L);
            when(consultaRepository.findById(ID_CONSULTA)).thenReturn(Optional.of(atual));

            servico().processarEvento(evento(TipoEvento.CONSULTA_CANCELADA, StatusConsulta.CANCELADA, 2L));

            assertThat(atual.getStatus()).isEqualTo(StatusConsulta.CANCELADA);
            verify(emailSender).enviar(anyString(), eq("Sua consulta foi cancelada"), anyString());
        }

        @Test
        void falhaDeSmtpDeveSerRegistradaSemDerrubarOProcessamento() {
            when(consultaRepository.findById(ID_CONSULTA)).thenReturn(Optional.empty());
            when(consultaRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            org.mockito.Mockito.doThrow(new MailSendException("servidor fora do ar"))
                    .when(emailSender).enviar(anyString(), anyString(), anyString());

            // Nao deve lancar: perder o e-mail e aceitavel, perder a projecao nao.
            servico().processarEvento(evento(TipoEvento.CONSULTA_CRIADA, StatusConsulta.AGENDADA, 1L));

            final var salva = ArgumentCaptor.forClass(Notificacao.class);
            verify(notificacaoRepository).save(salva.capture());
            assertThat(salva.getValue().getStatus()).isEqualTo(StatusEnvio.FALHA);
            assertThat(salva.getValue().getErro()).contains("servidor fora do ar");
        }
    }

    @Nested
    class AoValidarEvento {

        @Test
        void deveRecusarEventoSemEmailDoPaciente() {
            final var invalido = new ConsultaEvento(ID_CONSULTA, TipoEvento.CONSULTA_CRIADA,
                    StatusConsulta.AGENDADA, 4L, "Maria", "  ", 1L, "Dra. Ana", "Cardiologia",
                    DATA_HORA.toString(), null, 1L, null);

            assertThatThrownBy(() -> servico().processarEvento(invalido))
                    .isInstanceOf(EventoInvalidoException.class)
                    .hasMessageContaining("pacienteEmail");
        }

        @Test
        void deveRecusarEventoSemStatus() {
            final var invalido = new ConsultaEvento(ID_CONSULTA, TipoEvento.CONSULTA_CRIADA,
                    null, 4L, "Maria", "maria@paciente.com", 1L, "Dra. Ana", "Cardiologia",
                    DATA_HORA.toString(), null, 1L, null);

            assertThatThrownBy(() -> servico().processarEvento(invalido))
                    .isInstanceOf(EventoInvalidoException.class)
                    .hasMessageContaining("status");
        }

        @Test
        void deveRecusarDataForaDoFormatoIso() {
            final var invalido = new ConsultaEvento(ID_CONSULTA, TipoEvento.CONSULTA_CRIADA,
                    StatusConsulta.AGENDADA, 4L, "Maria", "maria@paciente.com", 1L, "Dra. Ana",
                    "Cardiologia", "10/09/2026 14:30", null, 1L, null);

            assertThatThrownBy(() -> servico().processarEvento(invalido))
                    .isInstanceOf(EventoInvalidoException.class)
                    .hasMessageContaining("ISO-8601");
        }
    }

    @Nested
    class AoEnviarLembrete {

        @Test
        void deveEnviarLembreteQuandoAindaNaoFoiEnviado() {
            when(notificacaoRepository.existsByConsultaIdAndTipoAndDataHoraConsultaAndStatus(
                    ID_CONSULTA, TipoNotificacao.LEMBRETE_24H, DATA_HORA, StatusEnvio.ENVIADA))
                    .thenReturn(false);

            final boolean enviado = servico().enviarLembrete(projecao(1L));

            assertThat(enviado).isTrue();
            verify(emailSender).enviar(anyString(), eq("Lembrete: sua consulta e amanha"), anyString());
        }

        @Test
        void naoDeveReenviarLembreteJaEnviadoParaOMesmoHorario() {
            when(notificacaoRepository.existsByConsultaIdAndTipoAndDataHoraConsultaAndStatus(
                    ID_CONSULTA, TipoNotificacao.LEMBRETE_24H, DATA_HORA, StatusEnvio.ENVIADA))
                    .thenReturn(true);

            final boolean enviado = servico().enviarLembrete(projecao(1L));

            assertThat(enviado).isFalse();
            verify(emailSender, never()).enviar(anyString(), anyString(), anyString());
        }
    }

    @Nested
    class AoRedigirMensagem {

        @Test
        void corpoDeveTrazerNomeDoPacienteMedicoEDataFormatada() {
            final String corpo = new RedatorDeMensagens()
                    .corpo(TipoNotificacao.CONFIRMACAO, projecao(1L));

            assertThat(corpo)
                    .contains("Ola, Maria Souza!")
                    .contains("Dra. Ana Lima (Cardiologia)")
                    .contains(DATA_HORA.format(
                            java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy 'as' HH:mm")));
        }

        @Test
        void especialidadeAusenteNaoDeveVazarNuloNoTexto() {
            final var semEspecialidade = new ConsultaAgendada(ID_CONSULTA, 4L, "Maria Souza",
                    "maria@paciente.com", "Dra. Ana Lima", null, DATA_HORA,
                    StatusConsulta.AGENDADA, 1L);

            final String corpo = new RedatorDeMensagens()
                    .corpo(TipoNotificacao.LEMBRETE_24H, semEspecialidade);

            assertThat(corpo).doesNotContain("null").contains("clinica geral");
        }
    }
}
