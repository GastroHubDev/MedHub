package br.com.fiap.historico.repository;

import br.com.fiap.historico.domain.EventoConsulta;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoConsultaRepository extends JpaRepository<EventoConsulta, Long> {

    List<EventoConsulta> findByConsultaIdOrderByVersaoAscIdAsc(Long consultaId);

    /** Carga em lote para o resolver de {@code eventos}, evitando o N+1 do GraphQL. */
    List<EventoConsulta> findByConsultaIdInOrderByVersaoAscIdAsc(Collection<Long> consultaIds);

    boolean existsByConsultaIdAndVersaoAndTipoEvento(
            Long consultaId, long versao, br.com.fiap.comum.evento.TipoEvento tipoEvento);
}
