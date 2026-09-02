package br.com.fiap.agendamento.exception;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Respostas de erro no formato RFC 7807 ({@code application/problem+json}). */
@RestControllerAdvice
public class RestExceptionHandler {

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ProblemDetail naoEncontrado(RecursoNaoEncontradoException e) {
        return montar(HttpStatus.NOT_FOUND, "Recurso nao encontrado", e.getMessage());
    }

    @ExceptionHandler(RegraDeNegocioException.class)
    public ProblemDetail regraDeNegocio(RegraDeNegocioException e) {
        return montar(HttpStatus.BAD_REQUEST, "Regra de negocio violada", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validacao(MethodArgumentNotValidException e) {
        final String detalhe = e.getBindingResult().getFieldErrors().stream()
                .map(erro -> erro.getField() + ": " + erro.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Requisicao invalida");
        return montar(HttpStatus.BAD_REQUEST, "Falha de validacao", detalhe);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail acessoNegado(AccessDeniedException e) {
        return montar(HttpStatus.FORBIDDEN, "Acesso negado", e.getMessage());
    }

    /**
     * Credenciais erradas e usuario inexistente colapsam na mesma resposta de proposito:
     * respostas distintas permitiriam descobrir quais e-mails existem na base.
     */
    @ExceptionHandler({BadCredentialsException.class, UsernameNotFoundException.class})
    public ProblemDetail naoAutenticado(RuntimeException e) {
        return montar(HttpStatus.UNAUTHORIZED, "Nao autenticado", "Credenciais invalidas");
    }

    private static ProblemDetail montar(HttpStatus status, String titulo, String detalhe) {
        final ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalhe);
        problema.setTitle(titulo);
        problema.setType(URI.create("https://tech-challenge-kafka/erros/" + status.value()));
        return problema;
    }
}
