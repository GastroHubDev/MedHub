package br.com.fiap.historico.repository;

import br.com.fiap.comum.evento.StatusConsulta;
import br.com.fiap.historico.domain.ConsultaHistorico;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ConsultaHistoricoRepository
        extends JpaRepository<ConsultaHistorico, Long>, JpaSpecificationExecutor<ConsultaHistorico> {

    /**
     * Consulta unica que atende a todas as combinacoes de filtro do GraphQL.
     *
     * <p>Cada filtro ausente simplesmente nao vira predicado: em vez do truque classico
     * {@code (:param IS NULL OR ...)} - que forca o Postgres a inferir o tipo de um bind
     * parameter mesmo quando ele nunca vai ser comparado de verdade, e que o Hibernate 6.6
     * infere errado quando o mesmo parametro aparece dentro de uma funcao como LOWER()
     * (vira "function lower(bytea) does not exist") - a Specification so adiciona a condicao
     * quando o valor do filtro existe. Um filtro nulo nunca chega a virar parametro, entao
     * nao ha tipo nenhum para o driver adivinhar.</p>
     */
    default List<ConsultaHistorico> buscarComFiltros(Long pacienteId, Long medicoId,
                                                      LocalDateTime de, LocalDateTime ate,
                                                      String especialidade,
                                                      Collection<StatusConsulta> status) {
        return findAll(comFiltros(pacienteId, medicoId, de, ate, especialidade, status),
                Sort.by(Sort.Direction.ASC, "dataHora"));
    }

    private static Specification<ConsultaHistorico> comFiltros(Long pacienteId, Long medicoId,
                                                                LocalDateTime de, LocalDateTime ate,
                                                                String especialidade,
                                                                Collection<StatusConsulta> status) {
        return (root, query, cb) -> {
            final List<Predicate> predicados = new ArrayList<>();
            if (pacienteId != null) {
                predicados.add(cb.equal(root.get("pacienteId"), pacienteId));
            }
            if (medicoId != null) {
                predicados.add(cb.equal(root.get("medicoId"), medicoId));
            }
            if (de != null) {
                predicados.add(cb.greaterThanOrEqualTo(root.get("dataHora"), de));
            }
            if (ate != null) {
                predicados.add(cb.lessThanOrEqualTo(root.get("dataHora"), ate));
            }
            if (especialidade != null) {
                predicados.add(cb.equal(cb.lower(root.get("medicoEspecialidade")),
                        especialidade.toLowerCase()));
            }
            if (status != null) {
                predicados.add(root.get("status").in(status));
            }
            return cb.and(predicados.toArray(new Predicate[0]));
        };
    }

    List<ConsultaHistorico> findByPacienteIdOrderByDataHoraAsc(Long pacienteId);

    long countByPacienteId(Long pacienteId);

    long countByPacienteIdAndStatus(Long pacienteId, StatusConsulta status);

    long countByPacienteIdAndStatusAndDataHoraGreaterThanEqual(Long pacienteId, StatusConsulta status,
                                                               LocalDateTime aPartirDe);
}
