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

### Defecto abierto

| Id | Qué pasa | Esperado | Dónde |
|---|---|---|---|
| CON-01 | `GET /v1/pedidos-externos/%20` y `POST /v1/pedidos-externos/%20/cancelacion` (un espacio como id) responden **500** | 400 o 404 en Problem Details | `PedidosExternosResource` / `PedidoService` (log: `WFLYEJB0034`) |
| CON-02 | `POST /v1/pedidos-externos` con `"idItem": -9223372036854775809` (fuera del rango de `long`) responde **201** y crea el pedido. El contrato pide `idItem` ≥ 1 y `PedidoExternoRequest.Linea.idItem` no tiene validación. Lo encontró Schemathesis en una vuelta de estabilidad (semilla `88224330309137619937126695567329018867`) | 400 en Problem Details | `PedidoExternoRequest.Linea` (falta `@Positive`; revisar también cómo se convierte un número fuera de rango) |

Mientras siga abierto, el paso de Schemathesis informa sin cortar el CI
(`continue-on-error`). Cuando se corrija, se saca esa línea del workflow.

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
