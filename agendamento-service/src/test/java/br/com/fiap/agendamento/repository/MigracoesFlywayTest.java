package br.com.fiap.agendamento.repository;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.fiap.agendamento.domain.Consulta;
import br.com.fiap.comum.evento.StatusConsulta;
import br.com.fiap.comum.seguranca.Role;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

/**
 * Prova que as migracoes Flyway rodam no H2 exatamente como rodarao no Postgres e que o
 * {@code ddl-auto=validate} passa - ou seja, entidades e DDL nao divergiram.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MigracoesFlywayTest {

    @Autowired
    private ConsultaRepository consultaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PacienteRepository pacienteRepository;

    @Test
    void deveAplicarMigracoesECarregarASeed() {
        assertThat(usuarioRepository.count()).isEqualTo(5);
        assertThat(pacienteRepository.count()).isEqualTo(2);
        assertThat(consultaRepository.count()).isEqualTo(4);
    }

    @Test
    void seedDeveGuardarSenhasComoHashBcrypt() {
        final var usuario = usuarioRepository.findByEmail("medico@hospital.com").orElseThrow();

        assertThat(usuario.getSenha()).startsWith("$2a$10$");
        assertThat(usuario.getSenha()).isNotEqualTo("senha123");
        assertThat(usuario.getRole()).isEqualTo(Role.MEDICO);
    }

    @Test
    void pacienteDeveCompartilharAChavePrimariaDoUsuario() {
        final var usuario = usuarioRepository.findByEmail("paciente@hospital.com").orElseThrow();
        final var paciente = pacienteRepository.findById(usuario.getId()).orElseThrow();

        assertThat(paciente.getId()).isEqualTo(usuario.getId());
        assertThat(paciente.getNome()).isEqualTo(usuario.getNome());
    }

    @Test
    void entityGraphDeveCarregarPacienteEMedicoSemConsultaExtra() {
        final List<Consulta> consultas = consultaRepository.findByPacienteIdOrderByDataHoraAsc(4L);

        assertThat(consultas).hasSize(3);
        assertThat(consultas).allSatisfy(consulta -> {
            assertThat(consulta.getPaciente().getNome()).isNotBlank();
            assertThat(consulta.getMedico().getEspecialidade()).isNotBlank();
        });
    }

    @Test
    void seedDeveTerConsultasPassadasEFuturasParaDemonstrarOsFiltros() {
        final var todas = consultaRepository.findAllByOrderByDataHoraAsc();
        final var futuras = consultaRepository.findByDataHoraGreaterThanEqualOrderByDataHoraAsc(
                LocalDateTime.now());

        assertThat(todas).hasSize(4);
        assertThat(futuras).hasSize(3);
        assertThat(todas.get(0).getStatus()).isEqualTo(StatusConsulta.REALIZADA);
    }
}
