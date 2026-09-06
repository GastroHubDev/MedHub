package br.com.fiap.agendamento.repository;

import br.com.fiap.agendamento.domain.Consulta;
import br.com.fiap.comum.evento.StatusConsulta;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsultaRepository extends JpaRepository<Consulta, Long> {

    /**
     * O {@code @EntityGraph} evita o N+1 ao montar o DTO: paciente e medico sao LAZY, e cada
     * consulta da lista precisaria de duas idas ao banco para expor nome e especialidade.
     */
    @EntityGraph(attributePaths = {"paciente", "paciente.usuario", "medico", "medico.usuario"})
    List<Consulta> findByPacienteIdOrderByDataHoraAsc(Long pacienteId);

    @EntityGraph(attributePaths = {"paciente", "paciente.usuario", "medico", "medico.usuario"})
    List<Consulta> findByPacienteIdAndDataHoraGreaterThanEqualOrderByDataHoraAsc(
            Long pacienteId, LocalDateTime aPartirDe);

    @EntityGraph(attributePaths = {"paciente", "paciente.usuario", "medico", "medico.usuario"})
    List<Consulta> findAllByOrderByDataHoraAsc();

    @EntityGraph(attributePaths = {"paciente", "paciente.usuario", "medico", "medico.usuario"})
    List<Consulta> findByDataHoraGreaterThanEqualOrderByDataHoraAsc(LocalDateTime aPartirDe);

    @EntityGraph(attributePaths = {"paciente", "paciente.usuario", "medico", "medico.usuario"})
    Optional<Consulta> findWithRelacoesById(Long id);

    /**
     * Barreira de double-booking na camada de servico; o indice unico parcial (que tambem
     * ignora CANCELADA) e a barreira final. Uma consulta cancelada nao ocupa mais o horario -
     * senao o mesmo par medico/horario ficaria bloqueado para sempre apos um cancelamento.
     */
    boolean existsByMedicoIdAndDataHoraAndStatusNot(Long medicoId, LocalDateTime dataHora,
                                                    StatusConsulta statusExcluido);

    boolean existsByMedicoIdAndDataHoraAndIdNotAndStatusNot(Long medicoId, LocalDateTime dataHora,
                                                            Long id, StatusConsulta statusExcluido);
}
