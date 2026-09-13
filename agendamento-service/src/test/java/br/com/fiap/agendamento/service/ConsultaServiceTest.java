package br.com.fiap.agendamento.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.fiap.agendamento.domain.Consulta;
import br.com.fiap.agendamento.domain.Medico;
import br.com.fiap.agendamento.domain.Paciente;
import br.com.fiap.agendamento.domain.Usuario;
import br.com.fiap.agendamento.dto.AtualizarConsultaRequest;
import br.com.fiap.agendamento.dto.CriarConsultaRequest;
import br.com.fiap.agendamento.exception.RecursoNaoEncontradoException;
import br.com.fiap.agendamento.exception.RegraDeNegocioException;
import br.com.fiap.agendamento.repository.ConsultaRepository;
import br.com.fiap.agendamento.repository.MedicoRepository;
import br.com.fiap.agendamento.repository.PacienteRepository;
import br.com.fiap.comum.evento.StatusConsulta;
import br.com.fiap.comum.evento.TipoEvento;
import br.com.fiap.comum.seguranca.ContextoSeguranca;
import br.com.fiap.comum.seguranca.Role;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ConsultaServiceTest {

    private static final Long ID_PACIENTE = 4L;
    private static final Long ID_MEDICO = 1L;
    private static final Long ID_CONSULTA = 10L;

    @Mock
    private ConsultaRepository consultaRepository;
    @Mock
    private PacienteRepository pacienteRepository;
    @Mock
    private MedicoRepository medicoRepository;
    @Mock
    private ContextoSeguranca contextoSeguranca;
    @Mock
    private RegistradorDeEventos registradorDeEventos;

    @InjectMocks
    private ConsultaService consultaService;

    private Paciente paciente;
    private Medico medico;

    @BeforeEach
    void prepararDominio() {
        final Usuario usuarioPaciente =
                new Usuario("Maria Souza", "paciente@hospital.com", "hash", Role.PACIENTE);
        ReflectionTestUtils.setField(usuarioPaciente, "id", ID_PACIENTE);
        paciente = new Paciente(usuarioPaciente, "123.456.789-00", "(11) 90000-0000",
                LocalDate.of(1985, 4, 12));
        ReflectionTestUtils.setField(paciente, "id", ID_PACIENTE);

        final Usuario usuarioMedico =
                new Usuario("Dra. Ana Lima", "medico@hospital.com", "hash", Role.MEDICO);
        ReflectionTestUtils.setField(usuarioMedico, "id", ID_MEDICO);
        medico = new Medico(usuarioMedico, "CRM-SP-123456", "Cardiologia");
        ReflectionTestUtils.setField(medico, "id", ID_MEDICO);
    }

    private Consulta consultaExistente(LocalDateTime dataHora) {
        final Consulta consulta = new Consulta(paciente, medico, dataHora, "Observacao inicial");
        ReflectionTestUtils.setField(consulta, "id", ID_CONSULTA);
        return consulta;
    }

    @Nested
    class AoCriar {

        @Test
        void deveSalvarConsultaERegistrarEventoDeCriacao() {
            final LocalDateTime amanha = LocalDateTime.now().plusDays(1);
            final var requisicao =
                    new CriarConsultaRequest(ID_PACIENTE, ID_MEDICO, amanha, "Retorno anual");

            when(pacienteRepository.findById(ID_PACIENTE)).thenReturn(Optional.of(paciente));
            when(medicoRepository.findById(ID_MEDICO)).thenReturn(Optional.of(medico));
            when(consultaRepository.existsByMedicoIdAndDataHoraAndStatusNot(
                    ID_MEDICO, amanha, StatusConsulta.CANCELADA)).thenReturn(false);
            when(consultaRepository.save(any(Consulta.class))).thenAnswer(i -> i.getArgument(0));

            final Consulta criada = consultaService.criar(requisicao);

            assertThat(criada.getStatus()).isEqualTo(StatusConsulta.AGENDADA);
            assertThat(criada.getVersao()).isEqualTo(1L);
            verify(registradorDeEventos).registrar(criada, TipoEvento.CONSULTA_CRIADA);
        }

        @Test
        void deveRecusarAgendamentoEmDataPassada() {
            final var requisicao = new CriarConsultaRequest(
                    ID_PACIENTE, ID_MEDICO, LocalDateTime.now().minusDays(1), "Atrasada");

            assertThatThrownBy(() -> consultaService.criar(requisicao))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("data passada");

            verify(consultaRepository, never()).save(any());
            verify(registradorDeEventos, never()).registrar(any(), any());
        }

        @Test
        void deveRecusarDoisAgendamentosDoMesmoMedicoNoMesmoHorario() {
            final LocalDateTime amanha = LocalDateTime.now().plusDays(1);
            final var requisicao =
                    new CriarConsultaRequest(ID_PACIENTE, ID_MEDICO, amanha, "Conflitante");

            when(pacienteRepository.findById(ID_PACIENTE)).thenReturn(Optional.of(paciente));
            when(medicoRepository.findById(ID_MEDICO)).thenReturn(Optional.of(medico));
            when(consultaRepository.existsByMedicoIdAndDataHoraAndStatusNot(
                    ID_MEDICO, amanha, StatusConsulta.CANCELADA)).thenReturn(true);

            assertThatThrownBy(() -> consultaService.criar(requisicao))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("ja possui uma consulta agendada");

            verify(consultaRepository, never()).save(any());
        }

        @Test
        void deveFalharQuandoPacienteNaoExiste() {
            final var requisicao = new CriarConsultaRequest(
                    999L, ID_MEDICO, LocalDateTime.now().plusDays(1), null);
            when(pacienteRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> consultaService.criar(requisicao))
                    .isInstanceOf(RecursoNaoEncontradoException.class)
                    .hasMessageContaining("Paciente 999");
        }
    }

    @Nested
    class AoAtualizar {

        @Test
        void deveGravarAntesDeRegistrarEventoDeAtualizacao() {
            // Data no passado de proposito: marcar como REALIZADA so e valido para uma consulta
            // que ja aconteceu.
            final Consulta consulta = consultaExistente(LocalDateTime.now().minusHours(1));
            when(consultaRepository.findWithRelacoesById(ID_CONSULTA)).thenReturn(Optional.of(consulta));

            final Consulta atualizada = consultaService.atualizar(ID_CONSULTA,
                    new AtualizarConsultaRequest(null, StatusConsulta.REALIZADA, "Paciente compareceu"));

            assertThat(atualizada.getStatus()).isEqualTo(StatusConsulta.REALIZADA);
            assertThat(atualizada.getObservacoes()).isEqualTo("Paciente compareceu");
            // A versao e incrementada pelo Hibernate no flush, que precisa vir antes do evento.
            final InOrder ordem = inOrder(consultaRepository, registradorDeEventos);
            ordem.verify(consultaRepository).saveAndFlush(consulta);
            ordem.verify(registradorDeEventos).registrar(consulta, TipoEvento.CONSULTA_ATUALIZADA);
        }

        @Test
        void deveRecusarMarcarComoRealizadaUmaConsultaQueAindaNaoAconteceu() {
            final Consulta consulta = consultaExistente(LocalDateTime.now().plusDays(2));
            when(consultaRepository.findWithRelacoesById(ID_CONSULTA)).thenReturn(Optional.of(consulta));

            assertThatThrownBy(() -> consultaService.atualizar(ID_CONSULTA,
                    new AtualizarConsultaRequest(null, StatusConsulta.REALIZADA, null)))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("ainda nao aconteceu");

            verify(registradorDeEventos, never()).registrar(any(), any());
        }

        @Test
        void deveRecusarMarcarComoRealizadaAoRemarcarParaDataFutura() {
            final Consulta consulta = consultaExistente(LocalDateTime.now().minusDays(1));
            final LocalDateTime novaDataFutura = LocalDateTime.now().plusDays(3);
            when(consultaRepository.findWithRelacoesById(ID_CONSULTA)).thenReturn(Optional.of(consulta));

            assertThatThrownBy(() -> consultaService.atualizar(ID_CONSULTA,
                    new AtualizarConsultaRequest(novaDataFutura, StatusConsulta.REALIZADA, null)))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("ainda nao aconteceu");
        }

        /**
         * Remarcar e agenda; marcar como realizada e prontuario. So o segundo passa pela
         * guarda de perfil, e e o servico que decide isso porque a diferenca esta no campo.
         */
        @Test
        void remarcarNaoDeveExigirPerfilDeMedico() {
            final Consulta consulta = consultaExistente(LocalDateTime.now().plusDays(2));
            when(consultaRepository.findWithRelacoesById(ID_CONSULTA)).thenReturn(Optional.of(consulta));

            consultaService.atualizar(ID_CONSULTA,
                    new AtualizarConsultaRequest(LocalDateTime.now().plusDays(4), null, null));

            verify(contextoSeguranca, never()).exigirMedico(any());
        }

        @Test
        void marcarComoRealizadaDeveExigirPerfilDeMedico() {
            final Consulta consulta = consultaExistente(LocalDateTime.now().minusHours(1));
            when(consultaRepository.findWithRelacoesById(ID_CONSULTA)).thenReturn(Optional.of(consulta));
            doThrow(new AccessDeniedException("Apenas medicos podem marcar uma consulta como realizada"))
                    .when(contextoSeguranca).exigirMedico(any());

            assertThatThrownBy(() -> consultaService.atualizar(ID_CONSULTA,
                    new AtualizarConsultaRequest(null, StatusConsulta.REALIZADA, null)))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Apenas medicos");

            assertThat(consulta.getStatus()).isEqualTo(StatusConsulta.AGENDADA);
            verify(registradorDeEventos, never()).registrar(any(), any());
        }

        @Test
        void campoNuloDeveManterOValorAnterior() {
            final LocalDateTime dataOriginal = LocalDateTime.now().plusDays(2);
            final Consulta consulta = consultaExistente(dataOriginal);
            when(consultaRepository.findWithRelacoesById(ID_CONSULTA)).thenReturn(Optional.of(consulta));

            final Consulta atualizada = consultaService.atualizar(ID_CONSULTA,
                    new AtualizarConsultaRequest(null, null, null));

            assertThat(atualizada.getDataHora()).isEqualTo(dataOriginal);
            assertThat(atualizada.getStatus()).isEqualTo(StatusConsulta.AGENDADA);
            assertThat(atualizada.getObservacoes()).isEqualTo("Observacao inicial");
        }

        @Test
        void deveRecusarAlteracaoDeConsultaCancelada() {
            final Consulta consulta = consultaExistente(LocalDateTime.now().plusDays(2));
            consulta.alterar(null, StatusConsulta.CANCELADA, null);
            when(consultaRepository.findWithRelacoesById(ID_CONSULTA)).thenReturn(Optional.of(consulta));

            assertThatThrownBy(() -> consultaService.atualizar(ID_CONSULTA,
                    new AtualizarConsultaRequest(null, StatusConsulta.REALIZADA, null)))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("cancelada");

            verify(registradorDeEventos, never()).registrar(any(), any());
        }

        @Test
        void deveRegistrarEventoDeCancelamentoQuandoAtualizacaoMudaStatusParaCancelada() {
            final Consulta consulta = consultaExistente(LocalDateTime.now().plusDays(2));
            when(consultaRepository.findWithRelacoesById(ID_CONSULTA)).thenReturn(Optional.of(consulta));

            final Consulta atualizada = consultaService.atualizar(ID_CONSULTA,
                    new AtualizarConsultaRequest(null, StatusConsulta.CANCELADA, null));

            assertThat(atualizada.getStatus()).isEqualTo(StatusConsulta.CANCELADA);
            verify(registradorDeEventos).registrar(consulta, TipoEvento.CONSULTA_CANCELADA);
            verify(registradorDeEventos, never()).registrar(consulta, TipoEvento.CONSULTA_ATUALIZADA);
        }

        @Test
        void deveRecusarRemarcacaoParaHorarioJaOcupadoPeloMesmoMedico() {
            final Consulta consulta = consultaExistente(LocalDateTime.now().plusDays(2));
            final LocalDateTime novoHorario = LocalDateTime.now().plusDays(5);
            when(consultaRepository.findWithRelacoesById(ID_CONSULTA)).thenReturn(Optional.of(consulta));
            when(consultaRepository.existsByMedicoIdAndDataHoraAndIdNotAndStatusNot(
                    ID_MEDICO, novoHorario, ID_CONSULTA, StatusConsulta.CANCELADA))
                    .thenReturn(true);

            assertThatThrownBy(() -> consultaService.atualizar(ID_CONSULTA,
                    new AtualizarConsultaRequest(novoHorario, null, null)))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("ja possui uma consulta agendada");
        }
    }

    @Nested
    class AoConsultar {

        @Test
        void deveDelegarAResolucaoDoPacienteAoContextoDeSeguranca() {
            when(contextoSeguranca.resolverPacienteAlvo(null)).thenReturn(ID_PACIENTE);
            when(consultaRepository.findByPacienteIdOrderByDataHoraAsc(ID_PACIENTE))
                    .thenReturn(List.of(consultaExistente(LocalDateTime.now().plusDays(1))));

            assertThat(consultaService.listar(null, false)).hasSize(1);
        }

        @Test
        void deveListarTodasQuandoOContextoNaoRestringePaciente() {
            when(contextoSeguranca.resolverPacienteAlvo(null)).thenReturn(null);
            when(consultaRepository.findAllByOrderByDataHoraAsc()).thenReturn(List.of());

            consultaService.listar(null, false);

            verify(consultaRepository).findAllByOrderByDataHoraAsc();
            verify(consultaRepository, never()).findByPacienteIdOrderByDataHoraAsc(any());
        }

        @Test
        void deveUsarAConsultaDeFuturasQuandoOFiltroEstaAtivo() {
            when(contextoSeguranca.resolverPacienteAlvo(ID_PACIENTE)).thenReturn(ID_PACIENTE);
            final ArgumentCaptor<LocalDateTime> corte = ArgumentCaptor.forClass(LocalDateTime.class);
            when(consultaRepository.findByPacienteIdAndDataHoraGreaterThanEqualOrderByDataHoraAsc(
                    any(), corte.capture())).thenReturn(List.of());

            final LocalDateTime antes = LocalDateTime.now();
            consultaService.listar(ID_PACIENTE, true);

            // O corte tem de ser "agora": consultas passadas ficam de fora do filtro.
            assertThat(corte.getValue()).isBetween(antes.minusSeconds(5), LocalDateTime.now());
        }

        @Test
        void devePropagarAcessoNegadoQuandoPacienteConsultaOutro() {
            final Consulta consulta = consultaExistente(LocalDateTime.now().plusDays(1));
            when(consultaRepository.findWithRelacoesById(ID_CONSULTA)).thenReturn(Optional.of(consulta));
            org.mockito.Mockito.doThrow(new AccessDeniedException("negado"))
                    .when(contextoSeguranca).exigirPosse(ID_PACIENTE);

            assertThatThrownBy(() -> consultaService.buscar(ID_CONSULTA))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        void deveFalharQuandoConsultaNaoExiste() {
            when(consultaRepository.findWithRelacoesById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> consultaService.buscar(999L))
                    .isInstanceOf(RecursoNaoEncontradoException.class)
                    .hasMessageContaining("Consulta 999");
        }
    }
}
