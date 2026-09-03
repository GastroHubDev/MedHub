/**
 * Renderizacao e ligacao dos controles.
 *
 * Nenhuma requisicao acontece aqui: tudo passa pelo Api, para que o Console HTTP veja todas.
 */
(() => {

  const $ = (id) => document.getElementById(id);
  const criar = (tag, classe, texto) => {
    const el = document.createElement(tag);
    if (classe) el.className = classe;
    if (texto !== undefined) el.textContent = texto;
    return el;
  };

  /** Sempre textContent, nunca innerHTML com dado do servidor - evita injecao via observacoes. */
  const json = (valor) => JSON.stringify(valor, null, 2);

  function marcaDeStatus(resultado) {
    const span = criar('span', 'status-marca', resultado.resumo);
    if (resultado.status === 0) span.classList.add('status-erro');
    else if (resultado.ok) span.classList.add('status-ok');
    else if (resultado.status >= 500) span.classList.add('status-erro');
    else span.classList.add('status-alerta');
    return span;
  }

  function etiquetaPerfil(perfil) {
    const classes = { MEDICO: 'etiqueta-medico', ENFERMEIRO: 'etiqueta-enfermeiro', PACIENTE: 'etiqueta-paciente' };
    return criar('span', `etiqueta ${classes[perfil] || ''}`, perfil);
  }

  function avisar(container, mensagem, tipo = 'erro') {
    container.replaceChildren(criar('div', tipo === 'erro' ? 'mensagem-erro' : 'mensagem-info', mensagem));
  }

  function formatarDataHora(iso) {
    if (!iso) return '—';
    const data = new Date(iso);
    if (Number.isNaN(data.getTime())) return iso;
    return data.toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' });
  }

  /** Sessoes exigidas para comparar; devolve null e avisa quando faltam. */
  function trioOuAviso(container) {
    const trio = Sessoes.trio();
    if (trio.length < 3) {
      avisar(container, 'Para comparar os tres perfis, entre com MEDICO, ENFERMEIRO e PACIENTE '
        + '(botao “Entrar nos 3 perfis” no topo).');
      return null;
    }
    return trio;
  }

  function sessaoSelecionada(seletor, container) {
    const email = seletor.value;
    const sessao = email ? Sessoes.porEmail(email) : null;
    if (!sessao) {
      avisar(container, 'Nenhuma sessao ativa. Entre com pelo menos um perfil no topo da pagina.');
      return null;
    }
    return sessao;
  }

  // =======================================================================
  // Abas
  // =======================================================================

  $('abas').addEventListener('click', (evento) => {
    const botao = evento.target.closest('.aba');
    if (!botao) return;
    document.querySelectorAll('.aba').forEach((a) => a.classList.toggle('aba-ativa', a === botao));
    document.querySelectorAll('.painel').forEach((p) => {
      p.classList.toggle('painel-ativo', p.dataset.painel === botao.dataset.aba);
    });
  });

  // =======================================================================
  // Saude dos servicos
  // =======================================================================

  async function atualizarSaude() {
    for (const pilula of document.querySelectorAll('#saudeServicos .pilula')) {
      const servico = pilula.dataset.servico;
      const resposta = await Api.saude(servico);
      const noAr = resposta.ok && resposta.corpo && resposta.corpo.status === 'UP';
      pilula.classList.toggle('pilula-ok', noAr);
      pilula.classList.toggle('pilula-erro', !noAr);
      pilula.classList.toggle('pilula-neutra', false);
      const porta = { agendamento: 8080, notificacao: 8081, historico: 8082 }[servico];
      pilula.textContent = `${servico} :${porta} ${noAr ? 'UP' : 'FORA'}`;
    }
  }

  // =======================================================================
  // Sessoes
  // =======================================================================

  function montarMenuUsuarios() {
    const lista = $('listaUsuarios');
    lista.replaceChildren();
    Sessoes.USUARIOS.forEach((usuario) => {
      const botao = criar('button', null);
      botao.append(`${usuario.rotulo} · `, etiquetaPerfil(usuario.perfil), ` id ${usuario.id}`);
      botao.addEventListener('click', async () => {
        botao.disabled = true;
        await Sessoes.entrar(usuario.email);
        botao.disabled = false;
        document.querySelector('.menu-usuarios').removeAttribute('open');
      });
      lista.append(botao);
    });
  }

  function renderizarSessoes() {
    const container = $('chipsSessoes');
    const sessoes = Sessoes.lista();
    container.replaceChildren();

    if (sessoes.length === 0) {
      container.append(criar('span', 'aviso-vazio',
        'Nenhuma sessao ativa. Comece por “Entrar nos 3 perfis”.'));
    } else {
      sessoes
        .sort((a, b) => Cenarios.ordemDoPerfil(a.perfil) - Cenarios.ordemDoPerfil(b.perfil))
        .forEach((sessao) => {
          const chip = criar('div', 'chip' + (sessao.expirada ? ' chip-expirada' : ''));
          chip.append(etiquetaPerfil(sessao.perfil));
          chip.append(criar('span', 'chip-nome', sessao.nome));

          const minutos = Sessoes.minutosRestantes(sessao);
          const meta = sessao.expirada
            ? 'sessao expirada'
            : `id ${sessao.usuarioId}${minutos !== null ? ` · ${minutos} min` : ''}`;
          chip.append(criar('span', 'chip-meta', meta));

          const sair = criar('button', 'chip-sair', '×');
          sair.title = `Sair de ${sessao.nome}`;
          sair.addEventListener('click', () => Sessoes.sair(sessao.email));
          chip.append(sair);

          container.append(chip);
        });
    }

    atualizarSeletoresDePerfil();
  }

  /** Os seletores "ver como" refletem exatamente quem esta logado no momento. */
  function atualizarSeletoresDePerfil() {
    const seletores = ['listarComoPerfil', 'graphqlComoPerfil', 'notificacaoComoPerfil'];
    const sessoes = Sessoes.lista()
      .sort((a, b) => Cenarios.ordemDoPerfil(a.perfil) - Cenarios.ordemDoPerfil(b.perfil));

    seletores.forEach((id) => {
      const seletor = $(id);
      const anterior = seletor.value;
      seletor.replaceChildren();

      if (sessoes.length === 0) {
        seletor.append(new Option('(nenhuma sessao)', ''));
        return;
      }
      sessoes.forEach((sessao) => {
        seletor.append(new Option(`${sessao.perfil} · ${sessao.nome}`, sessao.email));
      });
      if (sessoes.some((s) => s.email === anterior)) seletor.value = anterior;
    });
  }

  $('btnEntrarTrio').addEventListener('click', async (evento) => {
    const botao = evento.currentTarget;
    botao.disabled = true;
    botao.textContent = 'Entrando...';
    const erros = await Sessoes.entrarNoTrio();
    botao.disabled = false;
    botao.textContent = 'Entrar nos 3 perfis';
    if (erros.length > 0) {
      avisar($('passosFluxo'), `Falha ao autenticar: ${erros.join(' | ')}`);
    }
  });

  $('btnSairTodas').addEventListener('click', () => Sessoes.sairDeTodas());

  // =======================================================================
  // Painel: fluxo ponta a ponta
  // =======================================================================

  function renderizarPasso(registro) {
    const item = criar('li', `passo passo-${registro.estado}`);
    item.append(criar('span', 'passo-numero', String(registro.numero).padStart(2, '0')));

    const corpo = criar('div', 'passo-corpo');
    corpo.append(criar('div', 'passo-titulo', registro.titulo));
    if (registro.detalhe) corpo.append(criar('div', 'passo-detalhe', registro.detalhe));
    item.append(corpo);

    if (registro.duracaoMs !== undefined) {
      // O passo 3 e a travessia pelo Kafka: seu tempo e a evidencia do assincrono.
      const destaque = registro.numero === 3 ? ' passo-tempo-destaque' : '';
      item.append(criar('span', `passo-tempo${destaque}`, `${registro.duracaoMs} ms`));
    }
    $('passosFluxo').append(item);
  }

  $('btnFluxoCompleto').addEventListener('click', async (evento) => {
    const botao = evento.currentTarget;
    botao.disabled = true;
    botao.textContent = 'Executando...';
    $('passosFluxo').replaceChildren();

    const resultado = await Cenarios.executarFluxoCompleto(renderizarPasso);

    botao.disabled = false;
    botao.textContent = 'Rodar o fluxo completo';

    if (resultado.ok) {
      const nota = criar('div', 'mensagem-info',
        `Consulta #${resultado.consultaId} percorreu o caminho inteiro. `
        + 'Abra a aba Historico e consulte este id para ver a trilha completa, '
        + 'ou a aba Notificacoes para ver o e-mail que o evento gerou.');
      $('passosFluxo').append(nota);
      // Ja deixa os outros paineis apontando para a consulta recem-criada.
      $('editarConsultaId').value = resultado.consultaId;
      $('notificacaoConsultaId').value = resultado.consultaId;
    }
  });

  // =======================================================================
  // Agendamento
  // =======================================================================

  /** Bloco de resultado de um perfil, usado pela comparacao lado a lado. */
  function colunaDeResultado(sessao, resultado) {
    const coluna = criar('div', 'coluna-perfil');
    const cabecalho = criar('div', 'coluna-perfil-cabecalho');
    cabecalho.append(etiquetaPerfil(sessao ? sessao.perfil : 'SEM TOKEN'));
    cabecalho.append(marcaDeStatus(resultado));
    coluna.append(cabecalho);

    if (resultado.erro) {
      coluna.append(criar('div', 'passo-detalhe', resultado.erro));
    }
    coluna.append(criar('pre', null, json(resultado.corpo)));
    return coluna;
  }

  function renderizarComparacao(container, resultados) {
    const grade = criar('div', 'colunas-perfis');
    resultados.forEach(({ sessao, resultado }) => grade.append(colunaDeResultado(sessao, resultado)));
    container.replaceChildren(grade);
  }

  /** Executa uma acao num perfil so ou nos tres, conforme o interruptor. */
  async function executar(container, nosTres, seletor, acao) {
    container.replaceChildren(criar('div', 'mensagem-info', 'Executando...'));

    if (nosTres) {
      const trio = trioOuAviso(container);
      if (!trio) return null;
      const resultados = await Api.executarNosPerfis(acao, trio);
      renderizarComparacao(container, resultados);
      return resultados;
    }

    const sessao = sessaoSelecionada(seletor, container);
    if (!sessao) return null;
    const resultado = await acao(sessao);
    renderizarComparacao(container, [{ sessao, resultado }]);
    return [{ sessao, resultado }];
  }

  function dataHoraDoFormulario() {
    const valor = $('novaDataHora').value;
    // O input datetime-local ja entrega "YYYY-MM-DDTHH:mm"; o backend espera segundos.
    return valor ? `${valor}:00` : Cenarios.proximoHorario(15);
  }

  $('btnCriarConsulta').addEventListener('click', async (evento) => {
    const botao = evento.currentTarget;
    botao.disabled = true;
    const container = $('resultadoCriar');
    const nosTres = $('criarNosTres').checked;
    const base = {
      pacienteId: Number($('novaPacienteId').value),
      medicoId: Number($('novaMedicoId').value),
      observacoes: $('novaObservacoes').value || null,
    };
    const dataHoraBase = dataHoraDoFormulario();

    // Cada perfil ganha um horario proprio: o indice unico uk_consulta_medico_horario
    // recusaria o segundo agendamento no mesmo horario com 400, escondendo o 403 real.
    let deslocamento = 0;
    const resultados = await executar(container, nosTres, $('listarComoPerfil'), (sessao) => {
      const dataHora = nosTres ? deslocarHoras(dataHoraBase, deslocamento++) : dataHoraBase;
      return Api.agendamento('/consultas', {
        sessao, metodo: 'POST', corpo: { ...base, dataHora },
        rotulo: 'registrar consulta',
      });
    });

    if (resultados) {
      const criada = resultados.map((r) => r.resultado).find((r) => r.ok && r.corpo && r.corpo.id);
      if (criada) {
        $('editarConsultaId').value = criada.corpo.id;
        $('notificacaoConsultaId').value = criada.corpo.id;
        // Avanca um dia no formulario: sem isto, um segundo clique repetiria o horario e o
        // indice unico de double-booking devolveria 400, parecendo um erro do painel.
        avancarDataDoFormulario();
        container.append(criar('div', 'mensagem-info',
          `Consulta #${criada.corpo.id} registrada. O evento leva 1-2 s para chegar ao historico `
          + '(outbox + consumo); use a aba Historico para conferir. '
          + 'A data do formulario avancou um dia para o proximo registro nao colidir.'));
      }
      if (nosTres) {
        container.append(criar('div', 'mensagem-info',
          'Cada perfil usou um horario diferente de proposito: horarios iguais esbarrariam no '
          + 'indice de double-booking e devolveriam 400 em vez do 201/403 esperado.'));
      }
    }
    botao.disabled = false;
  });

  function avancarDataDoFormulario() {
    const campo = $('novaDataHora');
    const base = campo.value ? new Date(campo.value) : new Date();
    base.setDate(base.getDate() + 1);
    campo.value = Cenarios.isoLocal(base).slice(0, 16);
  }

  function deslocarHoras(dataHoraIso, horas) {
    if (horas === 0) return dataHoraIso;
    const data = new Date(dataHoraIso);
    data.setHours(data.getHours() + horas);
    return Cenarios.isoLocal(data);
  }

  $('btnListar').addEventListener('click', async (evento) => {
    const botao = evento.currentTarget;
    botao.disabled = true;
    const container = $('resultadoListar');
    const parametros = new URLSearchParams();
    if ($('listarPacienteId').value) parametros.set('pacienteId', $('listarPacienteId').value);
    if ($('listarApenasFuturas').checked) parametros.set('apenasFuturas', 'true');
    const caminho = `/consultas${parametros.toString() ? `?${parametros}` : ''}`;

    const acao = (sessao) => Api.agendamento(caminho, { sessao, rotulo: 'listar consultas' });

    if ($('listarNosTres').checked) {
      const trio = trioOuAviso(container);
      if (trio) {
        container.replaceChildren(criar('div', 'mensagem-info', 'Executando...'));
        const resultados = await Api.executarNosPerfis(acao, trio);
        renderizarComparacaoDeListas(container, resultados);
      }
    } else {
      const sessao = sessaoSelecionada($('listarComoPerfil'), container);
      if (sessao) {
        container.replaceChildren(criar('div', 'mensagem-info', 'Carregando...'));
        const resultado = await acao(sessao);
        container.replaceChildren(tabelaDeConsultas(resultado, sessao));
      }
    }
    botao.disabled = false;
  });

  function renderizarComparacaoDeListas(container, resultados) {
    const grade = criar('div', 'colunas-perfis');
    resultados.forEach(({ sessao, resultado }) => {
      const coluna = criar('div', 'coluna-perfil');
      const cabecalho = criar('div', 'coluna-perfil-cabecalho');
      cabecalho.append(etiquetaPerfil(sessao.perfil));
      cabecalho.append(marcaDeStatus(resultado));
      coluna.append(cabecalho);

      if (Array.isArray(resultado.corpo)) {
        coluna.append(criar('div', 'passo-detalhe', `${resultado.corpo.length} consulta(s)`));
        const lista = criar('div');
        resultado.corpo.forEach((consulta) => {
          lista.append(criar('div', 'passo-detalhe',
            `#${consulta.id} · ${formatarDataHora(consulta.dataHora)} · ${consulta.status} · ${consulta.paciente.nome}`));
        });
        coluna.append(lista);
      } else {
        coluna.append(criar('div', 'passo-detalhe', resultado.erro || 'sem corpo'));
      }
      grade.append(coluna);
    });
    container.replaceChildren(grade);
  }

  function tabelaDeConsultas(resultado, sessao) {
    if (!resultado.ok || !Array.isArray(resultado.corpo)) {
      const bloco = criar('div');
      bloco.append(criar('div', 'mensagem-erro',
        `${resultado.resumo} — ${resultado.erro || 'nao foi possivel listar'}`));
      return bloco;
    }

    const envolucro = criar('div', 'rolagem');
    const tabela = criar('table', 'tabela');
    const cabecalho = criar('thead');
    const linhaCabecalho = criar('tr');
    ['Id', 'Data e hora', 'Status', 'Paciente', 'Medico', 'Versao', 'Observacoes']
      .forEach((titulo) => linhaCabecalho.append(criar('th', null, titulo)));
    cabecalho.append(linhaCabecalho);
    tabela.append(cabecalho);

    const corpo = criar('tbody');
    resultado.corpo.forEach((consulta) => {
      const linha = criar('tr');
      linha.append(criar('td', 'mono', `#${consulta.id}`));
      linha.append(criar('td', null, formatarDataHora(consulta.dataHora)));
      const celulaStatus = criar('td');
      celulaStatus.append(criar('span',
        `status-marca ${consulta.status === 'CANCELADA' ? 'status-erro' : 'status-ok'}`,
        consulta.status));
      linha.append(celulaStatus);
      linha.append(criar('td', null, consulta.paciente.nome));
      linha.append(criar('td', null, `${consulta.medico.nome} (${consulta.medico.especialidade})`));
      linha.append(criar('td', 'mono', String(consulta.versao)));
      linha.append(criar('td', null, consulta.observacoes || '—'));
      corpo.append(linha);
    });
    tabela.append(corpo);
    envolucro.append(tabela);

    const resumo = criar('div', 'passo-detalhe',
      `${resultado.corpo.length} consulta(s) visiveis para ${sessao.perfil} · ${sessao.nome}`);
    const bloco = criar('div');
    bloco.append(resumo, envolucro);
    return bloco;
  }

  $('btnEditar').addEventListener('click', async (evento) => {
    const botao = evento.currentTarget;
    const id = $('editarConsultaId').value;
    const container = $('resultadoEditar');
    if (!id) return avisar(container, 'Informe o id da consulta a editar.');

    botao.disabled = true;
    const corpo = {};
    if ($('editarStatus').value) corpo.status = $('editarStatus').value;
    if ($('editarObservacoes').value) corpo.observacoes = $('editarObservacoes').value;

    await executar(container, $('editarNosTres').checked, $('listarComoPerfil'), (sessao) =>
      Api.agendamento(`/consultas/${id}`, {
        sessao, metodo: 'PUT', corpo, rotulo: `editar consulta ${id}`,
      }));

    container.append(criar('div', 'mensagem-info',
      'Este e o requisito “medicos podem visualizar e editar o historico”: a edicao acontece '
      + 'aqui e chega ao historico pelo evento CONSULTA_ATUALIZADA. Confira a trilha na aba '
      + 'Historico com consultaHistorico.'));
    botao.disabled = false;
  });

  $('btnCancelar').addEventListener('click', async (evento) => {
    const botao = evento.currentTarget;
    const id = $('editarConsultaId').value;
    const container = $('resultadoEditar');
    if (!id) return avisar(container, 'Informe o id da consulta a cancelar.');

    botao.disabled = true;
    await executar(container, $('editarNosTres').checked, $('listarComoPerfil'), (sessao) =>
      Api.agendamento(`/consultas/${id}/cancelar`, {
        sessao, metodo: 'POST', rotulo: `cancelar consulta ${id}`,
      }));
    botao.disabled = false;
  });

  // =======================================================================
  // Historico (GraphQL)
  // =======================================================================

  function montarSeletorDeQueries() {
    const seletor = $('seletorQuery');
    GraphQL.CATALOGO.forEach((item) => seletor.append(new Option(item.nome, item.id)));
    seletor.addEventListener('change', carregarQuerySelecionada);
    carregarQuerySelecionada();
  }

  function carregarQuerySelecionada() {
    const item = GraphQL.porId($('seletorQuery').value);
    if (!item) return;
    $('descricaoQuery').textContent = item.descricao;
    $('textoQuery').value = item.query;
    $('textoVariaveis').value = json(item.variaveis);
  }

  $('btnExecutarQuery').addEventListener('click', async (evento) => {
    const botao = evento.currentTarget;
    const container = $('resultadoGraphql');

    let variaveis;
    try {
      variaveis = JSON.parse($('textoVariaveis').value || '{}');
    } catch (e) {
      return avisar(container, `As variaveis nao sao um JSON valido: ${e.message}`);
    }

    botao.disabled = true;
    const query = $('textoQuery').value;
    const acao = (sessao) => Api.graphql(query, variaveis, { sessao, rotulo: 'graphql' });

    if ($('graphqlNosTres').checked) {
      const trio = trioOuAviso(container);
      if (trio) {
        container.replaceChildren(criar('div', 'mensagem-info', 'Executando...'));
        const resultados = await Api.executarNosPerfis(acao, trio);
        renderizarComparacaoGraphql(container, resultados);
      }
    } else {
      const sessao = sessaoSelecionada($('graphqlComoPerfil'), container);
      if (sessao) {
        container.replaceChildren(criar('div', 'mensagem-info', 'Executando...'));
        const resultado = await acao(sessao);
        container.replaceChildren(blocoGraphql(sessao, resultado));
      }
    }
    botao.disabled = false;
  });

  function renderizarComparacaoGraphql(container, resultados) {
    const grade = criar('div', 'colunas-perfis');
    resultados.forEach(({ sessao, resultado }) => grade.append(blocoGraphql(sessao, resultado, true)));
    container.replaceChildren(grade);
  }

  function blocoGraphql(sessao, resultado, compacto) {
    const bloco = criar('div', compacto ? 'coluna-perfil' : null);
    const cabecalho = criar('div', 'coluna-perfil-cabecalho');
    cabecalho.append(etiquetaPerfil(sessao.perfil));
    cabecalho.append(marcaDeStatus(resultado));
    bloco.append(cabecalho);

    if (resultado.classificacao) {
      bloco.append(criar('div', 'mensagem-erro',
        `${resultado.classificacao} — ${resultado.erro || 'acesso negado'}. `
        + 'Repare que o HTTP foi 200: no GraphQL a negacao vive em errors[].extensions.classification.'));
    }

    // A trilha de eventos e o que torna visivel que o historico vem do log do Kafka.
    const consultas = extrairConsultas(resultado.corpo);
    consultas.filter((c) => Array.isArray(c.eventos) && c.eventos.length > 0).forEach((consulta) => {
      const linha = criar('div');
      linha.append(criar('div', 'passo-detalhe',
        `#${consulta.consultaId} · ${formatarDataHora(consulta.dataHora)} · ${consulta.status} · versao ${consulta.versao ?? '—'}`));
      const trilha = criar('div', 'trilha');
      consulta.eventos.forEach((evento, indice) => {
        if (indice > 0) trilha.append(criar('span', 'trilha-seta', '→'));
        trilha.append(criar('span', 'trilha-evento', `${evento.tipo} (v${evento.versao})`));
      });
      linha.append(trilha);
      bloco.append(linha);
    });

    bloco.append(criar('pre', null, json(resultado.corpo)));
    return bloco;
  }

  /** As consultas aparecem em lugares diferentes conforme a query executada. */
  function extrairConsultas(corpo) {
    const dados = corpo && corpo.data ? corpo.data : null;
    if (!dados) return [];
    if (dados.historicoPaciente) return dados.historicoPaciente.consultas || [];
    if (dados.minhasConsultas) return dados.minhasConsultas;
    if (dados.consultasFuturas) return dados.consultasFuturas;
    if (dados.consultaHistorico) return [dados.consultaHistorico];
    return [];
  }

  // =======================================================================
  // Notificacoes
  // =======================================================================

  $('btnListarNotificacoes').addEventListener('click', async (evento) => {
    const botao = evento.currentTarget;
    botao.disabled = true;
    const container = $('resultadoNotificacoes');
    const id = $('notificacaoConsultaId').value;
    const caminho = `/notificacoes${id ? `?consultaId=${id}` : ''}`;
    const acao = (sessao) => Api.notificacao(caminho, { sessao, rotulo: 'listar notificacoes' });

    if ($('notificacaoNosTres').checked) {
      const trio = trioOuAviso(container);
      if (trio) {
        container.replaceChildren(criar('div', 'mensagem-info', 'Executando...'));
        const resultados = await Api.executarNosPerfis(acao, trio);
        renderizarComparacao(container, resultados);
        container.append(criar('div', 'mensagem-info',
          'O PACIENTE recebe 403: a trilha de notificacoes e uma visao operacional da equipe '
          + 'clinica, nao um recurso do paciente.'));
      }
    } else {
      const sessao = sessaoSelecionada($('notificacaoComoPerfil'), container);
      if (sessao) {
        container.replaceChildren(criar('div', 'mensagem-info', 'Carregando...'));
        const resultado = await acao(sessao);
        container.replaceChildren(tabelaDeNotificacoes(resultado));
      }
    }
    botao.disabled = false;
  });

  function tabelaDeNotificacoes(resultado) {
    if (!resultado.ok || !Array.isArray(resultado.corpo)) {
      const bloco = criar('div');
      bloco.append(criar('div', 'mensagem-erro',
        `${resultado.resumo} — ${resultado.erro || 'nao foi possivel listar'}`));
      return bloco;
    }
    if (resultado.corpo.length === 0) {
      const bloco = criar('div');
      bloco.append(criar('div', 'mensagem-info',
        'Nenhuma notificacao ainda. Registre uma consulta na aba Agendamento e volte em 1-2 s: '
        + 'o evento precisa ser publicado pela outbox e consumido por este servico.'));
      return bloco;
    }

    const envolucro = criar('div', 'rolagem');
    const tabela = criar('table', 'tabela');
    const cabecalho = criar('thead');
    const linhaCabecalho = criar('tr');
    ['Consulta', 'Tipo', 'Destinatario', 'Assunto', 'Versao', 'Status', 'Enviado em']
      .forEach((titulo) => linhaCabecalho.append(criar('th', null, titulo)));
    cabecalho.append(linhaCabecalho);
    tabela.append(cabecalho);

    const corpo = criar('tbody');
    resultado.corpo.forEach((notificacao) => {
      const linha = criar('tr');
      linha.append(criar('td', 'mono', `#${notificacao.consultaId}`));
      linha.append(criar('td', 'mono', notificacao.tipo));
      linha.append(criar('td', null, notificacao.destinatario));
      linha.append(criar('td', null, notificacao.assunto));
      linha.append(criar('td', 'mono', String(notificacao.versaoConsulta)));
      const celulaStatus = criar('td');
      celulaStatus.append(criar('span',
        `status-marca ${notificacao.status === 'ENVIADA' ? 'status-ok' : 'status-erro'}`,
        notificacao.status));
      linha.append(celulaStatus);
      linha.append(criar('td', null, formatarDataHora(notificacao.enviadoEm)));
      corpo.append(linha);
    });
    tabela.append(corpo);
    envolucro.append(tabela);

    const bloco = criar('div');
    bloco.append(criar('div', 'passo-detalhe',
      `${resultado.corpo.length} notificacao(oes) — cada uma nasceu de um evento consumido do Kafka`));
    bloco.append(envolucro);
    return bloco;
  }

  // =======================================================================
  // Matriz de permissoes
  // =======================================================================

  $('btnExecutarMatriz').addEventListener('click', async (evento) => {
    const botao = evento.currentTarget;
    const container = $('resultadoMatriz');
    const trio = trioOuAviso(container);
    if (!trio) return;

    botao.disabled = true;
    botao.textContent = 'Executando...';
    $('resumoMatriz').textContent = '';

    const tabela = criar('table', 'tabela');
    const cabecalho = criar('thead');
    const linhaCabecalho = criar('tr');
    ['Cenario', 'MEDICO', 'ENFERMEIRO', 'PACIENTE'].forEach((titulo) =>
      linhaCabecalho.append(criar('th', null, titulo)));
    cabecalho.append(linhaCabecalho);
    tabela.append(cabecalho);
    const corpoTabela = criar('tbody');
    tabela.append(corpoTabela);

    const envolucro = criar('div', 'rolagem');
    envolucro.append(tabela);
    container.replaceChildren(envolucro);

    let grupoAtual = null;
    const resultado = await Cenarios.executarMatriz(trio, (linha) => {
      if (linha.grupo !== grupoAtual) {
        grupoAtual = linha.grupo;
        const linhaGrupo = criar('tr');
        const celula = criar('td', 'matriz-grupo', linha.grupo);
        celula.colSpan = 4;
        linhaGrupo.append(celula);
        corpoTabela.append(linhaGrupo);
      }
      corpoTabela.append(linhaDaMatriz(linha));
    });

    botao.disabled = false;
    botao.textContent = 'Executar a matriz';

    if (!resultado.ok) {
      container.append(criar('div', 'mensagem-erro', resultado.erro));
      return;
    }

    const resumo = $('resumoMatriz');
    resumo.replaceChildren();
    if (resultado.divergentes === 0) {
      resumo.append(criar('span', 'status-marca status-ok',
        `${resultado.total} verificacoes · tudo conforme o enunciado`));
    } else {
      resumo.append(criar('span', 'status-marca status-erro',
        `${resultado.divergentes} cenario(s) divergente(s) de ${resultado.linhas.length}`));
    }
    container.append(criar('div', 'mensagem-info',
      `A consulta descartavel #${resultado.consultaDescartavel} foi criada e cancelada por esta `
      + 'execucao. As consultas 1 a 4 da seed permanecem intactas.'));
  });

  function linhaDaMatriz(linha) {
    const tr = criar('tr');
    const celulaCenario = criar('td');
    celulaCenario.append(criar('div', 'mono', linha.nome));
    celulaCenario.append(criar('div', 'passo-detalhe', linha.detalhe));
    tr.append(celulaCenario);

    ['MEDICO', 'ENFERMEIRO', 'PACIENTE'].forEach((perfil) => {
      const celula = linha.celulas.find((c) => c.perfil === perfil);
      const td = criar('td');
      if (!celula) {
        td.append(criar('span', 'status-marca status-neutro', '—'));
        tr.append(td);
        return;
      }
      const bloco = criar('div',
        `celula-matriz ${celula.conforme ? 'celula-conforme' : 'celula-divergente'}`);
      bloco.append(criar('span', 'status-marca', celula.obtido));
      if (!celula.conforme) {
        bloco.append(criar('span', 'celula-esperado', `esperado: ${celula.esperado}`));
      }
      td.append(bloco);
      tr.append(td);
    });
    return tr;
  }

  // =======================================================================
  // Console HTTP
  // =======================================================================

  function renderizarConsole() {
    const container = $('listaConsole');
    const filtro = $('filtroConsole').value.trim().toLowerCase();
    const entradas = Api.historico().filter((entrada) => {
      if (!filtro) return true;
      return `${entrada.metodo} ${entrada.caminho} ${entrada.perfil} ${entrada.resposta.resumo} ${entrada.rotulo}`
        .toLowerCase().includes(filtro);
    });

    $('contadorConsole').textContent = String(Api.historico().length);
    container.replaceChildren();

    if (entradas.length === 0) {
      container.append(criar('div', 'aviso-vazio',
        filtro ? 'Nada corresponde ao filtro.' : 'Nenhuma requisicao ainda.'));
      return;
    }

    entradas.forEach((entrada) => container.append(linhaDoConsole(entrada)));
  }

  function linhaDoConsole(entrada) {
    const linha = criar('div', 'console-linha');
    const cabecalho = criar('div', 'console-cabecalho');
    cabecalho.append(marcaDeStatus(entrada.resposta));
    cabecalho.append(criar('span', 'console-metodo', entrada.metodo));
    cabecalho.append(criar('span', 'console-caminho', entrada.caminho));
    cabecalho.append(criar('span', 'console-perfil', entrada.perfil));
    cabecalho.append(criar('span', 'console-tempo', `${entrada.resposta.duracaoMs} ms`));
    linha.append(cabecalho);

    const detalhe = criar('div', 'console-detalhe');
    detalhe.hidden = true;

    detalhe.append(criar('h4', null, 'Requisicao'));
    detalhe.append(criar('pre', null, json({
      instante: entrada.instante.toLocaleTimeString('pt-BR'),
      rotulo: entrada.rotulo,
      cabecalhos: entrada.requisicao.cabecalhos,
      corpo: entrada.requisicao.corpo,
    })));

    detalhe.append(criar('h4', null, 'Resposta'));
    detalhe.append(criar('pre', null, json({
      status: entrada.resposta.status,
      classificacao: entrada.resposta.classificacao,
      erro: entrada.resposta.erro,
      corpo: entrada.resposta.corpo,
    })));

    const copiar = criar('button', 'botao botao-fantasma', 'Copiar como curl');
    copiar.addEventListener('click', () => {
      const comando = montarCurl(entrada);
      const restaurar = (texto) => {
        copiar.textContent = texto;
        setTimeout(() => { copiar.textContent = 'Copiar como curl'; }, 1800);
      };
      // A area de transferencia pode ser negada pelo browser; nesse caso o comando ainda
      // precisa chegar ao usuario, entao cai para um bloco selecionavel.
      navigator.clipboard.writeText(comando)
        .then(() => restaurar('Copiado'))
        .catch(() => {
          restaurar('Copie do bloco abaixo');
          detalhe.append(criar('pre', null, comando));
        });
    });
    detalhe.append(copiar);

    linha.append(detalhe);
    cabecalho.addEventListener('click', () => { detalhe.hidden = !detalhe.hidden; });
    return linha;
  }

  /**
   * Reproduz a requisicao no terminal. Usa a porta direta do servico (8080/8081/8082) em vez do
   * proxy, porque fora do browser nao ha motivo para passar pelo nginx.
   */
  function montarCurl(entrada) {
    const portas = { '/api/agendamento': 8080, '/api/notificacao': 8081, '/graphql': 8082 };
    let url = entrada.caminho;
    for (const [prefixo, porta] of Object.entries(portas)) {
      if (entrada.caminho.startsWith(prefixo)) {
        const resto = prefixo === '/graphql' ? '/graphql' : `/api${entrada.caminho.slice(prefixo.length)}`;
        url = `http://localhost:${porta}${resto}`;
        break;
      }
    }
    const partes = [`curl -X ${entrada.metodo} '${url}'`];
    Object.entries(entrada.requisicao.cabecalhos || {}).forEach(([chave, valor]) => {
      // O token foi mascarado para exibicao; no curl fica o marcador para o usuario preencher.
      const conteudo = chave === 'Authorization' ? 'Bearer $TOKEN' : valor;
      partes.push(`  -H '${chave}: ${conteudo}'`);
    });
    if (entrada.requisicao.corpo !== undefined) {
      partes.push(`  -d '${JSON.stringify(entrada.requisicao.corpo)}'`);
    }
    return partes.join(' \\\n');
  }

  $('filtroConsole').addEventListener('input', renderizarConsole);
  $('btnLimparConsole').addEventListener('click', () => Api.limparHistorico());

  // =======================================================================
  // Inicializacao
  // =======================================================================

  Sessoes.aoMudar(renderizarSessoes);
  Api.aoRegistrar(renderizarConsole);

  montarMenuUsuarios();
  montarSeletorDeQueries();
  Sessoes.restaurar();
  renderizarSessoes();
  renderizarConsole();

  // Data padrao do formulario: 15 dias a frente, para nunca cair no passado.
  const padrao = new Date();
  padrao.setDate(padrao.getDate() + 15);
  padrao.setHours(9, 0, 0, 0);
  $('novaDataHora').value = Cenarios.isoLocal(padrao).slice(0, 16);

  atualizarSaude();
  setInterval(atualizarSaude, 15000);
  // A contagem regressiva dos tokens precisa avancar sozinha.
  setInterval(renderizarSessoes, 30000);
})();
