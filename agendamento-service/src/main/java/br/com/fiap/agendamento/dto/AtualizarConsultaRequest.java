package br.com.fiap.agendamento.dto;

import br.com.fiap.comum.evento.StatusConsulta;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * Atualizacao parcial: todo campo e opcional e {@code null} significa "manter como esta".
 */
public record AtualizarConsultaRequest(
        LocalDateTime dataHora,

        StatusConsulta status,

        @Size(max = 1000, message = "Observacoes devem ter no maximo 1000 caracteres.")
        String observacoes
) {
}
