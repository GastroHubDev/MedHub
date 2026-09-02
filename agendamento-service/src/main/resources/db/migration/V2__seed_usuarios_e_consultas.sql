-- Massa inicial para avaliacao e testes. Todos os usuarios tem a senha "senha123"
-- (hashes BCrypt de custo 10, gerados um a um - salts diferentes de proposito).
--
-- Lembrete: paciente e medico reaproveitam o id do usuario (@MapsId), por isso os ids
-- abaixo sao fixados explicitamente em vez de deixados a cargo do IDENTITY.

INSERT INTO usuario (id, nome, email, senha, role) VALUES
    (1, 'Dra. Ana Lima',      'medico@hospital.com',     '$2a$10$ObEDGR8H.ALQunfRXWjyde1dx0Vt4nuiUY8mpD3emCagGIJcBRcaC', 'MEDICO'),
    (2, 'Dr. Bruno Alves',    'medico2@hospital.com',    '$2a$10$F5NYc1LzyRyzbHFt5MDvoepvLrckbH7Z08/MNSKYbaZK9BMVUbi/m', 'MEDICO'),
    (3, 'Carla Enfermeira',   'enfermeiro@hospital.com', '$2a$10$JEdx3c884NuKTZlW5aqXwug6NwOkx7HzR4B9NlaU/CN6j6DIFrqFu', 'ENFERMEIRO'),
    (4, 'Maria Souza',        'paciente@hospital.com',   '$2a$10$md5rkok/05bVQ9ZfRD7Mou0ptJdn8gTDP6MpKo6fvOWzcisEeKifS', 'PACIENTE'),
    (5, 'Joao Pereira',       'paciente2@hospital.com',  '$2a$10$rJ7fVEJ/bf4lyLOg6zwiKeW/qsCxqBrqDjy7BOK58FJige7M5EVWe', 'PACIENTE');

INSERT INTO medico (id, crm, especialidade) VALUES
    (1, 'CRM-SP-123456', 'Cardiologia'),
    (2, 'CRM-SP-654321', 'Ortopedia');

INSERT INTO paciente (id, cpf, telefone, data_nascimento) VALUES
    (4, '123.456.789-00', '(11) 98888-1111', DATE '1985-04-12'),
    (5, '987.654.321-00', '(11) 97777-2222', DATE '1992-11-30');

-- Datas relativas ao momento da migracao: a massa continua coerente independentemente de
-- quando o projeto for avaliado (uma consulta passada, tres futuras).
-- O paciente 5 tem consulta propria para provar o bloqueio de acesso cruzado.
INSERT INTO consulta (id, paciente_id, medico_id, data_hora, status, observacoes, versao, criado_em, atualizado_em) VALUES
    (1, 4, 1, CURRENT_TIMESTAMP - 30 * INTERVAL '1' DAY, 'REALIZADA', 'Consulta de rotina - exames normais', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, 4, 1, CURRENT_TIMESTAMP + 3  * INTERVAL '1' DAY, 'AGENDADA',  'Retorno para avaliacao cardiologica', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, 4, 2, CURRENT_TIMESTAMP + 10 * INTERVAL '1' DAY, 'AGENDADA',  'Avaliacao ortopedica do joelho',      1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (4, 5, 1, CURRENT_TIMESTAMP + 5  * INTERVAL '1' DAY, 'AGENDADA',  'Primeira consulta',                   1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Realinha as sequencias de IDENTITY apos os inserts com id explicito.
ALTER TABLE usuario  ALTER COLUMN id RESTART WITH 6;
ALTER TABLE consulta ALTER COLUMN id RESTART WITH 5;
