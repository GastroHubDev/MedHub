/**
 * As cinco queries do schema (historico-service/src/main/resources/graphql/schema.graphqls),
 * prontas para uso, com variaveis editaveis.
 *
 * Quase todas pedem a trilha `eventos`: e ela que mostra, na propria resposta, que o historico
 * foi construido a partir do log do Kafka e nao de uma escrita direta neste servico.
 */
const GraphQL = (() => {

  const HISTORICO_PACIENTE = `query HistoricoPaciente($pacienteId: ID, $filtro: FiltroHistorico) {
  historicoPaciente(pacienteId: $pacienteId, filtro: $filtro) {
    pacienteId
    pacienteNome
    totalConsultas
    consultas {
      consultaId
      dataHora
      status
      observacoes
      versao
      atualizadoEm
      paciente { id nome email }
      medico { id nome especialidade }
      eventos { tipo statusResultante versao ocorridoEm registradoEm }
    }
  }
}`;

  const MINHAS_CONSULTAS = `query MinhasConsultas($filtro: FiltroHistorico) {
  minhasConsultas(filtro: $filtro) {
    consultaId
    dataHora
    status
    observacoes
    versao
    paciente { id nome email }
    medico { id nome especialidade }
    eventos { tipo statusResultante versao ocorridoEm }
  }
}`;

  const CONSULTAS_FUTURAS = `query ConsultasFuturas($pacienteId: ID) {
  consultasFuturas(pacienteId: $pacienteId) {
    consultaId
    dataHora
    status
    paciente { id nome }
    medico { nome especialidade }
    eventos { tipo versao }
  }
}`;

  const CONSULTA_HISTORICO = `query ConsultaHistorico($consultaId: ID!) {
  consultaHistorico(consultaId: $consultaId) {
    consultaId
    dataHora
    status
    observacoes
    versao
    atualizadoEm
    paciente { id nome email }
    medico { id nome especialidade }
    eventos { tipo statusResultante versao ocorridoEm registradoEm }
  }
}`;

  const ESTATISTICAS = `query EstatisticasPaciente($pacienteId: ID) {
  estatisticasPaciente(pacienteId: $pacienteId) {
    pacienteId
    total
    agendadas
    realizadas
    canceladas
    futuras
  }
}`;

  /** Catalogo exibido no seletor da aba Historico. */
  const CATALOGO = [
    {
      id: 'historicoPaciente',
      nome: 'historicoPaciente',
      descricao: 'Historico completo de um paciente, com totais e a trilha de eventos de cada '
        + 'consulta. Medico e enfermeiro podem informar qualquer pacienteId; um paciente que '
        + 'informar o id de outro recebe FORBIDDEN.',
      query: HISTORICO_PACIENTE,
      variaveis: { pacienteId: 4 },
    },
    {
      id: 'minhasConsultas',
      nome: 'minhasConsultas',
      descricao: 'Historico de quem esta autenticado, sem informar id: o servico usa o '
        + 'usuarioId que veio no token.',
      query: MINHAS_CONSULTAS,
      variaveis: { filtro: { apenasFuturas: false } },
    },
    {
      id: 'consultasFuturas',
      nome: 'consultasFuturas',
      descricao: 'Apenas as consultas que ainda vao acontecer.',
      query: CONSULTAS_FUTURAS,
      variaveis: { pacienteId: 4 },
    },
    {
      id: 'consultaHistorico',
      nome: 'consultaHistorico',
      descricao: 'Detalhe de uma consulta com a trilha completa. Use depois de editar a consulta '
        + 'na aba Agendamento para ver CONSULTA_CRIADA e CONSULTA_ATUALIZADA lado a lado.',
      query: CONSULTA_HISTORICO,
      variaveis: { consultaId: 2 },
    },
    {
      id: 'estatisticasPaciente',
      nome: 'estatisticasPaciente',
      descricao: 'Numeros consolidados do paciente. Agendadas + realizadas + canceladas '
        + 'devem fechar com o total.',
      query: ESTATISTICAS,
      variaveis: { pacienteId: 4 },
    },
    {
      id: 'filtroCombinado',
      nome: 'historicoPaciente (filtro combinado)',
      descricao: 'A consulta flexivel que justifica o GraphQL no enunciado: varios filtros '
        + 'combinados sem multiplicar endpoints. Edite o filtro a vontade.',
      query: HISTORICO_PACIENTE,
      variaveis: {
        pacienteId: 4,
        filtro: { status: ['AGENDADA'], especialidade: 'Cardiologia', apenasFuturas: true },
      },
    },
  ];

  const porId = (id) => CATALOGO.find((c) => c.id === id);

  /** Atalho usado pelo fluxo ponta a ponta e pela matriz de permissoes. */
  const detalheDaConsulta = (consultaId, sessao) =>
    Api.graphql(CONSULTA_HISTORICO, { consultaId }, {
      sessao,
      rotulo: `graphql consultaHistorico(${consultaId})`,
    });

  return {
    CATALOGO,
    porId,
    detalheDaConsulta,
    HISTORICO_PACIENTE,
    MINHAS_CONSULTAS,
    CONSULTAS_FUTURAS,
    CONSULTA_HISTORICO,
    ESTATISTICAS,
  };
})();
