# Transportista moderno (servicio aparte)

Una empresa de envíos con API REST, **fuera de Rabbit**: proceso, puerto y
tecnología propios (Python, solo biblioteca estándar). Es la versión
"real" del transportista simulado que vive dentro del WAR
(`TransportistaRestSimuladoResource`), con el mismo contrato y las mismas
reglas, así Rabbit no nota la diferencia.

Además **le avisa a Rabbit** cada cambio de estado por el webhook de
novedades, en vez de esperar a que Rabbit le pregunte cada 15 s.

## Levantarlo

```bash
python3 transportista-moderno/servidor.py
```

Escucha en `http://localhost:8095`. Variables opcionales:

| Variable | Para qué | Por defecto |
|---|---|---|
| `PUERTO` | Puerto HTTP | `8095` |
| `PASO_SEGUNDOS` | Cuánto dura cada estado (SOLICITADO, EN_TRANSITO) | `20` |
| `RABBIT_WEBHOOK_URL` | Webhook de Rabbit para avisar novedades | sin avisos |
| `RABBIT_WEBHOOK_CLAVE` | Clave que muestra Rabbit al generarla | sin avisos |
| `RABBIT_WEBHOOK_INSEGURO` | `1` acepta el certificado autofirmado de un WildFly local | verifica |

## Conectarlo con Rabbit

1. En Rabbit, **Transportistas → Registrar transportista**: tipo `API REST`,
   endpoint `http://localhost:8095`.
2. En la fila del transportista, **Generar clave** (webhook). Rabbit muestra
   la URL y la clave **una sola vez**.
3. Levantarlo con esos datos:

```bash
RABBIT_WEBHOOK_URL=https://localhost:8443/Rabbit/api/v1/transportistas/<id>/novedades \
RABBIT_WEBHOOK_CLAVE=<clave> RABBIT_WEBHOOK_INSEGURO=1 \
python3 transportista-moderno/servidor.py
```

Desde ahí, al derivarle un pedido, el pedido pasa a **En camino** y a
**Entregado** apenas el transportista avisa. Si se lo apaga, Rabbit lo
marca "no respondió" al cotizar o derivar, y el resto de la plataforma
sigue funcionando.

## Contrato

| Método y ruta | Respuestas |
|---|---|
| `POST /cotizaciones` | `200 {"precio", "plazoHoras"}` · `422 {"error"}` |
| `POST /envios` | `201 {"codigoSeguimiento"}` · `422 {"error"}` · `400` |
| `GET /envios/{codigo}` | `200 {"codigoSeguimiento", "estado"}` · `404` |
| `DELETE /envios/{codigo}` | `204` · `404` · `409` (ya entregado) |

Aviso al webhook: `POST <RABBIT_WEBHOOK_URL>` con
`Authorization: Bearer <clave>` y `{"codigoSeguimiento", "estado"}`.
