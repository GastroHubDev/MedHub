/**
 * Cenarios executaveis: a matriz de permissoes e o roteiro ponta a ponta.
 *
 * A matriz nao e uma tabela estatica de documentacao - ela dispara as requisicoes de verdade e
 * compara o resultado obtido com o esperado pelo enunciado. Celula verde significa que o
 * backend se comporta como especificado; vermelha e uma divergencia real.
 */
const Cenarios = (() => {

  const PACIENTE_PROPRIO = 4;   // Maria, o paciente logado nos testes
  const PACIENTE_ALHEIO = 5;    // Joao, usado para provar o bloqueio de acesso cruzado
  const CONSULTA_ALHEIA = 4;    // consulta do paciente 5 na seed
  const MEDICO_PADRAO = 1;

  /**
   * Horarios distintos por execucao.
   *
   * Necessario por causa do indice unico uk_consulta_medico_horario: se os tres perfis
   * tentassem agendar no mesmo horario, o segundo receberia 400 (double-booking) em vez do
   * 201/403 esperado, e a matriz acusaria uma falha que nao existe.
   */
  let contadorDeHorarios = 0;

  function proximoHorario(diasBase) {
    contadorDeHorarios += 1;
    const data = new Date();
    data.setDate(data.getDate() + diasBase + contadorDeHorarios);

    // Hora, minuto e segundo saem do relogio, e nao de um contador em memoria.
    //
    // Um contador zeraria a cada F5: rodar a matriz, recarregar a pagina e rodar de novo
    // reproduziria exatamente os mesmos horarios, o indice unico uk_consulta_medico_horario
    // recusaria com 400 e a matriz acusaria uma divergencia que nao existe.
    const agora = new Date();
    data.setHours(7 + (contadorDeHorarios % 12), agora.getMinutes(), agora.getSeconds(), 0);
    return isoLocal(data);
  }

  /** O backend espera LocalDateTime sem timezone; toISOString() mandaria UTC com Z. */
  function isoLocal(data) {
    const p = (n) => String(n).padStart(2, '0');
    return `${data.getFullYear()}-${p(data.getMonth() + 1)}-${p(data.getDate())}`
      + `T${p(data.getHours())}:${p(data.getMinutes())}:${p(data.getSeconds())}`;
  }

  // =========================================================================
  // Matriz de permissoes
  // =========================================================================

  /**
   * Cada cenario define o que fazer e o que se espera de cada perfil.
   *
   * `esperado` guarda o desfecho previsto pelo enunciado por perfil. A comparacao usa o campo
   * `resumo` do Api ("201", "403", "200 (FORBIDDEN)"), o que cobre REST e GraphQL com a mesma
   * regra - importante porque o GraphQL responde 200 mesmo ao negar acesso.
   */
  function construirCenarios(consultaDescartavel) {
    return [
      {
        grupo: 'Consultas (REST)',
        nome: 'GET /api/consultas',
        detalhe: 'Todos podem listar; o paciente recebe apenas as proprias.',
        esperado: { MEDICO: '200', ENFERMEIRO: '200', PACIENTE: '200' },
        executar: (sessao) => Api.agendamento('/consultas', {
          sessao, rotulo: 'matriz: listar consultas',
        }),
      },
      {
        grupo: 'Consultas (REST)',
        nome: `GET /api/consultas?pacienteId=${PACIENTE_ALHEIO}`,
        detalhe: 'Paciente pedindo dados de outro paciente deve receber 403 explicito.',
        esperado: { MEDICO: '200', ENFERMEIRO: '200', PACIENTE: '403' },
        executar: (sessao) => Api.agendamento(`/consultas?pacienteId=${PACIENTE_ALHEIO}`, {
          sessao, rotulo: 'matriz: listar consultas de outro paciente',
        }),
      },
      {
        grupo: 'Consultas (REST)',
        nome: `GET /api/consultas/${CONSULTA_ALHEIA}`,
        detalhe: 'Consulta que pertence ao paciente 5.',
        esperado: { MEDICO: '200', ENFERMEIRO: '200', PACIENTE: '403' },
        executar: (sessao) => Api.agendamento(`/consultas/${CONSULTA_ALHEIA}`, {
          sessao, rotulo: 'matriz: abrir consulta de outro paciente',
        }),
      },
      {
        grupo: 'Consultas (REST)',
        nome: 'POST /api/consultas',
        detalhe: 'Enfermeiro registra consultas; paciente nao. Cada perfil usa um horario proprio '
          + 'para nao esbarrar no indice de double-booking.',
        esperado: { MEDICO: '201', ENFERMEIRO: '201', PACIENTE: '403' },
        executar: (sessao) => Api.agendamento('/consultas', {
          sessao,
          metodo: 'POST',
          corpo: {
            pacienteId: PACIENTE_PROPRIO,
            medicoId: MEDICO_PADRAO,
            dataHora: proximoHorario(60),
            observacoes: 'Criada pela matriz de permissoes',
          },
          rotulo: 'matriz: registrar consulta',
        }),
      },
      {
        grupo: 'Consultas (REST)',
        nome: 'PUT /api/consultas/{id}',
        detalhe: 'Editar o historico e prerrogativa do medico. Roda sobre uma consulta '
          + 'descartavel criada para a matriz, nunca sobre a massa da seed.',
        esperado: { MEDICO: '200', ENFERMEIRO: '403', PACIENTE: '403' },
        executar: (sessao) => Api.agendamento(`/consultas/${consultaDescartavel}`, {
          sessao,
          metodo: 'PUT',
          corpo: { observacoes: `Editada pela matriz em ${new Date().toLocaleTimeString('pt-BR')}` },
          rotulo: 'matriz: editar consulta',
        }),
      },
      {
        grupo: 'Consultas (REST)',
        nome: 'POST /api/consultas/{id}/cancelar',
        detalhe: 'Tambem restrito ao medico. Os perfis sem permissao sao testados primeiro, '
          + 'porque o cancelamento do medico e irreversivel.',
        esperado: { MEDICO: '200', ENFERMEIRO: '403', PACIENTE: '403' },
        ordemInversa: true,
        executar: (sessao) => Api.agendamento(`/consultas/${consultaDescartavel}/cancelar`, {
          sessao, metodo: 'POST', rotulo: 'matriz: cancelar consulta',
        }),
      },
      {
        grupo: 'Notificacoes (REST)',
        nome: 'GET /api/notificacoes',
        detalhe: 'Visao operacional da equipe clinica; o paciente nao acessa.',
        esperado: { MEDICO: '200', ENFERMEIRO: '200', PACIENTE: '403' },
        executar: (sessao) => Api.notificacao('/notificacoes', {
          sessao, rotulo: 'matriz: listar notificacoes',
        }),
      },
      {
        grupo: 'Historico (GraphQL)',
        nome: 'minhasConsultas',
        detalhe: 'Cada perfil recebe o proprio historico; ninguem e barrado.',
        esperado: { MEDICO: '200', ENFERMEIRO: '200', PACIENTE: '200' },
        executar: (sessao) => Api.graphql(GraphQL.MINHAS_CONSULTAS, { filtro: {} }, {
          sessao, rotulo: 'matriz: graphql minhasConsultas',
        }),
      },
      {
        grupo: 'Historico (GraphQL)',
        nome: `historicoPaciente(${PACIENTE_PROPRIO})`,
        detalhe: 'O paciente 4 e o proprio usuario logado, entao ninguem e barrado aqui.',
        esperado: { MEDICO: '200', ENFERMEIRO: '200', PACIENTE: '200' },
        executar: (sessao) => Api.graphql(GraphQL.HISTORICO_PACIENTE, { pacienteId: PACIENTE_PROPRIO }, {
          sessao, rotulo: 'matriz: graphql historicoPaciente(proprio)',
        }),
      },
      {
        grupo: 'Historico (GraphQL)',
        nome: `historicoPaciente(${PACIENTE_ALHEIO})`,
        detalhe: 'GraphQL responde 200 mesmo negando: o que prova a negacao e a classification.',
        esperado: { MEDICO: '200', ENFERMEIRO: '200', PACIENTE: '200 (FORBIDDEN)' },
        executar: (sessao) => Api.graphql(GraphQL.HISTORICO_PACIENTE, { pacienteId: PACIENTE_ALHEIO }, {
          sessao, rotulo: 'matriz: graphql historicoPaciente(alheio)',
        }),
      },
      {
        grupo: 'Historico (GraphQL)',
        nome: `consultaHistorico(${CONSULTA_ALHEIA})`,
        detalhe: 'Consulta do paciente 5 aberta por quem nao e ele.',
        esperado: { MEDICO: '200', ENFERMEIRO: '200', PACIENTE: '200 (FORBIDDEN)' },
        executar: (sessao) => GraphQL.detalheDaConsulta(CONSULTA_ALHEIA, sessao),
      },
      {
        grupo: 'Historico (GraphQL)',
        nome: `estatisticasPaciente(${PACIENTE_ALHEIO})`,
        detalhe: 'Ate os numeros agregados de outro paciente sao barrados.',
        esperado: { MEDICO: '200', ENFERMEIRO: '200', PACIENTE: '200 (FORBIDDEN)' },
        executar: (sessao) => Api.graphql(GraphQL.ESTATISTICAS, { pacienteId: PACIENTE_ALHEIO }, {
          sessao, rotulo: 'matriz: graphql estatisticasPaciente(alheio)',
        }),
      },
      {
        grupo: 'Historico (GraphQL)',
        nome: 'consultaHistorico(999999)',
        detalhe: 'Registro inexistente deve virar NOT_FOUND, nao FORBIDDEN nem erro interno.',
        esperado: { MEDICO: '200 (NOT_FOUND)', ENFERMEIRO: '200 (NOT_FOUND)', PACIENTE: '200 (NOT_FOUND)' },
        executar: (sessao) => GraphQL.detalheDaConsulta(999999, sessao),
      },
      {
        grupo: 'Autenticacao',
        nome: 'REST sem token',
        detalhe: 'Independe do perfil: sem Authorization o filtro barra antes de chegar ao controller.',
        esperado: { MEDICO: '401', ENFERMEIRO: '401', PACIENTE: '401' },
        executar: () => Api.agendamento('/consultas', { rotulo: 'matriz: REST sem token' }),
      },
      {
        grupo: 'Autenticacao',
        nome: 'REST com token adulterado',
        detalhe: 'A assinatura HS256 nao confere, entao o token e descartado silenciosamente.',
        esperado: { MEDICO: '401', ENFERMEIRO: '401', PACIENTE: '401' },
        executar: (sessao) => Api.agendamento('/consultas', {
          tokenCru: `${sessao.token}-assinatura-alterada`,
          rotulo: 'matriz: REST com token adulterado',
        }),
      },
      {
        grupo: 'Autenticacao',
        nome: 'GraphQL sem token',
        detalhe: 'Sem principal autenticado a classificacao e UNAUTHORIZED, nao FORBIDDEN.',
        esperado: {
          MEDICO: '200 (UNAUTHORIZED)',
          ENFERMEIRO: '200 (UNAUTHORIZED)',
          PACIENTE: '200 (UNAUTHORIZED)',
        },
        executar: () => Api.graphql(GraphQL.MINHAS_CONSULTAS, { filtro: {} }, {
          rotulo: 'matriz: graphql sem token',
        }),
      },
    ];
  }

  /**
   * Cria a consulta que os cenarios de PUT e cancelar vao maltratar.
   *
   * Existe para que a matriz jamais altere as consultas 1-4 da seed: rodar a matriz duas vezes
   * seguidas tem de dar o mesmo resultado, e mexer na massa fixa quebraria isso.
   */
  async function criarConsultaDescartavel(sessaoMedico) {
    const resposta = await Api.agendamento('/consultas', {
      sessao: sessaoMedico,
      metodo: 'POST',
      corpo: {
        pacienteId: PACIENTE_PROPRIO,
        medicoId: MEDICO_PADRAO,
        dataHora: proximoHorario(90),
        observacoes: 'Consulta descartavel criada para a matriz de permissoes',
      },
      rotulo: 'matriz: preparar consulta descartavel',
    });

    if (!resposta.ok) {
      return { ok: false, erro: resposta.erro || `HTTP ${resposta.status}` };
    }
    return { ok: true, id: resposta.corpo.id };
  }

  /**
   * Executa a matriz inteira.
   *
   * @param {Array} sessoes  um representante por perfil (Sessoes.trio())
   * @param {Function} aoProgredir  recebe cada linha assim que ela termina, para a tela ir
   *                                preenchendo em vez de congelar ate o fim
   */
  async function executarMatriz(sessoes, aoProgredir) {
    const sessaoMedico = sessoes.find((s) => s.perfil === 'MEDICO');
    if (!sessaoMedico) {
      return { ok: false, erro: 'E preciso uma sessao MEDICO para preparar a consulta descartavel.' };
    }

    const preparo = await criarConsultaDescartavel(sessaoMedico);
    if (!preparo.ok) {
      return { ok: false, erro: `Nao foi possivel preparar a massa da matriz: ${preparo.erro}` };
    }

    const linhas = [];
    for (const cenario of construirCenarios(preparo.id)) {
      // O cancelamento pelo medico e irreversivel: os perfis sem permissao rodam antes, senao
      // encontrariam a consulta ja cancelada e receberiam 400 em vez do 403 esperado.
      const ordem = cenario.ordemInversa ? [...sessoes].reverse() : sessoes;

      const celulas = [];
      for (const sessao of ordem) {
        const resultado = await cenario.executar(sessao);
        if (resultado.status === 401 && !cenario.nome.includes('sem token')
            && !cenario.nome.includes('adulterado')) {
          Sessoes.marcarExpirada(sessao.email);
        }
        const esperado = cenario.esperado[sessao.perfil];
        celulas.push({
          perfil: sessao.perfil,
          esperado,
          obtido: resultado.resumo,
          conforme: resultado.resumo === esperado,
          erro: resultado.erro,
        });
      }
      // Reordena para a tabela sair sempre na ordem do enunciado.
      celulas.sort((a, b) => ordemDoPerfil(a.perfil) - ordemDoPerfil(b.perfil));

      const linha = { ...cenario, celulas, conforme: celulas.every((c) => c.conforme) };
      linhas.push(linha);
      if (aoProgredir) aoProgredir(linha, linhas.length);
    }

    return {
      ok: true,
      linhas,
      consultaDescartavel: preparo.id,
      total: linhas.reduce((soma, l) => soma + l.celulas.length, 0),
      divergentes: linhas.filter((l) => !l.conforme).length,
    };
  }

  const ORDEM_PERFIS = { MEDICO: 0, ENFERMEIRO: 1, PACIENTE: 2 };
  const ordemDoPerfil = (perfil) => (perfil in ORDEM_PERFIS ? ORDEM_PERFIS[perfil] : 9);

  // =========================================================================
  // Fluxo ponta a ponta
  // =========================================================================

  const espera = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

  /**
   * Aguarda a consulta aparecer no historico, medindo quanto demorou.
   *
   * Esta espera nao e um contorno de bug: e o assincrono do enunciado acontecendo. O evento
   * precisa ser publicado pela outbox (a cada 2s) e consumido pelo grupo do historico. Mostrar
   * o tempo medido e a evidencia mais direta de que o Kafka esta no meio do caminho.
   */
  async function aguardarPropagacao(consultaId, sessao, tempoLimiteMs = 20000) {
    const inicio = performance.now();
    let tentativas = 0;

    while (performance.now() - inicio < tempoLimiteMs) {
      tentativas += 1;
      const resposta = await GraphQL.detalheDaConsulta(consultaId, sessao);
      if (resposta.ok && resposta.corpo && resposta.corpo.data
          && resposta.corpo.data.consultaHistorico) {
        return {
          ok: true,
          tentativas,
          duracaoMs: Math.round(performance.now() - inicio),
          consulta: resposta.corpo.data.consultaHistorico,
        };
      }
      await espera(500);
    }

    return { ok: false, tentativas, duracaoMs: Math.round(performance.now() - inicio) };
  }

  /** Idem para a notificacao, que tem seu proprio consumer group. */
  async function aguardarNotificacao(consultaId, sessao, tempoLimiteMs = 20000) {
    const inicio = performance.now();
    let tentativas = 0;

    while (performance.now() - inicio < tempoLimiteMs) {
      tentativas += 1;
      const resposta = await Api.notificacao(`/notificacoes?consultaId=${consultaId}`, {
        sessao, rotulo: `fluxo: notificacoes da consulta ${consultaId}`,
      });
      if (resposta.ok && Array.isArray(resposta.corpo) && resposta.corpo.length > 0) {
        return {
          ok: true,
          tentativas,
          duracaoMs: Math.round(performance.now() - inicio),
          notificacoes: resposta.corpo,
        };
      }
      await espera(500);
    }

    return { ok: false, tentativas, duracaoMs: Math.round(performance.now() - inicio) };
  }

  /**
   * Roteiro completo: escrita no agendamento, travessia pelo Kafka, leitura no historico e
   * efeito no servico de notificacao. Cada passo e reportado assim que termina.
   */
  async function executarFluxoCompleto(aoProgredir) {
    const passo = (numero, titulo, estado, detalhe, duracaoMs) => {
      const registro = { numero, titulo, estado, detalhe, duracaoMs };
      if (aoProgredir) aoProgredir(registro);
      return registro;
    };

    const passos = [];
    const falhar = (numero, titulo, detalhe) => {
      passos.push(passo(numero, titulo, 'falha', detalhe));
      return { ok: false, passos };
    };

    // 1. Autenticacao do enfermeiro
    const login = await Sessoes.entrar('enfermeiro@hospital.com');
    if (!login.ok) {
      return falhar(1, 'Enfermeiro autentica', login.erro);
    }
    const enfermeiro = login.sessao;
    passos.push(passo(1, 'Enfermeiro autentica', 'ok', `perfil ${enfermeiro.perfil}`));

    // 2. Registro da consulta
    const dataHora = proximoHorario(30);
    const criacao = await Api.agendamento('/consultas', {
      sessao: enfermeiro,
      metodo: 'POST',
      corpo: {
        pacienteId: PACIENTE_PROPRIO,
        medicoId: MEDICO_PADRAO,
        dataHora,
        observacoes: 'Criada pelo fluxo ponta a ponta do painel',
      },
      rotulo: 'fluxo: registrar consulta',
    });
    if (!criacao.ok) {
      return falhar(2, 'Enfermeiro registra a consulta', criacao.erro || `HTTP ${criacao.status}`);
    }
    const consultaId = criacao.corpo.id;
    passos.push(passo(2, `Enfermeiro registra a consulta #${consultaId}`, 'ok',
      `${criacao.status} · versao ${criacao.corpo.versao}`, criacao.duracaoMs));

    // 3. Travessia pelo Kafka
    const medico = Sessoes.porPerfil('MEDICO') || (await Sessoes.entrar('medico@hospital.com')).sessao;
    if (!medico) {
      return falhar(3, 'Aguardando o evento atravessar o Kafka', 'Sem sessao MEDICO para consultar o historico.');
    }
    const propagacao = await aguardarPropagacao(consultaId, medico);
    if (!propagacao.ok) {
      return falhar(3, 'Aguardando o evento atravessar o Kafka',
        `A consulta nao apareceu no historico em ${propagacao.duracaoMs} ms. `
        + 'Verifique se o Kafka e o historico-service estao no ar.');
    }
    passos.push(passo(3, 'Evento atravessa o Kafka (outbox → topico → historico)', 'ok',
      `${propagacao.tentativas} tentativa(s) de leitura`, propagacao.duracaoMs));

    // 4. Leitura no historico
    passos.push(passo(4, 'Historico ja conhece a consulta (GraphQL)', 'ok',
      `status ${propagacao.consulta.status} · ${propagacao.consulta.eventos.length} evento(s) na trilha`));

    // 5. Efeito no servico de notificacao
    const notificacao = await aguardarNotificacao(consultaId, medico);
    if (notificacao.ok) {
      const tipos = notificacao.notificacoes.map((n) => n.tipo).join(', ');
      passos.push(passo(5, 'Notificacao registrada pelo outro consumer group', 'ok',
        `${tipos} · confira em http://localhost:8125`, notificacao.duracaoMs));
    } else {
      passos.push(passo(5, 'Notificacao registrada pelo outro consumer group', 'alerta',
        `Nada em ${notificacao.duracaoMs} ms. O historico funcionou, entao o topico esta ok; `
        + 'olhe o notificacao-service.'));
    }

    // 6. Edicao pelo medico
    const edicao = await Api.agendamento(`/consultas/${consultaId}`, {
      sessao: medico,
      metodo: 'PUT',
      corpo: { status: 'REALIZADA', observacoes: 'Paciente compareceu (editado pelo fluxo)' },
      rotulo: 'fluxo: medico edita a consulta',
    });
    if (!edicao.ok) {
      return falhar(6, 'Medico edita a consulta', edicao.erro || `HTTP ${edicao.status}`);
    }
    passos.push(passo(6, 'Medico edita a consulta', 'ok',
      `status ${edicao.corpo.status} · versao ${edicao.corpo.versao}`, edicao.duracaoMs));

    // 7. A edicao chega ao historico
    const inicioTrilha = performance.now();
    let trilha = null;
    while (performance.now() - inicioTrilha < 20000) {
      const detalhe = await GraphQL.detalheDaConsulta(consultaId, medico);
      const atual = detalhe.ok && detalhe.corpo.data ? detalhe.corpo.data.consultaHistorico : null;
      if (atual && atual.eventos.length >= 2) {
        trilha = atual;
        break;
      }
      await espera(500);
    }
    if (!trilha) {
      return falhar(7, 'A edicao chega ao historico',
        'A trilha nao ganhou o segundo evento dentro do tempo limite.');
    }
    passos.push(passo(7, 'A edicao do medico chega ao historico', 'ok',
      `trilha: ${trilha.eventos.map((e) => e.tipo).join(' → ')}`,
      Math.round(performance.now() - inicioTrilha)));

    return { ok: true, passos, consultaId, trilha };
  }

  return {
    executarMatriz, executarFluxoCompleto, aguardarPropagacao, aguardarNotificacao,
    proximoHorario, isoLocal, ordemDoPerfil,
    PACIENTE_PROPRIO, PACIENTE_ALHEIO, MEDICO_PADRAO,
  };
})();
