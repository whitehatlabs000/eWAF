-- =========================================================================
-- 1. CONFIGURACIÓN INICIAL Y HERRAMIENTAS DE MIGRACIÓN
-- =========================================================================
CREATE DATABASE IF NOT EXISTS ewaf;
USE ewaf;

-- Activar el planificador de eventos (Scheduler) para la autolimpieza
SET GLOBAL event_scheduler = ON;

-- Procedimiento de migración segura (Listo para futuros cambios en caliente)
DELIMITER $$
DROP PROCEDURE IF EXISTS AddColumnSafely$$
CREATE PROCEDURE AddColumnSafely(IN p_db VARCHAR(255), IN p_table VARCHAR(255), IN p_col VARCHAR(255), IN p_def TEXT)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = p_db AND TABLE_NAME = p_table AND COLUMN_NAME = p_col
    ) THEN
        SET @s = CONCAT('ALTER TABLE ', p_table, ' ADD COLUMN ', p_col, ' ', p_def);
        PREPARE stmt FROM @s;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

-- =========================================================================
-- 2. TABLAS BASE (Estructura consolidada para Desarrollo/Producción)
-- =========================================================================

CREATE TABLE IF NOT EXISTS usuarios (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    tipo ENUM('admin','standard') NOT NULL DEFAULT 'standard',
    active TINYINT(1) NOT NULL DEFAULT 1,
    CONSTRAINT chk_active CHECK (active IN (0, 1))
);

CREATE TABLE IF NOT EXISTS access_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ip_address VARCHAR(45) NOT NULL,
    username VARCHAR(50),
    event_type VARCHAR(50) NOT NULL,
    target_path VARCHAR(255) DEFAULT NULL,
    http_method VARCHAR(10) DEFAULT NULL,
    details VARCHAR(255),
    INDEX idx_access_logs_timestamp (event_timestamp DESC),
    INDEX idx_access_logs_path (target_path)
);

CREATE TABLE IF NOT EXISTS proxy_routes (
    id INT AUTO_INCREMENT PRIMARY KEY,
    incoming_path VARCHAR(255) NOT NULL,
    target_url VARCHAR(255) NOT NULL,
    active TINYINT(1) DEFAULT 1,
    custom_replacements TEXT DEFAULT NULL,
    engine_type VARCHAR(20) DEFAULT 'NATIVE',
    cache_ttl_seconds INT DEFAULT 0,
    use_modsecurity TINYINT(1) DEFAULT 0
);

-- =========================================================================
-- 3. MIGRACIONES EN CALIENTE (Plantilla para el futuro)
-- =========================================================================
-- Cuando eWAF esté en producción y necesites agregar columnas sin perder datos,
-- simplemente descomenta y usa esta estructura:

-- CALL AddColumnSafely('ewaf', 'nombre_de_tabla', 'nombre_columna', 'TIPO_DATO DEFAULT VALOR');

-- =========================================================================
-- 4. EVENTOS AUTOMÁTICOS (Mantenimiento)
-- =========================================================================
DELIMITER $$

CREATE EVENT IF NOT EXISTS clean_ewaf_access_logs
ON SCHEDULE EVERY 1 DAY
STARTS CURRENT_TIMESTAMP
DO
BEGIN
    DELETE FROM access_logs WHERE event_timestamp < NOW() - INTERVAL 2 MONTH;
END$$

DELIMITER ;

-- =========================================================================
-- 5. USUARIO ADMINISTRADOR POR DEFECTO
-- =========================================================================
-- Inserta el usuario admin/admin usando SHA-256 como fallback inicial.
-- Cambiar la misma al instante, al cambiarla incluye BCrypt por defecto.
-- La contraseña original es: admin
INSERT IGNORE INTO usuarios (username, password, tipo, active) 
VALUES ('admin', '8c6976e5b5410415bde908bd4dee15dfb167a9c873fc4bb8a81f6f2ab448a918', 'admin', 1);

-- =========================================================================
-- 6. LIMPIEZA
-- =========================================================================
-- Borramos el procedimiento temporal para mantener la base de datos limpia
DROP PROCEDURE IF EXISTS AddColumnSafely;