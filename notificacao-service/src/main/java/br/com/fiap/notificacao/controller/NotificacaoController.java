package br.com.fiap.notificacao.controller;

import br.com.fiap.notificacao.dto.NotificacaoResponse;
import br.com.fiap.notificacao.repository.NotificacaoRepository;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta a trilha de notificacoes.
 *
 * <p>Existe para tornar o efeito do Kafka verificavel sem abrir a caixa do MailHog: depois de
 * criar uma consulta, esta rota mostra a notificacao que o evento produziu. Restrita a equipe
 * clinica - e uma visao operacional, nao um recurso do paciente.</p>
 */
@RestController
@RequestMapping("/api/notificacoes")
public class NotificacaoController {

    private final NotificacaoRepository notificacaoRepository;

    public NotificacaoController(NotificacaoRepository notificacaoRepository) {
        this.notificacaoRepository = notificacaoRepository;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('MEDICO','ENFERMEIRO')")
    @Transactional(readOnly = true)
    public List<NotificacaoResponse> listar(@RequestParam(required = false) Long consultaId) {
        final var notificacoes = consultaId == null
                ? notificacaoRepository.findAllByOrderByEnviadoEmDesc()
                : notificacaoRepository.findByConsultaIdOrderByEnviadoEmDesc(consultaId);
        return NotificacaoResponse.deLista(notificacoes);
    }
}
