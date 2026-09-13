package br.com.fiap.notificacao.config;

import br.com.fiap.comum.seguranca.JsonAccessDeniedHandler;
import br.com.fiap.comum.seguranca.JsonAuthenticationEntryPoint;
import br.com.fiap.comum.seguranca.JwtAuthenticationFilter;
import br.com.fiap.comum.seguranca.JwtProperties;
import br.com.fiap.comum.seguranca.TokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Este servico apenas <b>valida</b> o token emitido pelo agendamento - nao emite nenhum, e por
 * isso nao precisa de {@code AuthenticationManager} nem de codificador de senha.
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    @Bean
    public TokenService tokenService(JwtProperties propriedades) {
        return new TokenService(propriedades);
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(TokenService tokenService) {
        return new JwtAuthenticationFilter(tokenService);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter,
                                           ObjectMapper objectMapper) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(rotas -> rotas
                        // O 404 de rota nao mapeada chega aqui como um dispatch para /error, e o
                        // SecurityContext ja foi limpo: sem liberar o ERROR o forward e barrado e o
                        // cliente recebe 401 no lugar do 404. Nao afrouxa nada - o dispatch original
                        // continua passando pelas regras abaixo.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(tratamento -> tratamento
                        .authenticationEntryPoint(new JsonAuthenticationEntryPoint(objectMapper))
                        .accessDeniedHandler(new JsonAccessDeniedHandler(objectMapper)))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
