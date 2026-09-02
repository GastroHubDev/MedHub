package br.com.fiap.historico.dto;

import br.com.fiap.comum.evento.StatusConsulta;
import java.util.List;

/**
 * Filtros aceitos pelas queries. Espelha o input do schema GraphQL; campo nulo nao filtra.
 */
public record FiltroHistorico(
        Boolean apenasFuturas,
        List<StatusConsulta> status,
        String de,
        String ate,
        String especialidade,
        Long medicoId
) {

    public static FiltroHistorico vazio() {
        return new FiltroHistorico(null, null, null, null, null, null);
    }

    public boolean querApenasFuturas() {
        return Boolean.TRUE.equals(apenasFuturas);
    }
}
