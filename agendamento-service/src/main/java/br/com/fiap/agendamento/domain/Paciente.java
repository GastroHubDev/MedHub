package br.com.fiap.agendamento.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * Dados clinicos e de contato do paciente.
 *
 * <p>Usa {@code @MapsId}: a chave primaria do paciente <b>e</b> a do seu usuario. Isso faz o
 * {@code usuarioId} que viaja no JWT ser o mesmo {@code pacienteId} que viaja no evento Kafka,
 * de modo que a regra "paciente so ve as proprias consultas" e uma comparacao direta de ids,
 * sem claim adicional nem consulta extra ao banco nos servicos de leitura.</p>
 */
@Entity
@Table(name = "paciente")
public class Paciente {

    @Id
    private Long id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id")
    private Usuario usuario;

    @Column(nullable = false, unique = true, length = 14)
    private String cpf;

    @Column(length = 20)
    private String telefone;

    @Column(name = "data_nascimento")
    private LocalDate dataNascimento;

    protected Paciente() {
    }

    public Paciente(Usuario usuario, String cpf, String telefone, LocalDate dataNascimento) {
        this.usuario = usuario;
        this.cpf = cpf;
        this.telefone = telefone;
        this.dataNascimento = dataNascimento;
    }

    public Long getId() {
        return id;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public String getNome() {
        return usuario.getNome();
    }

    public String getEmail() {
        return usuario.getEmail();
    }

    public String getCpf() {
        return cpf;
    }

    public String getTelefone() {
        return telefone;
    }

    public LocalDate getDataNascimento() {
        return dataNascimento;
    }
}
