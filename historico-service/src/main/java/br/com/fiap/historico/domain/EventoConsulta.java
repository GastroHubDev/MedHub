package br.com.fiap.historico.domain;

import br.com.fiap.comum.evento.StatusConsulta;
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
 * Trilha de auditoria: uma linha por evento recebido, nunca alterada nem removida.
 *
 * <p>Enquanto {@link ConsultaHistorico} guarda o "agora", esta tabela guarda o "como chegou
 * ate aqui" - e e exposta no GraphQL, tornando visivel na resposta da API que o historico e
 * derivado do log de eventos.</p>
 */
@Entity
@Table(name = "evento_consulta")
public class EventoConsulta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "consulta_id", nullable = false)
    private Long consultaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_evento", nullable = false, length = 40)
    private TipoEvento tipoEvento;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_resultante", nullable = false, length = 20)
    private StatusConsulta statusResultante;

    @Column(name = "data_hora_consulta", nullable = false)
    private LocalDateTime dataHoraConsulta;

    @Column(nullable = false)
    private long versao;

    @Column(name = "ocorrido_em", nullable = false)
    private LocalDateTime ocorridoEm;

    @Column(name = "registrado_em", nullable = false)
    private LocalDateTime registradoEm;

    protected EventoConsulta() {
    }

    public EventoConsulta(Long consultaId, TipoEvento tipoEvento, StatusConsulta statusResultante,
                          LocalDateTime dataHoraConsulta, long versao, LocalDateTime ocorridoEm) {
        this.consultaId = consultaId;
        this.tipoEvento = tipoEvento;
        this.statusResultante = statusResultante;
        this.dataHoraConsulta = dataHoraConsulta;
        this.versao = versao;
        this.ocorridoEm = ocorridoEm;
        this.registradoEm = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getConsultaId() {
        return consultaId;
    }

    public TipoEvento getTipoEvento() {
        return tipoEvento;
    }

    public StatusConsulta getStatusResultante() {
        return statusResultante;
    }

    public LocalDateTime getDataHoraConsulta() {
        return dataHoraConsulta;
    }

    public long getVersao() {
        return versao;
    }

    public LocalDateTime getOcorridoEm() {
        return ocorridoEm;
    }

    public LocalDateTime getRegistradoEm() {
        return registradoEm;
    }
}
