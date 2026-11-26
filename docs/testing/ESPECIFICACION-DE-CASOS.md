# Especificación de casos de prueba

Entregable 3 de los *Criterios de Testing* (Clase 15, estructura de ISO/IEC/IEEE 29119-3). Los casos salen de los
criterios de aceptación en Gherkin de [DOCUMENTACION-FUNCIONAL.md](../funcional/DOCUMENTACION-FUNCIONAL.md): cada
`CA-xx.y` tiene su `CP-xx.y` con el mismo número. **Dado** es la precondición y los datos, **Cuando** son los pasos y
**Entonces** es el resultado esperado. Un guion largo («—») significa que el escenario original no tiene ese paso.

## Cómo leer las columnas

- **Prioridad:** la de la HU (MoSCoW): Must = Alta, Should = Media, Could = Baja.
- **Tipo:** *Positivo* (camino feliz), *Negativo* (el sistema tiene que rechazar, fallar bien o no permitir) o *Borde*
  (límite, repetición o concurrencia). Es una clasificación inicial hecha por reglas sobre el texto del caso: el QA
  la tiene que revisar. Se eligió una regla conservadora (un caso dudoso queda como positivo).
- **Automatizado:** *Sí* si la HU tiene pruebas automatizadas, *Parcial* si además tiene pasos manuales y *No* si es
  solo manual. Se decide por HU, no por criterio: la clase exacta está en la
  [matriz de trazabilidad](MATRIZ-DE-TRAZABILIDAD.md).

## Resumen

- Casos: **130**. Positivos: **73**. Negativos: **48**. De borde: **9**.
- Negativos o de borde: **57 de 130 = 44 %** (el mínimo exigido es 40 %).

## Casos

| Id | HU | Caso | Precondición y datos (Dado) | Pasos (Cuando) | Resultado esperado (Entonces) | Prioridad | Tipo | Automatizado |
|---|---|---|---|---|---|---|---|---|
| CP-01.1 | HU-01 | Alta correcta con día prometido | un ERP autenticado de un comercio activo | envía un pedido válido con líneas, importe, medio de pago, dirección, código postal y email de quien recibe | responde 201 con Location, el número del pedido y la fecha prometida y no espera a que se reserve el stock | Alta (Must) | Positivo | Sí |
| CP-01.2 | HU-01 | Reintento con la misma Idempotency-Key | un pedido ya enviado con la clave "K1" | el ERP reenvía el mismo cuerpo con la clave "K1" | recibe el mismo pedido y no se crea otro y si reenvía otro cuerpo con la clave "K1", responde 422 | Alta (Must) | Negativo | Sí |
| CP-01.3 | HU-01 | Dato faltante o con formato inválido | un ERP autenticado | envía un pedido sin email de quien recibe, sin Idempotency-Key o con un campo mal formado | responde 400 en Problem Details y dice qué campo corregir y si el código postal no es argentino o el punto de picking no existe, responde 422 | Alta (Must) | Negativo | Sí |
| CP-01.4 | HU-01 | El comercio sale de la cuenta, no del pedido | un ERP del comercio A | manda en el cuerpo un idComercio, un estado o un importe que no le corresponden | esos campos se ignoran y el pedido queda a nombre del comercio A | Alta (Must) | Positivo | Sí |
| CP-01.5 | HU-01 | La cola de mensajería no está disponible (sistema externo) | que el mensaje del pedido no llega a procesarse por la cola | pasa el polling de respaldo | el pedido se sincroniza igual y no se pierde | Alta (Must) | Negativo | Sí |
| CP-02.1 | HU-02 | Pedido sincronizado | un pedido del ERP ya procesado | lo consulta | responde 200 con "Sincronizado", el estado, el código de seguimiento, las visitas fallidas y si está en lista de espera y la respuesta trae un ETag | Alta (Must) | Positivo | Sí |
| CP-02.2 | HU-02 | Pedido descartado | un pedido que no se pudo procesar por falta de stock o comercio dado de baja | lo consulta | responde "Descartado" con el motivo | Alta (Must) | Positivo | Sí |
| CP-02.3 | HU-02 | Pedido de otro comercio | un pedido del comercio A | lo consulta el ERP del comercio B | responde 404, igual que para un id inexistente, sin ningún dato del pedido | Alta (Must) | Negativo | Sí |
| CP-02.4 | HU-02 | Consulta condicional | un ETag de una consulta anterior | consulta con If-None-Match y el pedido no cambió | responde 304 sin cuerpo | Alta (Must) | Positivo | Sí |
| CP-03.1 | HU-03 | Cancelar un pedido pendiente | un pedido PENDIENTE del ERP | lo cancela | el pedido pasa a CANCELADO y el stock reservado vuelve al depósito y repetir la cancelación responde lo mismo | Media (Should) | Positivo | Sí |
| CP-03.2 | HU-03 | Pedido ya confirmado | un pedido CONFIRMADO (con cobro y repartidor) | el ERP lo intenta cancelar | responde 409 y el pedido lo tiene que cancelar el personal de Rabbit | Media (Should) | Negativo | Sí |
| CP-03.3 | HU-03 | Pedido que ya salió a reparto | un pedido que salió a reparto, aunque haya vuelto al depósito para otra visita | el ERP lo intenta cancelar | responde 409 con el motivo y el pedido no cambia | Media (Should) | Negativo | Sí |
| CP-03.4 | HU-03 | ETag desactualizado | un ETag viejo del pedido | cancela con If-Match y ese ETag | responde 412 con el ETag actual y no cancela | Media (Should) | Negativo | Sí |
| CP-04.1 | HU-04 | Pedido pendiente o confirmado | un pedido con código RB-XXXXXXXXXX | quien recibe lo consulta sin iniciar sesión | ve el estado, la línea de tiempo y el día prometido ("Llega hoy", "Llega mañana") | Alta (Must) | Positivo | Sí |
| CP-04.2 | HU-04 | Pedido en camino | un pedido EN_CAMINO con un repartidor de Rabbit | lo consulta | ve en qué parada va ("Sos la parada 2 de 3"), la franja horaria y el mapa | Alta (Must) | Positivo | Sí |
| CP-04.3 | HU-04 | Código inexistente | — | consulta un código que no existe | ve "No encontramos ese código" (la API responde 404 en Problem Details) y un código válido escrito en minúsculas o con espacios al principio o al final se encuentra igual | Alta (Must) | Negativo | Sí |
| CP-04.4 | HU-04 | Sin datos personales | un pedido con nombre, teléfono, email y dirección de quien recibe | se consulta el seguimiento público | la respuesta no incluye ninguno de esos datos ni el comercio | Alta (Must) | Positivo | Sí |
| CP-05.1 | HU-05 | Avisos en cada cambio | un pedido con email de quien recibe | se recibe, se le asigna repartidor, sale a reparto y se entrega | llega un mail en cada paso, siempre con el link al seguimiento y el de "en camino" trae el código de entrega de 4 dígitos | Media (Should) | Positivo | Sí |
| CP-05.2 | HU-05 | Visita fallida | — | una visita falla | el mail explica qué pasó y qué sigue (confirmar la dirección, que la próxima es la última) | Media (Should) | Negativo | Sí |
| CP-05.3 | HU-05 | Aviso viejo descartado | que ya se avisó un cambio más nuevo | llega tarde el aviso de un estado anterior | no se manda | Media (Should) | Positivo | Sí |
| CP-05.4 | HU-05 | Servidor de correo caído (sistema externo) | que el servidor SMTP no responde | el pedido cambia de estado | el pedido avanza igual y el error queda en el log sin el email completo | Media (Should) | Negativo | Sí |
| CP-06.1 | HU-06 | Listado de mis pedidos | un comercio con pedidos | entra a Mis pedidos | ve cada pedido con estado, quien recibe, día prometido, visitas fallidas, código de seguimiento y el link "Ver lo que ve el cliente" | Media (Should) | Positivo | Sí |
| CP-06.2 | HU-06 | Aviso por cada cambio | — | un pedido suyo cambia de estado | aparece un aviso en la campana (y por mail si hay correo configurado) y si una visita falla, el aviso dice el motivo y qué conviene hacer | Media (Should) | Negativo | Sí |
| CP-06.3 | HU-06 | Solo lo suyo | un pedido del comercio A | el comercio B entra a Mis pedidos | no lo ve | Media (Should) | Positivo | Sí |
| CP-06.4 | HU-06 | Texto con HTML | un pedido con HTML en la dirección o el nombre de quien recibe | el comercio ve sus pedidos | el texto se muestra tal cual y no se ejecuta ningún script | Media (Should) | Positivo | Sí |
| CP-07.1 | HU-07 | Ver el remito | un pedido ENTREGADO del comercio | abre "Remito" | ve la foto del remito firmado | Media (Should) | Positivo | Parcial |
| CP-07.2 | HU-07 | Ver las visitas fallidas | un pedido con visitas fallidas | abre "Ver visitas" | ve de cada una el motivo, el comentario, quién la registró, la ubicación con link a Google Maps y hasta 3 fotos | Media (Should) | Positivo | Parcial |
| CP-07.3 | HU-07 | Evidencia de otro comercio | un pedido del comercio A con remito y fotos | otro comercio o un repartidor que no lo lleva pide el remito o una foto | responde 404, como si no existiera | Media (Should) | Negativo | Parcial |
| CP-08.1 | HU-08 | Stock por depósito | un comercio con stock consignado | entra a Mi stock | ve cada producto por depósito con disponible, reservado, libre y peso por unidad | Baja (Could) | Positivo | Parcial |
| CP-08.2 | HU-08 | Solo lectura | — | el comercio está en Mi stock | no puede editar cantidades: el stock lo carga el personal al recibir la mercadería | Baja (Could) | Negativo | Parcial |
| CP-08.3 | HU-08 | Solo su stock | stock del comercio A | entra el comercio B | no lo ve | Baja (Could) | Positivo | Parcial |
| CP-09.1 | HU-09 | Agregar un punto | — | el comercio carga un punto con nombre y dirección | queda activo y se puede usar en los pedidos de su ERP | Baja (Could) | Positivo | Parcial |
| CP-09.2 | HU-09 | Dar de baja y reactivar | un punto activo | lo da de baja y después lo reactiva | el punto vuelve a estar disponible | Baja (Could) | Positivo | Parcial |
| CP-09.3 | HU-09 | Puntos de otro comercio | — | el comercio B abre la pantalla con "?idComercio=" del comercio A | el parámetro se ignora y solo ve y modifica los suyos | Baja (Could) | Negativo | Parcial |
| CP-10.1 | HU-10 | Meses cerrados | un comercio con liquidaciones | entra a Mis liquidaciones | ve por mes el abono, los envíos entregados, los kilos, el almacenamiento, el total y el estado del cobro | Baja (Could) | Positivo | Sí |
| CP-10.2 | HU-10 | Vista previa del mes en curso | — | mira el mes en curso | ve lo que se cobraría hoy, calculado solo con sus entregas, sin cobrar nada | Baja (Could) | Positivo | Sí |
| CP-10.3 | HU-10 | Solo lo suyo | — | otro comercio entra a sus liquidaciones | no ve las del comercio A | Baja (Could) | Negativo | Sí |
| CP-11.1 | HU-11 | Indicadores del período | un comercio con pedidos en los últimos 30 días | elige el período de 30 días | ve el porcentaje a tiempo y al primer intento, las horas de recibido a entregado, los pedidos en espera, el costo por envío y la calificación promedio y la comparación con el período anterior | Baja (Could) | Borde | Sí |
| CP-11.2 | HU-11 | Exportar | — | descarga el CSV | el archivo trae los mismos indicadores del período elegido | Baja (Could) | Positivo | Sí |
| CP-11.3 | HU-11 | Sin datos | un período sin pedidos entregados o sin calificaciones | — | los indicadores muestran "—" y no 0 | Baja (Could) | Positivo | Sí |
| CP-12.1 | HU-12 | Orden de las paradas | un repartidor con retiros y entregas asignados | abre su hoja de ruta | primero van los retiros y después las entregas, cada una la más cercana a la anterior según OpenStreetMap | Alta (Must) | Positivo | Sí |
| CP-12.2 | HU-12 | Dirección sin ubicar | una dirección que todavía no se pudo ubicar en el mapa | — | esa parada se ordena por código postal | Alta (Must) | Positivo | Sí |
| CP-12.3 | HU-12 | Datos de cada parada | — | — | cada parada muestra la dirección, quien recibe, el teléfono que abre la llamada y las instrucciones que dejó quien recibe y "Abrir en Google Maps" arma el recorrido en tramos de hasta 9 paradas | Alta (Must) | Positivo | Sí |
| CP-12.4 | HU-12 | Solo lo suyo | — | un repartidor abre su hoja de ruta | no aparecen pedidos de otros repartidores | Alta (Must) | Negativo | Sí |
| CP-13.1 | HU-13 | Retiro | un pedido CONFIRMADO asignado al repartidor | marca "Ya retiré el pedido" | el pedido pasa a EN_CAMINO, se genera el código de entrega y se avisa al comercio y a quien recibe | Alta (Must) | Positivo | Sí |
| CP-13.2 | HU-13 | Ubicación compartida | que el repartidor dio permiso de ubicación | — | su posición se ve en el mapa del personal y en el seguimiento de ese pedido | Alta (Must) | Positivo | Sí |
| CP-13.3 | HU-13 | Pedido ajeno | un pedido asignado a otro repartidor | intenta marcar el retiro | se rechaza con "no está asignado a vos" y el pedido no cambia | Alta (Must) | Negativo | Sí |
| CP-14.1 | HU-14 | Entrega correcta | un pedido EN_CAMINO asignado al repartidor | carga la foto del remito (JPEG, PNG o WEBP, hasta 5 MB) y el código de 4 dígitos correcto | el pedido pasa a ENTREGADO, se guarda la ubicación si el celular la dio y si era contra entrega, el cobro se acredita y el vehículo libera su lugar | Alta (Must) | Positivo | Sí |
| CP-14.2 | HU-14 | Sin foto o con un archivo que no es imagen | — | intenta entregar sin foto o con un archivo que no es JPEG, PNG ni WEBP por su contenido | no se registra la entrega | Alta (Must) | Positivo | Sí |
| CP-14.3 | HU-14 | Código incorrecto | — | ingresa un código incorrecto | no se registra la entrega y el intento queda contado y al 5.º intento fallido ni el código correcto sirve: la entrega la registra un administrador | Alta (Must) | Negativo | Sí |
| CP-14.4 | HU-14 | Pedido ajeno | un pedido que no tiene asignado | intenta entregarlo | se rechaza | Alta (Must) | Negativo | Sí |
| CP-15.1 | HU-15 | Primera visita fallida | un pedido EN_CAMINO con visitas disponibles | elige un motivo de la lista (cliente ausente, dirección incorrecta, rechazo, paquete dañado, no entra en el vehículo, problema del repartidor, otro) | la pantalla dice qué va a pasar, se guarda la ubicación y el pedido vuelve al depósito en lista de espera para otro intento | Media (Should) | Negativo | Sí |
| CP-15.2 | HU-15 | Evidencia obligatoria según el motivo | — | el motivo es "rechazo" u "otro" sin comentario, o "paquete dañado" sin foto | no se registra la visita | Media (Should) | Negativo | Sí |
| CP-15.3 | HU-15 | Vuelve al comercio | un pedido con una visita fallida | falla la segunda visita, o el cliente lo rechaza | el pedido vuelve al comercio, el stock consignado se repone y el cobro se anula | Media (Should) | Negativo | Sí |
| CP-15.4 | HU-15 | Motivos que no son culpa del cliente | — | el motivo es "no entra en el vehículo" o "problema del repartidor" | no se gasta una visita | Media (Should) | Positivo | Sí |
| CP-16.1 | HU-16 | Alta con datos fiscales y puntos | — | el personal carga un comercio con sus datos fiscales y sus puntos de picking | el comercio queda activo y su ERP puede mandar pedidos | Alta (Must) | Positivo | Sí |
| CP-16.2 | HU-16 | Baja en cascada y reactivación | un comercio activo | lo da de baja | se dan de baja sus puntos y el pedido que mande su ERP queda descartado con el motivo y se puede reactivar | Alta (Must) | Positivo | Sí |
| CP-16.3 | HU-16 | Eliminar (solo administrador) | un comercio dado de baja que solo tiene pedidos cancelados | un administrador lo elimina | se borra junto con sus pedidos cancelados y si tiene algo vivo u órdenes de recepción anunciadas, no se elimina y un operador no ve la opción | Alta (Must) | Positivo | Sí |
| CP-17.1 | HU-17 | Alta de stock | — | carga producto, cantidad, peso por unidad y medidas | el stock queda disponible para los pedidos del comercio | Alta (Must) | Positivo | Sí |
| CP-17.2 | HU-17 | Editar sin quedar debajo de lo reservado | un producto con unidades reservadas | intenta bajar la cantidad por debajo de lo reservado | se rechaza con el motivo | Alta (Must) | Negativo | Sí |
| CP-17.3 | HU-17 | Eliminar (solo administrador) | — | un administrador elimina un producto sin unidades reservadas ni pedidos en curso | se borra y sus reservas quedan en el historial y si tiene reservas o un pedido en curso, no se elimina | Alta (Must) | Positivo | Sí |
| CP-17.4 | HU-17 | Producto fuera de rango | — | el peso o las medidas superan el tamaño grande | no se acepta en consignación | Alta (Must) | Positivo | Sí |
| CP-18.1 | HU-18 | Reserva y confirmación | — | reserva unidades disponibles | la reserva dura 5 minutos y se puede extender y al confirmarla se descuenta el stock | Media (Should) | Borde | Parcial |
| CP-18.2 | HU-18 | Vencimiento | una reserva que nadie confirma ni libera | pasan los 5 minutos | vence sola y el stock vuelve a estar disponible | Media (Should) | Borde | Parcial |
| CP-18.3 | HU-18 | Más de lo disponible | — | intenta reservar más unidades de las libres | se rechaza y el historial muestra todas las reservas y cómo terminaron | Media (Should) | Negativo | Parcial |
| CP-19.1 | HU-19 | Confirmación con asignación y cobro | un pedido PENDIENTE a domicilio | lo confirma antes de la hora de corte | se asigna el primer repartidor de la zona al que le entra por kilos y bultos y si es prepago, se cobra en el banco en el mismo momento y el pedido pasa a CONFIRMADO | Alta (Must) | Borde | Parcial |
| CP-19.2 | HU-19 | No entra en ningún vehículo o pasó la hora de corte | — | no le entra a ningún repartidor, o ya pasó la hora de corte | queda en lista de espera, sin cobrar y se asigna solo cuando se libera lugar | Alta (Must) | Borde | Parcial |
| CP-19.3 | HU-19 | El banco rechaza o no responde (sistema externo) | un pedido prepago | el banco rechaza o no responde en 5 segundos | el pedido sigue PENDIENTE, sin cobro, con el mensaje "El banco no respondió" y tras 3 fallas seguidas el circuit breaker responde al instante sin llamar al banco | Alta (Must) | Negativo | Parcial |
| CP-19.4 | HU-19 | Falla después de cobrar | que el banco ya cobró | la asignación falla después | el cobro se reversa en el banco | Alta (Must) | Negativo | Parcial |
| CP-19.5 | HU-19 | Doble clic | — | el botón Confirmar se presiona dos veces seguidas | el pedido se confirma una sola vez y hay un solo cobro | Alta (Must) | Borde | Parcial |
| CP-19.6 | HU-19 | Transición inválida | un pedido en un estado desde el que no se puede confirmar | se intenta confirmar | se rechaza (409 en la API) | Alta (Must) | Negativo | Parcial |
| CP-20.1 | HU-20 | Pedido pendiente | un pedido PENDIENTE | cualquiera del personal lo cancela | pasa a CANCELADO y el stock vuelve al depósito | Alta (Must) | Positivo | Sí |
| CP-20.2 | HU-20 | Pedido confirmado (solo administrador) | un pedido CONFIRMADO, ya cobrado | un administrador lo cancela | el cobro se anula y un operador no puede cancelarlo | Alta (Must) | Negativo | Sí |
| CP-20.3 | HU-20 | Pedido que salió a reparto | un pedido que salió a reparto | — | el botón no aparece y el sistema dice "Ya salió a reparto: no se puede cancelar" | Alta (Must) | Negativo | Sí |
| CP-21.1 | HU-21 | Cotizar y derivar | — | pide "Cotizar" | ve el precio y el plazo de cada transportista (el legado no cotiza) y al elegir uno y derivar, si lo toma, el pedido queda CONFIRMADO con su código de seguimiento | Baja (Could) | Positivo | Sí |
| CP-21.2 | HU-21 | Seguimiento automático | un pedido derivado | — | Rabbit consulta el envío cada 15 segundos y mueve el pedido solo hasta ENTREGADO | Baja (Could) | Positivo | Sí |
| CP-21.3 | HU-21 | El transportista rechaza o no responde (sistema externo) | — | el transportista rechaza el envío o no responde en 5 segundos | el pedido no se confirma y si ya se había cobrado, el cobro se reversa | Baja (Could) | Negativo | Sí |
| CP-22.1 | HU-22 | Zonas por código postal | — | define una zona con un rango de códigos postales | no se puede superponer con otra zona y la zona dice quién reparte: repartidores propios o un transportista, con uno de respaldo | Baja (Could) | Positivo | Sí |
| CP-22.2 | HU-22 | Despachar toda la zona | — | presiona "Despachar toda la zona" | asigna a los repartidores de la zona y, si no alcanzan, deriva al transportista de respaldo y muestra qué pasó con cada pedido | Baja (Could) | Positivo | Sí |
| CP-22.3 | HU-22 | Sin zona | un pedido sin código postal reconocible | — | queda en "Sin zona" y se resuelve a mano y los retiros en depósito no aparecen en Ruteo | Baja (Could) | Negativo | Sí |
| CP-23.1 | HU-23 | Alta con zona y capacidad | — | carga un repartidor con zona y capacidad en kilos y bultos | queda disponible para la asignación | Alta (Must) | Positivo | Sí |
| CP-23.2 | HU-23 | Varios pedidos mientras entren | un repartidor con pedidos asignados | se confirma otro pedido que entra en lo que le queda | se le asigna también y si supera los kilos o los bultos, se prueba con el siguiente | Alta (Must) | Positivo | Sí |
| CP-23.3 | HU-23 | Eliminar (solo administrador) | — | un administrador elimina un repartidor sin entregas en curso ni cuenta de usuario | se borra y si tiene entregas en curso o cuenta, no se elimina | Alta (Must) | Positivo | Sí |
| CP-24.1 | HU-24 | Tablero de entregas | — | entra a Entregas en curso | ve cada entrega con su retiro y su destino, y el recorrido de cada repartidor en Google Maps | Media (Should) | Positivo | Parcial |
| CP-24.2 | HU-24 | Mapa | — | abre el mapa | ve la última ubicación de los repartidores que están repartiendo y los nombres se muestran como texto, aunque tengan HTML | Media (Should) | Positivo | Parcial |
| CP-24.3 | HU-24 | Requiere atención | — | entra al Inicio del personal | ve cuántos pedidos están en lista de espera, por confirmar, devueltos, retiros listos, liquidaciones sin cobrar y órdenes de recepción anunciadas | Media (Should) | Positivo | Parcial |
| CP-24.4 | HU-24 | Visitas y avisos en Pedidos | — | abre Pedidos | ve las visitas fallidas con su evidencia y los avisos que se mandaron a cada cliente | Media (Should) | Positivo | Parcial |
| CP-25.1 | HU-25 | Toda la red | — | entra a Indicadores con el período de 30 días | ve los mismos indicadores que el comercio (HU-11) para toda la red | Baja (Could) | Positivo | Sí |
| CP-25.2 | HU-25 | Por comercio y por repartidor | — | filtra por un comercio | ve solo los de ese comercio y el detalle por repartidor trae a tiempo, primer intento y calificación promedio | Baja (Could) | Positivo | Sí |
| CP-25.3 | HU-25 | Gráficos accesibles | — | — | cada gráfico tiene su versión en tabla y los motivos de las visitas fallidas | Baja (Could) | Positivo | Sí |
| CP-26.1 | HU-26 | Mismo camino que la API | — | carga un pedido con los mismos datos que pide la API, incluido el email de quien recibe | sigue el mismo camino que un pedido real (sincronización, stock, seguimiento) | Baja (Could) | Positivo | Parcial |
| CP-26.2 | HU-26 | Validaciones iguales | — | falta el email o un campo tiene formato inválido | se rechaza con el mismo mensaje que da la API | Baja (Could) | Negativo | Parcial |
| CP-26.3 | HU-26 | Comercio dado de baja | — | simula un pedido para un comercio dado de baja | el pedido queda descartado con el motivo | Baja (Could) | Positivo | Parcial |
| CP-27.1 | HU-27 | Alta por tipo | — | crea una cuenta de operador, administrador, comercio, repartidor o ERP | las de comercio, repartidor y ERP quedan asociadas a su comercio o repartidor y la contraseña se guarda con bcrypt | Alta (Must) | Positivo | Sí |
| CP-27.2 | HU-27 | Baja, reactivación y restablecer | — | da de baja una cuenta | deja de poder entrar en todos los servidores y se puede reactivar con una contraseña nueva, restablecer la contraseña o eliminar | Alta (Must) | Positivo | Sí |
| CP-27.3 | HU-27 | Protecciones | — | intenta darse de baja a sí mismo o dejar el sistema sin administradores activos | se rechaza | Alta (Must) | Negativo | Sí |
| CP-27.4 | HU-27 | Fuerza bruta | — | alguien se equivoca 5 veces seguidas de contraseña con una cuenta | el 6.º intento se rechaza durante 15 minutos con el mismo mensaje exista o no el usuario y la API del ERP responde 429 en el mismo caso | Alta (Must) | Negativo | Sí |
| CP-27.5 | HU-27 | Solo el administrador | — | un operador abre Usuarios | no puede entrar a la pantalla | Alta (Must) | Negativo | Sí |
| CP-28.1 | HU-28 | Liquidación del mes cerrado | un mes cerrado | se liquida (a mano o el timer del día 1 a las 6) | a cada comercio activo se le calcula abono + cargo por envío entregado + cargo por kilo entregado + almacenamiento y se cobra por el banco | Alta (Must) | Positivo | Sí |
| CP-28.2 | HU-28 | Mes en curso | — | mira el mes en curso | ve una vista previa sin guardar ni cobrar y no puede liquidarlo | Alta (Must) | Negativo | Sí |
| CP-28.3 | HU-28 | El banco rechaza o no responde (sistema externo) | — | el banco rechaza el cobro de una liquidación | queda RECHAZADA y el administrador la puede reintentar y si no responde, queda PENDIENTE y el timer la reintenta | Alta (Must) | Negativo | Sí |
| CP-29.1 | HU-29 | Alta de un retiro | — | el ERP manda un pedido con tipoEntrega RETIRO_EN_DEPOSITO y stock consignado en un solo depósito | el pedido no pide dirección, no lleva repartidor ni pasa por Ruteo y al confirmarlo queda "listo para retirar" | Media (Should) | Positivo | Sí |
| CP-29.2 | HU-29 | Entrega con el código | un retiro CONFIRMADO | el personal ingresa el código de seguimiento (sin importar mayúsculas ni espacios) | el pedido pasa directo a ENTREGADO sin pasar por EN_CAMINO | Media (Should) | Positivo | Sí |
| CP-29.3 | HU-29 | Productos en distintos depósitos o con punto de picking | — | los productos están en distintos depósitos o el pedido usa un punto de picking | responde 422 explicando por qué | Media (Should) | Negativo | Sí |
| CP-29.4 | HU-29 | Operaciones de reparto | un retiro | se intenta despachar, derivar, cotizar o devolver al depósito | se rechaza con un mensaje claro | Media (Should) | Negativo | Sí |
| CP-29.5 | HU-29 | Retiro vencido | un retiro CONFIRMADO que nadie buscó en 21 días (configurable) | corre la revisión diaria de retiros | el pedido vuelve al comercio (DEVUELTO_AL_COMERCIO) con el motivo y el stock vuelve a quedar disponible y el cobro se anula | Media (Should) | Negativo | Sí |
| CP-30.1 | HU-30 | Calificar | un pedido ENTREGADO | quien recibe elige de 1 a 5 estrellas y, si quiere, un comentario de hasta 500 caracteres | se guarda y el seguimiento muestra lo calificado sin el formulario | Baja (Could) | Negativo | Sí |
| CP-30.2 | HU-30 | Una sola vez | un pedido ya calificado | se intenta calificar de nuevo | no se acepta | Baja (Could) | Positivo | Sí |
| CP-30.3 | HU-30 | Pedido no entregado o datos inválidos | — | el pedido no está ENTREGADO, o las estrellas no están entre 1 y 5 | no se acepta la calificación y el comentario se muestra siempre escapado y no va al log | Baja (Could) | Negativo | Sí |
| CP-31.1 | HU-31 | Reprogramar | un pedido a domicilio PENDIENTE, o CONFIRMADO con un repartidor de Rabbit | elige otro día hasta 7 días desde hoy y no antes de lo prometido | cambia la fecha prometida y el personal ve que fue reprogramado | Media (Should) | Positivo | Sí |
| CP-31.2 | HU-31 | Instrucciones y persona autorizada | — | deja instrucciones (hasta 200 caracteres) o el nombre de una persona autorizada (hasta 100) | el repartidor las ve en Mis entregas y el personal en Pedidos y la persona autorizada igual necesita el código de entrega para recibir | Media (Should) | Borde | Sí |
| CP-31.3 | HU-31 | Cuándo no se puede | — | el pedido está EN_CAMINO o resuelto, es un retiro, o lo lleva un transportista | el formulario no se ofrece y el servicio lo rechaza y después de 5 cambios no se aceptan más | Media (Should) | Negativo | Sí |
| CP-32.1 | HU-32 | Imprimir etiquetas | un pedido de 3 bultos | el personal abre "Etiquetas" | se arman 3 etiquetas de 100×60 mm con QR, "BULTO n DE 3", código de seguimiento, dirección, CP y quien recibe | Baja (Could) | Positivo | Parcial |
| CP-32.2 | HU-32 | Retiro escaneando | un repartidor con sesión y un pedido suyo por retirar | escanea el QR | ve el pedido y un botón que marca el retiro (con los mismos controles de HU-13) | Baja (Could) | Positivo | Parcial |
| CP-32.3 | HU-32 | QR de un pedido ajeno o sin sesión | — | un repartidor escanea un pedido que no es suyo | no se muestra nada de ese pedido y cualquier persona sin sesión llega al seguimiento público | Baja (Could) | Negativo | Parcial |
| CP-33.1 | HU-33 | Anunciar | — | anuncia de 1 a 50 productos sin repetir, con cantidad de 1 a 100.000 y peso por unidad, a un depósito | la orden queda ANUNCIADA a nombre de su comercio | Media (Should) | Positivo | Sí |
| CP-33.2 | HU-33 | Cancelar | una orden ANUNCIADA propia | la cancela | queda CANCELADA y no puede cancelar órdenes de otro comercio | Media (Should) | Negativo | Sí |
| CP-33.3 | HU-33 | Datos inválidos | — | repite un producto, supera los límites o el producto es fuera de rango | la orden no se crea | Media (Should) | Negativo | Sí |
| CP-34.1 | HU-34 | Llegó todo | una orden ANUNCIADA | registra que en cada línea llegó lo esperado | la orden queda RECIBIDA y el stock del comercio suma esas cantidades | Media (Should) | Positivo | Sí |
| CP-34.2 | HU-34 | Faltó algo | — | en alguna línea llegó menos | la orden queda PARCIAL, se suma solo lo que llegó y las dos pantallas muestran "Faltaron N" (o "Llegaron N de más") | Media (Should) | Positivo | Sí |
| CP-34.3 | HU-34 | No llegó nada o doble registro | — | informa que no llegó nada en ninguna línea | no se registra (eso es cancelar) y dos registros simultáneos de la misma orden no suman el stock dos veces | Media (Should) | Borde | Sí |
| CP-35.1 | HU-35 | Categoría deducida | — | un producto tiene peso y medidas | su categoría (pequeño, mediano o grande) se deduce y manda la más grande que le toque por cualquiera de sus datos | Baja (Could) | Positivo | Sí |
| CP-35.2 | HU-35 | Cargo en la liquidación | la foto diaria de unidades por categoría | se liquida el mes | el cargo es unidades-día × tarifa de cada categoría ($10, $30 y $90 por defecto) y aparece como línea propia | Baja (Could) | Positivo | Sí |
| CP-35.3 | HU-35 | Sin medidas | un producto sin medidas cargadas | — | cuenta como mediano (o más, si lo pesa) y se marca "medidas sin cargar" | Baja (Could) | Positivo | Sí |
| CP-36.1 | HU-36 | Días gratis | un producto que ingresó hace menos de 7 días | se saca la foto diaria de ocupación | sus unidades cuentan como gratis y no se cobran, y la liquidación muestra el ahorro | Baja (Could) | Positivo | Sí |
| CP-36.2 | HU-36 | Recargo por stock antiguo | un producto con 120 días o más sin ingresos ni ventas, fuera de sus días gratis | se liquida el mes | esas unidades pagan el recargo del 50 % y aparece como línea propia | Baja (Could) | Positivo | Sí |
| CP-36.3 | HU-36 | Productos anteriores | un producto que existía antes de la regla y no tiene fechas | se saca la primera foto diaria | su fecha de movimiento arranca ese día, sin recargo retroactivo ni días gratis | Baja (Could) | De borde | Sí |
| CP-37.1 | HU-37 | Comentario clasificado | un comentario en una calificación y un proveedor de IA configurado | quien recibió el pedido envía la calificación | se guarda enseguida y, en segundo plano, se agrega el sentimiento y el tema | Baja (Could) | Positivo | Sí |
| CP-37.2 | HU-37 | Sin respuesta de la IA | no hay clave de API, o el proveedor no responde en 5 s o falla | se envía la calificación | la calificación se guarda igual y el comentario queda sin clasificar | Baja (Could) | Negativo | Sí |
| CP-37.3 | HU-37 | Respuesta no válida | un comentario que intenta darle órdenes al modelo, o una respuesta fuera de las listas cerradas | se valida lo que contestó el modelo | se descarta: no se guarda texto libre y solo se manda el texto del comentario | Baja (Could) | Negativo | Sí |
