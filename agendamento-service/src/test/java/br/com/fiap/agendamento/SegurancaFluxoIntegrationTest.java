package br.com.fiap.agendamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exercita os niveis de acesso do enunciado ponta a ponta: login real contra os hashes BCrypt
 * da seed, e cada perfil batendo nos endpoints que pode e nos que nao pode.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SegurancaFluxoIntegrationTest {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String tokenMedico;
    private String tokenEnfermeiro;
    private String tokenPaciente;

    @BeforeEach
    void autenticarOsTresPerfis() throws Exception {
        tokenMedico = login("medico@hospital.com");
        tokenEnfermeiro = login("enfermeiro@hospital.com");
        tokenPaciente = login("paciente@hospital.com");
    }

    private String login(String email) throws Exception {
        final String corpo = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "senha": "senha123"}""".formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        final JsonNode json = objectMapper.readTree(corpo);
        assertThat(json.get("tipo").asText()).isEqualTo("Bearer");
        return json.get("token").asText();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    // ---------- Autenticacao ----------

    @Test
    @DisplayName("login com senha errada nao revela se o e-mail existe")
    void deveRecusarLoginComSenhaInvalida() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "medico@hospital.com", "senha": "errada"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Credenciais invalidas"));
    }

    @Test
    void deveDevolverAMesmaRespostaParaUsuarioInexistente() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "ninguem@hospital.com", "senha": "senha123"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Credenciais invalidas"));
    }

    @Test
    void deveDevolverPerfilCorretoParaCadaUsuario() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "enfermeiro@hospital.com", "senha": "senha123"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perfil").value("ENFERMEIRO"))
                .andExpect(jsonPath("$.nome").value("Carla Enfermeira"));
    }

    // ---------- Token ausente ou invalido ----------

    @Test
    void deveExigirTokenNosEndpointsProtegidos() throws Exception {
        mockMvc.perform(get("/api/consultas"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.erro").value("Token ausente ou invalido"));
    }

    @Test
    void deveRecusarTokenAdulterado() throws Exception {
        mockMvc.perform(get("/api/consultas")
                        .header("Authorization", bearer(tokenMedico) + "-assinatura-alterada"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- Acesso permitido por perfil ----------

    @Test
    void enfermeiroDeveRegistrarConsulta() throws Exception {
        final String dataHora = LocalDateTime.now().plusDays(20).withNano(0).format(ISO);

        mockMvc.perform(post("/api/consultas")
                        .header("Authorization", bearer(tokenEnfermeiro))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pacienteId": 4, "medicoId": 1, "dataHora": "%s",
                                 "observacoes": "Registrada pela enfermagem"}""".formatted(dataHora)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AGENDADA"))
                .andExpect(jsonPath("$.versao").value(1));
    }

    @Test
    void medicoDeveEditarConsulta() throws Exception {
        mockMvc.perform(put("/api/consultas/2")
                        .header("Authorization", bearer(tokenMedico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"observacoes": "Reagendamento confirmado por telefone"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.observacoes").value("Reagendamento confirmado por telefone"))
                .andExpect(jsonPath("$.versao").value(2));
    }

    @Test
    void medicoDeveCancelarConsulta() throws Exception {
        mockMvc.perform(post("/api/consultas/3/cancelar")
                        .header("Authorization", bearer(tokenMedico)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADA"));
    }

    @Test
    void enfermeiroDeveAcessarOHistoricoDeQualquerPaciente() throws Exception {
        mockMvc.perform(get("/api/consultas").param("pacienteId", "4")
                        .header("Authorization", bearer(tokenEnfermeiro)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void pacienteDeveVerApenasAsProprias() throws Exception {
        mockMvc.perform(get("/api/consultas")
                        .header("Authorization", bearer(tokenPaciente)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[*].paciente.email")
                        .value(org.hamcrest.Matchers.everyItem(
                                org.hamcrest.Matchers.equalTo("paciente@hospital.com"))));
    }

    @Test
    void filtroDeFuturasDeveExcluirConsultaPassada() throws Exception {
        mockMvc.perform(get("/api/consultas").param("apenasFuturas", "true")
                        .header("Authorization", bearer(tokenPaciente)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    // ---------- Acesso negado ----------

    @Test
    void pacienteNaoDeveRegistrarConsulta() throws Exception {
        final String dataHora = LocalDateTime.now().plusDays(21).withNano(0).format(ISO);

        mockMvc.perform(post("/api/consultas")
                        .header("Authorization", bearer(tokenPaciente))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pacienteId": 4, "medicoId": 1, "dataHora": "%s"}""".formatted(dataHora)))
                .andExpect(status().isForbidden());
    }

    @Test
    void enfermeiroNaoDeveEditarConsulta() throws Exception {
        mockMvc.perform(put("/api/consultas/2")
                        .header("Authorization", bearer(tokenEnfermeiro))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "REALIZADA"}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    void pacienteNaoDeveListarConsultasDeOutroPaciente() throws Exception {
        mockMvc.perform(get("/api/consultas").param("pacienteId", "5")
                        .header("Authorization", bearer(tokenPaciente)))
                .andExpect(status().isForbidden());
    }

    @Test
    void pacienteNaoDeveAbrirConsultaDeOutroPaciente() throws Exception {
        mockMvc.perform(get("/api/consultas/4")
                        .header("Authorization", bearer(tokenPaciente)))
                .andExpect(status().isForbidden());
    }

    // ---------- Validacoes de dominio ----------

    @Test
    void naoDeveAgendarNoPassado() throws Exception {
        final String ontem = LocalDateTime.now().minusDays(1).withNano(0).format(ISO);

        mockMvc.perform(post("/api/consultas")
                        .header("Authorization", bearer(tokenMedico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pacienteId": 4, "medicoId": 1, "dataHora": "%s"}""".formatted(ontem)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("data passada")));
    }

    @Test
    void naoDeveAgendarParaPacienteInexistente() throws Exception {
        final String dataHora = LocalDateTime.now().plusDays(22).withNano(0).format(ISO);

        mockMvc.perform(post("/api/consultas")
                        .header("Authorization", bearer(tokenMedico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pacienteId": 999, "medicoId": 1, "dataHora": "%s"}""".formatted(dataHora)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deveRejeitarRequisicaoSemCamposObrigatorios() throws Exception {
        mockMvc.perform(post("/api/consultas")
                        .header("Authorization", bearer(tokenMedico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Falha de validacao"));
    }
}
