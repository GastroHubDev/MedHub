package br.com.fiap.notificacao.domain;

import br.com.fiap.comum.evento.StatusConsulta;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * Projecao local das consultas, alimentada exclusivamente pelos eventos do Kafka.
 *
 * <p>Existe para que o servico seja autonomo: o agendador de lembretes varre esta tabela e
 * nunca chama o servico de agendamento, de modo que uma indisponibilidade la nao impede o
 * envio dos lembretes daqui.</p>
 *
 * <p>A chave primaria e o id da consulta de origem - nao um id proprio gerado. E o que torna o
 * consumo idempotente: reprocessar o mesmo evento sobrescreve a linha em vez de duplicar.</p>
 */
@Entity
@Table(name = "consulta_agendada")
public class ConsultaAgendada {

    @Id
    @Column(name = "consulta_id")
    private Long consultaId;

    @Column(name = "paciente_id", nullable = false)
    private Long pacienteId;

    @Column(name = "paciente_nome", nullable = false, length = 120)
    private String pacienteNome;

    @Column(name = "paciente_email", nullable = false, length = 150)
    private String pacienteEmail;

    @Column(name = "medico_nome", nullable = false, length = 120)
    private String medicoNome;

    @Column(name = "medico_especialidade", length = 80)
    private String medicoEspecialidade;

    @Column(name = "data_hora", nullable = false)
    private LocalDateTime dataHora;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusConsulta status;

    /** Ultima versao do agregado aplicada aqui; base para descartar reentregas antigas. */
    @Column(nullable = false)
    private long versao;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    protected ConsultaAgendada() {
    }

    public ConsultaAgendada(Long consultaId, Long pacienteId, String pacienteNome,
                            String pacienteEmail, String medicoNome, String medicoEspecialidade,
                            LocalDateTime dataHora, StatusConsulta status, long versao) {
        this.consultaId = consultaId;
        atualizar(pacienteId, pacienteNome, pacienteEmail, medicoNome, medicoEspecialidade,
                dataHora, status, versao);
    }

    public void atualizar(Long pacienteId, String pacienteNome, String pacienteEmail,
                          String medicoNome, String medicoEspecialidade, LocalDateTime dataHora,
                          StatusConsulta status, long versao) {
        this.pacienteId = pacienteId;
        this.pacienteNome = pacienteNome;
        this.pacienteEmail = pacienteEmail;
        this.medicoNome = medicoNome;
        this.medicoEspecialidade = medicoEspecialidade;
        this.dataHora = dataHora;
        this.status = status;
        this.versao = versao;
        this.atualizadoEm = LocalDateTime.now();
    }

    /** Evento mais antigo que o estado ja aplicado nao deve sobrescrever o mais novo. */
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

    public long getVersao() {
        return versao;
    }

    public LocalDateTime getAtualizadoEm() {
        return atualizadoEm;
    }
}
