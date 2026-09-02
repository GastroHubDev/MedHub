-- Cria uma base por servico dentro do mesmo container Postgres.
--
-- Cada servico e dono exclusivo do seu schema (nao ha SELECT cruzado entre eles: o que um
-- precisa do outro chega por evento). Um unico container em vez de tres economiza recursos
-- na maquina de avaliacao sem abrir mao desse isolamento logico.

CREATE DATABASE agendamento;
CREATE DATABASE notificacao;
CREATE DATABASE historico;
