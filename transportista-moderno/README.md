# Transportista moderno (servicio aparte)

Una empresa de envíos con API REST, **fuera de Rabbit**: proceso, puerto y
tecnología propios (Python, solo biblioteca estándar). Es la versión
"real" del transportista simulado que vive dentro del WAR
(`TransportistaRestSimuladoResource`), con el mismo contrato y las mismas
reglas, así Rabbit no nota la diferencia.

Rabbit sigue el estado de cada envío consultándolo cada 15 s (polling),
igual que con los demás transportistas.

## Levantarlo

```bash
python3 transportista-moderno/servidor.py
```

Escucha en `http://localhost:8095`. Variables opcionales:

| Variable | Para qué | Por defecto |
|---|---|---|
| `PUERTO` | Puerto HTTP | `8095` |
| `PASO_SEGUNDOS` | Cuánto dura cada estado (SOLICITADO, EN_TRANSITO) | `20` |

## Conectarlo con Rabbit

En Rabbit, **Transportistas → Registrar transportista**: tipo `API REST`,
endpoint `http://localhost:8095`.

Desde ahí, al derivarle un pedido, el pedido pasa a **En camino** y a
**Entregado** a medida que el seguimiento de Rabbit lo consulta. Si se lo
apaga, Rabbit lo marca "no respondió" al cotizar o derivar, y el resto de
la plataforma sigue funcionando.

## Contrato

| Método y ruta | Respuestas |
|---|---|
| `POST /cotizaciones` | `200 {"precio", "plazoHoras"}` · `422 {"error"}` |
| `POST /envios` | `201 {"codigoSeguimiento"}` · `422 {"error"}` · `400` |
| `GET /envios/{codigo}` | `200 {"codigoSeguimiento", "estado"}` · `404` |
| `DELETE /envios/{codigo}` | `204` · `404` · `409` (ya entregado) |
