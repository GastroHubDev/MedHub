package br.com.fiap.historico.repository;

import br.com.fiap.comum.evento.StatusConsulta;
import br.com.fiap.historico.domain.ConsultaHistorico;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConsultaHistoricoRepository extends JpaRepository<ConsultaHistorico, Long> {

    /**
     * Consulta unica que atende a todas as combinacoes de filtro do GraphQL.
     *
     * <p>Cada parametro nulo desliga o proprio predicado. Isso evita o efeito colateral tipico
     * de GraphQL - uma consulta derivada por combinacao de filtros, que cresce em explosao
     * combinatoria - mantendo um unico plano de execucao para o banco.</p>
     */
    @Query("""
            SELECT c FROM ConsultaHistorico c
             WHERE (:pacienteId    IS NULL OR c.pacienteId = :pacienteId)
               AND (:medicoId      IS NULL OR c.medicoId = :medicoId)
               AND (:de            IS NULL OR c.dataHora >= :de)
               AND (:ate           IS NULL OR c.dataHora <= :ate)
               AND (:especialidade IS NULL OR LOWER(c.medicoEspecialidade) = LOWER(:especialidade))
               AND (:status        IS NULL OR c.status IN :status)
             ORDER BY c.dataHora ASC
            """)
    List<ConsultaHistorico> buscarComFiltros(@Param("pacienteId") Long pacienteId,
                                             @Param("medicoId") Long medicoId,
                                             @Param("de") LocalDateTime de,
                                             @Param("ate") LocalDateTime ate,
                                             @Param("especialidade") String especialidade,
                                             @Param("status") Collection<StatusConsulta> status);

    List<ConsultaHistorico> findByPacienteIdOrderByDataHoraAsc(Long pacienteId);

    long countByPacienteId(Long pacienteId);

    long countByPacienteIdAndStatus(Long pacienteId, StatusConsulta status);

    long countByPacienteIdAndDataHoraGreaterThanEqual(Long pacienteId, LocalDateTime aPartirDe);
}
