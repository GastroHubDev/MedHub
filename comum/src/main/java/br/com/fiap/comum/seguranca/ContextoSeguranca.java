package br.com.fiap.comum.seguranca;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Envelope fino sobre o {@link SecurityContextHolder}.
 *
 * <p>Existe para que as regras de posse (um paciente so enxerga o proprio historico) morem
 * na camada de servico e possam ser testadas com um mock, em vez de dependerem de um holder
 * estatico - que forcaria todo teste de servico a montar contexto de seguranca.</p>
 *
 * <p><b>Invariante assumida:</b> {@code Paciente} e {@code Medico} compartilham a chave
 * primaria do seu {@code Usuario} (mapeamento {@code @MapsId} no agendamento). Por isso o
 * {@code usuarioId} que viaja no token e o mesmo id de paciente que viaja no evento, e a
 * comparacao abaixo e direta - sem claim extra e sem consulta ao banco.</p>
 *
 * <p>Declarado como {@code @Bean} pela configuracao de seguranca de cada servico, e nao com
 * {@code @Component}: o modulo comum nao presume que alguem esteja escaneando seus pacotes.</p>
 */
public class ContextoSeguranca {

    public UsuarioAutenticado usuarioLogado() {
        final Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao == null || !(autenticacao.getPrincipal() instanceof UsuarioAutenticado usuario)) {
            throw new AccessDeniedException("Nenhum usuario autenticado no contexto");
        }
        return usuario;
    }

    /**
     * Resolve qual paciente esta sendo consultado e aplica a regra de posse.
     *
     * <p>Paciente que omite o id recebe o proprio; paciente que informa o id de outro recebe
     * 403 explicito - nunca uma reescrita silenciosa para o proprio id, que mascararia a
     * tentativa de acesso indevido.</p>
     */
    public Long resolverPacienteAlvo(Long pacienteIdSolicitado) {
        final UsuarioAutenticado usuario = usuarioLogado();
        if (!usuario.isPaciente()) {
            return pacienteIdSolicitado;
        }
        if (pacienteIdSolicitado != null && !pacienteIdSolicitado.equals(usuario.getId())) {
            throw new AccessDeniedException("Paciente so pode consultar o proprio historico");
        }
        return usuario.getId();
    }

    /** Garante que um paciente so acesse um recurso que lhe pertence. */
    public void exigirPosse(Long pacienteIdDoRecurso) {
        final UsuarioAutenticado usuario = usuarioLogado();
        if (usuario.isPaciente() && !usuario.getId().equals(pacienteIdDoRecurso)) {
            throw new AccessDeniedException("Paciente so pode acessar as proprias consultas");
        }
    }

    /**
     * Reserva ao medico um ato que e clinico, e nao de agenda.
     *
     * <p>O enunciado separa os dois: "medicos podem visualizar e editar o <b>historico</b> de
     * consultas", enquanto "medicos e enfermeiros poderao registrar novas consultas e modificar
     * <b>consultas existentes</b>". Remarcar e cancelar sao agenda e cabem aos dois perfis;
     * atestar o que aconteceu no atendimento e prontuario e cabe so ao medico. Como a distincao
     * e por campo e nao por rota, ela nao caberia num {@code @PreAuthorize} do controller.</p>
     */
    public void exigirMedico(String acao) {
        if (!usuarioLogado().isMedico()) {
            throw new AccessDeniedException("Apenas medicos podem " + acao);
        }
    }
}
