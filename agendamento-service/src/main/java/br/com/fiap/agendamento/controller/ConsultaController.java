package br.com.fiap.agendamento.controller;

import br.com.fiap.agendamento.dto.AtualizarConsultaRequest;
import br.com.fiap.agendamento.dto.ConsultaResponse;
import br.com.fiap.agendamento.dto.CriarConsultaRequest;
import br.com.fiap.agendamento.service.ConsultaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lado de escrita das consultas.
 *
 * <p>Os {@code @PreAuthorize} implementam os niveis de acesso do enunciado: medicos e
 * enfermeiros registram e modificam consultas, e pacientes apenas consultam - a restricao
 * de que so enxergam as proprias consultas e aplicada no servico.</p>
 *
 * <p>Um nivel a mais mora no servico, porque depende do campo e nao da rota: so o medico
 * marca uma consulta como {@code REALIZADA}. E a separacao que o enunciado faz entre
 * <i>modificar consultas existentes</i> (agenda, dos dois perfis) e <i>editar o historico</i>
 * (prontuario, do medico).</p>
 *
 * <p>Nao existe rota dedicada de cancelamento: cancelar e uma alteracao de status como
 * qualquer outra, feita pelo {@code PUT} com {@code status: CANCELADA}. Uma rota separada
 * seria um segundo caminho para o mesmo efeito, com as mesmas regras duplicadas.</p>
 */
@RestController
@RequestMapping("/api/consultas")
@Tag(name = "Consultas", description = "Registro e edicao de consultas (lado de escrita)")
public class ConsultaController {

    private final ConsultaService consultaService;

    public ConsultaController(ConsultaService consultaService) {
        this.consultaService = consultaService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('MEDICO','ENFERMEIRO','PACIENTE')")
    @Operation(summary = "Lista consultas",
            description = "Medicos e enfermeiros veem todas as consultas ou filtram por paciente. "
                    + "Pacientes recebem sempre apenas as proprias; informar o id de outro paciente "
                    + "resulta em 403.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista retornada"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou invalido"),
            @ApiResponse(responseCode = "403", description = "Paciente tentando ver dados de outro")
    })
    public List<ConsultaResponse> listar(
            @RequestParam(required = false) Long pacienteId,
            @RequestParam(defaultValue = "false") boolean apenasFuturas) {
        return ConsultaResponse.deLista(consultaService.listar(pacienteId, apenasFuturas));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('MEDICO','ENFERMEIRO','PACIENTE')")
    @Operation(summary = "Detalha uma consulta")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Consulta encontrada"),
            @ApiResponse(responseCode = "403", description = "Consulta pertence a outro paciente"),
            @ApiResponse(responseCode = "404", description = "Consulta inexistente")
    })
    public ConsultaResponse buscar(@PathVariable Long id) {
        return ConsultaResponse.de(consultaService.buscar(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('MEDICO','ENFERMEIRO')")
    @Operation(summary = "Registra uma nova consulta",
            description = "Publica um evento CONSULTA_CRIADA para os servicos de notificacao e historico.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Consulta registrada"),
            @ApiResponse(responseCode = "400", description = "Data no passado ou horario ja ocupado"),
            @ApiResponse(responseCode = "403", description = "Perfil sem permissao para registrar"),
            @ApiResponse(responseCode = "404", description = "Paciente ou medico inexistente")
    })
    public ResponseEntity<ConsultaResponse> criar(@Valid @RequestBody CriarConsultaRequest requisicao) {
        final ConsultaResponse criada = ConsultaResponse.de(consultaService.criar(requisicao));
        return ResponseEntity.created(URI.create("/api/consultas/" + criada.id())).body(criada);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('MEDICO','ENFERMEIRO')")
    @Operation(summary = "Edita uma consulta",
            description = "Atende ao requisito \"medicos e enfermeiros poderao registrar novas "
                    + "consultas e modificar consultas existentes\": a alteracao feita aqui chega ao "
                    + "servico de historico via evento CONSULTA_ATUALIZADA. Campos nulos sao mantidos "
                    + "como estao. Enviar status CANCELADA cancela a consulta e publica "
                    + "CONSULTA_CANCELADA, fazendo o servico de notificacao parar os lembretes. "
                    + "Marcar status REALIZADA e ato clinico e exige o perfil MEDICO - e o requisito "
                    + "\"medicos podem visualizar e editar o historico de consultas\".")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Consulta atualizada"),
            @ApiResponse(responseCode = "400", description = "Consulta cancelada, data no passado ou horario ocupado"),
            @ApiResponse(responseCode = "403", description = "Perfil sem permissao para editar, "
                    + "ou enfermeiro tentando marcar como REALIZADA"),
            @ApiResponse(responseCode = "404", description = "Consulta inexistente")
    })
    public ConsultaResponse atualizar(@PathVariable Long id,
                                      @Valid @RequestBody AtualizarConsultaRequest requisicao) {
        return ConsultaResponse.de(consultaService.atualizar(id, requisicao));
    }
}
