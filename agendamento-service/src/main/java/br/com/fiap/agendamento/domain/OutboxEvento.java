package br.com.fiap.agendamento.domain;

import br.com.fiap.comum.evento.TipoEvento;
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
 * Linha da tabela de outbox.
 *
 * <p>Gravada na <b>mesma transacao</b> que altera a consulta. Um publicador assincrono a
 * envia ao Kafka depois do commit, o que elimina o risco de publicar um evento cuja
 * transacao acabou revertida - deixando os consumidores com uma consulta fantasma.</p>
 */
@Entity
@Table(name = "outbox_evento")
public class OutboxEvento {

    private static final int TAMANHO_MAXIMO_ERRO = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Id da consulta. Vira a chave da mensagem, garantindo ordem por consulta na particao. */
    @Column(name = "agregado_id", nullable = false)
    private Long agregadoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_evento", nullable = false, length = 40)
    private TipoEvento tipoEvento;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "publicado_em")
    private LocalDateTime publicadoEm;

    @Column(nullable = false)
    private int tentativas;

    @Column(name = "ultimo_erro", length = TAMANHO_MAXIMO_ERRO)
    private String ultimoErro;

    protected OutboxEvento() {
    }

    public OutboxEvento(Long agregadoId, TipoEvento tipoEvento, String payload) {
        this.agregadoId = agregadoId;
        this.tipoEvento = tipoEvento;
        this.payload = payload;
        this.criadoEm = LocalDateTime.now();
        this.tentativas = 0;
    }

    public void marcarPublicado() {
        this.publicadoEm = LocalDateTime.now();
        this.ultimoErro = null;
    }

    public void registrarFalha(String erro) {
        this.tentativas++;
        this.ultimoErro = truncar(erro);
    }

    private static String truncar(String erro) {
        if (erro == null) {
            return null;
        }
        return erro.length() <= TAMANHO_MAXIMO_ERRO ? erro : erro.substring(0, TAMANHO_MAXIMO_ERRO);
    }

    public Long getId() {
        return id;
    }

    public Long getAgregadoId() {
        return agregadoId;
    }

    public TipoEvento getTipoEvento() {
        return tipoEvento;
    }

    public String getPayload() {
        return payload;
    }

    public LocalDateTime getPublicadoEm() {
        return publicadoEm;
    }

    public int getTentativas() {
        return tentativas;
    }

    public String getUltimoErro() {
        return ultimoErro;
    }
}
