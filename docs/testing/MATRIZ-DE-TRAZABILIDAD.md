# Matriz de trazabilidad

Entregable 2 de los *Criterios de Testing* (Clase 15). Cada historia de usuario (HU) llega a sus criterios de
aceptación (CA), sus casos de prueba (CP, ver [ESPECIFICACION-DE-CASOS.md](ESPECIFICACION-DE-CASOS.md)), la prueba
automatizada que la cubre (clase o ruta), el resultado, la evidencia y el defecto vinculado, si lo hay.

Resultados: corrida del CI sobre el commit `8b97ec6` (el de la etiqueta `v1.0-testing`). **«Sin ejecutar (manual)»**
significa que la HU solo tiene prueba manual y todavía no se ejecutó: no es un pase. Las pruebas manuales se
ejecutan con [FLUJO-DE-PRUEBAS.md](../pruebas/FLUJO-DE-PRUEBAS.md) y el resultado se anota acá y en Jira.

**Alcance del resultado:** es por historia, no criterio por criterio. «Pasa» quiere decir que las pruebas automatizadas de
la fila pasaron en el CI; no distingue qué criterio cubre cada una (eso lo dice la columna de pruebas).

| HU | Historia | MoSCoW | CA → CP | Prueba automatizada (clase / ruta / escenario) | Resultado | Evidencia | Defecto |
|---|---|---|---|---|---|---|---|
| HU-01 | Enviar un pedido a Rabbit | Must | 01.1–01.5 | `ApiRestIT` (alta 201, idempotencia, 422 misma clave, 400 sin clave y por campo, campos ignorados), `PedidoExternoRequestTest`, `HuellaDePedidoTest`, `NumerosEstrictosTest`, `MejorasUltimaMillaIT` (fecha prometida, email, teléfono), `PedidosEnSegundoPlanoTest` (01.5), Schemathesis | Pasa (CI, `8b97ec6`) | [CI](https://github.com/feliforte14/Rabbit/actions/runs/41496170001) | CON-02 |
| HU-02 | Saber cómo terminó un pedido | Must | 02.1–02.4 | `ApiRestIT` (404 inexistente, otro comercio 404, 304/ETag), `EtagDelPedidoExternoTest`, `PedidosExternosResourceTest` | Pasa (CI, `8b97ec6`) | [CI](https://github.com/feliforte14/Rabbit/actions/runs/41496170001) | CON-01 |
| HU-03 | Cancelar un pedido | Should | 03.1–03.4 | `ApiRestIT` (cancelar idempotente, 412), `ReglaDeCancelacionTest`, `TransicionesInvalidasDevuelven409Test` | Pasa (CI, `8b97ec6`) | [CI](https://github.com/feliforte14/Rabbit/actions/runs/41496170001) | CON-01 |
| HU-04 | Seguir mi pedido sin registrarme | Must | 04.1–04.4 | `ApiRestIT` (seguimiento sin login, inyección), `MejorasUltimaMillaIT.elSeguimientoPublicoNoMuestraAQuienRecibe`, `EstimacionDeLlegadaTest`, E2E-3 | Pasa (CI, `8b97ec6`) | [CI](https://github.com/feliforte14/Rabbit/actions/runs/41496170001) | — |
| HU-05 | Enterarme de cada novedad por mail | Should | 05.1–05.4 | `NotificacionesServiciosTest`, `TextosAlDestinatarioTest`, `AvisosDeFallaTest`, `ConfiguracionSmtpTest`, `DatosPersonalesTest` | Pasa (CI, `8b97ec6`) | [CI](https://github.com/feliforte14/Rabbit/actions/runs/41496170001) | — |
| HU-06 | Seguir mis pedidos y sus avisos | Should | 06.1–06.4 | E2E-4, E2E-5, SEG-1, `TextosAlComercioTest` | Pasa (CI, `8b97ec6`) | [CI](https://github.com/feliforte14/Rabbit/actions/runs/41496170001) | — |
| HU-07 | Ver la prueba de entrega o de la visita fallida | Should | 07.1–07.3 | `RemitosTest`, `EvidenciaDeVisitaTest`; 07.3 probado en vivo el 02/11 (404 a otro comercio y repartidor) | Automatizado: pasa (CI, `8b97ec6`). Manual: sin ejecutar | [CI](https://github.com/feliforte14/Rabbit/actions/runs/41496170001) + manual pendiente | — |
| HU-08 | Ver mi stock en los depósitos | Could | 08.1–08.3 | `ConsultaStockServiceTest`; manual | Automatizado: pasa (CI, `8b97ec6`). Manual: sin ejecutar | [CI](https://github.com/feliforte14/Rabbit/actions/runs/41496170001) + manual pendiente | — |
| HU-09 | Administrar mis puntos de picking | Could | 09.1–09.3 | `ComercioServiceTest`; 09.3 probado en vivo el 02/11 | Automatizado: pasa (CI, `8b97ec6`). Manual: sin ejecutar | [CI](https://github.com/feliforte14/Rabbit/actions/runs/41496170001) + manual pendiente | — |
| HU-10 | Saber cuánto me cobra Rabbit | Could | 10.1–10.3 | `LiquidacionesTest`, `TarifaMensualTest` | Pasa (CI, `8b97ec6`) | [CI](https://github.com/feliforte14/Rabbit/actions/runs/41496170001) | — |
| HU-11 | Medir cómo me entregan | Could | 11.1–11.3 | `CalculoDeIndicadoresTest`, `IndicadoresDTOTest` | Pasa (CI, `8b97ec6`) | [CI](https://github.com/feliforte14/Rabbit/actions/runs/41496170001) | — |
| HU-12 | Saber en qué orden hacer el recorrido | Must | 12.1–12.4 | `OrdenDeRecorridoTest`, `NominatimTest`, `RuteoServiciosTest` | Pasa (CI, `8b97ec6`) | [CI](https://github.com/feliforte14/Rabbit/actions/runs/41496170001) | — |
| HU-13 | Marcar que retiré el pedido | Must | 13.1–13.3 | `PedidoServiceTest`, `CodigoDeEntregaTest`, `EstadoPedidoTest` | Pasa (CI, `8b97ec6`) | [CI](https://github.com/feliforte14/Rabbit/actions/runs/41496170001) | — |
| HU-14 | Registrar la entrega con remito y código | Must | 14.1–14.4 | `RemitosTest`, `CodigoDeEntregaTest`, `PedidoServiceTest`, `PagoServiceTest` (acreditación contra entrega) | Pasa (CI, `8b97ec6`) | [CI](https://github.com/feliforte14/Rabbit/actions/runs/41496170001) | — |
| HU-15 | Registrar una entrega fallida | Should | 15.1–15.4 | `IntentosDeEntregaTest`, `EvidenciaDeVisitaTest`, `PagoServiceTest` (anulación) | Pasa (CI, `8b97ec6`) | [CI](https://github.com/feliforte14/Rabbit/actions/runs/41496170001) | — |
| HU-16 | Dar de alta y mantener los comercios | Must | 16.1–16.3 | `ComercioServiceTest`, E2E-2 | Pasa (CI, `8b97ec6`) | [CI](https://github.com/feliforte14/Rabbit/actions/runs/41496170001) | — |
| HU-17 | Cargar y corregir el stock de un depósito | Must | 17.1–17.4 | `ReglasDeStockTest`, `MovimientosDeStockTest`, `CategoriaDeTamanoTest` | Pasa (CI, `8b97ec6`) | [CI](https://github.com/feliforte14/Rabbit/actions/runs/41496170001) | — |
| HU-18 | Reservar stock para un pedido | Should | 18.1–18.3 | `MovimientosDeStockTest`; manual (vencimiento a los 5 min) | Automatizado: pasa (CI, `8b97ec6`). Manual: sin ejecutar | [CI](https://github.com/feliforte14/Rabbit/actions/runs/41496170001) + manual pendiente | — |
| HU-19 | Confirmar un pedido | Must | 19.1–19.6 | `PedidoServiceTest`, `AsignacionPorCapacidadTest`, `HoraDeCorteTest`, `CircuitBreakerBancoTest`, `BancoClientTest`, `PagoServiceTest`, `TransicionesInvalidasDevuelven409Test`, E2E-2; 19.3 y 19.5 probados en vivo el 02/11 | Automatizado: pasa (CI, `8b97ec6`). Manual: sin ejecutar | [CI](https://github.com/feliforte14/Rabbit/actions/runs/41496170001) + manual pendiente | — |
| HU-20 | Cancelar un pedido | Must | 20.1–20.3 | `ReglaDeCancelacionTest`, `PedidoServiceTest`, `PagoServiceTest` | Pasa (CI, `8b97ec6`) | [CI](https://github.com/feliforte14/Rabbit/actions/runs/41496170001) | — |
| HU-21 | Derivar un pedido a un transportista | Could | 21.1–21.3 | `TransportistaServiceTest`, `SeguimientoYCancelacionesDeEnviosTest`, `SimuladorDeEnviosTest` | Pasa (CI, `8b97ec6`) | [CI](https://github.com/feliforte14/Rabbit/actions/runs/41496170001) | — |
| HU-22 | Despachar por zona | Could | 22.1–22.3 | `RuteoServiciosTest`, `CodigosPostalesTest` | Pasa (CI, `8b97ec6`) | [CI](https://github.com/feliforte14/Rabbit/actions/runs/41496170001) | — |
| HU-23 | Administrar la flota de repartidores | Must | 23.1–23.3 | `RepartidorServiceTest`, `CapacidadRepartidorTest`, `AsignacionPorCapacidadTest` | Pasa (CI, `8b97ec6`) | [CI](https://github.com/feliforte14/Rabbit/actions/runs/41496170001) | — |
| HU-24 | Seguir las entregas del día | Should | 24.1–24.4 | SEG-2 (mapa como texto); manual | Automatizado: pasa (CI, `8b97ec6`). Manual: sin ejecutar | [CI](https://github.com/feliforte14/Rabbit/actions/runs/41496170001) + manual pendiente | SEG-01 |
| HU-25 | Medir la operación | Could | 25.1–25.3 | `CalculoDeIndicadoresTest` | Pasa (CI, `8b97ec6`) | [CI](https://github.com/feliforte14/Rabbit/actions/runs/41496170001) | — |
| HU-26 | Probar un pedido sin un ERP | Could | 26.1–26.3 | `PedidoServiceTest`; manual | Automatizado: pasa (CI, `8b97ec6`). Manual: sin ejecutar | [CI](https://github.com/feliforte14/Rabbit/actions/runs/41496170001) + manual pendiente | — |
| HU-27 | Administrar las cuentas | Must | 27.1–27.5 | `UsuarioServiceTest`, `ClavesTest`, `LimiteDeIntentosTest`, `LoginBeanTest`, `SeguridadIT` (429), E2E-1 | Pasa (CI, `8b97ec6`) | [CI](https://github.com/feliforte14/Rabbit/actions/runs/41496170001) | — |
| HU-28 | Liquidar el mes a los comercios | Must | 28.1–28.3 | `LiquidacionesTest`, `TarifaMensualTest`, `PagoServiceTest` | Pasa (CI, `8b97ec6`) | [CI](https://github.com/feliforte14/Rabbit/actions/runs/41496170001) | — |
| HU-29 | Retirar el pedido en un depósito | Should | 29.1–29.4 | `ReglaDeRetiroTest`, `EstadoDelPedidoTest` | Pasa (CI, `8b97ec6`) | [CI](https://github.com/feliforte14/Rabbit/actions/runs/41496170001) | — |
| HU-30 | Calificar la entrega | Could | 30.1–30.3 | `ReglaDeCalificacionTest` | Pasa (CI, `8b97ec6`) | [CI](https://github.com/feliforte14/Rabbit/actions/runs/41496170001) | — |
| HU-31 | Cambiar algo de la entrega | Should | 31.1–31.3 | `ReglaDeOpcionesDeEntregaTest` | Pasa (CI, `8b97ec6`) | [CI](https://github.com/feliforte14/Rabbit/actions/runs/41496170001) | — |
| HU-32 | Etiquetar los bultos y retirar escaneando | Could | 32.1–32.3 | `BultosTest`; manual (impresión y escaneo) | Automatizado: pasa (CI, `8b97ec6`). Manual: sin ejecutar | [CI](https://github.com/feliforte14/Rabbit/actions/runs/41496170001) + manual pendiente | — |
| HU-33 | Anunciar mercadería al depósito | Should | 33.1–33.3 | `ReglaDeRecepcionTest`, `OrdenDeRecepcionDTOTest` | Pasa (CI, `8b97ec6`) | [CI](https://github.com/feliforte14/Rabbit/actions/runs/41496170001) | — |
| HU-34 | Registrar lo que llegó al depósito | Should | 34.1–34.3 | `ReglaDeRecepcionTest`, `MovimientosDeStockTest` | Pasa (CI, `8b97ec6`) | [CI](https://github.com/feliforte14/Rabbit/actions/runs/41496170001) | — |
| HU-35 | Cobrar el almacenamiento | Could | 35.1–35.3 | `CategoriaDeTamanoTest`, `UnidadesDiaTest`, `TarifaMensualTest` | Pasa (CI, `8b97ec6`) | [CI](https://github.com/feliforte14/Rabbit/actions/runs/41496170001) | — |

## Resumen

- HU en el alcance: **35**; casos de prueba (CP): **123**.
- Con prueba automatizada que pasa: **27** HU. Mixtas (automatizada y manual): **8**. Solo manual, sin ejecutar: **0**.
- Cada HU del alcance tiene al menos un criterio y un caso (sin huecos en HU → CA → CP).
- Ninguna HU «Must» tiene un criterio fallado en la última corrida automatizada.
