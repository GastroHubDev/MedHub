package br.com.fiap.historico;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Servico de historico: lado de leitura do sistema.
 *
 * <p>Nao tem {@code @EnableScheduling} - nao ha nada agendado aqui. Todo o estado chega pelo
 * consumidor Kafka.</p>
 */
@SpringBootApplication
public class HistoricoApplication {

    public static void main(String[] args) {
        SpringApplication.run(HistoricoApplication.class, args);
    }
}
