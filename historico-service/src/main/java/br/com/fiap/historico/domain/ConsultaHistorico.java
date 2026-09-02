package br.com.fiap.historico.domain;

import br.com.fiap.comum.evento.StatusConsulta;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * Estado atual de uma consulta no read model.
 *
 * <p>Este servico nao tem lado de escrita: a tabela e derivada <b>exclusivamente</b> dos
 * eventos do topico do Kafka. E o que permite reconstruir o historico inteiro a partir do log,
 * apagando a base e reprocessando do offset zero.</p>
 */
@Entity
@Table(name = "consulta_historico")
public class ConsultaHistorico {

    @Id
    @Column(name = "consulta_id")
    private Long consultaId;

    @Column(name = "paciente_id", nullable = false)
    private Long pacienteId;

    @Column(name = "paciente_nome", nullable = false, length = 120)
    private String pacienteNome;

    @Column(name = "paciente_email", nullable = false, length = 150)
    private String pacienteEmail;

    @Column(name = "medico_id", nullable = false)
    private Long medicoId;

    @Column(name = "medico_nome", nullable = false, length = 120)
    private String medicoNome;

    @Column(name = "medico_especialidade", length = 80)
    private String medicoEspecialidade;

    @Column(name = "data_hora", nullable = false)
    private LocalDateTime dataHora;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusConsulta status;

    @Column(length = 1000)
    private String observacoes;

    @Column(nullable = false)
    private long versao;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    protected ConsultaHistorico() {
    }

    public ConsultaHistorico(Long consultaId) {
        this.consultaId = consultaId;
    }

    public void aplicar(Long pacienteId, String pacienteNome, String pacienteEmail, Long medicoId,
                        String medicoNome, String medicoEspecialidade, LocalDateTime dataHora,
                        StatusConsulta status, String observacoes, long versao) {
        this.pacienteId = pacienteId;
        this.pacienteNome = pacienteNome;
        this.pacienteEmail = pacienteEmail;
        this.medicoId = medicoId;
        this.medicoNome = medicoNome;
        this.medicoEspecialidade = medicoEspecialidade;
        this.dataHora = dataHora;
        this.status = status;
        this.observacoes = observacoes;
        this.versao = versao;
        this.atualizadoEm = LocalDateTime.now();
    }

    /** Reentrega ou evento fora de ordem: nao pode sobrescrever um estado mais novo. */
    public boolean ehMaisAntigoQue(long versaoRecebida) {
        return versaoRecebida <= this.versao;
    }

    public Long getConsultaId() {
        return consultaId;
    }

    public Long getPacienteId() {
        return pacienteId;
    }

    public String getPacienteNome() {
        return pacienteNome;
    }

    public String getPacienteEmail() {
        return pacienteEmail;
    }

    public Long getMedicoId() {
        return medicoId;
    }

    public String getMedicoNome() {
        return medicoNome;
    }

    public String getMedicoEspecialidade() {
        return medicoEspecialidade;
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

    public LocalDateTime getAtualizadoEm() {
        return atualizadoEm;
    }
}
