package br.com.fiap.historico.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.fiap.comum.evento.ConsultaEvento;
import br.com.fiap.comum.evento.StatusConsulta;
import br.com.fiap.comum.evento.TipoEvento;
import br.com.fiap.comum.seguranca.Role;
import br.com.fiap.comum.seguranca.UsuarioAutenticado;
import br.com.fiap.historico.repository.ConsultaHistoricoRepository;
import br.com.fiap.historico.repository.EventoConsultaRepository;
import br.com.fiap.historico.service.ProjecaoHistoricoService;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.graphql.test.tester.ExecutionGraphQlServiceTester;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.graphql.ExecutionGraphQlService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exercita cada query do schema e, principalmente, as regras de posse: um paciente nunca
 * enxerga o historico de outro, e a resposta traz {@code FORBIDDEN} sem vazar dados - lembrando
 * que GraphQL responde HTTP 200 mesmo em erro.
 */
@SpringBootTest
@Transactional
class HistoricoGraphQlTest {

    private static final Long PACIENTE_MARIA = 4L;
    private static final Long PACIENTE_JOAO = 5L;
    private static final Long MEDICO_ANA = 1L;

    @Autowired
    private ExecutionGraphQlService graphQlService;

    @Autowired
    private ProjecaoHistoricoService projecaoService;

    @Autowired
    private ConsultaHistoricoRepository consultaRepository;

    @Autowired
    private EventoConsultaRepository eventoRepository;

    private GraphQlTester tester;

    @BeforeEach
    void prepararMassa() {
        tester = ExecutionGraphQlServiceTester.create(graphQlService);
        eventoRepository.deleteAll();
        consultaRepository.deleteAll();

        // Maria: uma realizada no passado, duas agendadas no futuro.
        projecaoService.aplicar(evento(1L, PACIENTE_MARIA, "Maria Souza", MEDICO_ANA,
                "Cardiologia", LocalDateTime.now().minusDays(30), StatusConsulta.REALIZADA,
                TipoEvento.CONSULTA_CRIADA, 1L));
        projecaoService.aplicar(evento(2L, PACIENTE_MARIA, "Maria Souza", MEDICO_ANA,
                "Cardiologia", LocalDateTime.now().plusDays(3), StatusConsulta.AGENDADA,
                TipoEvento.CONSULTA_CRIADA, 1L));
        projecaoService.aplicar(evento(3L, PACIENTE_MARIA, "Maria Souza", 2L,
                "Ortopedia", LocalDateTime.now().plusDays(10), StatusConsulta.AGENDADA,
                TipoEvento.CONSULTA_CRIADA, 1L));
        // Joao: usada para provar o bloqueio de acesso cruzado.
        projecaoService.aplicar(evento(4L, PACIENTE_JOAO, "Joao Pereira", MEDICO_ANA,
                "Cardiologia", LocalDateTime.now().plusDays(5), StatusConsulta.AGENDADA,
                TipoEvento.CONSULTA_CRIADA, 1L));
    }

    private static ConsultaEvento evento(long consultaId, long pacienteId, String pacienteNome,
                                         long medicoId, String especialidade,
                                         LocalDateTime dataHora, StatusConsulta status,
                                         TipoEvento tipo, long versao) {
        return new ConsultaEvento(consultaId, tipo, status, pacienteId, pacienteNome,
                pacienteNome.toLowerCase().replace(" ", ".") + "@hospital.com",
                medicoId, "Dr(a). " + especialidade, especialidade,
                dataHora.withNano(0).toString(), "Observacao", versao, Instant.now().toString());
    }

    private void autenticar(Long id, String email, Role role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        UsuarioAutenticado.doToken(id, email, role), null,
                        List.of(new org.springframework.security.core.authority
                                .SimpleGrantedAuthority(role.authority()))));
    }

    @Nested
    class ComoMedico {

        @BeforeEach
        void logar() {
            autenticar(MEDICO_ANA, "medico@hospital.com", Role.MEDICO);
        }

        @Test
        void deveVerOHistoricoDeQualquerPaciente() {
            tester.document("""
                            query { historicoPaciente(pacienteId: 4) {
                                pacienteId pacienteNome totalConsultas
                                consultas { consultaId status }
                            } }""")
                    .execute()
                    .path("historicoPaciente.totalConsultas").entity(Integer.class).isEqualTo(3)
                    .path("historicoPaciente.pacienteNome").entity(String.class).isEqualTo("Maria Souza");
        }

        @Test
        void deveVerATrilhaDeEventosQueFormouOHistorico() {
            // Uma edicao feita no agendamento chega aqui como CONSULTA_ATUALIZADA.
            projecaoService.aplicar(evento(2L, PACIENTE_MARIA, "Maria Souza", MEDICO_ANA,
                    "Cardiologia", LocalDateTime.now().plusDays(3), StatusConsulta.REALIZADA,
                    TipoEvento.CONSULTA_ATUALIZADA, 2L));

            tester.document("""
                            query { consultaHistorico(consultaId: 2) {
                                status versao eventos { tipo statusResultante versao }
                            } }""")
                    .execute()
                    .path("consultaHistorico.status").entity(String.class).isEqualTo("REALIZADA")
                    .path("consultaHistorico.eventos").entityList(Object.class).hasSize(2)
                    .path("consultaHistorico.eventos[0].tipo").entity(String.class)
                    .isEqualTo("CONSULTA_CRIADA")
                    .path("consultaHistorico.eventos[1].tipo").entity(String.class)
                    .isEqualTo("CONSULTA_ATUALIZADA");
        }

        @Test
        void deveFiltrarPorEspecialidade() {
            tester.document("""
                            query { historicoPaciente(pacienteId: 4,
                                       filtro: { especialidade: "Ortopedia" }) {
                                consultas { consultaId medico { especialidade } }
                            } }""")
                    .execute()
                    .path("historicoPaciente.consultas").entityList(Object.class).hasSize(1)
                    .path("historicoPaciente.consultas[0].consultaId").entity(String.class)
                    .isEqualTo("3");
        }

        @Test
        void deveFiltrarPorStatus() {
            tester.document("""
                            query { historicoPaciente(pacienteId: 4,
                                       filtro: { status: [REALIZADA] }) {
                                consultas { consultaId status }
                            } }""")
                    .execute()
                    .path("historicoPaciente.consultas").entityList(Object.class).hasSize(1);
        }

        @Test
        void deveFiltrarApenasFuturas() {
            tester.document("""
                            query { consultasFuturas(pacienteId: 4) { consultaId dataHora } }""")
                    .execute()
                    .path("consultasFuturas").entityList(Object.class).hasSize(2);
        }

        @Test
        void deveCalcularEstatisticasDoPaciente() {
            tester.document("""
                            query { estatisticasPaciente(pacienteId: 4) {
                                total agendadas realizadas canceladas futuras
                            } }""")
                    .execute()
                    .path("estatisticasPaciente.total").entity(Integer.class).isEqualTo(3)
                    .path("estatisticasPaciente.agendadas").entity(Integer.class).isEqualTo(2)
                    .path("estatisticasPaciente.realizadas").entity(Integer.class).isEqualTo(1)
                    .path("estatisticasPaciente.futuras").entity(Integer.class).isEqualTo(2);
        }

        @Test
        void deveRecusarPeriodoInvertido() {
            tester.document("""
                            query { historicoPaciente(pacienteId: 4,
                                       filtro: { de: "2027-01-01T00:00:00", ate: "2026-01-01T00:00:00" }) {
                                totalConsultas
                            } }""")
                    .execute()
                    .errors()
                    .expect(erro -> erro.getErrorType() == ErrorType.BAD_REQUEST);
        }

        @Test
        void deveRecusarDataForaDoFormatoIso() {
            tester.document("""
                            query { historicoPaciente(pacienteId: 4, filtro: { de: "01/01/2027" }) {
                                totalConsultas
                            } }""")
                    .execute()
                    .errors()
                    .expect(erro -> erro.getErrorType() == ErrorType.BAD_REQUEST);
        }

        @Test
        void consultaInexistenteDeveDevolverNotFound() {
            tester.document("query { consultaHistorico(consultaId: 999) { consultaId } }")
                    .execute()
                    .errors()
                    .expect(erro -> erro.getErrorType() == ErrorType.NOT_FOUND);
        }
    }

    @Nested
    class ComoEnfermeiro {

        @BeforeEach
        void logar() {
            autenticar(3L, "enfermeiro@hospital.com", Role.ENFERMEIRO);
        }

        @Test
        void deveAcessarOHistoricoDosPacientes() {
            tester.document("""
                            query { historicoPaciente(pacienteId: 5) {
                                pacienteNome totalConsultas
                            } }""")
                    .execute()
                    .path("historicoPaciente.pacienteNome").entity(String.class)
                    .isEqualTo("Joao Pereira")
                    .path("historicoPaciente.totalConsultas").entity(Integer.class).isEqualTo(1);
        }
    }

    @Nested
    class ComoPaciente {

        @BeforeEach
        void logar() {
            autenticar(PACIENTE_MARIA, "paciente@hospital.com", Role.PACIENTE);
        }

        @Test
        void deveReceberOProprioHistoricoSemInformarId() {
            tester.document("""
                            query { historicoPaciente { pacienteId totalConsultas
                                consultas { paciente { email } } } }""")
                    .execute()
                    .path("historicoPaciente.pacienteId").entity(String.class).isEqualTo("4")
                    .path("historicoPaciente.totalConsultas").entity(Integer.class).isEqualTo(3);
        }

        @Test
        void minhasConsultasDeveTrazerApenasAsSuas() {
            tester.document("query { minhasConsultas { consultaId paciente { id } } }")
                    .execute()
                    .path("minhasConsultas").entityList(Object.class).hasSize(3);
        }

        @Test
        void naoDeveVerOHistoricoDeOutroPaciente() {
            // O campo e nao-nulo no schema (HistoricoPaciente!), entao o erro propaga para a
            // raiz e "data" volta inteiramente nula - nenhum dado do paciente 5 vaza.
            tester.document("query { historicoPaciente(pacienteId: 5) { totalConsultas } }")
                    .execute()
                    .errors()
                    .expect(erro -> erro.getErrorType() == ErrorType.FORBIDDEN)
                    .verify()
                    .path("$.data").valueIsNull();
        }

        @Test
        void naoDeveAbrirConsultaDeOutroPaciente() {
            tester.document("query { consultaHistorico(consultaId: 4) { consultaId } }")
                    .execute()
                    .errors()
                    .expect(erro -> erro.getErrorType() == ErrorType.FORBIDDEN);
        }

        @Test
        void naoDeveVerEstatisticasDeOutroPaciente() {
            tester.document("query { estatisticasPaciente(pacienteId: 5) { total } }")
                    .execute()
                    .errors()
                    .expect(erro -> erro.getErrorType() == ErrorType.FORBIDDEN);
        }

        @Test
        void consultasFuturasDeveSerRestritaAoProprioPaciente() {
            tester.document("query { consultasFuturas { consultaId paciente { id } } }")
                    .execute()
                    .path("consultasFuturas").entityList(Object.class).hasSize(2);

            assertThat(consultaRepository.countByPacienteId(PACIENTE_JOAO)).isEqualTo(1);
        }
    }
}
