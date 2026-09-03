/**
 * Sessoes simultaneas.
 *
 * O ponto do painel e comparar perfis, entao varios usuarios ficam logados ao mesmo tempo -
 * cada um com seu token - em vez do modelo usual de "um usuario logado por vez".
 */
const Sessoes = (() => {

  const CHAVE_ARMAZENAMENTO = 'tc3-sessoes';

  /** Usuarios da seed (V2__seed_usuarios_e_consultas.sql). Senha unica no ambiente de avaliacao. */
  const USUARIOS = [
    { email: 'medico@hospital.com',     rotulo: 'Dra. Ana Lima',     perfil: 'MEDICO',     id: 1 },
    { email: 'medico2@hospital.com',    rotulo: 'Dr. Bruno Alves',   perfil: 'MEDICO',     id: 2 },
    { email: 'enfermeiro@hospital.com', rotulo: 'Carla Enfermeira',  perfil: 'ENFERMEIRO', id: 3 },
    { email: 'paciente@hospital.com',   rotulo: 'Maria Souza',       perfil: 'PACIENTE',   id: 4 },
    { email: 'paciente2@hospital.com',  rotulo: 'Joao Pereira',      perfil: 'PACIENTE',   id: 5 },
  ];

  const SENHA = 'senha123';

  /** email -> sessao ativa */
  const ativas = new Map();
  const ouvintes = [];

  function aoMudar(callback) {
    ouvintes.push(callback);
  }

  function notificar() {
    persistir();
    ouvintes.forEach((cb) => cb());
  }

  /**
   * Le as claims do JWT apenas para exibi-las. Nao ha validacao de assinatura aqui - isso e
   * responsabilidade do servidor, e um front nunca deve fingir que faz.
   */
  function lerClaims(token) {
    try {
      const payload = token.split('.')[1];
      const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
      return JSON.parse(json);
    } catch (e) {
      return null;
    }
  }

  async function entrar(email) {
    const usuario = USUARIOS.find((u) => u.email === email);
    const resposta = await Api.agendamento('/auth/login', {
      metodo: 'POST',
      corpo: { email, senha: SENHA },
      rotulo: `login ${usuario ? usuario.perfil : email}`,
    });

    if (!resposta.ok) {
      notificar();
      return { ok: false, erro: resposta.erro || `HTTP ${resposta.status}` };
    }

    const corpo = resposta.corpo;
    const sessao = {
      email,
      token: corpo.token,
      nome: corpo.nome,
      perfil: corpo.perfil,
      expiraEm: corpo.expiraEm,
      claims: lerClaims(corpo.token),
      expirada: false,
    };
    // O usuarioId da claim e o mesmo id de paciente/medico (mapeamento @MapsId no backend),
    // e e o que as telas usam para montar filtros "meus dados".
    sessao.usuarioId = sessao.claims ? sessao.claims.usuarioId : null;

    ativas.set(email, sessao);
    notificar();
    return { ok: true, sessao };
  }

  function sair(email) {
    ativas.delete(email);
    notificar();
  }

  function sairDeTodas() {
    ativas.clear();
    notificar();
  }

  /** Chamado quando uma requisicao devolve 401: o chip fica cinza em vez de sumir. */
  function marcarExpirada(email) {
    const sessao = ativas.get(email);
    if (sessao && !sessao.expirada) {
      sessao.expirada = true;
      notificar();
    }
  }

  const lista = () => Array.from(ativas.values());
  const porEmail = (email) => ativas.get(email);
  const porPerfil = (perfil) => lista().find((s) => s.perfil === perfil && !s.expirada);

  /**
   * Um representante de cada perfil, na ordem do enunciado. E o conjunto usado pela
   * comparacao lado a lado e pela matriz de permissoes.
   */
  function trio() {
    return ['MEDICO', 'ENFERMEIRO', 'PACIENTE']
      .map(porPerfil)
      .filter(Boolean);
  }

  /** Faz login nos tres perfis de uma vez, para comecar a testar sem cliques repetidos. */
  async function entrarNoTrio() {
    const alvos = ['medico@hospital.com', 'enfermeiro@hospital.com', 'paciente@hospital.com'];
    const erros = [];
    for (const email of alvos) {
      const r = await entrar(email);
      if (!r.ok) erros.push(`${email}: ${r.erro}`);
    }
    return erros;
  }

  function minutosRestantes(sessao) {
    if (!sessao.claims || !sessao.claims.exp) return null;
    const restante = sessao.claims.exp * 1000 - Date.now();
    return Math.max(0, Math.round(restante / 60000));
  }

  // ---- Persistencia ------------------------------------------------------
  // sessionStorage e nao localStorage: os tokens somem ao fechar a aba. Sao credenciais de
  // ambiente de avaliacao, mas nao ha motivo para deixa-las no disco alem do necessario.

  function persistir() {
    try {
      sessionStorage.setItem(CHAVE_ARMAZENAMENTO, JSON.stringify(lista()));
    } catch (e) {
      // Aba anonima ou storage bloqueado: a pagina funciona igual, so nao sobrevive ao F5.
    }
  }

  function restaurar() {
    try {
      const bruto = sessionStorage.getItem(CHAVE_ARMAZENAMENTO);
      if (!bruto) return;
      JSON.parse(bruto).forEach((sessao) => {
        // Token vencido enquanto a aba estava fechada nao deve voltar como sessao valida.
        if (sessao.claims && sessao.claims.exp * 1000 < Date.now()) return;
        ativas.set(sessao.email, sessao);
      });
    } catch (e) {
      // Formato antigo ou storage indisponivel: comeca limpo.
    }
  }

  return {
    USUARIOS, entrar, entrarNoTrio, sair, sairDeTodas, marcarExpirada,
    lista, porEmail, porPerfil, trio, minutosRestantes, aoMudar, restaurar,
  };
})();
