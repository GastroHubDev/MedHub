package br.com.fiap.notificacao.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * Registro de auditoria de cada notificacao tentada.
 *
 * <p>Serve a dois propositos: comprovar o envio na avaliacao (via
 * {@code GET /api/notificacoes}) e sustentar a guarda de idempotencia - a chave
 * {@code (consultaId, tipo, versaoConsulta)} responde "esta notificacao ja saiu?" sem
 * depender do broker nao reentregar.</p>
 */
@Entity
@Table(name = "notificacao")
public class Notificacao {

    private static final int TAMANHO_MAXIMO_ERRO = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "consulta_id", nullable = false)
    private Long consultaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoNotificacao tipo;

    @Column(nullable = false, length = 150)
    private String destinatario;

    @Column(nullable = false, length = 200)
    private String assunto;

    @Column(nullable = false, length = 2000)
    private String mensagem;

    @Column(name = "data_hora_consulta", nullable = false)
    private LocalDateTime dataHoraConsulta;

    /** Versao do agregado que originou esta notificacao; compoe a chave de idempotencia. */
    @Column(name = "versao_consulta", nullable = false)
    private long versaoConsulta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusEnvio status;

    @Column(name = "enviado_em", nullable = false)
    private LocalDateTime enviadoEm;

    @Column(length = TAMANHO_MAXIMO_ERRO)
    private String erro;

    protected Notificacao() {
    }

    private Notificacao(Long consultaId, TipoNotificacao tipo, String destinatario, String assunto,
                        String mensagem, LocalDateTime dataHoraConsulta, long versaoConsulta,
                        StatusEnvio status, String erro) {
        this.consultaId = consultaId;
        this.tipo = tipo;
        this.destinatario = destinatario;
        this.assunto = assunto;
        this.mensagem = mensagem;
        this.dataHoraConsulta = dataHoraConsulta;
        this.versaoConsulta = versaoConsulta;
        this.status = status;
        this.erro = truncar(erro);
        this.enviadoEm = LocalDateTime.now();
    }

    public static Notificacao enviada(Long consultaId, TipoNotificacao tipo, String destinatario,
                                      String assunto, String mensagem,
                                      LocalDateTime dataHoraConsulta, long versaoConsulta) {
        return new Notificacao(consultaId, tipo, destinatario, assunto, mensagem,
                dataHoraConsulta, versaoConsulta, StatusEnvio.ENVIADA, null);
    }

    public static Notificacao falha(Long consultaId, TipoNotificacao tipo, String destinatario,
                                    String assunto, String mensagem, LocalDateTime dataHoraConsulta,
                                    long versaoConsulta, String erro) {
        return new Notificacao(consultaId, tipo, destinatario, assunto, mensagem,
                dataHoraConsulta, versaoConsulta, StatusEnvio.FALHA, erro);
    }

    private static String truncar(String texto) {
        if (texto == null) {
            return null;
        }
        return texto.length() <= TAMANHO_MAXIMO_ERRO ? texto : texto.substring(0, TAMANHO_MAXIMO_ERRO);
    }

    public Long getId() {
        return id;
    }

    public Long getConsultaId() {
        return consultaId;
    }

    public TipoNotificacao getTipo() {
        return tipo;
    }

    public String getDestinatario() {
        return destinatario;
    }

    public String getAssunto() {
        return assunto;
    }

    public String getMensagem() {
        return mensagem;
    }

    public LocalDateTime getDataHoraConsulta() {
        return dataHoraConsulta;
    }

    public long getVersaoConsulta() {
        return versaoConsulta;
    }

    public StatusEnvio getStatus() {
        return status;
    }

    public LocalDateTime getEnviadoEm() {
        return enviadoEm;
    }

    public String getErro() {
        return erro;
    }
}
