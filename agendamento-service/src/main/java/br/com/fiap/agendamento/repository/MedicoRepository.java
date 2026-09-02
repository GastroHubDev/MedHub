package br.com.fiap.agendamento.repository;

import br.com.fiap.agendamento.domain.Medico;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicoRepository extends JpaRepository<Medico, Long> {
}
