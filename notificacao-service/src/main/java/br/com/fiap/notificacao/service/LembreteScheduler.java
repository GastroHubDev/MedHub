package br.com.fiap.notificacao.service;

import br.com.fiap.comum.evento.StatusConsulta;
import br.com.fiap.notificacao.domain.ConsultaAgendada;
import br.com.fiap.notificacao.repository.ConsultaAgendadaRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Dispara os lembretes das consultas que acontecem nas proximas 24 horas.
 *
 * <p>Le a projecao local - o filtro por {@link StatusConsulta#AGENDADA} e o que impede lembrar
 * uma consulta que ja foi cancelada, informacao que chega pelo evento de cancelamento.</p>
 */
@Component
public class LembreteScheduler {

    private static final Logger log = LoggerFactory.getLogger(LembreteScheduler.class);
    private static final int JANELA_HORAS = 24;
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    private final ConsultaAgendadaRepository consultaRepository;
    private final NotificacaoService notificacaoService;

    public LembreteScheduler(ConsultaAgendadaRepository consultaRepository,
                             NotificacaoService notificacaoService) {
        this.consultaRepository = consultaRepository;
        this.notificacaoService = notificacaoService;
    }

    /**
     * Sem {@code @Transactional} de proposito: cada lembrete e enviado na sua propria
     * transacao, dentro de {@link NotificacaoService}. Uma transacao unica para o lote inteiro
     * faria uma falha no ultimo envio desfazer o registro de todos os anteriores.
     */
    @Scheduled(cron = "${app.notificacao.cron-lembretes}", zone = "America/Sao_Paulo")
    public void enviarLembretesDoProximoDia() {
        final LocalDateTime agora = LocalDateTime.now(FUSO);
        final List<ConsultaAgendada> consultas =
                consultaRepository.findByStatusAndDataHoraBetweenOrderByDataHoraAsc(
                        StatusConsulta.AGENDADA, agora, agora.plusHours(JANELA_HORAS));

        int enviados = 0;
        int jaEnviados = 0;
        int falhas = 0;

        for (ConsultaAgendada consulta : consultas) {
            try {
                if (notificacaoService.enviarLembrete(consulta)) {
                    enviados++;
                } else {
                    jaEnviados++;
                }
            } catch (RuntimeException e) {
                // Uma consulta problematica nao pode interromper o lote.
                falhas++;
                log.warn("Falha ao enviar lembrete da consulta {}: {}",
                        consulta.getConsultaId(), e.getMessage());
            }
        }

        log.info("Lembretes das proximas {}h: {} enviados, {} ja enviados, {} falhas",
                JANELA_HORAS, enviados, jaEnviados, falhas);
    }
}
