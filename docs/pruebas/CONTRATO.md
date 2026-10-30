# Pruebas de contrato

Comprueban que la API REST y los servicios SOAP se comportan como dicen
sus contratos. Corren en el CI (job **Contrato**) contra Rabbit levantado
con el compose.

## REST: Schemathesis contra `openapi.yaml`

[Schemathesis](https://schemathesis.readthedocs.io) genera pedidos a partir
de [`openapi.yaml`](../integraciones/openapi.yaml): ejemplos, casos de
borde, datos al azar y secuencias de llamadas encadenadas. Comprueba:

- que no haya errores 500;
- que cada código de estado esté documentado;
- que cada respuesta cumpla su schema y su `Content-Type`;
- que un dato inválido se rechace.

En local, con Rabbit corriendo:

```bash
uvx --from schemathesis==4.32.7 schemathesis run docs/integraciones/openapi.yaml \
  --url https://localhost:8443/Rabbit/api --tls-verify false \
  --auth demo.erp:<contraseña> --max-examples 50 \
  --seed 20261110 --generation-deterministic \
  --checks all --exclude-checks positive_data_acceptance,unsupported_method
```

Con `--seed` fija y `--generation-deterministic`, cada corrida genera los
mismos casos: así el CI da el mismo resultado en las 3 vueltas de
estabilidad y un hallazgo se reproduce. Sin la semilla, cada corrida explora
casos nuevos; sirve a mano, para buscar defectos, pero no en el CI.

### Desvíos aceptados (checks excluidos)

| Check | Por qué se excluye |
|---|---|
| `positive_data_acceptance` | Supone que todo pedido válido según el schema se acepta. En Rabbit varias reglas de negocio no se pueden escribir en un schema: que el ítem exista y sea del comercio, que haya stock, que el punto de picking sea del comercio, que la `Idempotency-Key` no se haya usado con otro cuerpo. Esos casos responden 422 a propósito, y el contrato los documenta. |
| `unsupported_method` | Undertow bloquea `TRACE` antes de llegar a la app (protección contra Cross-Site Tracing) y responde 405 sin el header `Allow`. Es una decisión de seguridad del servidor. |

### Lo que encontró y se corrigió en el contrato (29/10/2026)

- `openapi.yaml` no era YAML válido: una descripción con `: ` sin comillas.
  Ninguna herramienta podía leer el contrato.
- La respuesta **415** (cuerpo que no es `application/json`) no estaba
  documentada en los dos `POST`.
- `direccionEntrega` no figuraba como obligatoria para una entrega a
  domicilio. Ahora el contrato lo dice con un `if/then`, y ya no admite
  una dirección vacía.

### Defectos encontrados (corregidos en la PR #15)

| Id | Qué pasaba | Ahora |
|---|---|---|
| CON-01 | `GET /v1/pedidos-externos/%20` y `POST /v1/pedidos-externos/%20/cancelacion` (un espacio como id) respondían **500** (log: `WFLYEJB0034`). | Un id que no son solo dígitos (hasta 18) no matchea la ruta: **404** en Problem Details. |
| CON-02 | `POST /v1/pedidos-externos` con `"idItem": -9223372036854775809` (fuera del rango de `long`) respondía **201**, con el `idItem` guardado como `9223372036854775807`: el desborde cambiaba el signo sin avisar. También se aceptaban `0` y `-5`. Lo encontró Schemathesis en una vuelta de estabilidad (semilla `88224330309137619937126695567329018867`). | **400** con el campo marcado, también para `cantidad`, `bultos` e `idPuntoPicking`: cero, negativo, fuera de rango, decimal o texto. |

Desde entonces el paso de Schemathesis corta el CI.

### Concurrencia (ETag)

El contrato documenta el `ETag` de cada respuesta con un pedido, el
`If-None-Match` del `GET` (304 si no cambió) y el `If-Match` de la
cancelación (412 si cambió desde que el ERP lo leyó). Schemathesis prueba
esas cabeceras con valores cualquiera: un ETag que no coincide tiene que
dar 200 en el `GET` y 412 en la cancelación, ambos documentados.

## SOAP: WSDL y operaciones

[`scripts/contrato/soap.py`](../../scripts/contrato/soap.py) comprueba:

1. **El WSDL publicado es igual al versionado** en
   [`docs/integraciones/wsdl/`](../integraciones/wsdl/). Si alguien cambia
   la interfaz `@WebService`, el CI falla y muestra el diff. Si el cambio
   es a propósito, se actualiza el WSDL versionado en el mismo PR.
2. **Cada operación responde según el WSDL.** zeep valida el pedido y la
   respuesta contra el XSD. Los errores de negocio (banco: más de
   $500.000; transportista: más de 50 bultos) llegan como `soap:Fault`.

```bash
uv run --with zeep scripts/contrato/soap.py http://localhost:8080/Rabbit
```
