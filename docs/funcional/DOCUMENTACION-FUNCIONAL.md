---
title: "Documentación funcional · Rabbit"
subtitle: "Del requisito al criterio · Desarrollo de Aplicaciones II · Grupo 9 · Lunes TM"
date: "Versión 1.0 · 02/11/2026"
---

# Documentación funcional · Rabbit

**Grupo 9 · Lunes TM · Desarrollo de Aplicaciones II (3.4.218) · UADE · 2.º cuatrimestre 2026**

| Dato | Valor |
|---|---|
| Documento | Especificación funcional (SRS): alcance, actores, historias de usuario, reglas de negocio, requisitos no funcionales y trazabilidad |
| Estándares | ISO/IEC/IEEE 29148:2018 (SRS) · INVEST y Gherkin para las HU · ISO/IEC 25010:2023 para los RNF |
| Versión | 1.0 · 02/11/2026 |
| Integrantes | Felipe Forte · Florencia Ayala · Facundo (a completar apellido) · *(completar si hay más)* |
| Fuente versionada | `docs/funcional/DOCUMENTACION-FUNCIONAL.md` |
| Tablero | Trello: <https://trello.com/b/9RM3cIkR/rabbit> (aceptado por la cátedra en lugar de Jira) |
| Prototipo | Figma: *(enlace a completar)* · la aplicación desplegada es el prototipo navegable de referencia |
| Requisitos de negocio | [BRD](../negocio/BRD.md) (BR-01 a BR-11) |

Este documento baja cada requisito de negocio (BR) a historias de usuario (HU), cada historia a criterios de aceptación en Gherkin (CA), y cada criterio a un caso de prueba (CP) con su test automatizado. La cadena completa está en la matriz de la sección 7.

```text
BR-01 ──► HU-01 Enviar un pedido ──► CA-01.1 (Gherkin) ──► CP-01.1 (prueba) ──► ApiRestIT.altaDevuelve201ConLocationYLinks
```

## 1. Alcance

Rabbit recibe pedidos de los ERP de los comercios, guarda su mercadería en depósitos, la reparte con su flota o con transportistas, cobra el pedido y su propio servicio, y le da a cada actor una vista de lo que le toca. El alcance detallado (incluye / excluye) está en la sección 4 del [BRD](../negocio/BRD.md).

**Entra en esta versión:** las 35 historias de la sección 3: las 28 de la entrega del diseño (HU-01 a HU-28), con HU-14 actualizada por la ADR-055, y 7 historias que formalizan funciones agregadas entre el 21 y el 26/10 (HU-29 a HU-35, ADR-052 a 059).

**Queda fuera:** catálogo de productos (vive en el ERP), facturación fiscal, app nativa del repartidor, devoluciones iniciadas por quien compra, lotes y vencimientos, días gratis y recargo por stock antiguo en el almacenamiento (ADR-059, sin valores definidos).

## 2. Actores

| Actor | Tipo | Cómo usa Rabbit | Qué ve |
|---|---|---|---|
| ERP del comercio | Sistema externo | API REST `/api/v1/pedidos-externos` con HTTP Basic por HTTPS | Solo los pedidos de su comercio |
| Quien recibe (cliente final) | Persona sin cuenta | Seguimiento público con el código; mails | Estado, día prometido, parada y franja; nunca datos personales |
| Comercio | Usuario con cuenta | Portal del comercio (web) | Sus pedidos, avisos, stock, puntos de picking, liquidaciones e indicadores |
| Repartidor | Usuario con cuenta | Web responsive desde el celular | Solo los pedidos que tiene asignados |
| Operador | Personal de Rabbit | Pantallas del personal | Toda la red |
| Administrador | Personal de Rabbit | Lo del operador más cuentas, eliminaciones y liquidaciones | Toda la red |
| Banco legado | Sistema externo | SOAP (`BancoLegadoService`) | Cobros, reversas y liquidaciones |
| Transportistas | Sistemas externos | REST y SOAP | Envíos derivados |

## 3. Historias de usuario con criterios de aceptación

Formato: **Como** … **quiero** … **para** …, con prioridad MoSCoW, el BR al que responde y la pantalla o endpoint. Cada historia tiene al menos tres criterios en Gherkin (camino feliz, alternativo y error); las que dependen de un sistema externo suman un criterio de falla de ese sistema. Los criterios se escribieron contra el comportamiento real de la aplicación (código y ADR al 02/11/2026).

**Prioridades:** Must = 13 historias · Should = 11 · Could = 11. Ningún criterio de una HU Must puede quedar fallando al cierre de la prueba.

### 3.1 ERP del comercio

#### HU-01 · Enviar un pedido a Rabbit · Must · BR-01

**Como** ERP del comercio, **quiero** mandarle a Rabbit cada venta que hay que entregar, **para** que se reparta sin cargarla a mano. *Dónde:* `POST /api/v1/pedidos-externos`

```gherkin
Escenario: CA-01.1 Alta correcta con día prometido
  Dado un ERP autenticado de un comercio activo
  Cuando envía un pedido válido con líneas, importe, medio de pago, dirección, código postal y email de quien recibe
  Entonces responde 201 con Location, el número del pedido y la fecha prometida
  Y no espera a que se reserve el stock

Escenario: CA-01.2 Reintento con la misma Idempotency-Key
  Dado un pedido ya enviado con la clave "K1"
  Cuando el ERP reenvía el mismo cuerpo con la clave "K1"
  Entonces recibe el mismo pedido y no se crea otro
  Pero si reenvía otro cuerpo con la clave "K1", responde 422

Escenario: CA-01.3 Dato faltante o con formato inválido
  Dado un ERP autenticado
  Cuando envía un pedido sin email de quien recibe, sin Idempotency-Key o con un campo mal formado
  Entonces responde 400 en Problem Details y dice qué campo corregir
  Y si el código postal no es argentino o el punto de picking no existe, responde 422

Escenario: CA-01.4 El comercio sale de la cuenta, no del pedido
  Dado un ERP del comercio A
  Cuando manda en el cuerpo un idComercio, un estado o un importe que no le corresponden
  Entonces esos campos se ignoran y el pedido queda a nombre del comercio A

Escenario: CA-01.5 La cola de mensajería no está disponible (sistema externo)
  Dado que el mensaje del pedido no llega a procesarse por la cola
  Cuando pasa el polling de respaldo
  Entonces el pedido se sincroniza igual y no se pierde
```

#### HU-02 · Saber cómo terminó un pedido · Must · BR-01

**Como** ERP, **quiero** consultar un pedido que mandé, **para** mostrarle a mi cliente en qué está. *Dónde:* `GET /api/v1/pedidos-externos/{id}`

```gherkin
Escenario: CA-02.1 Pedido sincronizado
  Dado un pedido del ERP ya procesado
  Cuando lo consulta
  Entonces responde 200 con "Sincronizado", el estado, el código de seguimiento, las visitas fallidas y si está en lista de espera
  Y la respuesta trae un ETag

Escenario: CA-02.2 Pedido descartado
  Dado un pedido que no se pudo procesar por falta de stock o comercio dado de baja
  Cuando lo consulta
  Entonces responde "Descartado" con el motivo

Escenario: CA-02.3 Pedido de otro comercio
  Dado un pedido del comercio A
  Cuando lo consulta el ERP del comercio B
  Entonces responde 404, igual que para un id inexistente, sin ningún dato del pedido

Escenario: CA-02.4 Consulta condicional
  Dado un ETag de una consulta anterior
  Cuando consulta con If-None-Match y el pedido no cambió
  Entonces responde 304 sin cuerpo
```

#### HU-03 · Cancelar un pedido · Should · BR-01

**Como** ERP, **quiero** cancelar un pedido que mi cliente ya no quiere, **para** que no salga a reparto. *Dónde:* `POST /api/v1/pedidos-externos/{id}/cancelacion`

```gherkin
Escenario: CA-03.1 Cancelar un pedido pendiente
  Dado un pedido PENDIENTE del ERP
  Cuando lo cancela
  Entonces el pedido pasa a CANCELADO y el stock reservado vuelve al depósito
  Y repetir la cancelación responde lo mismo

Escenario: CA-03.2 Pedido ya confirmado
  Dado un pedido CONFIRMADO (con cobro y repartidor)
  Cuando el ERP lo intenta cancelar
  Entonces responde 409 y el pedido lo tiene que cancelar el personal de Rabbit

Escenario: CA-03.3 Pedido que ya salió a reparto
  Dado un pedido que salió a reparto, aunque haya vuelto al depósito para otra visita
  Cuando el ERP lo intenta cancelar
  Entonces responde 409 con el motivo y el pedido no cambia

Escenario: CA-03.4 ETag desactualizado
  Dado un ETag viejo del pedido
  Cuando cancela con If-Match y ese ETag
  Entonces responde 412 con el ETag actual y no cancela
```

### 3.2 Quien recibe el pedido

#### HU-04 · Seguir mi pedido sin registrarme · Must · BR-06, BR-04

**Como** cliente, **quiero** ver en qué está mi pedido con el código que me dieron, **para** saber cuándo llega. *Dónde:* Seguí tu pedido · `GET /api/v1/seguimiento/{codigo}`

```gherkin
Escenario: CA-04.1 Pedido pendiente o confirmado
  Dado un pedido con código RB-XXXXXXXXXX
  Cuando quien recibe lo consulta sin iniciar sesión
  Entonces ve el estado, la línea de tiempo y el día prometido ("Llega hoy", "Llega mañana")

Escenario: CA-04.2 Pedido en camino
  Dado un pedido EN_CAMINO con un repartidor de Rabbit
  Cuando lo consulta
  Entonces ve en qué parada va ("Sos la parada 2 de 3"), la franja horaria y el mapa

Escenario: CA-04.3 Código inexistente
  Cuando consulta un código que no existe
  Entonces ve "No encontramos ese código" (la API responde 404 en Problem Details)
  Y un código válido escrito en minúsculas o con espacios al principio o al final se encuentra igual

Escenario: CA-04.4 Sin datos personales
  Dado un pedido con nombre, teléfono, email y dirección de quien recibe
  Cuando se consulta el seguimiento público
  Entonces la respuesta no incluye ninguno de esos datos ni el comercio
```

#### HU-05 · Enterarme de cada novedad por mail · Should · BR-06

**Como** cliente, **quiero** recibir un mail cada vez que mi pedido cambia, **para** no tener que entrar a mirarlo. *Dónde:* mail al email que mandó el ERP

```gherkin
Escenario: CA-05.1 Avisos en cada cambio
  Dado un pedido con email de quien recibe
  Cuando se recibe, se le asigna repartidor, sale a reparto y se entrega
  Entonces llega un mail en cada paso, siempre con el link al seguimiento
  Y el de "en camino" trae el código de entrega de 4 dígitos

Escenario: CA-05.2 Visita fallida
  Cuando una visita falla
  Entonces el mail explica qué pasó y qué sigue (confirmar la dirección, que la próxima es la última)

Escenario: CA-05.3 Aviso viejo descartado
  Dado que ya se avisó un cambio más nuevo
  Cuando llega tarde el aviso de un estado anterior
  Entonces no se manda

Escenario: CA-05.4 Servidor de correo caído (sistema externo)
  Dado que el servidor SMTP no responde
  Cuando el pedido cambia de estado
  Entonces el pedido avanza igual y el error queda en el log sin el email completo
```

### 3.3 Comercio

#### HU-06 · Seguir mis pedidos y sus avisos · Should · BR-06, BR-08

**Como** comercio, **quiero** ver todos mis pedidos y los avisos de Rabbit, **para** responderle a mi cliente sin llamar a nadie. *Dónde:* Mis pedidos · campana de avisos

```gherkin
Escenario: CA-06.1 Listado de mis pedidos
  Dado un comercio con pedidos
  Cuando entra a Mis pedidos
  Entonces ve cada pedido con estado, quien recibe, día prometido, visitas fallidas, código de seguimiento y el link "Ver lo que ve el cliente"

Escenario: CA-06.2 Aviso por cada cambio
  Cuando un pedido suyo cambia de estado
  Entonces aparece un aviso en la campana (y por mail si hay correo configurado)
  Y si una visita falla, el aviso dice el motivo y qué conviene hacer

Escenario: CA-06.3 Solo lo suyo
  Dado un pedido del comercio A
  Cuando el comercio B entra a Mis pedidos
  Entonces no lo ve

Escenario: CA-06.4 Texto con HTML
  Dado un pedido con HTML en la dirección o el nombre de quien recibe
  Cuando el comercio ve sus pedidos
  Entonces el texto se muestra tal cual y no se ejecuta ningún script
```

#### HU-07 · Ver la prueba de entrega o de la visita fallida · Should · BR-02, BR-04

**Como** comercio, **quiero** ver la foto del remito firmado y la evidencia de cada visita fallida, **para** resolver un reclamo de mi cliente. *Dónde:* Mis pedidos → Remito / Ver visitas

```gherkin
Escenario: CA-07.1 Ver el remito
  Dado un pedido ENTREGADO del comercio
  Cuando abre "Remito"
  Entonces ve la foto del remito firmado

Escenario: CA-07.2 Ver las visitas fallidas
  Dado un pedido con visitas fallidas
  Cuando abre "Ver visitas"
  Entonces ve de cada una el motivo, el comentario, quién la registró, la ubicación con link a Google Maps y hasta 3 fotos

Escenario: CA-07.3 Evidencia de otro comercio
  Dado un pedido del comercio A con remito y fotos
  Cuando otro comercio o un repartidor que no lo lleva pide el remito o una foto
  Entonces responde 404, como si no existiera
```

#### HU-08 · Ver mi stock en los depósitos · Could · BR-08

**Como** comercio, **quiero** ver cuánto tengo de cada producto en los depósitos de Rabbit, **para** saber qué puedo vender. *Dónde:* Mi stock

```gherkin
Escenario: CA-08.1 Stock por depósito
  Dado un comercio con stock consignado
  Cuando entra a Mi stock
  Entonces ve cada producto por depósito con disponible, reservado, libre y peso por unidad

Escenario: CA-08.2 Solo lectura
  Cuando el comercio está en Mi stock
  Entonces no puede editar cantidades: el stock lo carga el personal al recibir la mercadería

Escenario: CA-08.3 Solo su stock
  Dado stock del comercio A
  Cuando entra el comercio B
  Entonces no lo ve
```

#### HU-09 · Administrar mis puntos de picking · Could · BR-01

**Como** comercio, **quiero** cargar los lugares donde Rabbit puede retirar mercadería, **para** despachar lo que no tengo en sus depósitos. *Dónde:* Mis puntos de picking

```gherkin
Escenario: CA-09.1 Agregar un punto
  Cuando el comercio carga un punto con nombre y dirección
  Entonces queda activo y se puede usar en los pedidos de su ERP

Escenario: CA-09.2 Dar de baja y reactivar
  Dado un punto activo
  Cuando lo da de baja y después lo reactiva
  Entonces el punto vuelve a estar disponible

Escenario: CA-09.3 Puntos de otro comercio
  Cuando el comercio B abre la pantalla con "?idComercio=" del comercio A
  Entonces el parámetro se ignora y solo ve y modifica los suyos
```

#### HU-10 · Saber cuánto me cobra Rabbit · Could · BR-08, BR-03

**Como** comercio, **quiero** ver lo que Rabbit me cobra cada mes, **para** controlarlo y prever el gasto. *Dónde:* Mis liquidaciones

```gherkin
Escenario: CA-10.1 Meses cerrados
  Dado un comercio con liquidaciones
  Cuando entra a Mis liquidaciones
  Entonces ve por mes el abono, los envíos entregados, los kilos, el almacenamiento, el total y el estado del cobro

Escenario: CA-10.2 Vista previa del mes en curso
  Cuando mira el mes en curso
  Entonces ve lo que se cobraría hoy, calculado solo con sus entregas, sin cobrar nada

Escenario: CA-10.3 Solo lo suyo
  Cuando otro comercio entra a sus liquidaciones
  Entonces no ve las del comercio A
```

#### HU-11 · Medir cómo me entregan · Could · BR-09

**Como** comercio, **quiero** ver indicadores de mis entregas, **para** saber si el servicio cumple. *Dónde:* Mis indicadores

```gherkin
Escenario: CA-11.1 Indicadores del período
  Dado un comercio con pedidos en los últimos 30 días
  Cuando elige el período de 30 días
  Entonces ve el porcentaje a tiempo y al primer intento, las horas de recibido a entregado, los pedidos en espera, el costo por envío y la calificación promedio
  Y la comparación con el período anterior

Escenario: CA-11.2 Exportar
  Cuando descarga el CSV
  Entonces el archivo trae los mismos indicadores del período elegido

Escenario: CA-11.3 Sin datos
  Dado un período sin pedidos entregados o sin calificaciones
  Entonces los indicadores muestran "—" y no 0
```

### 3.4 Repartidor

#### HU-12 · Saber en qué orden hacer el recorrido · Must · BR-05

**Como** repartidor, **quiero** ver todas mis paradas en orden, **para** no perder tiempo armando el recorrido. *Dónde:* Mis entregas → Hoja de ruta recomendada

```gherkin
Escenario: CA-12.1 Orden de las paradas
  Dado un repartidor con retiros y entregas asignados
  Cuando abre su hoja de ruta
  Entonces primero van los retiros y después las entregas, cada una la más cercana a la anterior según OpenStreetMap

Escenario: CA-12.2 Dirección sin ubicar
  Dado una dirección que todavía no se pudo ubicar en el mapa
  Entonces esa parada se ordena por código postal

Escenario: CA-12.3 Datos de cada parada
  Entonces cada parada muestra la dirección, quien recibe, el teléfono que abre la llamada y las instrucciones que dejó quien recibe
  Y "Abrir en Google Maps" arma el recorrido en tramos de hasta 9 paradas

Escenario: CA-12.4 Solo lo suyo
  Cuando un repartidor abre su hoja de ruta
  Entonces no aparecen pedidos de otros repartidores
```

#### HU-13 · Marcar que retiré el pedido · Must · BR-05, BR-06

**Como** repartidor, **quiero** marcar que ya tengo el paquete, **para** que el comercio y el cliente sepan que está en camino. *Dónde:* Mis entregas → Ya retiré el pedido

```gherkin
Escenario: CA-13.1 Retiro
  Dado un pedido CONFIRMADO asignado al repartidor
  Cuando marca "Ya retiré el pedido"
  Entonces el pedido pasa a EN_CAMINO, se genera el código de entrega y se avisa al comercio y a quien recibe

Escenario: CA-13.2 Ubicación compartida
  Dado que el repartidor dio permiso de ubicación
  Mientras tenga entregas en curso
  Entonces su posición se ve en el mapa del personal y en el seguimiento de ese pedido

Escenario: CA-13.3 Pedido ajeno
  Dado un pedido asignado a otro repartidor
  Cuando intenta marcar el retiro
  Entonces se rechaza con "no está asignado a vos" y el pedido no cambia
```

#### HU-14 · Registrar la entrega con remito y código · Must · BR-02

**Como** repartidor, **quiero** registrar la entrega con la foto del remito firmado y el código de quien recibe, **para** dejar la prueba de que se entregó a quien compró. *Dónde:* Mis entregas → Entregar pedido *(actualizada por la ADR-055)*

```gherkin
Escenario: CA-14.1 Entrega correcta
  Dado un pedido EN_CAMINO asignado al repartidor
  Cuando carga la foto del remito (JPEG, PNG o WEBP, hasta 5 MB) y el código de 4 dígitos correcto
  Entonces el pedido pasa a ENTREGADO, se guarda la ubicación si el celular la dio
  Y si era contra entrega, el cobro se acredita y el vehículo libera su lugar

Escenario: CA-14.2 Sin foto o con un archivo que no es imagen
  Cuando intenta entregar sin foto o con un archivo que no es JPEG, PNG ni WEBP por su contenido
  Entonces no se registra la entrega

Escenario: CA-14.3 Código incorrecto
  Cuando ingresa un código incorrecto
  Entonces no se registra la entrega y el intento queda contado
  Y al 5.º intento fallido ni el código correcto sirve: la entrega la registra un administrador

Escenario: CA-14.4 Pedido ajeno
  Dado un pedido que no tiene asignado
  Cuando intenta entregarlo
  Entonces se rechaza
```

#### HU-15 · Registrar una entrega fallida · Should · BR-07

**Como** repartidor, **quiero** registrar por qué no pude entregar, **para** que Rabbit lo reintente o se lo devuelva al comercio. *Dónde:* Mis entregas → Devolver pedido

```gherkin
Escenario: CA-15.1 Primera visita fallida
  Dado un pedido EN_CAMINO con visitas disponibles
  Cuando elige un motivo de la lista (cliente ausente, dirección incorrecta, rechazo, paquete dañado, no entra en el vehículo, problema del repartidor, otro)
  Entonces la pantalla dice qué va a pasar, se guarda la ubicación
  Y el pedido vuelve al depósito en lista de espera para otro intento

Escenario: CA-15.2 Evidencia obligatoria según el motivo
  Cuando el motivo es "rechazo" u "otro" sin comentario, o "paquete dañado" sin foto
  Entonces no se registra la visita

Escenario: CA-15.3 Vuelve al comercio
  Dado un pedido con una visita fallida
  Cuando falla la segunda visita, o el cliente lo rechaza
  Entonces el pedido vuelve al comercio, el stock consignado se repone y el cobro se anula

Escenario: CA-15.4 Motivos que no son culpa del cliente
  Cuando el motivo es "no entra en el vehículo" o "problema del repartidor"
  Entonces no se gasta una visita
```

### 3.5 Personal de Rabbit (operador y administrador)

#### HU-16 · Dar de alta y mantener los comercios · Must · BR-01

**Como** personal, **quiero** registrar los comercios que contratan a Rabbit, **para** poder recibir sus pedidos. *Dónde:* Comercios

```gherkin
Escenario: CA-16.1 Alta con datos fiscales y puntos
  Cuando el personal carga un comercio con sus datos fiscales y sus puntos de picking
  Entonces el comercio queda activo y su ERP puede mandar pedidos

Escenario: CA-16.2 Baja en cascada y reactivación
  Dado un comercio activo
  Cuando lo da de baja
  Entonces se dan de baja sus puntos y el pedido que mande su ERP queda descartado con el motivo
  Y se puede reactivar

Escenario: CA-16.3 Eliminar (solo administrador)
  Dado un comercio dado de baja que solo tiene pedidos cancelados
  Cuando un administrador lo elimina
  Entonces se borra junto con sus pedidos cancelados
  Pero si tiene algo vivo u órdenes de recepción anunciadas, no se elimina
  Y un operador no ve la opción
```

#### HU-17 · Cargar y corregir el stock de un depósito · Must · BR-08

**Como** personal, **quiero** cargar la mercadería que cada comercio deja en un depósito, **para** que sus pedidos se puedan reservar. *Dónde:* Depósitos y stock

```gherkin
Escenario: CA-17.1 Alta de stock
  Cuando carga producto, cantidad, peso por unidad y medidas
  Entonces el stock queda disponible para los pedidos del comercio

Escenario: CA-17.2 Editar sin quedar debajo de lo reservado
  Dado un producto con unidades reservadas
  Cuando intenta bajar la cantidad por debajo de lo reservado
  Entonces se rechaza con el motivo

Escenario: CA-17.3 Eliminar (solo administrador)
  Cuando un administrador elimina un producto sin unidades reservadas ni pedidos en curso
  Entonces se borra y sus reservas quedan en el historial
  Pero si tiene reservas o un pedido en curso, no se elimina

Escenario: CA-17.4 Producto fuera de rango
  Cuando el peso o las medidas superan el tamaño grande
  Entonces no se acepta en consignación
```

#### HU-18 · Reservar stock para un pedido · Should · BR-05

**Como** personal, **quiero** apartar stock mientras armo un pedido, **para** que nadie más lo tome mientras tanto. *Dónde:* Reserva de stock · Historial de reservas

```gherkin
Escenario: CA-18.1 Reserva y confirmación
  Cuando reserva unidades disponibles
  Entonces la reserva dura 5 minutos y se puede extender
  Y al confirmarla se descuenta el stock

Escenario: CA-18.2 Vencimiento
  Dado una reserva que nadie confirma ni libera
  Cuando pasan los 5 minutos
  Entonces vence sola y el stock vuelve a estar disponible

Escenario: CA-18.3 Más de lo disponible
  Cuando intenta reservar más unidades de las libres
  Entonces se rechaza
  Y el historial muestra todas las reservas y cómo terminaron
```

#### HU-19 · Confirmar un pedido · Must · BR-03, BR-05

**Como** personal, **quiero** confirmar un pedido pendiente, **para** cobrarlo y asignarle quién lo lleve. *Dónde:* Pedidos → Confirmar

```gherkin
Escenario: CA-19.1 Confirmación con asignación y cobro
  Dado un pedido PENDIENTE a domicilio
  Cuando lo confirma antes de la hora de corte
  Entonces se asigna el primer repartidor de la zona al que le entra por kilos y bultos
  Y si es prepago, se cobra en el banco en el mismo momento
  Y el pedido pasa a CONFIRMADO

Escenario: CA-19.2 No entra en ningún vehículo o pasó la hora de corte
  Cuando no le entra a ningún repartidor, o ya pasó la hora de corte
  Entonces queda en lista de espera, sin cobrar
  Y se asigna solo cuando se libera lugar

Escenario: CA-19.3 El banco rechaza o no responde (sistema externo)
  Dado un pedido prepago
  Cuando el banco rechaza o no responde en 5 segundos
  Entonces el pedido sigue PENDIENTE, sin cobro, con el mensaje "El banco no respondió"
  Y tras 3 fallas seguidas el circuit breaker responde al instante sin llamar al banco

Escenario: CA-19.4 Falla después de cobrar
  Dado que el banco ya cobró
  Cuando la asignación falla después
  Entonces el cobro se reversa en el banco

Escenario: CA-19.5 Doble clic
  Cuando el botón Confirmar se presiona dos veces seguidas
  Entonces el pedido se confirma una sola vez y hay un solo cobro

Escenario: CA-19.6 Transición inválida
  Dado un pedido en un estado desde el que no se puede confirmar
  Cuando se intenta confirmar
  Entonces se rechaza (409 en la API)
```

#### HU-20 · Cancelar un pedido · Must · BR-03

**Como** personal, **quiero** cancelar un pedido que no va a salir, **para** devolver el stock y no cobrar de más. *Dónde:* Pedidos → Cancelar

```gherkin
Escenario: CA-20.1 Pedido pendiente
  Dado un pedido PENDIENTE
  Cuando cualquiera del personal lo cancela
  Entonces pasa a CANCELADO y el stock vuelve al depósito

Escenario: CA-20.2 Pedido confirmado (solo administrador)
  Dado un pedido CONFIRMADO, ya cobrado
  Cuando un administrador lo cancela
  Entonces el cobro se anula
  Pero un operador no puede cancelarlo

Escenario: CA-20.3 Pedido que salió a reparto
  Dado un pedido que salió a reparto
  Entonces el botón no aparece y el sistema dice "Ya salió a reparto: no se puede cancelar"
```

#### HU-21 · Derivar un pedido a un transportista · Could · BR-10

**Como** personal, **quiero** mandar un pedido con una empresa de envíos, **para** cubrir zonas o picos que la flota propia no alcanza. *Dónde:* Transportistas · Pedidos → Derivar

```gherkin
Escenario: CA-21.1 Cotizar y derivar
  Cuando pide "Cotizar"
  Entonces ve el precio y el plazo de cada transportista (el legado no cotiza)
  Y al elegir uno y derivar, si lo toma, el pedido queda CONFIRMADO con su código de seguimiento

Escenario: CA-21.2 Seguimiento automático
  Dado un pedido derivado
  Entonces Rabbit consulta el envío cada 15 segundos y mueve el pedido solo hasta ENTREGADO

Escenario: CA-21.3 El transportista rechaza o no responde (sistema externo)
  Cuando el transportista rechaza el envío o no responde en 5 segundos
  Entonces el pedido no se confirma
  Y si ya se había cobrado, el cobro se reversa
```

#### HU-22 · Despachar por zona · Could · BR-05, BR-10

**Como** personal, **quiero** ver los pendientes agrupados por zona y despacharlos juntos, **para** no decidir uno por uno. *Dónde:* Ruteo por zona

```gherkin
Escenario: CA-22.1 Zonas por código postal
  Cuando define una zona con un rango de códigos postales
  Entonces no se puede superponer con otra zona
  Y la zona dice quién reparte: repartidores propios o un transportista, con uno de respaldo

Escenario: CA-22.2 Despachar toda la zona
  Cuando presiona "Despachar toda la zona"
  Entonces asigna a los repartidores de la zona y, si no alcanzan, deriva al transportista de respaldo
  Y muestra qué pasó con cada pedido

Escenario: CA-22.3 Sin zona
  Dado un pedido sin código postal reconocible
  Entonces queda en "Sin zona" y se resuelve a mano
  Y los retiros en depósito no aparecen en Ruteo
```

#### HU-23 · Administrar la flota de repartidores · Must · BR-05

**Como** personal, **quiero** cargar a los repartidores con su zona y lo que entra en su vehículo, **para** que la asignación sea realista. *Dónde:* Repartidores

```gherkin
Escenario: CA-23.1 Alta con zona y capacidad
  Cuando carga un repartidor con zona y capacidad en kilos y bultos
  Entonces queda disponible para la asignación

Escenario: CA-23.2 Varios pedidos mientras entren
  Dado un repartidor con pedidos asignados
  Cuando se confirma otro pedido que entra en lo que le queda
  Entonces se le asigna también
  Pero si supera los kilos o los bultos, se prueba con el siguiente

Escenario: CA-23.3 Eliminar (solo administrador)
  Cuando un administrador elimina un repartidor sin entregas en curso ni cuenta de usuario
  Entonces se borra
  Pero si tiene entregas en curso o cuenta, no se elimina
```

#### HU-24 · Seguir las entregas del día · Should · BR-09

**Como** personal, **quiero** ver qué está saliendo y dónde está cada repartidor, **para** anticiparme a los problemas. *Dónde:* Inicio · Entregas en curso · Mapa de repartidores

```gherkin
Escenario: CA-24.1 Tablero de entregas
  Cuando entra a Entregas en curso
  Entonces ve cada entrega con su retiro y su destino, y el recorrido de cada repartidor en Google Maps

Escenario: CA-24.2 Mapa
  Cuando abre el mapa
  Entonces ve la última ubicación de los repartidores que están repartiendo
  Y los nombres se muestran como texto, aunque tengan HTML

Escenario: CA-24.3 Requiere atención
  Cuando entra al Inicio del personal
  Entonces ve cuántos pedidos están en lista de espera, por confirmar, devueltos, retiros listos, liquidaciones sin cobrar y órdenes de recepción anunciadas

Escenario: CA-24.4 Visitas y avisos en Pedidos
  Cuando abre Pedidos
  Entonces ve las visitas fallidas con su evidencia y los avisos que se mandaron a cada cliente
```

#### HU-25 · Medir la operación · Could · BR-09

**Como** personal, **quiero** indicadores de toda la red, por comercio y por repartidor, **para** saber dónde mejorar. *Dónde:* Indicadores

```gherkin
Escenario: CA-25.1 Toda la red
  Cuando entra a Indicadores con el período de 30 días
  Entonces ve los mismos indicadores que el comercio (HU-11) para toda la red

Escenario: CA-25.2 Por comercio y por repartidor
  Cuando filtra por un comercio
  Entonces ve solo los de ese comercio
  Y el detalle por repartidor trae a tiempo, primer intento y calificación promedio

Escenario: CA-25.3 Gráficos accesibles
  Entonces cada gráfico tiene su versión en tabla y los motivos de las visitas fallidas
```

#### HU-26 · Probar un pedido sin un ERP · Could · BR-01

**Como** personal, **quiero** cargar un pedido como si viniera del ERP, **para** probar o atender un comercio que todavía no se integró. *Dónde:* Pedidos → Simular un pedido del ERP

```gherkin
Escenario: CA-26.1 Mismo camino que la API
  Cuando carga un pedido con los mismos datos que pide la API, incluido el email de quien recibe
  Entonces sigue el mismo camino que un pedido real (sincronización, stock, seguimiento)

Escenario: CA-26.2 Validaciones iguales
  Cuando falta el email o un campo tiene formato inválido
  Entonces se rechaza con el mismo mensaje que da la API

Escenario: CA-26.3 Comercio dado de baja
  Cuando simula un pedido para un comercio dado de baja
  Entonces el pedido queda descartado con el motivo
```

#### HU-27 · Administrar las cuentas · Must · BR-04

**Como** administrador, **quiero** crear y dar de baja cuentas, **para** controlar quién entra a Rabbit. *Dónde:* Usuarios (solo administrador)

```gherkin
Escenario: CA-27.1 Alta por tipo
  Cuando crea una cuenta de operador, administrador, comercio, repartidor o ERP
  Entonces las de comercio, repartidor y ERP quedan asociadas a su comercio o repartidor
  Y la contraseña se guarda con bcrypt

Escenario: CA-27.2 Baja, reactivación y restablecer
  Cuando da de baja una cuenta
  Entonces deja de poder entrar en todos los servidores
  Y se puede reactivar con una contraseña nueva, restablecer la contraseña o eliminar

Escenario: CA-27.3 Protecciones
  Cuando intenta darse de baja a sí mismo o dejar el sistema sin administradores activos
  Entonces se rechaza

Escenario: CA-27.4 Fuerza bruta
  Cuando alguien se equivoca 5 veces seguidas de contraseña con una cuenta
  Entonces el 6.º intento se rechaza durante 15 minutos con el mismo mensaje exista o no el usuario
  Y la API del ERP responde 429 en el mismo caso

Escenario: CA-27.5 Solo el administrador
  Cuando un operador abre Usuarios
  Entonces no puede entrar a la pantalla
```

#### HU-28 · Liquidar el mes a los comercios · Must · BR-03

**Como** administrador, **quiero** cobrarle a cada comercio el servicio del mes, **para** facturar lo que Rabbit hizo. *Dónde:* Liquidaciones (solo administrador)

```gherkin
Escenario: CA-28.1 Liquidación del mes cerrado
  Dado un mes cerrado
  Cuando se liquida (a mano o el timer del día 1 a las 6)
  Entonces a cada comercio activo se le calcula abono + cargo por envío entregado + cargo por kilo entregado + almacenamiento
  Y se cobra por el banco

Escenario: CA-28.2 Mes en curso
  Cuando mira el mes en curso
  Entonces ve una vista previa sin guardar ni cobrar
  Y no puede liquidarlo

Escenario: CA-28.3 El banco rechaza o no responde (sistema externo)
  Cuando el banco rechaza el cobro de una liquidación
  Entonces queda RECHAZADA y el administrador la puede reintentar
  Y si no responde, queda PENDIENTE y el timer la reintenta
```

### 3.6 Historias agregadas (ADR-052 a 059)

#### HU-29 · Retirar el pedido en un depósito · Should · BR-11

**Como** ERP del comercio y como personal, **quiero** que quien compró pueda retirar el pedido en un depósito, **para** no pagar ni esperar un reparto. *Dónde:* `tipoEntrega = RETIRO_EN_DEPOSITO` · Pedidos → Entregar en depósito

```gherkin
Escenario: CA-29.1 Alta de un retiro
  Cuando el ERP manda un pedido con tipoEntrega RETIRO_EN_DEPOSITO y stock consignado en un solo depósito
  Entonces el pedido no pide dirección, no lleva repartidor ni pasa por Ruteo
  Y al confirmarlo queda "listo para retirar"

Escenario: CA-29.2 Entrega con el código
  Dado un retiro CONFIRMADO
  Cuando el personal ingresa el código de seguimiento (sin importar mayúsculas ni espacios)
  Entonces el pedido pasa directo a ENTREGADO sin pasar por EN_CAMINO

Escenario: CA-29.3 Productos en distintos depósitos o con punto de picking
  Cuando los productos están en distintos depósitos o el pedido usa un punto de picking
  Entonces responde 422 explicando por qué

Escenario: CA-29.4 Operaciones de reparto
  Dado un retiro
  Cuando se intenta despachar, derivar, cotizar o devolver al depósito
  Entonces se rechaza con un mensaje claro
```

#### HU-30 · Calificar la entrega · Could · BR-11

**Como** cliente, **quiero** calificar cómo fue la entrega, **para** que Rabbit sepa si el servicio cumple. *Dónde:* Seguí tu pedido (pedido entregado)

```gherkin
Escenario: CA-30.1 Calificar
  Dado un pedido ENTREGADO
  Cuando quien recibe elige de 1 a 5 estrellas y, si quiere, un comentario de hasta 500 caracteres
  Entonces se guarda y el seguimiento muestra lo calificado sin el formulario

Escenario: CA-30.2 Una sola vez
  Dado un pedido ya calificado
  Cuando se intenta calificar de nuevo
  Entonces no se acepta

Escenario: CA-30.3 Pedido no entregado o datos inválidos
  Cuando el pedido no está ENTREGADO, o las estrellas no están entre 1 y 5
  Entonces no se acepta la calificación
  Y el comentario se muestra siempre escapado y no va al log
```

#### HU-31 · Cambiar algo de la entrega · Should · BR-07

**Como** cliente, **quiero** reprogramar el día, dejar instrucciones o autorizar a otra persona, **para** que la entrega no falle. *Dónde:* Seguí tu pedido → "¿Querés cambiar algo de la entrega?"

```gherkin
Escenario: CA-31.1 Reprogramar
  Dado un pedido a domicilio PENDIENTE, o CONFIRMADO con un repartidor de Rabbit
  Cuando elige otro día hasta 7 días desde hoy y no antes de lo prometido
  Entonces cambia la fecha prometida y el personal ve que fue reprogramado

Escenario: CA-31.2 Instrucciones y persona autorizada
  Cuando deja instrucciones (hasta 200 caracteres) o el nombre de una persona autorizada (hasta 100)
  Entonces el repartidor las ve en Mis entregas y el personal en Pedidos
  Y la persona autorizada igual necesita el código de entrega para recibir

Escenario: CA-31.3 Cuándo no se puede
  Cuando el pedido está EN_CAMINO o resuelto, es un retiro, o lo lleva un transportista
  Entonces el formulario no se ofrece y el servicio lo rechaza
  Y después de 5 cambios no se aceptan más
```

#### HU-32 · Etiquetar los bultos y retirar escaneando · Could · BR-05

**Como** personal y como repartidor, **quiero** una etiqueta con QR por bulto, **para** identificar cada caja y marcar el retiro con la cámara. *Dónde:* Pedidos → Etiquetas · escaneo del QR

```gherkin
Escenario: CA-32.1 Imprimir etiquetas
  Dado un pedido de 3 bultos
  Cuando el personal abre "Etiquetas"
  Entonces se arman 3 etiquetas de 100×60 mm con QR, "BULTO n DE 3", código de seguimiento, dirección, CP y quien recibe

Escenario: CA-32.2 Retiro escaneando
  Dado un repartidor con sesión y un pedido suyo por retirar
  Cuando escanea el QR
  Entonces ve el pedido y un botón que marca el retiro (con los mismos controles de HU-13)

Escenario: CA-32.3 QR de un pedido ajeno o sin sesión
  Cuando un repartidor escanea un pedido que no es suyo
  Entonces no se muestra nada de ese pedido
  Y cualquier persona sin sesión llega al seguimiento público
```

#### HU-33 · Anunciar mercadería al depósito · Should · BR-08

**Como** comercio, **quiero** anunciar la mercadería que voy a mandar a un depósito, **para** que el depósito sepa qué esperar y quede registro. *Dónde:* Mi stock → Anunciar mercadería

```gherkin
Escenario: CA-33.1 Anunciar
  Cuando anuncia de 1 a 50 productos sin repetir, con cantidad de 1 a 100.000 y peso por unidad, a un depósito
  Entonces la orden queda ANUNCIADA a nombre de su comercio

Escenario: CA-33.2 Cancelar
  Dado una orden ANUNCIADA propia
  Cuando la cancela
  Entonces queda CANCELADA
  Pero no puede cancelar órdenes de otro comercio

Escenario: CA-33.3 Datos inválidos
  Cuando repite un producto, supera los límites o el producto es fuera de rango
  Entonces la orden no se crea
```

#### HU-34 · Registrar lo que llegó al depósito · Should · BR-08

**Como** personal, **quiero** registrar lo que llegó de cada orden, línea por línea, **para** sumar al stock solo lo recibido y dejar las diferencias a la vista. *Dónde:* Recepción de mercadería

```gherkin
Escenario: CA-34.1 Llegó todo
  Dado una orden ANUNCIADA
  Cuando registra que en cada línea llegó lo esperado
  Entonces la orden queda RECIBIDA y el stock del comercio suma esas cantidades

Escenario: CA-34.2 Faltó algo
  Cuando en alguna línea llegó menos
  Entonces la orden queda PARCIAL, se suma solo lo que llegó
  Y las dos pantallas muestran "Faltaron N" (o "Llegaron N de más")

Escenario: CA-34.3 No llegó nada o doble registro
  Cuando informa que no llegó nada en ninguna línea
  Entonces no se registra (eso es cancelar)
  Y dos registros simultáneos de la misma orden no suman el stock dos veces
```

#### HU-35 · Cobrar el almacenamiento · Could · BR-11, BR-03

**Como** administrador, **quiero** cobrarle al comercio por unidad y por día según el tamaño de lo que guarda, **para** que el lugar que ocupa tenga su precio. *Dónde:* Depósitos y stock (medidas) · Liquidaciones

```gherkin
Escenario: CA-35.1 Categoría deducida
  Cuando un producto tiene peso y medidas
  Entonces su categoría (pequeño, mediano o grande) se deduce y manda la más grande que le toque por cualquiera de sus datos

Escenario: CA-35.2 Cargo en la liquidación
  Dado la foto diaria de unidades por categoría
  Cuando se liquida el mes
  Entonces el cargo es unidades-día × tarifa de cada categoría ($10, $30 y $90 por defecto) y aparece como línea propia

Escenario: CA-35.3 Sin medidas
  Dado un producto sin medidas cargadas
  Entonces cuenta como mediano (o más, si lo pesa) y se marca "medidas sin cargar"
```

## 4. Reglas de negocio (RN-xx)

| Id | Regla | HU | Dónde se implementa |
|---|---|---|---|
| RN-01 | Ciclo de vida del pedido: PENDIENTE → CONFIRMADO → EN_CAMINO → ENTREGADO, con CANCELADO y devolución al comercio. Toda transición inválida se rechaza (409 en la API). Un retiro va de CONFIRMADO a ENTREGADO sin EN_CAMINO | HU-19, 20, 29 | `EstadoPedido` (patrón State) |
| RN-02 | Hora de corte 07:00: antes, el pedido se promete para hoy; después, para mañana. Sin código postal no se promete fecha | HU-01, 04, 19 | `HoraDeCorte` |
| RN-03 | El email de quien recibe es obligatorio; nombre y teléfono son opcionales (teléfono de 6 a 30 caracteres válidos) | HU-01, 05 | `PedidoExternoRequest`, `Destinatarios` |
| RN-04 | Alta idempotente: misma Idempotency-Key y mismo cuerpo devuelven el mismo pedido; misma clave con otro cuerpo, 422 | HU-01 | `HuellaDePedido` |
| RN-05 | El comercio de un pedido sale de la cuenta del ERP, nunca del cuerpo; lo ajeno responde 404 | HU-01, 02, 03 | `PedidoService`, `IContextoUsuario` |
| RN-06 | Asignación: primero los repartidores de la zona; gana el primero al que le entra por kilos y bultos; puede llevar varios pedidos | HU-19, 23 | `AsignacionPorCapacidad` |
| RN-07 | Lista de espera: si no entra en ningún vehículo o pasó la hora de corte, el pedido queda PENDIENTE en espera, sin cobrar, y se asigna solo por orden de llegada | HU-19 | `AsignadorDeEspera` |
| RN-08 | Prepago: se cobra en el banco al confirmar; si algo falla después de cobrar, se reversa. Contra entrega: se acredita al entregar | HU-19, 14 | `PagoService`, `ReversasBancarias` |
| RN-09 | Banco: timeout de 5 s; tras 3 fallas seguidas el circuito queda abierto 30 s y responde NO_DISPONIBLE sin llamar | HU-19, 28 | `BancoClient`, `CircuitBreakerBanco` |
| RN-10 | Cancelación: el ERP solo cancela pendientes; un confirmado solo lo cancela un administrador (anula el cobro); nadie cancela lo que salió a reparto | HU-03, 20 | `ReglaDeCancelacion` |
| RN-11 | Entrega: foto del remito obligatoria (JPEG, PNG o WEBP por contenido, hasta 5 MB); con la flota propia, además el código de 4 dígitos; 5 intentos de código por pedido | HU-14 | `Remitos`, `CodigoDeEntrega` |
| RN-12 | Visitas: hasta 2 visitas; a la 2.ª fallida o con rechazo vuelve al comercio (stock repuesto, cobro anulado). "No entra en el vehículo" y "problema del repartidor" no gastan visita | HU-15 | `IntentosDeEntrega` |
| RN-13 | Evidencia de la visita: hasta 3 fotos; comentario obligatorio con "rechazo" u "otro"; al menos una foto con "paquete dañado" | HU-15 | `EvidenciaDeVisita` |
| RN-14 | Reserva de stock: dura 5 minutos, extensible; vence sola; no se reserva más de lo libre | HU-18 | `InventarioService` (stateful) |
| RN-15 | Stock: la cantidad no puede quedar debajo de lo reservado; no se elimina un producto con reservas o pedidos en curso | HU-17 | `ReglasDeStock` |
| RN-16 | Liquidación: solo meses cerrados; abono $15.000 + $1.200 por envío + $80 por kilo (por defecto) + almacenamiento; se cobra por el banco | HU-28, 10 | `TarifaMensual`, `LiquidacionMensual` |
| RN-17 | Almacenamiento: categoría por peso y medidas; tarifas por unidad y día $10, $30 y $90; sin medidas cuenta como mediano; fuera de rango no se acepta | HU-35, 17 | `CategoriaDeTamano`, `UnidadesDia` |
| RN-18 | Opciones de quien recibe: reprogramar hasta 7 días y no antes de lo prometido; instrucciones ≤ 200; autorizado ≤ 100; máximo 5 cambios | HU-31 | `ReglaDeOpcionesDeEntrega` |
| RN-19 | Calificación: solo ENTREGADO, 1 a 5 estrellas, comentario ≤ 500, una por pedido | HU-30 | `ReglaDeCalificacion` |
| RN-20 | Recepción: 1 a 50 productos sin repetir, cantidad 1 a 100.000; se suma solo lo recibido; PARCIAL si faltó algo | HU-33, 34 | `ReglaDeRecepcion` |
| RN-21 | Cuentas: no hay alta pública; 5 intentos fallidos bloquean 15 minutos; no se puede dar de baja la propia cuenta ni al último administrador | HU-27 | `UsuarioService`, `LimiteDeIntentos` |
| RN-22 | Transportistas: el estado del envío se consulta cada 15 s; el pedido avanza solo hasta ENTREGADO | HU-21 | `SeguimientoDeEnvios` |
| RN-23 | Zonas: rangos de código postal sin superposición; cada zona define quién reparte y un transportista de respaldo | HU-22 | `ZonaService` |

## 5. Requisitos no funcionales (ISO/IEC 25010:2023)

Cada RNF tiene una métrica y la evidencia que la mide hoy. Los valores son de la última corrida del CI sobre `main` (02/11/2026).

| Id | Característica (25010) | Requisito | Métrica | Medido | Evidencia |
|---|---|---|---|---|---|
| RNF-01 | Eficiencia de desempeño · comportamiento temporal | Los endpoints críticos responden rápido con carga | p95 < 500 ms con 50 usuarios durante 5 min; errores < 1 % | p95: alta 118 ms, consulta 102 ms, seguimiento 5 ms; 0 % errores | Workflow Carga (k6) |
| RNF-02 | Eficiencia · capacidad | El consumidor de la cola escala | Throughput con N consumidores | 6,3× con 8 consumidores vs. 1 | `DESAFIOS-OPCIONALES.md` |
| RNF-03 | Fiabilidad · tolerancia a fallos | Una falla del banco, el correo o un transportista no cuelga la operación ni deja estados inconsistentes | Timeout 5 s; circuit breaker; reversa | Cumple | `CircuitBreakerBancoTest`, `BancoClientTest`, SEC-07 |
| RNF-04 | Fiabilidad · recuperabilidad | Un pedido no se pierde si la mensajería falla | Polling de respaldo | Cumple | ADR-001, `PedidosEnSegundoPlanoTest` |
| RNF-05 | Seguridad · confidencialidad | Cada actor ve solo lo suyo; datos personales protegidos (Ley 25.326) | 10/10 criterios SEC; 0 alertas altas de ZAP | 10/10 CUMPLE; 0 altas | Matriz SEC, ZAP baseline y API |
| RNF-06 | Seguridad · autenticidad | Contraseñas con hash adaptativo y freno a la fuerza bruta | bcrypt; bloqueo tras 5 intentos | bcrypt costo 10; 429 al 6.º intento | SEC-01, SEC-02 |
| RNF-07 | Seguridad · integridad | Toda la comunicación cifrada | Solo HTTPS (TLS ≥ 1.2) con HSTS | Cumple | SEC-05 |
| RNF-08 | Compatibilidad · interoperabilidad | API REST y SOAP según sus contratos | 0 diferencias contra OpenAPI y WSDL | Cumple | Job Contrato (Schemathesis, zeep) |
| RNF-09 | Capacidad de interacción · accesibilidad | WCAG 2.2 AA | 0 violaciones serias o críticas de axe; Lighthouse ≥ 90 | 0 violaciones en 8 pantallas; Lighthouse ≥ 90 | Job E2E y accesibilidad |
| RNF-10 | Capacidad de interacción · adaptabilidad | Responsive en celular, tablet y escritorio | Sin scroll horizontal a 360, 768 y 1280 px | Cumple en 8 pantallas | Job E2E y accesibilidad |
| RNF-11 | Mantenibilidad · capacidad de prueba | Pruebas automatizadas efectivas | Cobertura negocio ≥ 80 % líneas / 70 % ramas; mutación ≥ 60 % | 97,8 % / 91,9 %; 88 % | JaCoCo, PIT |
| RNF-12 | Mantenibilidad · modularidad | Código sin deuda grave | Quality Gate aprobado; duplicación ≤ 3 % | Aprobado; 0 % | SonarCloud |
| RNF-13 | Portabilidad · instalabilidad | Se levanta desde cero con un comando | `docker compose up` en ≤ 15 min | ~1,5 min en el CI | README, job Configuración del .env |

## 6. Prototipo

El prototipo navegable en Figma está pendiente de enlace: *(completar con el link de Figma)*. Mientras tanto, la aplicación desplegada con `docker compose up` cubre las 35 historias y es la referencia visual; el [manual de usuario](../manual/) muestra cada pantalla con capturas.

## 7. Matriz de trazabilidad BR → HU → CA → CP

Cada criterio de aceptación CA-xx.y tiene su caso de prueba CP-xx.y con el mismo número. La columna de tests dice qué prueba automatizada lo cubre. Los criterios que no tienen test automático se prueban a mano siguiendo el escenario, y así se marcan.

| BR | HU | Prioridad | CA / CP | Test automatizado o evidencia |
|---|---|---|---|---|
| BR-01 | HU-01 | Must | 01.1–01.5 | `ApiRestIT` (alta 201, idempotencia, 422 misma clave, 400 sin clave y por campo, campos ignorados), `PedidoExternoRequestTest`, `HuellaDePedidoTest`, `NumerosEstrictosTest`, `MejorasUltimaMillaIT` (fecha prometida, email, teléfono), `PedidosEnSegundoPlanoTest` (01.5), Schemathesis |
| BR-01 | HU-02 | Must | 02.1–02.4 | `ApiRestIT` (404 inexistente, otro comercio 404, 304/ETag), `EtagDelPedidoExternoTest`, `PedidosExternosResourceTest` |
| BR-01 | HU-03 | Should | 03.1–03.4 | `ApiRestIT` (cancelar idempotente, 412), `ReglaDeCancelacionTest`, `TransicionesInvalidasDevuelven409Test` |
| BR-06, BR-04 | HU-04 | Must | 04.1–04.4 | `ApiRestIT` (seguimiento sin login, inyección), `MejorasUltimaMillaIT.elSeguimientoPublicoNoMuestraAQuienRecibe`, `EstimacionDeLlegadaTest`, E2E-3 |
| BR-06 | HU-05 | Should | 05.1–05.4 | `NotificacionesServiciosTest`, `TextosAlDestinatarioTest`, `AvisosDeFallaTest`, `ConfiguracionSmtpTest`, `DatosPersonalesTest` |
| BR-06, BR-08 | HU-06 | Should | 06.1–06.4 | E2E-4, E2E-5, SEG-1, `TextosAlComercioTest` |
| BR-02, BR-04 | HU-07 | Should | 07.1–07.3 | `RemitosTest`, `EvidenciaDeVisitaTest`; 07.3 probado en vivo el 02/11 (404 a otro comercio y repartidor) |
| BR-08 | HU-08 | Could | 08.1–08.3 | `ConsultaStockServiceTest`; manual |
| BR-01 | HU-09 | Could | 09.1–09.3 | `ComercioServiceTest`; 09.3 probado en vivo el 02/11 |
| BR-08, BR-03 | HU-10 | Could | 10.1–10.3 | `LiquidacionesTest`, `TarifaMensualTest` |
| BR-09 | HU-11 | Could | 11.1–11.3 | `CalculoDeIndicadoresTest`, `IndicadoresDTOTest` |
| BR-05 | HU-12 | Must | 12.1–12.4 | `OrdenDeRecorridoTest`, `NominatimTest`, `RuteoServiciosTest` |
| BR-05, BR-06 | HU-13 | Must | 13.1–13.3 | `PedidoServiceTest`, `CodigoDeEntregaTest`, `EstadoPedidoTest` |
| BR-02 | HU-14 | Must | 14.1–14.4 | `RemitosTest`, `CodigoDeEntregaTest`, `PedidoServiceTest`, `PagoServiceTest` (acreditación contra entrega) |
| BR-07 | HU-15 | Should | 15.1–15.4 | `IntentosDeEntregaTest`, `EvidenciaDeVisitaTest`, `PagoServiceTest` (anulación) |
| BR-01 | HU-16 | Must | 16.1–16.3 | `ComercioServiceTest`, E2E-2 |
| BR-08 | HU-17 | Must | 17.1–17.4 | `ReglasDeStockTest`, `MovimientosDeStockTest`, `CategoriaDeTamanoTest` |
| BR-05 | HU-18 | Should | 18.1–18.3 | `MovimientosDeStockTest`; manual (vencimiento a los 5 min) |
| BR-03, BR-05 | HU-19 | Must | 19.1–19.6 | `PedidoServiceTest`, `AsignacionPorCapacidadTest`, `HoraDeCorteTest`, `CircuitBreakerBancoTest`, `BancoClientTest`, `PagoServiceTest`, `TransicionesInvalidasDevuelven409Test`, E2E-2; 19.3 y 19.5 probados en vivo el 02/11 |
| BR-03 | HU-20 | Must | 20.1–20.3 | `ReglaDeCancelacionTest`, `PedidoServiceTest`, `PagoServiceTest` |
| BR-10 | HU-21 | Could | 21.1–21.3 | `TransportistaServiceTest`, `SeguimientoYCancelacionesDeEnviosTest`, `SimuladorDeEnviosTest` |
| BR-05, BR-10 | HU-22 | Could | 22.1–22.3 | `RuteoServiciosTest`, `CodigosPostalesTest` |
| BR-05 | HU-23 | Must | 23.1–23.3 | `RepartidorServiceTest`, `CapacidadRepartidorTest`, `AsignacionPorCapacidadTest` |
| BR-09 | HU-24 | Should | 24.1–24.4 | SEG-2 (mapa como texto); manual |
| BR-09 | HU-25 | Could | 25.1–25.3 | `CalculoDeIndicadoresTest` |
| BR-01 | HU-26 | Could | 26.1–26.3 | `PedidoServiceTest`; manual |
| BR-04 | HU-27 | Must | 27.1–27.5 | `UsuarioServiceTest`, `ClavesTest`, `LimiteDeIntentosTest`, `LoginBeanTest`, `SeguridadIT` (429), E2E-1 |
| BR-03 | HU-28 | Must | 28.1–28.3 | `LiquidacionesTest`, `TarifaMensualTest`, `PagoServiceTest` |
| BR-11 | HU-29 | Should | 29.1–29.4 | `ReglaDeRetiroTest`, `EstadoDelPedidoTest` |
| BR-11 | HU-30 | Could | 30.1–30.3 | `ReglaDeCalificacionTest` |
| BR-07 | HU-31 | Should | 31.1–31.3 | `ReglaDeOpcionesDeEntregaTest` |
| BR-05 | HU-32 | Could | 32.1–32.3 | `BultosTest`; manual (impresión y escaneo) |
| BR-08 | HU-33 | Should | 33.1–33.3 | `ReglaDeRecepcionTest`, `OrdenDeRecepcionDTOTest` |
| BR-08 | HU-34 | Should | 34.1–34.3 | `ReglaDeRecepcionTest`, `MovimientosDeStockTest` |
| BR-11, BR-03 | HU-35 | Could | 35.1–35.3 | `CategoriaDeTamanoTest`, `UnidadesDiaTest`, `TarifaMensualTest` |

**Cobertura de la matriz:** los 11 BR tienen al menos una HU; las 35 HU tienen entre 3 y 6 criterios (123 en total); todos los criterios tienen un caso de prueba. Los criterios de seguridad de la matriz SEC-01 a SEC-10 (Clase 11) se trazan a HU-01, 02, 04, 05, 07, 14, 19 y 27.

**Para la matriz del QA (entregable 2):** esta tabla se extiende con el resultado de la última ejecución, la evidencia (run del CI o captura) y el defecto si lo hay.
