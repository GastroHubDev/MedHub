package br.com.fiap.agendamento.repository;

import br.com.fiap.agendamento.domain.OutboxEvento;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventoRepository extends JpaRepository<OutboxEvento, Long> {

    /** Pendentes em ordem de criacao - preserva a ordem dos eventos de uma mesma consulta. */
    List<OutboxEvento> findByPublicadoEmIsNullOrderByIdAsc(Limit limite);

    long countByPublicadoEmIsNull();
}
