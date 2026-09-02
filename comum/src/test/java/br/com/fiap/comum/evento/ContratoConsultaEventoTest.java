package br.com.fiap.comum.evento;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * O contrato do evento e o unico acoplamento entre os tres servicos. Este teste trava o
 * formato serializado: qualquer renomeacao de campo quebra aqui, e nao em producao com um
 * consumidor lendo {@code null}.
 */
class ContratoConsultaEventoTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private static ConsultaEvento eventoExemplo() {
        return new ConsultaEvento(
                42L,
                TipoEvento.CONSULTA_CRIADA,
                StatusConsulta.AGENDADA,
                7L, "Maria Souza", "maria@paciente.com",
                3L, "Dra. Ana Lima", "Cardiologia",
                "2026-09-10T14:30:00",
                "Retorno anual",
                1L,
                "2026-09-02T10:00:00Z");
    }

    @Test
    void deveManterOsNomesDosCamposDoContrato() throws Exception {
        final String json = mapper.writeValueAsString(eventoExemplo());

        assertThat(mapper.readTree(json).fieldNames()).toIterable().containsExactlyInAnyOrder(
                "consultaId", "tipoEvento", "status",
                "pacienteId", "pacienteNome", "pacienteEmail",
                "medicoId", "medicoNome", "medicoEspecialidade",
                "dataHora", "observacoes", "versao", "ocorridoEm");
    }

    @Test
    void deveSobreviverAoRoundTripDeSerializacao() throws Exception {
        final ConsultaEvento original = eventoExemplo();

        final String json = mapper.writeValueAsString(original);
        final ConsultaEvento reconstruido = mapper.readValue(json, ConsultaEvento.class);

        assertThat(reconstruido).isEqualTo(original);
    }

    @Test
    void deveSerializarEnumsComoTextoLegivel() throws Exception {
        final String json = mapper.writeValueAsString(eventoExemplo());

        assertThat(json).contains("\"tipoEvento\":\"CONSULTA_CRIADA\"")
                .contains("\"status\":\"AGENDADA\"");
    }

    @Test
    void topicoDeDlqDeveDerivarDoTopicoPrincipal() {
        assertThat(Topicos.CONSULTAS_DLT).isEqualTo(Topicos.CONSULTAS + ".DLT");
        assertThat(Topicos.GRUPO_NOTIFICACAO).isNotEqualTo(Topicos.GRUPO_HISTORICO);
    }
}
