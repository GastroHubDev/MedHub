package br.com.fiap.notificacao.service;

import br.com.fiap.comum.evento.ConsultaEvento;
import br.com.fiap.comum.evento.TipoEvento;
import br.com.fiap.notificacao.domain.ConsultaAgendada;
import br.com.fiap.notificacao.domain.Notificacao;
import br.com.fiap.notificacao.domain.StatusEnvio;
import br.com.fiap.notificacao.domain.TipoNotificacao;
import br.com.fiap.notificacao.exception.EventoInvalidoException;
import br.com.fiap.notificacao.repository.ConsultaAgendadaRepository;
import br.com.fiap.notificacao.repository.NotificacaoRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Regras de notificacao: atualiza a projecao local e envia o e-mail correspondente. */
@Service
public class NotificacaoService {

    private static final Logger log = LoggerFactory.getLogger(NotificacaoService.class);

    private final ConsultaAgendadaRepository consultaRepository;
    private final NotificacaoRepository notificacaoRepository;
    private final RedatorDeMensagens redator;
    private final EmailSender emailSender;

    public NotificacaoService(ConsultaAgendadaRepository consultaRepository,
                              NotificacaoRepository notificacaoRepository,
                              RedatorDeMensagens redator,
                              EmailSender emailSender) {
        this.consultaRepository = consultaRepository;
        this.notificacaoRepository = notificacaoRepository;
        this.redator = redator;
        this.emailSender = emailSender;
    }

    @Transactional
    public void processarEvento(ConsultaEvento evento) {
        validar(evento);

        final ConsultaAgendada consulta = aplicarNaProjecao(evento);
        if (consulta == null) {
            return;
        }

        final TipoNotificacao tipo = tipoDaNotificacao(evento.tipoEvento());
        if (jaNotificado(evento, tipo)) {
            log.debug("Notificacao {} da consulta {} versao {} ja enviada; ignorando reentrega",
                    tipo, evento.consultaId(), evento.versao());
            return;
        }

        enviar(consulta, tipo, evento.versao());
    }

    /**
     * Upsert idempotente. Devolve {@code null} quando o evento e mais antigo que o estado ja
     * aplicado - caso em que nao ha nada a atualizar nem a notificar.
     */
    private ConsultaAgendada aplicarNaProjecao(ConsultaEvento evento) {
        final LocalDateTime dataHora = parseDataHora(evento);

        return consultaRepository.findById(evento.consultaId())
                .map(existente -> {
                    if (existente.ehMaisAntigoQue(evento.versao())) {
                        log.debug("Evento da consulta {} na versao {} descartado; projecao ja esta na {}",
                                evento.consultaId(), evento.versao(), existente.getVersao());
                        return null;
                    }
                    existente.atualizar(evento.pacienteId(), evento.pacienteNome(),
                            evento.pacienteEmail(), evento.medicoNome(), evento.medicoEspecialidade(),
                            dataHora, evento.status(), evento.versao());
                    return existente;
                })
                .orElseGet(() -> consultaRepository.save(new ConsultaAgendada(
                        evento.consultaId(), evento.pacienteId(), evento.pacienteNome(),
                        evento.pacienteEmail(), evento.medicoNome(), evento.medicoEspecialidade(),
                        dataHora, evento.status(), evento.versao())));
    }

    private boolean jaNotificado(ConsultaEvento evento, TipoNotificacao tipo) {
        return notificacaoRepository.existsByConsultaIdAndTipoAndVersaoConsultaAndStatus(
                evento.consultaId(), tipo, evento.versao(), StatusEnvio.ENVIADA);
    }

    /**
     * Envia e registra o resultado.
     *
     * <p>Uma falha de SMTP e gravada como {@link StatusEnvio#FALHA} e <b>nao</b> e relancada:
     * o e-mail e o efeito menos valioso do processamento. Relancar descartaria a atualizacao da
     * projecao e mandaria a mensagem para o dead letter topic so porque o servidor de e-mail
     * esta fora do ar.</p>
     */
    private void enviar(ConsultaAgendada consulta, TipoNotificacao tipo, long versao) {
        final String assunto = redator.assunto(tipo);
        final String mensagem = redator.corpo(tipo, consulta);

        try {
            emailSender.enviar(consulta.getPacienteEmail(), assunto, mensagem);
            notificacaoRepository.save(Notificacao.enviada(consulta.getConsultaId(), tipo,
                    consulta.getPacienteEmail(), assunto, mensagem, consulta.getDataHora(), versao));
            log.info("Notificacao {} enviada para {} (consulta {})",
                    tipo, consulta.getPacienteEmail(), consulta.getConsultaId());
        } catch (MailException e) {
            notificacaoRepository.save(Notificacao.falha(consulta.getConsultaId(), tipo,
                    consulta.getPacienteEmail(), assunto, mensagem, consulta.getDataHora(),
                    versao, e.getMessage()));
            log.warn("Falha ao enviar notificacao {} da consulta {}: {}",
                    tipo, consulta.getConsultaId(), e.getMessage());
        }
    }

    /** Usado pelo agendador; a guarda de idempotencia considera a data, nao a versao. */
    @Transactional
    public boolean enviarLembrete(ConsultaAgendada consulta) {
        final boolean jaEnviado = notificacaoRepository
                .existsByConsultaIdAndTipoAndDataHoraConsultaAndStatus(consulta.getConsultaId(),
                        TipoNotificacao.LEMBRETE_24H, consulta.getDataHora(), StatusEnvio.ENVIADA);
        if (jaEnviado) {
            return false;
        }

        enviar(consulta, TipoNotificacao.LEMBRETE_24H, consulta.getVersao());
        return true;
    }

    private static TipoNotificacao tipoDaNotificacao(TipoEvento tipoEvento) {
        return switch (tipoEvento) {
            case CONSULTA_CRIADA -> TipoNotificacao.CONFIRMACAO;
            case CONSULTA_ATUALIZADA -> TipoNotificacao.ATUALIZACAO;
            case CONSULTA_CANCELADA -> TipoNotificacao.CANCELAMENTO;
        };
    }

    private static void validar(ConsultaEvento evento) {
        if (evento == null) {
            throw new EventoInvalidoException("Evento nulo");
        }
        exigir(evento.consultaId() != null, "consultaId ausente");
        exigir(evento.tipoEvento() != null, "tipoEvento ausente");
        exigir(evento.status() != null, "status ausente");
        exigir(evento.pacienteId() != null, "pacienteId ausente");
        exigir(evento.pacienteEmail() != null && !evento.pacienteEmail().isBlank(),
                "pacienteEmail ausente");
        exigir(evento.dataHora() != null, "dataHora ausente");
    }

    private static void exigir(boolean condicao, String descricao) {
        if (!condicao) {
            throw new EventoInvalidoException("Evento invalido: " + descricao);
        }
    }

    private static LocalDateTime parseDataHora(ConsultaEvento evento) {
        try {
            return LocalDateTime.parse(evento.dataHora());
        } catch (DateTimeParseException e) {
            throw new EventoInvalidoException(
                    "Evento invalido: dataHora '%s' fora do formato ISO-8601"
                            .formatted(evento.dataHora()), e);
        }
    }
}
