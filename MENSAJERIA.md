# Mensajería en Rabbit: sincrónica vs. asincrónica

Resumen de referencia rápida — qué hay implementado, dónde vive cada pieza,
y por qué cada tramo es síncrono o asíncrono. Ver también `README.md`
(secciones "Pedidos" e "Integración con sistemas legados").

---

## 1. Asincrónica — JMS (Pedidos)

**Qué resuelve:** cuando llega un pedido externo (simulando el ERP de un
comercio), Rabbit lo tiene que convertir en un pedido real sin que quien
lo registró tenga que esperar esa conversión.

**Broker:** ActiveMQ Artemis, embebido en WildFly (requiere el perfil
`standalone-full.xml` — con `standalone.xml` el deploy falla).

**Cola:** `cola.pedidos.externos` (JNDI `java:/jms/queue/PedidosExternos`),
declarada por la propia app con `@JMSDestinationDefinition` — no hay que
crearla a mano en WildFly.

**Flujo:**

```
PedidoService.registrarPedidoExterno()
  → guarda la fila PedidoExterno (INSERT confirmado)
  → dispara evento CDI PedidoExternoRegistrado
      (Event<PedidoExternoRegistrado>, @Observes AFTER_SUCCESS)
  → PublicadorPedidosExternos publica un mensaje en cola.pedidos.externos
  → PedidoExternoListener (@MessageDriven) lo recibe casi al instante
  → llama a PedidoService.sincronizarPedidoExterno(id) — mismo Facade
```

**Por qué el evento CDI en vez de llamar directo:**
`PublicadorPedidosExternos.publicarPedidoExternoRegistrado(...)` está
anotado `@Observes(during = TransactionPhase.AFTER_SUCCESS)` +
`@TransactionAttribute(NOT_SUPPORTED)`. El publish corre **después** de
que el INSERT ya confirmó, y **fuera** de esa transacción — así, si el
envío del mensaje falla (broker caído), no vuelca un pedido externo que
ya es válido. Es el patrón Observer: `PedidoService` no conoce a
`PublicadorPedidosExternos`.

**Conector `in-vm`:** la connection factory (`@JMSConnectionFactoryDefinition`)
usa `properties = {"connectors=in-vm"}` porque el broker corre dentro del
mismo WildFly. Sin esto, WildFly usa el conector HTTP remoto, que exige
usuario/contraseña, y el envío falla con `AMQ229031 Unable to validate user`.

**Red de contención (por qué sigue existiendo el polling):**
`SincronizadorDePedidos` (`@Singleton @Startup`, `@Schedule` cada minuto)
sigue barriendo TODOS los pedidos externos no sincronizados. El camino
por mensaje es el rápido; el polling es la garantía de que ningún pedido
queda huérfano si un mensaje se pierde. Si los dos llegan a la vez sobre
el mismo pedido, la fila se lee con `PESSIMISTIC_WRITE` y el segundo la
encuentra ya sincronizada (`PedidoYaSincronizadoException`, se ignora).

**Archivos clave:**

| Clase | Rol |
|---|---|
| `pedidos/negocio/PublicadorPedidosExternos.java` | Productor JMS + declaración de la cola |
| `pedidos/negocio/PedidoExternoListener.java` | Consumidor (`@MessageDriven`) |
| `pedidos/negocio/PedidoExternoRegistrado.java` | Evento CDI que dispara la publicación |
| `pedidos/negocio/SincronizadorDePedidos.java` | Polling de contención (`@Schedule`) |
| `pedidos/negocio/PedidoService.java` | Dueño de `registrarPedidoExterno` / `sincronizarPedidoExterno` |

---

## 2. Sincrónica — SOAP (Comercios → padrón fiscal)

**Qué resuelve:** antes de dar de alta o actualizar los datos fiscales de
un comercio, Rabbit necesita saber SI el CUIT existe y está habilitado —
no puede seguir sin esa respuesta (igual que "MediConecta valida
cobertura antes de confirmar un turno", clase 9). Caso real de referencia:
ARCA/AFIP (clase 9, slide 37).

**Servicio:** `PadronFiscalService` — mock de un padrón tipo ARCA/AFIP,
`document/literal wrapped`, una sola operación `consultarCuit(cuit)`.
Vive en el mismo WAR de Rabbit (simplicidad de despliegue: WildFly
publica solo cualquier POJO `@WebService` empaquetado), pero se consume
como si fuera externo — por SOAP/HTTP, nunca con una llamada Java directa.

**Endpoint publicado:** `http://localhost:8080/Rabbit/PadronFiscalService`
(WSDL en `?wsdl`).

**Flujo:**

```
ComercioService.registrarComercio() / .actualizarDatosFiscales()
  → valida formato/unicidad del CUIT (local)
  → IPadronFiscalClient.consultar(cuit)          [SOAP, bloquea acá]
      → PadronFiscalClient arma un proxy dinámico (Service.getPort)
        a partir del MISMO SEI que implementa el proveedor —
        sin generar stubs con wsimport
      → timeout 5s (connectionTimeout + receiveTimeout)
  → según el resultado, sigue o corta el alta (ver abajo)
```

**Los 3 desenlaces posibles (`ResultadoConsultaCuit.Estado`):**

| Estado | Qué pasa |
|---|---|
| `HABILITADO` | Sigue el alta, `Comercio.cuitValidado = true` |
| `NO_ENCONTRADO` | `ValidacionException`, **no se persiste nada** — falla de negocio |
| `SERVICIO_NO_DISPONIBLE` | Timeout o padrón caído. **No bloquea el alta** — falla de infraestructura ajena, se guarda con `cuitValidado = false` |

Esa distinción (negocio vs. infraestructura) es la respuesta concreta al
"desafío del timeout" de la clase 9 (slide 42): un servicio legado que no
contesta no puede tumbar una operación que en sí misma es válida.

**Fault SOAP:** `CuitInexistenteException` (`@WebFault`) viaja como
`<soap:Fault>` con `<detail><CuitInexistente><cuit>...` — el CUIT
`20-00000000-0` está reservado para dispararlo de forma determinística en
una demo.

**Archivos clave:**

| Clase | Rol |
|---|---|
| `integracion/legado/PadronFiscalService.java` | Contrato SOAP (SEI) |
| `integracion/legado/PadronFiscalServiceImpl.java` | Proveedor (el mock) |
| `integracion/legado/CuitInexistenteException.java` | El Fault de negocio |
| `integracion/legado/IPadronFiscalClient.java` | Puerto — lo único que conoce `ComercioService` |
| `integracion/legado/PadronFiscalClient.java` | Adapter: cliente JAX-WS real |
| `comercios/negocio/ComercioService.java` | Dueño de la llamada, decide qué hacer con cada resultado |

---

## 3. Cuándo es cada una (criterio aplicado en Rabbit)

| | Asincrónico (JMS) | Sincrónico (SOAP) |
|---|---|---|
| Caso en Rabbit | Sincronizar un pedido externo | Validar un CUIT antes de guardar |
| ¿El proceso puede seguir sin la respuesta? | Sí — por eso hay polling de respaldo | No — el alta depende de esa respuesta |
| ¿Qué pasa si el otro lado no contesta? | No importa: se reintenta solo (mensaje + polling) | Hay que decidir explícitamente (acá: no bloquear, marcar `cuitValidado=false`) |
| Acoplamiento | Solo de formato de mensaje (JSON del body) | Temporal + de contrato (WSDL) |

---

## 4. Gotcha de infraestructura (no es mensajería, pero rompe todo si no se sabe)

El datasource de Supabase tiene que usar el **Session Pooler** (puerto
`5432`), no el **Transaction Pooler** (`6543`): con `6543` el deploy
falla con `Unable to determine Dialect without JDBC metadata` porque ese
pooler no soporta la lectura de metadata que Hibernate necesita al
arrancar. Ver README, sección "Cómo levantar el sistema".
