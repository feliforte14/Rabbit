-- =====================================================================
-- Rabbit: limpiar datos de prueba en la base
-- =====================================================================
-- Dos opciones, según qué haya que limpiar:
--
--   OPCIÓN A: solo los pedidos que generan las pruebas automáticas.
--     Los tests de integración (mvn verify -Pintegracion) marcan sus
--     pedidos con "[TEST]" en la dirección, y la prueba de escalabilidad
--     (scripts/prueba_escalabilidad.py) con "[CARGA]". Borra esos pedidos
--     y todo lo que colgaba de ellos. No toca nada más.
--
--   OPCIÓN B: vaciar todo. Borra TODAS las filas de las tablas de
--     negocio y todas las cuentas de la tabla usuarios. Deja las tablas,
--     las columnas y las restricciones como están.
--
-- Historial: la base se vació por última vez el 12/10/2026, antes de la
-- corrida de pruebas del 14/10. La base es COMPARTIDA con el equipo: si
-- alguien cargó datos que necesita, NO corras la opción B sin avisar.
--
-- Cómo usarlo, en el SQL Editor de Supabase:
--   1. Corré solo el PASO 1 y revisá los conteos.
--   2. Corré el bloque de la opción elegida completo. Termina SIN
--      confirmar.
--   3. Si los conteos del final dan todos 0, corré COMMIT;
--      si algo no cierra, corré ROLLBACK; y no se borra nada.
--
-- Los IDs no se reinician: los próximos registros siguen numerando desde
-- donde quedaron.
-- =====================================================================


-- ---------------------------------------------------------------------
-- PASO 1: qué hay hoy (solo lectura)
-- ---------------------------------------------------------------------
SELECT 'pedidos_lineas' AS tabla, COUNT(*) FROM pedidos_lineas
UNION ALL SELECT 'pedidos', COUNT(*) FROM pedidos
UNION ALL SELECT 'pedidos_externos_lineas', COUNT(*) FROM pedidos_externos_lineas
UNION ALL SELECT 'pedidos_externos', COUNT(*) FROM pedidos_externos
UNION ALL SELECT 'cobros', COUNT(*) FROM cobros
UNION ALL SELECT 'liquidaciones', COUNT(*) FROM liquidaciones
UNION ALL SELECT 'notificaciones', COUNT(*) FROM notificaciones
UNION ALL SELECT 'remitos_entrega', COUNT(*) FROM remitos_entrega
UNION ALL SELECT 'repartidores_cargas', COUNT(*) FROM repartidores_cargas
UNION ALL SELECT 'envios', COUNT(*) FROM envios
UNION ALL SELECT 'reservas_stock', COUNT(*) FROM reservas_stock
UNION ALL SELECT 'items_inventario', COUNT(*) FROM items_inventario
UNION ALL SELECT 'depositos', COUNT(*) FROM depositos
UNION ALL SELECT 'puntos_picking', COUNT(*) FROM puntos_picking
UNION ALL SELECT 'comercios', COUNT(*) FROM comercios
UNION ALL SELECT 'repartidores', COUNT(*) FROM repartidores
UNION ALL SELECT 'usuarios', COUNT(*) FROM usuarios
UNION ALL SELECT 'transportistas', COUNT(*) FROM transportistas
UNION ALL SELECT 'zonas', COUNT(*) FROM zonas;

-- Cuántos pedidos de pruebas automáticas hay (los que borra la opción A)
SELECT 'pedidos [TEST]/[CARGA]' AS que, COUNT(*) FROM pedidos
 WHERE direccionentrega LIKE '[TEST]%' OR direccionentrega LIKE '[CARGA]%'
UNION ALL
SELECT 'pedidos externos [TEST]/[CARGA]', COUNT(*) FROM pedidos_externos
 WHERE direccionentrega LIKE '[TEST]%' OR direccionentrega LIKE '[CARGA]%';

-- Comercios y usuarios (para confirmar que son de prueba antes de la opción B)
SELECT id, nombre, activo FROM comercios ORDER BY id;
SELECT id, username, rol, activo FROM usuarios ORDER BY id;


-- ---------------------------------------------------------------------
-- OPCIÓN A: borrar solo los pedidos de las pruebas automáticas
-- (en una transacción; termina sin confirmar)
-- ---------------------------------------------------------------------
-- Los tests y la carga usan pedidos de punto de picking: no reservan
-- stock, así que no hay stock que devolver.
BEGIN;

-- Lo que cuelga de cada pedido, después el pedido.
DELETE FROM notificaciones WHERE idpedido IN (
  SELECT id FROM pedidos
   WHERE direccionentrega LIKE '[TEST]%' OR direccionentrega LIKE '[CARGA]%');
DELETE FROM cobros WHERE idpedido IN (
  SELECT id FROM pedidos
   WHERE direccionentrega LIKE '[TEST]%' OR direccionentrega LIKE '[CARGA]%');
DELETE FROM envios WHERE idpedido IN (
  SELECT id FROM pedidos
   WHERE direccionentrega LIKE '[TEST]%' OR direccionentrega LIKE '[CARGA]%');
DELETE FROM remitos_entrega WHERE idpedido IN (
  SELECT id FROM pedidos
   WHERE direccionentrega LIKE '[TEST]%' OR direccionentrega LIKE '[CARGA]%');
DELETE FROM repartidores_cargas WHERE idpedido IN (
  SELECT id FROM pedidos
   WHERE direccionentrega LIKE '[TEST]%' OR direccionentrega LIKE '[CARGA]%');
DELETE FROM pedidos_lineas WHERE pedido_id IN (
  SELECT id FROM pedidos
   WHERE direccionentrega LIKE '[TEST]%' OR direccionentrega LIKE '[CARGA]%');
DELETE FROM pedidos
 WHERE direccionentrega LIKE '[TEST]%' OR direccionentrega LIKE '[CARGA]%';

-- Un repartidor que llevaba solo pedidos de prueba vuelve a quedar libre.
UPDATE repartidores r SET estado = 'DISPONIBLE', idpedidoactual = NULL
 WHERE r.estado = 'OCUPADO'
   AND NOT EXISTS (SELECT 1 FROM repartidores_cargas c WHERE c.idrepartidor = r.id)
   AND (r.idpedidoactual IS NULL OR NOT EXISTS (SELECT 1 FROM pedidos p WHERE p.id = r.idpedidoactual));

-- Los pedidos que mandó el ERP de prueba (incluye los descartados, que
-- nunca llegaron a ser pedidos).
DELETE FROM pedidos_externos_lineas WHERE pedido_externo_id IN (
  SELECT id FROM pedidos_externos
   WHERE direccionentrega LIKE '[TEST]%' OR direccionentrega LIKE '[CARGA]%');
DELETE FROM pedidos_externos
 WHERE direccionentrega LIKE '[TEST]%' OR direccionentrega LIKE '[CARGA]%';

-- Tienen que dar los dos 0 antes de confirmar.
SELECT 'pedidos [TEST]/[CARGA]' AS que, COUNT(*) FROM pedidos
 WHERE direccionentrega LIKE '[TEST]%' OR direccionentrega LIKE '[CARGA]%'
UNION ALL
SELECT 'pedidos externos [TEST]/[CARGA]', COUNT(*) FROM pedidos_externos
 WHERE direccionentrega LIKE '[TEST]%' OR direccionentrega LIKE '[CARGA]%';

-- COMMIT;     -- confirma el borrado
-- ROLLBACK;   -- deshace todo, no se borra nada


-- ---------------------------------------------------------------------
-- OPCIÓN B: vaciar todo (en una transacción; termina sin confirmar)
-- ---------------------------------------------------------------------
BEGIN;

-- Primero lo que referencia a otras tablas, después lo referenciado.
DELETE FROM notificaciones;
DELETE FROM cobros;
DELETE FROM liquidaciones;
DELETE FROM remitos_entrega;
DELETE FROM repartidores_cargas;
DELETE FROM envios;
DELETE FROM pedidos_lineas;
DELETE FROM pedidos;
DELETE FROM pedidos_externos_lineas;
DELETE FROM pedidos_externos;
DELETE FROM reservas_stock;
DELETE FROM items_inventario;
DELETE FROM depositos;
DELETE FROM puntos_picking;
-- Todas las cuentas: una cuenta activa que quedara apuntaría a un
-- comercio o a un repartidor que ya no existe.
DELETE FROM usuarios;
DELETE FROM comercios;
DELETE FROM repartidores;
DELETE FROM zonas;
DELETE FROM transportistas;

-- Tienen que dar todos 0 antes de confirmar.
SELECT 'pedidos' AS tabla, COUNT(*) FROM pedidos
UNION ALL SELECT 'pedidos_externos', COUNT(*) FROM pedidos_externos
UNION ALL SELECT 'cobros', COUNT(*) FROM cobros
UNION ALL SELECT 'liquidaciones', COUNT(*) FROM liquidaciones
UNION ALL SELECT 'remitos_entrega', COUNT(*) FROM remitos_entrega
UNION ALL SELECT 'repartidores_cargas', COUNT(*) FROM repartidores_cargas
UNION ALL SELECT 'notificaciones', COUNT(*) FROM notificaciones
UNION ALL SELECT 'envios', COUNT(*) FROM envios
UNION ALL SELECT 'reservas_stock', COUNT(*) FROM reservas_stock
UNION ALL SELECT 'items_inventario', COUNT(*) FROM items_inventario
UNION ALL SELECT 'comercios', COUNT(*) FROM comercios
UNION ALL SELECT 'repartidores', COUNT(*) FROM repartidores
UNION ALL SELECT 'usuarios', COUNT(*) FROM usuarios
UNION ALL SELECT 'transportistas', COUNT(*) FROM transportistas
UNION ALL SELECT 'zonas', COUNT(*) FROM zonas;

-- COMMIT;     -- confirma el borrado
-- ROLLBACK;   -- deshace todo, no se borra nada


-- ---------------------------------------------------------------------
-- Después de la opción B: el realm de cada WildFly
-- ---------------------------------------------------------------------
-- Las contraseñas de las cuentas no viven en la base sino en el realm de
-- cada WildFly. Para que no queden logins sueltos, en cada máquina sacá
-- todas las líneas salvo la del administrador (la que se creó con
-- add-user.sh) de:
--   $WILDFLY_HOME/standalone/configuration/application-users.properties
--   $WILDFLY_HOME/standalone/configuration/application-roles.properties
-- Después, el administrador vuelve a crear las cuentas desde Usuarios.
