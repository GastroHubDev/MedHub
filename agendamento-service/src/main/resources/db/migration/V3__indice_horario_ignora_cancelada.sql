-- horario_ocupado espelha data_hora enquanto a consulta ocupa o horario, e vira NULL quando
-- ela e cancelada (mantido pela entidade em @PrePersist/@PreUpdate, ver Consulta.java).
-- Varias linhas com NULL nunca colidem numa constraint UNIQUE, entao consultas canceladas
-- nao contam para o indice unico de medico/horario.
DROP INDEX uk_consulta_medico_horario;

ALTER TABLE consulta ADD COLUMN horario_ocupado TIMESTAMP;

UPDATE consulta SET horario_ocupado = data_hora WHERE status <> 'CANCELADA';

CREATE UNIQUE INDEX uk_consulta_medico_horario ON consulta (medico_id, horario_ocupado);
