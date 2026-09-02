package br.com.fiap.comum.evento;

/**
 * Situacao de uma consulta. Faz parte do contrato do evento porque os consumidores
 * precisam dela para decidir o que fazer sem consultar o servico de agendamento
 * (ex.: nao enviar lembrete de consulta cancelada).
 */
public enum StatusConsulta {
    AGENDADA,
    REALIZADA,
    CANCELADA
}
