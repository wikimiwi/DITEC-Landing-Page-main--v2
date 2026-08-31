-- ============================================================
-- Rode este script UMA VEZ, como root/admin do MySQL, para criar
-- o banco e o usuario que a aplicacao vai usar. Depois disso, o
-- Flyway (embutido no backend) cria as tabelas sozinho ao iniciar
-- a aplicacao — voce NAO precisa rodar schema.sql manualmente.
--
-- Exemplo de uso:
--   mysql -u root -p < database/00-criar-banco-e-usuario.sql
-- ============================================================

CREATE DATABASE IF NOT EXISTS ditec_assistencia
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Troque 'uma-senha-forte-local' por uma senha real e use o MESMO valor
-- na variavel de ambiente DB_PASSWORD do backend (.env).
CREATE USER IF NOT EXISTS 'ditec_user'@'localhost' IDENTIFIED BY 'uma-senha-forte-local';
GRANT ALL PRIVILEGES ON ditec_assistencia.* TO 'ditec_user'@'localhost';
FLUSH PRIVILEGES;
