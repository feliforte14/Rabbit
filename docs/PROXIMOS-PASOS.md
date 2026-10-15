# Próximos pasos

La consigna (obligatorio + los tres desafíos opcionales) ya está completa
— ver [CHECKLIST.md](consigna/CHECKLIST.md), todo en "Cumple". Lo que
sigue acá **no es obligatorio**: son cosas concretas para repartirse,
pensadas para que cada uno pueda tomar una sin pisar el trabajo de otro.
Tachá o movelo a "Hecho" cuando lo termines (y avisá en el grupo, para que
no lo tome otro en paralelo).

## Hecho: mejoras del tablero (15/10/2026)

Implementadas en la rama `feature/mejoras-trello` (sobre
`feature/cuentas-compartidas`), probadas de punta a punta (ver la
sección 10 de [FLUJO-DE-PRUEBAS.md](pruebas/FLUJO-DE-PRUEBAS.md)):

- [x] Contraseñas centralizadas en la base (ADR-031).
- [x] Peso de los bultos y límite por repartidor en kilos y bultos (ADR-032).
- [x] Quién marca la entrega y foto del remito obligatoria (ADR-033).
- [x] Liquidación mensual a comercios (ADR-034). **A definir por el
      equipo:** los valores de la tarifa (hoy $15.000 + $1.200 por envío +
      $80 por kilo, configurables).
- [x] Ubicación del repartidor en vivo, mapa del personal y del cliente (ADR-035).

## 1. Revisar en el código

### Limitaciones ya documentadas (pendientes de resolver)

Estas están señaladas a propósito en los docs como "se sabe y no se
resolvió". Son las candidatas más directas para sumar algo real sin
inventar alcance nuevo.

- [ ] **Cobro prepago huérfano si se pierde la respuesta del banco**
      (`docs/integraciones/MENSAJERIA-SINCRONICA.md`, "Limitaciones
      conocidas"): si el banco cobró pero el timeout de 5 s corta antes
      de que llegue la respuesta, ese cobro queda en el banco sin que
      Rabbit lo sepa. Se resolvería con una clave de idempotencia en
      `autorizarPago` + una consulta de estado antes de reintentar.
      Archivos: `BancoClient.java`, `PagoService.registrarCobro`.
- [ ] **Reversa que falla queda solo logueada** (mismo documento): si
      `reversarPago` no responde, hoy se loguea para hacerla a mano. Un
      reintento programado (parecido al `@Schedule` de
      `BarredorDeReservas` o `SincronizadorDePedidos`) la cerraría sola.
      Archivo: `ReversasBancarias.java`.
- [ ] **Banco simulado en memoria**: `BancoLegadoServiceImpl` guarda los
      movimientos en una colección en memoria — se pierden al
      redesplegar. Si hiciera falta persistirlos, sería una entidad y un
      repositorio nuevos (bajo riesgo, no toca nada existente).

### Duplicación entre componentes (limpieza, no bug)

Cada `ServicioDeX` es independiente a propósito (no depende de clases
internas de otro componente — ver el comentario en cada
`ValidacionException.java`), así que esto es una decisión de diseño, no
un error. Pero si alguien quiere practicar una refactorización de bajo
riesgo, sin tocar comportamiento:

- [ ] **`ValidacionException` duplicada en 8 paquetes**
      (`comercios`, `inventario`, `pagos`, `pedidos`, `repartidores`,
      `ruteo`, `seguridad`, `transportistas` — todas idénticas:
      `@ApplicationException(rollback=true) extends RuntimeException`,
      un constructor). Se podría dejar una base común en
      `com.rabbit.infraestructura` y que cada módulo la extienda con una
      subclase de 3 líneas (mantiene el catch-por-módulo, sin duplicar
      la anotación EJB).
- [ ] **Validación "campo obligatorio" repetida** (~12 veces entre
      `ComercioService`, `InventarioService`, `UsuarioService`,
      `TransportistaService`, `ZonaService`): siempre el mismo
      `if (valor == null || valor.isBlank()) throw new
      ValidacionException(mensaje)`. Un helper único en
      `infraestructura` (`Validaciones.requerido(valor, mensaje)`)
      los reemplaza a todos por una línea.
- [ ] **`try { ... } catch (ValidacionException e) { Mensajes.error(...) }`
      repetido en casi todos los métodos de acción de los `*Bean.java`**
      (~40 ocurrencias). Un helper tipo
      `Mensajes.ejecutar(Runnable accion, String mensajeExito)` que
      envuelva ese try/catch común.

### Cobertura de tests (hoy hay 27 unitarios + 10 de integración)

Los tests unitarios de hoy cubren sobre todo Pedidos y Seguridad. Sin
tests propios todavía:

- [ ] `comercios` (alta, baja/reactivación, eliminación en cascada)
- [ ] `inventario` (reservar/confirmar/liberar stock, vencimiento)
- [ ] `repartidores`
- [ ] `ruteo` (zonas, despacho)
- [ ] `transportistas` (cotizar, derivar, seguimiento)
- [ ] `pagos` (circuit breaker, reversa)
- [ ] `notificaciones`

No hace falta levantar WildFly para esto: son tests unitarios con mocks,
como los que ya existen en `src/test/java/com/rabbit/pedidos/negocio/`
— son el mejor ejemplo para copiar el estilo.

### Cosas menores

- [ ] Log `SEVERE` cosmético de Mojarra en `personal/pedidos.xhtml`
      (línea del `<f:convertNumber parseBigDecimal="true">`): ya está
      documentado en un comentario ahí mismo como un wart conocido de
      esta versión de Mojarra (no rompe nada), pero si alguien quiere
      indagar una forma real de silenciarlo, sería un lindo tema de
      investigación — dos intentos ya fallaron (ver el comentario en el
      archivo).
- [ ] Revisar el **PR #6** (`desafio-heterogeneidad` → propone traer de
      vuelta `banco-legado/` y `transportista-moderno/` a `main`): decidir
      en equipo si se mergea o se deja como rama de referencia aparte.

## 2. Cosas que suman participación visible

Para que el historial de commits muestre claramente el aporte de cada
uno (no solo el código: la consigna también valora la documentación y las
pruebas).

- [ ] **Correr `docs/pruebas/FLUJO-DE-PRUEBAS.md` de punta a punta**, cada
      uno por su cuenta, marcando qué funcionó y qué no — si algo falla,
      es un hallazgo real para arreglar antes de la entrega.
- [ ] **Completar la lista de `docs/pruebas/PRUEBAS-Y-COMANDOS.md`**
      (tiene casilleros para marcar) y dejar constancia de quién la
      corrió y cuándo.
- [ ] **Limpiar la base compartida** con
      `scripts/Rabbit-limpieza-datos-de-prueba.sql` antes de la entrega
      (y de nuevo si alguien deja pedidos de prueba sueltos).
- [ ] **Preparar su propia parte para la defensa**: cada integrante puede
      repasar los ADR de lo que programó en
      [DECISIONES.md](arquitectura/DECISIONES.md) y estar listo para
      explicar por qué se decidió así y qué alternativa se descartó —
      son justamente las preguntas típicas de la defensa.
- [ ] **Tomar uno de los ítems de la sección 1** de este documento y
      hacer el commit a su nombre (ver cómo configurar la identidad de
      git en la guía que ya se las pasé por chat, o pedírsela a Felipe).

## Cómo tomar un ítem

1. Avisar en el grupo cuál vas a hacer (para no pisarse).
2. `git pull origin main` antes de empezar.
3. Idealmente en una rama propia (`git checkout -b fix/lo-que-sea`) y
   después un Pull Request — deja más claro el historial y permite que
   alguien lo revise antes de mergear.
4. Si tocás un componente, una integración, una regla de seguridad o un
   flujo transaccional, actualizá el doc que corresponde en el mismo
   commit (es la regla de mantenimiento de [docs/README.md](README.md)).
5. Tachar el ítem acá (o borrar la línea) cuando esté mergeado.
