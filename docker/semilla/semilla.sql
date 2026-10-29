-- =====================================================================
-- Datos semilla de Rabbit para la base local del compose.
-- Lo corre docker/semilla/cargar.sh después del primer despliegue.
-- Idempotente: cada INSERT se saltea si el dato ya existe.
--
-- Los IDs salen de las secuencias de Hibernate (nextval), así no chocan
-- con los que la app asigne después.
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Primer administrador, con la contraseña en bcrypt (el mismo formato
-- que usa la app y que valida el jdbc-realm de WildFly).
INSERT INTO usuarios (id, username, rol, activo, hash_clave)
SELECT nextval('usuarios_seq'), :'admin_user', 'ADMINISTRADOR', true,
       crypt(:'admin_password', gen_salt('bf', 10))
WHERE NOT EXISTS (SELECT 1 FROM usuarios WHERE username = :'admin_user');

-- PENDIENTE (se completa al probar con OrbStack, mirando el esquema que
-- genera Hibernate): comercio, depósito, zona, ítems con stock,
-- repartidor y las cuentas demo.comercio, demo.repartidor y demo.erp.
