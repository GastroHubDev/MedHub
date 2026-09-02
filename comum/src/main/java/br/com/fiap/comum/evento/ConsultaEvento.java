package br.com.fiap.comum.evento;

/**
 * Contrato do evento trafegado no topico {@link Topicos#CONSULTAS}.
 *
 * <p>O evento carrega o estado necessario para os consumidores trabalharem sozinhos
 * (event-carried state transfer): nem o servico de notificacao nem o de historico
 * precisam chamar o agendamento de volta para montar um e-mail ou o read model.</p>
 *
 * <p>Datas trafegam como {@code String} ISO-8601 de proposito - mantem o payload legivel
 * no Kafka UI e evita depender de um scalar customizado no GraphQL.</p>
 *
 * @param versao versao do agregado no momento do evento. E o que torna o consumo idempotente:
 *               reentrega de um evento com versao menor ou igual a ja aplicada e ignorada.
 */
public record ConsultaEvento(
        Long consultaId,
        TipoEvento tipoEvento,
        StatusConsulta status,
        Long pacienteId,
        String pacienteNome,
        String pacienteEmail,
        Long medicoId,
        String medicoNome,
        String medicoEspecialidade,
        String dataHora,
        String observacoes,
        long versao,
        String ocorridoEm
) {
}
