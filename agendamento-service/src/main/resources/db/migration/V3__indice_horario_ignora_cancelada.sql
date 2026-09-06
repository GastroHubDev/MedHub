-- O indice unico original (medico_id, data_hora) bloqueava o horario para sempre: como
-- cancelamento e um soft-delete (a linha continua na tabela com status = CANCELADA), o mesmo
-- par medico/horario nunca ficava livre de novo para reagendar, mesmo com o horario original
-- desocupado de verdade.
--
-- Um indice unico parcial de verdade (CREATE UNIQUE INDEX ... WHERE status <> 'CANCELADA')
-- resolveria isso, mas e sintaxe especifica do Postgres - o H2 usado nos testes nao entende
-- filtro num indice. A alternativa portavel e uma coluna-sombra: horario_ocupado espelha
-- data_hora enquanto a consulta ocupa o horario, e vira NULL quando ela e cancelada (mantido
-- pela entidade em @PrePersist/@PreUpdate, ver Consulta.java). Varias linhas com NULL nunca
-- colidem numa constraint UNIQUE - isso vale tanto no Postgres quanto no H2 - entao qualquer
-- numero de cancelamentos no mesmo par medico/horario convive sem problema.
DROP INDEX uk_consulta_medico_horario;

ALTER TABLE consulta ADD COLUMN horario_ocupado TIMESTAMP;

UPDATE consulta SET horario_ocupado = data_hora WHERE status <> 'CANCELADA';

CREATE UNIQUE INDEX uk_consulta_medico_horario ON consulta (medico_id, horario_ocupado);
