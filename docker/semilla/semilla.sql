-- =====================================================================
-- Datos semilla de Rabbit para la base local del compose.
-- Lo corre docker/semilla/cargar.sh después del primer despliegue.
-- Idempotente: cada INSERT se saltea si el dato ya existe (por nombre).
--
-- Los IDs salen de las secuencias de Hibernate (nextval), así no chocan
-- con los que la app asigne después. Ninguna contraseña está escrita acá:
-- llegan como variables de psql desde el .env y se guardan en bcrypt.
--
-- Variables: admin_user, admin_password, demo_password ('' = sin cuentas demo).
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Primer administrador.
INSERT INTO usuarios (id, username, rol, activo, hash_clave)
SELECT nextval('usuarios_seq'), :'admin_user', 'ADMINISTRADOR', true,
       crypt(:'admin_password', gen_salt('bf', 10))
WHERE NOT EXISTS (SELECT 1 FROM usuarios WHERE username = :'admin_user');

-- ---------------------------------------------------------------------
-- Datos de negocio de demostración (sin datos personales reales).
-- Dos comercios: hacen falta para las pruebas de BOLA (un ERP no ve los
-- pedidos del otro).
-- ---------------------------------------------------------------------

INSERT INTO comercios (id, nombre, razonsocial, cuit, email, telefono, activo)
SELECT nextval('comercios_seq'), v.nombre, v.razon, v.cuit, v.email, v.tel, true
FROM (VALUES
  ('Almacén Demo 1', 'Almacén Demo Uno S.R.L.', '30-00000001-0', 'almacen1@demo.test', '+54 11 0000-0001'),
  ('Almacén Demo 2', 'Almacén Demo Dos S.R.L.', '30-00000002-0', 'almacen2@demo.test', '+54 11 0000-0002')
) AS v(nombre, razon, cuit, email, tel)
WHERE NOT EXISTS (SELECT 1 FROM comercios c WHERE c.nombre = v.nombre);

INSERT INTO depositos (id, nombre, direccion, localidad, provincia, codigopostal, basedesalida)
SELECT nextval('depositos_seq'), 'Depósito Central', 'Av. Juan B. Justo 1000', 'CABA',
       'Buenos Aires', '1414', true
WHERE NOT EXISTS (SELECT 1 FROM depositos WHERE nombre = 'Depósito Central');

-- Stock consignado de los dos comercios en el depósito central.
INSERT INTO items_inventario (id, idcomercio, deposito_id, producto, cantidaddisponible,
                              cantidadreservada, pesokg, largocm, anchocm, altocm, version)
SELECT nextval('items_inventario_seq'), c.id, d.id, v.producto, v.cantidad, 0,
       v.peso, v.largo, v.ancho, v.alto, 0
FROM (VALUES
  ('Almacén Demo 1', 'Yerba mate 1 kg',     500, 1.0, 25, 12, 8),
  ('Almacén Demo 1', 'Caja de zapatillas',  200, 1.2, 35, 22, 13),
  ('Almacén Demo 2', 'Café molido 500 g',   300, 0.5, 18, 10, 6)
) AS v(comercio, producto, cantidad, peso, largo, ancho, alto)
JOIN comercios c ON c.nombre = v.comercio
CROSS JOIN (SELECT id FROM depositos WHERE nombre = 'Depósito Central') d
WHERE NOT EXISTS (SELECT 1 FROM items_inventario i WHERE i.idcomercio = c.id AND i.producto = v.producto);

INSERT INTO puntos_picking (id, comercio_id, nombre, direccion, activa)
SELECT nextval('puntos_picking_seq'), c.id, 'Local Palermo', 'Av. Santa Fe 3200, CABA', true
FROM comercios c
WHERE c.nombre = 'Almacén Demo 1'
  AND NOT EXISTS (SELECT 1 FROM puntos_picking p WHERE p.comercio_id = c.id AND p.nombre = 'Local Palermo');

INSERT INTO repartidores (id, nombre, telefono, estado, capacidadbultos, capacidadkg)
SELECT nextval('repartidores_seq'), 'Repartidor Demo', '+54 11 0000-0003', 'DISPONIBLE', 20, 100
WHERE NOT EXISTS (SELECT 1 FROM repartidores WHERE nombre = 'Repartidor Demo');

-- ---------------------------------------------------------------------
-- Cuentas demo (todas con DEMO_PASSWORD). Sin DEMO_PASSWORD no se crean.
-- ---------------------------------------------------------------------

INSERT INTO usuarios (id, username, rol, activo, hash_clave, idcomercio, idrepartidor)
SELECT nextval('usuarios_seq'), v.usuario, v.rol, true,
       crypt(:'demo_password', gen_salt('bf', 10)),
       (SELECT id FROM comercios WHERE nombre = v.comercio),
       (SELECT id FROM repartidores WHERE nombre = v.repartidor)
FROM (VALUES
  ('demo.operador',   'OPERADOR',   NULL,             NULL),
  ('demo.erp',        'ERP',        'Almacén Demo 1', NULL),
  ('demo.erp2',       'ERP',        'Almacén Demo 2', NULL),
  ('demo.comercio',   'COMERCIO',   'Almacén Demo 1', NULL),
  ('demo.comercio2',  'COMERCIO',   'Almacén Demo 2', NULL),
  ('demo.repartidor', 'REPARTIDOR', NULL,             'Repartidor Demo')
) AS v(usuario, rol, comercio, repartidor)
WHERE :'demo_password' <> ''
  AND NOT EXISTS (SELECT 1 FROM usuarios u WHERE u.username = v.usuario);

-- Resumen para el log de la semilla y para el CI (IDs que usan los tests).
SELECT 'punto_picking=' || p.id FROM puntos_picking p JOIN comercios c ON c.id = p.comercio_id
 WHERE c.nombre = 'Almacén Demo 1' AND p.nombre = 'Local Palermo';
SELECT 'item=' || i.id FROM items_inventario i JOIN comercios c ON c.id = i.idcomercio
 WHERE c.nombre = 'Almacén Demo 1' AND i.producto = 'Yerba mate 1 kg';
SELECT 'cuentas=' || count(*) FROM usuarios;
