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
--   OPCIÓN C: borrar todo menos las cuentas. Conserva la tabla usuarios
--     y los comercios, repartidores y zonas a los que apuntan esas
--     cuentas (sin ellos, el usuario de un comercio o de un repartidor
--     no podría trabajar). Borra pedidos, stock, cobros, liquidaciones,
--     avisos, puntos de picking, depósitos, transportistas y todo lo
--     demás. Los repartidores quedan libres.
--
--   OPCIÓN B: vaciar todo. Borra TODAS las filas de las tablas de
--     negocio y todas las cuentas de la tabla usuarios. Deja las tablas,
--     las columnas y las restricciones como están.
--
-- Historial: la base se vació por última vez el 15/10/2026, conservando
-- usuarios, comercios, repartidores y zonas (para las pruebas de
-- ADR-032 a 036). La base es COMPARTIDA con el equipo: si
-- alguien cargó datos que necesita, NO corras la opción B sin avisar.
--
-- Cómo usarlo, en el SQL Editor de Supabase:
--   1. Corré solo el PASO 1 y revisá los conteos.
--   2. Corré el bloque de la opción elegida completo, tal cual. Es un
--      ensayo: el editor corre cada ejecución en una conexión aparte y,
--      como el bloque termina sin COMMIT, al terminar se deshace solo.
--      Revisá que los conteos del final den 0.
--   3. Si dan 0, volvé a correr el MISMO bloque con la línea "COMMIT;"
--      del final descomentada: tiene que ir en la misma ejecución. Un
--      COMMIT corrido después, solo, no confirma nada (la transacción ya
--      se descartó).
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
UNION ALL SELECT 'intentos_entrega', COUNT(*) FROM intentos_entrega
UNION ALL SELECT 'fotos_intento_entrega', COUNT(*) FROM fotos_intento_entrega
UNION ALL SELECT 'avisos_destinatario', COUNT(*) FROM avisos_destinatario
UNION ALL SELECT 'coordenadas_direcciones', COUNT(*) FROM coordenadas_direcciones
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
DELETE FROM fotos_intento_entrega WHERE idpedido IN (
  SELECT id FROM pedidos
   WHERE direccionentrega LIKE '[TEST]%' OR direccionentrega LIKE '[CARGA]%');
DELETE FROM intentos_entrega WHERE idpedido IN (
  SELECT id FROM pedidos
   WHERE direccionentrega LIKE '[TEST]%' OR direccionentrega LIKE '[CARGA]%');
DELETE FROM avisos_destinatario WHERE idpedido IN (
  SELECT id FROM pedidos
   WHERE direccionentrega LIKE '[TEST]%' OR direccionentrega LIKE '[CARGA]%');
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

-- COMMIT;     -- descomentar para confirmar, en la misma ejecución (paso 3)


-- ---------------------------------------------------------------------
-- OPCIÓN C: borrar todo menos las cuentas (en una transacción; termina
-- sin confirmar)
-- ---------------------------------------------------------------------
BEGIN;

-- Lo que cuelga de los pedidos, después los pedidos.
DELETE FROM fotos_intento_entrega;
DELETE FROM intentos_entrega;
DELETE FROM avisos_destinatario;
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
-- Stock, depósitos, puntos de picking, transportistas y el caché de
-- direcciones del mapa (se vuelve a llenar solo).
DELETE FROM reservas_stock;
DELETE FROM items_inventario;
DELETE FROM depositos;
DELETE FROM puntos_picking;
DELETE FROM transportistas;
DELETE FROM coordenadas_direcciones;

-- Comercios y repartidores: quedan solo los que tienen una cuenta.
DELETE FROM comercios
 WHERE id NOT IN (SELECT idcomercio FROM usuarios WHERE idcomercio IS NOT NULL);
DELETE FROM repartidores
 WHERE id NOT IN (SELECT idrepartidor FROM usuarios WHERE idrepartidor IS NOT NULL);
-- Los que quedan, libres y sin ubicación vieja.
UPDATE repartidores
   SET estado = 'DISPONIBLE', idpedidoactual = NULL,
       latitud = NULL, longitud = NULL, fechaubicacion = NULL;
-- Zonas: quedan solo las de esos repartidores.
DELETE FROM zonas
 WHERE id NOT IN (SELECT idzona FROM repartidores WHERE idzona IS NOT NULL);

-- Lo borrado tiene que dar 0; lo conservado, lo que había.
SELECT 'pedidos' AS tabla, COUNT(*) FROM pedidos
UNION ALL SELECT 'pedidos_externos', COUNT(*) FROM pedidos_externos
UNION ALL SELECT 'intentos_entrega', COUNT(*) FROM intentos_entrega
UNION ALL SELECT 'avisos_destinatario', COUNT(*) FROM avisos_destinatario
UNION ALL SELECT 'notificaciones', COUNT(*) FROM notificaciones
UNION ALL SELECT 'cobros', COUNT(*) FROM cobros
UNION ALL SELECT 'liquidaciones', COUNT(*) FROM liquidaciones
UNION ALL SELECT 'items_inventario', COUNT(*) FROM items_inventario
UNION ALL SELECT 'depositos', COUNT(*) FROM depositos
UNION ALL SELECT 'puntos_picking', COUNT(*) FROM puntos_picking
UNION ALL SELECT 'transportistas', COUNT(*) FROM transportistas
UNION ALL SELECT '-- conservados --', NULL
UNION ALL SELECT 'usuarios', COUNT(*) FROM usuarios
UNION ALL SELECT 'comercios', COUNT(*) FROM comercios
UNION ALL SELECT 'repartidores', COUNT(*) FROM repartidores
UNION ALL SELECT 'zonas', COUNT(*) FROM zonas;

-- COMMIT;     -- descomentar para confirmar, en la misma ejecución (paso 3)



-- ---------------------------------------------------------------------
-- OPCIÓN B: vaciar todo (en una transacción; termina sin confirmar)
-- ---------------------------------------------------------------------
BEGIN;

-- Primero lo que referencia a otras tablas, después lo referenciado.
DELETE FROM fotos_intento_entrega;
DELETE FROM intentos_entrega;
DELETE FROM avisos_destinatario;
DELETE FROM coordenadas_direcciones;
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

-- COMMIT;     -- descomentar para confirmar, en la misma ejecución (paso 3)


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
