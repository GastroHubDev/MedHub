package br.com.fiap.notificacao.repository;

import br.com.fiap.notificacao.domain.Notificacao;
import br.com.fiap.notificacao.domain.StatusEnvio;
import br.com.fiap.notificacao.domain.TipoNotificacao;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificacaoRepository extends JpaRepository<Notificacao, Long> {

    /**
     * Guarda de idempotencia dos eventos: a mesma consulta, na mesma versao, com o mesmo tipo,
     * ja notificada com sucesso. Reentrega do broker nao gera um segundo e-mail.
     */
    boolean existsByConsultaIdAndTipoAndVersaoConsultaAndStatus(
            Long consultaId, TipoNotificacao tipo, long versaoConsulta, StatusEnvio status);

    /**
     * Guarda dos lembretes. Aqui a chave inclui a data da consulta em vez da versao: remarcar
     * uma consulta deve, legitimamente, gerar um novo lembrete para o novo horario.
     */
    boolean existsByConsultaIdAndTipoAndDataHoraConsultaAndStatus(
            Long consultaId, TipoNotificacao tipo, LocalDateTime dataHoraConsulta, StatusEnvio status);

    List<Notificacao> findByConsultaIdOrderByEnviadoEmDesc(Long consultaId);

    List<Notificacao> findAllByOrderByEnviadoEmDesc();
}
