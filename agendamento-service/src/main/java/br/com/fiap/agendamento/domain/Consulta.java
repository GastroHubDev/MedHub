package br.com.fiap.agendamento.domain;

import br.com.fiap.comum.evento.StatusConsulta;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** Agregado central: uma consulta entre um paciente e um medico em determinado horario. */
@Entity
@Table(name = "consulta")
public class Consulta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paciente_id", nullable = false)
    private Paciente paciente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medico_id", nullable = false)
    private Medico medico;

    @Column(name = "data_hora", nullable = false)
    private LocalDateTime dataHora;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusConsulta status;

    @Column(length = 1000)
    private String observacoes;

    /**
     * Espelha {@code dataHora} enquanto a consulta ocupa o horario, e vira {@code null} quando
     * ela e cancelada. Sustenta {@code uk_consulta_medico_horario}: linhas com {@code null}
     * nunca colidem numa constraint UNIQUE.
     */
    @Column(name = "horario_ocupado")
    private LocalDateTime horarioOcupado;

    /**
     * Incrementada a cada alteracao e copiada para o evento. E o que permite aos consumidores
     * descartarem uma reentrega antiga sem sobrescrever um estado mais novo.
     */
    @Column(nullable = false)
    private long versao;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    protected Consulta() {
    }

    public Consulta(Paciente paciente, Medico medico, LocalDateTime dataHora, String observacoes) {
        this.paciente = paciente;
        this.medico = medico;
        this.dataHora = dataHora;
        this.observacoes = observacoes;
        this.status = StatusConsulta.AGENDADA;
        this.versao = 1L;
    }

    @PrePersist
    void aoCriar() {
        final LocalDateTime agora = LocalDateTime.now();
        this.criadoEm = agora;
        this.atualizadoEm = agora;
        atualizarHorarioOcupado();
    }

    @PreUpdate
    void aoAtualizar() {
        this.atualizadoEm = LocalDateTime.now();
        atualizarHorarioOcupado();
    }

    private void atualizarHorarioOcupado() {
        this.horarioOcupado = estaCancelada() ? null : dataHora;
    }

    /** Alteracao parcial: campo nulo significa "nao mexer", nao "apagar". */
    public void alterar(LocalDateTime novaDataHora, StatusConsulta novoStatus, String novasObservacoes) {
        if (novaDataHora != null) {
            this.dataHora = novaDataHora;
        }
        if (novoStatus != null) {
            this.status = novoStatus;
        }
        if (novasObservacoes != null) {
            this.observacoes = novasObservacoes;
        }
        this.versao++;
    }

    public void cancelar() {
        this.status = StatusConsulta.CANCELADA;
        this.versao++;
    }

    public boolean estaCancelada() {
        return status == StatusConsulta.CANCELADA;
    }

    public Long getId() {
        return id;
    }

    public Paciente getPaciente() {
        return paciente;
    }

    public Medico getMedico() {
        return medico;
    }

    public LocalDateTime getDataHora() {
        return dataHora;
    }

    public StatusConsulta getStatus() {
        return status;
    }

    public String getObservacoes() {
        return observacoes;
    }

    public long getVersao() {
        return versao;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public LocalDateTime getAtualizadoEm() {
        return atualizadoEm;
    }
}
