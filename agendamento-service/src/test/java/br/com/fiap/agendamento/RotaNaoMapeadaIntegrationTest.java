package br.com.fiap.agendamento;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * Rota inexistente tem de responder 404, e nao 401.
 *
 * <p>Sobe em container real de proposito: o 404 de rota nao mapeada so aparece depois do
 * dispatch para {@code /error}, e o MockMvc nao simula esse segundo dispatch - um teste com
 * MockMvc passaria mesmo com a cadeia de seguranca barrando o forward.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RotaNaoMapeadaIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private String tokenMedico;

    @BeforeEach
    void autenticar() throws Exception {
        final HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        final ResponseEntity<String> resposta = restTemplate.postForEntity("/api/auth/login",
                new HttpEntity<>("""
                        {"email": "medico@hospital.com", "senha": "senha123"}""", cabecalhos),
                String.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        final JsonNode json = objectMapper.readTree(resposta.getBody());
        tokenMedico = json.get("token").asText();
    }

    private ResponseEntity<String> chamar(HttpMethod metodo, String caminho, String token) {
        final HttpHeaders cabecalhos = new HttpHeaders();
        if (token != null) {
            cabecalhos.setBearerAuth(token);
        }
        return restTemplate.exchange(caminho, metodo, new HttpEntity<>(cabecalhos), String.class);
    }

    @Test
    @DisplayName("rota inexistente com token valido devolve 404, nao 401")
    void deveDevolver404EmRotaInexistente() {
        assertThat(chamar(HttpMethod.GET, "/api/rota-que-nao-existe", tokenMedico).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("a rota de cancelamento removida responde 404, provando que nao existe mais")
    void deveDevolver404NaRotaDeCancelamentoRemovida() {
        assertThat(chamar(HttpMethod.POST, "/api/consultas/3/cancelar", tokenMedico).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("liberar o dispatch de erro nao abre rota real: sem token continua 401")
    void deveManter401EmRotaRealSemToken() {
        assertThat(chamar(HttpMethod.GET, "/api/consultas", null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
