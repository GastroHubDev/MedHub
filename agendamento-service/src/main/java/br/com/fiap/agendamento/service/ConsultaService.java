package br.com.fiap.agendamento.service;

import br.com.fiap.agendamento.domain.Consulta;
import br.com.fiap.agendamento.domain.Medico;
import br.com.fiap.agendamento.domain.Paciente;
import br.com.fiap.agendamento.dto.AtualizarConsultaRequest;
import br.com.fiap.agendamento.dto.CriarConsultaRequest;
import br.com.fiap.agendamento.exception.RecursoNaoEncontradoException;
import br.com.fiap.agendamento.exception.RegraDeNegocioException;
import br.com.fiap.agendamento.repository.ConsultaRepository;
import br.com.fiap.agendamento.repository.MedicoRepository;
import br.com.fiap.agendamento.repository.PacienteRepository;
import br.com.fiap.comum.evento.TipoEvento;
import br.com.fiap.comum.seguranca.ContextoSeguranca;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras de agendamento e de posse.
 *
 * <p>A autorizacao acontece em dois niveis: o controller barra por perfil com
 * {@code @PreAuthorize} (quem pode chamar), e este servico barra por posse (sobre quais dados),
 * porque essa decisao depende do registro e nao apenas do papel.</p>
 */
@Service
public class ConsultaService {

    private final ConsultaRepository consultaRepository;
    private final PacienteRepository pacienteRepository;
    private final MedicoRepository medicoRepository;
    private final ContextoSeguranca contextoSeguranca;
    private final RegistradorDeEventos registradorDeEventos;

    public ConsultaService(ConsultaRepository consultaRepository,
                           PacienteRepository pacienteRepository,
                           MedicoRepository medicoRepository,
                           ContextoSeguranca contextoSeguranca,
                           RegistradorDeEventos registradorDeEventos) {
        this.consultaRepository = consultaRepository;
        this.pacienteRepository = pacienteRepository;
        this.medicoRepository = medicoRepository;
        this.contextoSeguranca = contextoSeguranca;
        this.registradorDeEventos = registradorDeEventos;
    }

    @Transactional(readOnly = true)
    public List<Consulta> listar(Long pacienteIdSolicitado, boolean apenasFuturas) {
        final Long pacienteAlvo = contextoSeguranca.resolverPacienteAlvo(pacienteIdSolicitado);
        final LocalDateTime agora = LocalDateTime.now();

        if (pacienteAlvo == null) {
            return apenasFuturas
                    ? consultaRepository.findByDataHoraGreaterThanEqualOrderByDataHoraAsc(agora)
                    : consultaRepository.findAllByOrderByDataHoraAsc();
        }
        return apenasFuturas
                ? consultaRepository.findByPacienteIdAndDataHoraGreaterThanEqualOrderByDataHoraAsc(pacienteAlvo, agora)
                : consultaRepository.findByPacienteIdOrderByDataHoraAsc(pacienteAlvo);
    }

    @Transactional(readOnly = true)
    public Consulta buscar(Long id) {
        final Consulta consulta = buscarOuFalhar(id);
        contextoSeguranca.exigirPosse(consulta.getPaciente().getId());
        return consulta;
    }

    @Transactional
    public Consulta criar(CriarConsultaRequest requisicao) {
        validarDataFutura(requisicao.dataHora());

        final Paciente paciente = pacienteRepository.findById(requisicao.pacienteId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Paciente %d nao encontrado".formatted(requisicao.pacienteId())));
        final Medico medico = medicoRepository.findById(requisicao.medicoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Medico %d nao encontrado".formatted(requisicao.medicoId())));

        if (consultaRepository.existsByMedicoIdAndDataHora(medico.getId(), requisicao.dataHora())) {
            throw new RegraDeNegocioException(
                    "O medico ja possui uma consulta agendada para este horario");
        }

        final Consulta consulta = consultaRepository.save(
                new Consulta(paciente, medico, requisicao.dataHora(), requisicao.observacoes()));

        registradorDeEventos.registrar(consulta, TipoEvento.CONSULTA_CRIADA);
        return consulta;
    }

    @Transactional
    public Consulta atualizar(Long id, AtualizarConsultaRequest requisicao) {
        final Consulta consulta = buscarOuFalhar(id);

        if (consulta.estaCancelada()) {
            throw new RegraDeNegocioException(
                    "Consulta cancelada nao pode ser alterada; agende uma nova");
        }
        if (requisicao.dataHora() != null) {
            validarDataFutura(requisicao.dataHora());
            if (consultaRepository.existsByMedicoIdAndDataHoraAndIdNot(
                    consulta.getMedico().getId(), requisicao.dataHora(), consulta.getId())) {
                throw new RegraDeNegocioException(
                        "O medico ja possui uma consulta agendada para este horario");
            }
        }

        consulta.alterar(requisicao.dataHora(), requisicao.status(), requisicao.observacoes());
        registradorDeEventos.registrar(consulta, TipoEvento.CONSULTA_ATUALIZADA);
        return consulta;
    }

    @Transactional
    public Consulta cancelar(Long id) {
        final Consulta consulta = buscarOuFalhar(id);

        if (consulta.estaCancelada()) {
            throw new RegraDeNegocioException("Consulta ja esta cancelada");
        }

        consulta.cancelar();
        registradorDeEventos.registrar(consulta, TipoEvento.CONSULTA_CANCELADA);
        return consulta;
    }

    private Consulta buscarOuFalhar(Long id) {
        return consultaRepository.findWithRelacoesById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Consulta %d nao encontrada".formatted(id)));
    }

    private static void validarDataFutura(LocalDateTime dataHora) {
        if (dataHora.isBefore(LocalDateTime.now())) {
            throw new RegraDeNegocioException("Nao e possivel agendar consulta em data passada");
        }
    }
}
