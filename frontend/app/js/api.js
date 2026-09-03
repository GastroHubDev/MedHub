/**
 * Cliente HTTP. Este e o UNICO lugar do front que fala com a rede.
 *
 * Concentrar tudo aqui garante duas coisas: nenhuma requisicao escapa do Console HTTP, e a
 * normalizacao de erro acontece num lugar so - o que importa porque o backend devolve erro
 * em tres formatos diferentes (ver normalizarErro abaixo).
 */
const Api = (() => {

  const BASE_AGENDAMENTO = '/api/agendamento';
  const BASE_NOTIFICACAO = '/api/notificacao';
  const BASE_GRAPHQL = '/graphql';

  /** Registro de todas as requisicoes, consumido pela aba Console. */
  const registro = [];
  const ouvintes = [];

  function aoRegistrar(callback) {
    ouvintes.push(callback);
  }

  function registrar(entrada) {
    registro.unshift(entrada);
    // Sem teto a pagina cresceria indefinidamente numa sessao longa de testes.
    if (registro.length > 200) registro.length = 200;
    ouvintes.forEach((cb) => cb(entrada));
  }

  function historico() {
    return registro;
  }

  function limparHistorico() {
    registro.length = 0;
    ouvintes.forEach((cb) => cb(null));
  }

  /**
   * O backend responde erro em tres formatos, e a interface precisa de um so:
   *
   *   ProblemDetail (RFC 7807)  { title, detail, status }   <- RestExceptionHandler
   *   Handler da filter chain   { status, erro }            <- accessDeniedHandler / entryPoint
   *   GraphQL                   { errors: [{ message, extensions.classification }] }
   */
  function normalizarErro(corpo) {
    if (!corpo || typeof corpo !== 'object') return null;
    if (Array.isArray(corpo.errors) && corpo.errors.length > 0) {
      return corpo.errors[0].message || 'Erro GraphQL';
    }
    return corpo.detail || corpo.erro || corpo.message || null;
  }

  /**
   * O GraphQL responde HTTP 200 mesmo quando nega acesso. Sem olhar a classification, a
   * matriz de permissoes daria tudo verde e nao provaria nada.
   */
  function classificacaoGraphQl(corpo) {
    if (!corpo || !Array.isArray(corpo.errors) || corpo.errors.length === 0) return null;
    const extensions = corpo.errors[0].extensions || {};
    return extensions.classification || 'INTERNAL_ERROR';
  }

  async function lerCorpo(resposta) {
    const tipo = resposta.headers.get('content-type') || '';
    const texto = await resposta.text();
    if (!texto) return null;
    if (tipo.includes('json')) {
      try {
        return JSON.parse(texto);
      } catch (e) {
        return texto;
      }
    }
    return texto;
  }

  /**
   * Dispara uma requisicao e devolve sempre o mesmo formato, tenha dado certo ou nao.
   *
   * @param {object} opcoes
   * @param {string} opcoes.caminho    caminho completo ja resolvido (use os helpers abaixo)
   * @param {string} [opcoes.metodo]   GET por padrao
   * @param {object} [opcoes.corpo]    serializado como JSON
   * @param {object} [opcoes.sessao]   sessao cujo token sera enviado; ausente = sem Authorization
   * @param {string} [opcoes.tokenCru] token literal, para o cenario de token adulterado
   * @param {string} [opcoes.rotulo]   descricao legivel para o Console
   */
  async function requisitar({ caminho, metodo = 'GET', corpo, sessao, tokenCru, rotulo }) {
    const cabecalhos = {};
    let perfil = 'sem token';

    if (tokenCru !== undefined) {
      cabecalhos.Authorization = `Bearer ${tokenCru}`;
      perfil = 'token adulterado';
    } else if (sessao) {
      cabecalhos.Authorization = `Bearer ${sessao.token}`;
      perfil = sessao.perfil;
    }
    if (corpo !== undefined) {
      cabecalhos['Content-Type'] = 'application/json';
    }

    const inicio = performance.now();
    let resposta = null;
    let corpoResposta = null;
    let falhaDeRede = null;

    try {
      resposta = await fetch(caminho, {
        method: metodo,
        headers: cabecalhos,
        body: corpo !== undefined ? JSON.stringify(corpo) : undefined,
      });
      corpoResposta = await lerCorpo(resposta);
    } catch (e) {
      // Servico fora do ar, nginx sem upstream, rede caida.
      falhaDeRede = e.message || String(e);
    }

    const duracaoMs = Math.round(performance.now() - inicio);
    const classificacao = classificacaoGraphQl(corpoResposta);

    const resultado = {
      ok: !falhaDeRede && resposta.ok && !classificacao,
      status: falhaDeRede ? 0 : resposta.status,
      classificacao,
      corpo: corpoResposta,
      erro: falhaDeRede || normalizarErro(corpoResposta),
      duracaoMs,
      // Descricao curta do desfecho, usada na matriz e no console.
      // "200 (FORBIDDEN)" torna visivel a negacao que o GraphQL esconde atras do 200.
      resumo: falhaDeRede
        ? 'rede'
        : classificacao
          ? `${resposta.status} (${classificacao})`
          : String(resposta.status),
    };

    registrar({
      instante: new Date(),
      metodo,
      caminho,
      perfil,
      rotulo: rotulo || `${metodo} ${caminho}`,
      requisicao: { cabecalhos: mascararToken(cabecalhos), corpo },
      resposta: resultado,
    });

    return resultado;
  }

  /** O token inteiro polui o painel; o suficiente e ver que perfil o assinou. */
  function mascararToken(cabecalhos) {
    const copia = { ...cabecalhos };
    if (copia.Authorization) {
      copia.Authorization = copia.Authorization.slice(0, 27) + '...';
    }
    return copia;
  }

  // ---- Atalhos por servico ------------------------------------------------

  const agendamento = (caminho, opcoes = {}) =>
    requisitar({ ...opcoes, caminho: BASE_AGENDAMENTO + caminho });

  const notificacao = (caminho, opcoes = {}) =>
    requisitar({ ...opcoes, caminho: BASE_NOTIFICACAO + caminho });

  const graphql = (query, variables, opcoes = {}) =>
    requisitar({
      ...opcoes,
      caminho: BASE_GRAPHQL,
      metodo: 'POST',
      corpo: { query, variables: variables || {} },
    });

  const saude = (servico) =>
    requisitar({ caminho: `/health/${servico}`, rotulo: `health ${servico}` });

  /**
   * Executa a MESMA acao com varias sessoes e devolve o resultado de cada uma.
   *
   * E o coracao da comparacao lado a lado: a diferenca entre as colunas e exatamente a
   * diferenca de permissao entre os perfis, porque o resto da requisicao e identico.
   *
   * Sequencial de proposito - em paralelo, duas escritas concorrentes poderiam colidir no
   * indice unico de double-booking e produzir um falso negativo.
   */
  async function executarNosPerfis(construirAcao, sessoes) {
    const resultados = [];
    for (const sessao of sessoes) {
      resultados.push({ sessao, resultado: await construirAcao(sessao) });
    }
    return resultados;
  }

  return {
    requisitar, agendamento, notificacao, graphql, saude,
    executarNosPerfis, historico, limparHistorico, aoRegistrar,
  };
})();
