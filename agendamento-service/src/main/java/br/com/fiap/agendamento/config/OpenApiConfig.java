package br.com.fiap.agendamento.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String ESQUEMA_BEARER = "bearer-jwt";

    /**
     * Registra o esquema Bearer para que o botao "Authorize" do Swagger UI funcione: sem isso
     * todo endpoint protegido responderia 401 ao ser testado pela propria pagina.
     */
    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Agendamento Service - Tech Challenge Fase 3")
                        .version("1.0.0")
                        .description("""
                                Lado de escrita do backend hospitalar. Autentica usuarios, registra e
                                edita consultas e publica os eventos consumidos pelos servicos de
                                notificacao e historico.

                                Obtenha um token em POST /api/auth/login e informe-o em Authorize.
                                Usuarios de exemplo (senha: senha123): medico@hospital.com,
                                enfermeiro@hospital.com, paciente@hospital.com."""))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_BEARER))
                .components(new Components().addSecuritySchemes(ESQUEMA_BEARER,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
