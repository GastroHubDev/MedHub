package br.com.fiap.notificacao.repository;

import br.com.fiap.comum.evento.StatusConsulta;
import br.com.fiap.notificacao.domain.ConsultaAgendada;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsultaAgendadaRepository extends JpaRepository<ConsultaAgendada, Long> {

    /** Base do agendador de lembretes: consultas ainda AGENDADAS dentro da janela de 24h. */
    List<ConsultaAgendada> findByStatusAndDataHoraBetweenOrderByDataHoraAsc(
            StatusConsulta status, LocalDateTime inicio, LocalDateTime fim);
}
