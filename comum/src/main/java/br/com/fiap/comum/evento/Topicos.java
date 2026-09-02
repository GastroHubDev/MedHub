package br.com.fiap.comum.evento;

/**
 * Nomes de topico e de consumer group compartilhados entre produtor e consumidores.
 *
 * <p>O sufixo {@code .v1} versiona o contrato: uma mudanca incompativel no
 * {@link ConsultaEvento} vira um topico novo, e nao uma quebra silenciosa dos
 * consumidores que ainda leem o formato antigo.</p>
 */
public final class Topicos {

    /** Topico principal. Chave da mensagem = id da consulta, o que garante ordem por consulta. */
    public static final String CONSULTAS = "hospital.consultas.v1";

    /** Dead letter topic: mensagens que falharam apos as retentativas. */
    public static final String CONSULTAS_DLT = CONSULTAS + ".DLT";

    /**
     * Consumer groups. Sao distintos de proposito: cada servico recebe todos os eventos
     * com offsets proprios, em vez de disputarem as mesmas mensagens.
     */
    public static final String GRUPO_NOTIFICACAO = "notificacao-service";
    public static final String GRUPO_HISTORICO = "historico-service";

    private Topicos() {
    }
}
