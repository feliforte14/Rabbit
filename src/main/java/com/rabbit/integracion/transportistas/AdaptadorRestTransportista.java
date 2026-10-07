package com.rabbit.integracion.transportistas;

/**
 * ADAPTER de un transportista moderno: API REST con JSON.
 *
 * Contrato del transportista (el que publica el simulado,
 * simulador.TransportistaRestSimuladoResource):
 *   POST   {endpoint}/cotizaciones     -> 200 {"precio", "plazoHoras"} | 422 {"error"}
 *   POST   {endpoint}/envios           -> 201 {"codigoSeguimiento"} | 422 {"error"}
 *   GET    {endpoint}/envios/{codigo}  -> 200 {"codigoSeguimiento", "estado"} | 404
 *   DELETE {endpoint}/envios/{codigo}  -> 204 | 404 | 409 (ya entregado)
 * con estados SOLICITADO, EN_TRANSITO, ENTREGADO y CANCELADO.
 *
 * Cliente JAX-RS estándar con timeout de 5 s (conexión y lectura), igual
 * criterio que el banco: sin respuesta, NO_DISPONIBLE. Una respuesta que no
 * es el JSON esperado (HTML de error, campos que faltan) se trata igual que
 * la falta de respuesta: nunca se escapa una excepción hacia el negocio.
 */

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.StringReader;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class AdaptadorRestTransportista implements IAdaptadorTransportista {

    private static final Logger LOG = Logger.getLogger(AdaptadorRestTransportista.class.getName());
    private static final int TIMEOUT_S = 5;

    // Un Client de JAX-RS es caro de crear y seguro de compartir entre hilos.
    private static final Client CLIENTE = ClientBuilder.newBuilder()
            .connectTimeout(TIMEOUT_S, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_S, TimeUnit.SECONDS)
            .build();

    @Override
    public ResultadoSolicitud solicitarEnvio(String endpoint, SolicitudEnvio s) {
        try (Response r = CLIENTE.target(endpoint).path("envios").request(MediaType.APPLICATION_JSON)
                .post(Entity.json(cuerpo(s)))) {
            JsonObject json = leer(r);
            if (r.getStatus() == 201) {
                String codigo = json.getString("codigoSeguimiento", null);
                if (codigo == null || codigo.isBlank()) {
                    // Lo tomó pero no dijo con qué código: Rabbit no lo puede
                    // seguir ni cancelar solo. Se registra para resolverlo a mano.
                    LOG.severe("[Transportistas][REST] Envío " + s.referencia()
                            + " tomado (201) sin codigoSeguimiento: revisarlo a mano con el transportista");
                    return ResultadoSolicitud.noDisponible();
                }
                return ResultadoSolicitud.aceptado(codigo);
            }
            if (r.getStatus() == 422) {
                return ResultadoSolicitud.rechazado(json.getString("error", "Envío rechazado"));
            }
            LOG.warning("[Transportistas][REST] Respuesta inesperada al solicitar " + s.referencia() + ": HTTP " + r.getStatus());
            return ResultadoSolicitud.noDisponible();
        } catch (JsonException | ClassCastException e) {
            // Respondió algo que no es el JSON esperado. Si fue un 201, el
            // envío quedó tomado del otro lado sin un código para seguirlo.
            LOG.log(Level.SEVERE, "[Transportistas][REST] Respuesta ilegible al solicitar " + s.referencia()
                    + ": si el transportista lo tomó, revisarlo a mano", e);
            return ResultadoSolicitud.noDisponible();
        } catch (ProcessingException | IllegalArgumentException e) {
            LOG.log(Level.WARNING, "[Transportistas][REST] El transportista no respondió al solicitar " + s.referencia(), e);
            return ResultadoSolicitud.noDisponible();
        }
    }

    // Misma información que el pedido de envío: el transportista cotiza
    // sobre lo que después tendría que llevar.
    @Override
    public ResultadoCotizacion cotizarEnvio(String endpoint, SolicitudEnvio s) {
        try (Response r = CLIENTE.target(endpoint).path("cotizaciones").request(MediaType.APPLICATION_JSON)
                .post(Entity.json(cuerpo(s)))) {
            JsonObject json = leer(r);
            if (r.getStatus() == 200 && json.containsKey("precio")) {
                return ResultadoCotizacion.cotizado(json.getJsonNumber("precio").bigDecimalValue(),
                        json.containsKey("plazoHoras") ? json.getInt("plazoHoras") : null);
            }
            if (r.getStatus() == 422) {
                return ResultadoCotizacion.rechazado(json.getString("error", "No toma el envío"));
            }
            LOG.warning("[Transportistas][REST] Respuesta inesperada al cotizar " + s.referencia() + ": HTTP " + r.getStatus());
            return ResultadoCotizacion.noDisponible();
        } catch (ProcessingException | IllegalArgumentException | JsonException | ClassCastException e) {
            LOG.log(Level.WARNING, "[Transportistas][REST] El transportista no respondió (o respondió algo ilegible) al cotizar " + s.referencia(), e);
            return ResultadoCotizacion.noDisponible();
        }
    }

    @Override
    public EstadoExterno consultarEstado(String endpoint, String codigo) {
        try (Response r = CLIENTE.target(endpoint).path("envios").path(codigo).request(MediaType.APPLICATION_JSON).get()) {
            if (r.getStatus() != 200) {
                return EstadoExterno.DESCONOCIDO;
            }
            return traducir(leer(r).getString("estado", ""));
        } catch (ProcessingException | IllegalArgumentException | JsonException | ClassCastException e) {
            LOG.log(Level.FINE, "[Transportistas][REST] Sin respuesta al consultar " + codigo, e);
            return EstadoExterno.DESCONOCIDO;
        }
    }

    @Override
    public boolean cancelarEnvio(String endpoint, String codigo) {
        try (Response r = CLIENTE.target(endpoint).path("envios").path(codigo).request().delete()) {
            // 404: no lo conoce, no hay nada que retirar. Cualquier otra cosa
            // (409 = ya entregado) no confirma la cancelación.
            if (r.getStatus() != 204 && r.getStatus() != 404) {
                LOG.warning("[Transportistas][REST] No canceló " + codigo + ": HTTP " + r.getStatus());
                return false;
            }
            return true;
        } catch (ProcessingException | IllegalArgumentException e) {
            LOG.log(Level.WARNING, "[Transportistas][REST] Sin respuesta al cancelar " + codigo, e);
            return false;
        }
    }

    // Vocabulario del transportista REST -> modelo de Rabbit.
    private static EstadoExterno traducir(String estado) {
        switch (estado) {
            case "SOLICITADO": return EstadoExterno.SOLICITADO;
            case "EN_TRANSITO": return EstadoExterno.EN_TRANSITO;
            case "ENTREGADO": return EstadoExterno.ENTREGADO;
            case "CANCELADO": return EstadoExterno.CANCELADO;
            default: return EstadoExterno.DESCONOCIDO;
        }
    }

    private static String cuerpo(SolicitudEnvio s) {
        JsonObjectBuilder cuerpo = Json.createObjectBuilder()
                .add("referencia", s.referencia())
                .add("direccionRetiro", s.direccionRetiro())
                .add("direccionEntrega", s.direccionEntrega())
                .add("bultos", s.bultos());
        if (s.cobrarAlEntregar() != null) {
            cuerpo.add("cobrarAlEntregar", s.cobrarAlEntregar());
        }
        return cuerpo.build().toString();
    }

    private static JsonObject leer(Response r) {
        String cuerpo = r.hasEntity() ? r.readEntity(String.class) : "";
        if (cuerpo == null || cuerpo.isBlank()) {
            return Json.createObjectBuilder().build();
        }
        try (var lector = Json.createReader(new StringReader(cuerpo))) {
            return lector.readObject();
        }
    }
}
