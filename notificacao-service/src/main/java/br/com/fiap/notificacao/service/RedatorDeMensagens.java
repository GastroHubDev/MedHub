package br.com.fiap.notificacao.service;

import br.com.fiap.notificacao.domain.ConsultaAgendada;
import br.com.fiap.notificacao.domain.TipoNotificacao;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

/** Monta assunto e corpo dos e-mails, isolando o texto das regras de envio. */
@Component
public class RedatorDeMensagens {

    private static final DateTimeFormatter FORMATO =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'as' HH:mm");

    public String assunto(TipoNotificacao tipo) {
        return switch (tipo) {
            case CONFIRMACAO -> "Consulta agendada";
            case ATUALIZACAO -> "Sua consulta foi atualizada";
            case CANCELAMENTO -> "Sua consulta foi cancelada";
            case LEMBRETE_24H -> "Lembrete: sua consulta e amanha";
        };
    }

    public String corpo(TipoNotificacao tipo, ConsultaAgendada consulta) {
        final String dataHora = consulta.getDataHora().format(FORMATO);
        final String medico = "%s (%s)".formatted(
                consulta.getMedicoNome(),
                consulta.getMedicoEspecialidade() == null ? "clinica geral"
                        : consulta.getMedicoEspecialidade());

        final String miolo = switch (tipo) {
            case CONFIRMACAO -> """
                    Sua consulta foi agendada para %s com %s.

                    Chegue com 15 minutos de antecedencia e traga um documento com foto.""";
            case ATUALIZACAO -> """
                    Sua consulta com %2$s foi atualizada e agora esta marcada para %1$s.

                    Em caso de duvida, entre em contato com a recepcao.""";
            case CANCELAMENTO -> """
                    Sua consulta de %s com %s foi cancelada.

                    Procure a recepcao para reagendar quando desejar.""";
            case LEMBRETE_24H -> """
                    Lembramos que sua consulta com %2$s acontece em %1$s.

                    Se nao puder comparecer, avise a recepcao com antecedencia.""";
        };

        return """
                Ola, %s!

                %s

                Atenciosamente,
                Equipe do Hospital""".formatted(consulta.getPacienteNome(),
                miolo.formatted(dataHora, medico));
    }
}
