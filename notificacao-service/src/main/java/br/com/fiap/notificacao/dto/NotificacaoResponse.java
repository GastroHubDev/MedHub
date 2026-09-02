package br.com.fiap.notificacao.dto;

import br.com.fiap.notificacao.domain.Notificacao;
import br.com.fiap.notificacao.domain.StatusEnvio;
import br.com.fiap.notificacao.domain.TipoNotificacao;
import java.time.LocalDateTime;
import java.util.List;

public record NotificacaoResponse(
        Long id,
        Long consultaId,
        TipoNotificacao tipo,
        String destinatario,
        String assunto,
        LocalDateTime dataHoraConsulta,
        long versaoConsulta,
        StatusEnvio status,
        LocalDateTime enviadoEm,
        String erro
) {

    public static NotificacaoResponse de(Notificacao notificacao) {
        return new NotificacaoResponse(
                notificacao.getId(),
                notificacao.getConsultaId(),
                notificacao.getTipo(),
                notificacao.getDestinatario(),
                notificacao.getAssunto(),
                notificacao.getDataHoraConsulta(),
                notificacao.getVersaoConsulta(),
                notificacao.getStatus(),
                notificacao.getEnviadoEm(),
                notificacao.getErro());
    }

    public static List<NotificacaoResponse> deLista(List<Notificacao> notificacoes) {
        return notificacoes.stream().map(NotificacaoResponse::de).toList();
    }
}
