package com.rabbit.transportistas.negocio;

/**
 * CAPA DE NEGOCIO — componente Transportistas (EJB @Stateless).
 *
 * Rabbit reparte con sus propios repartidores; un pedido que no conviene
 * llevar así (fuera de zona, sin repartidores libres) se DERIVA a una
 * empresa de envíos externa. Cada transportista se integra con su propia
 * tecnología, y este servicio no la conoce: habla con él a través de
 * IAdaptadorTransportista (patrón Adapter; AdaptadoresTransportista elige
 * la implementación REST o SOAP).
 *
 * Stateless: cada operación recibe todo lo que necesita y el estado de los
 * envíos vive en la base.
 *
 * TRANSACCIONES: solicitarEnvio se suma a la transacción de la derivación
 * (REQUIRED); si esa transacción se deshace después de que el transportista
 * tomó el envío, CancelacionesDeEnvios lo cancela (compensación, igual que
 * la reversa del banco). registrarNovedad corre en su propia transacción
 * (REQUIRES_NEW): el seguimiento procesa varios envíos por pasada y uno que
 * falla no arrastra a los demás.
 *
 * SEGURIDAD: @PermitAll de clase y @RolesAllowed del personal de Rabbit en
 * lo que dispara una persona; el seguimiento corre con @RunAs("OPERADOR").
 * Un COMERCIO solo lee sus propios envíos.
 */

import com.rabbit.integracion.transportistas.ResultadoCotizacion;
import com.rabbit.integracion.transportistas.ResultadoSolicitud;
import com.rabbit.integracion.transportistas.SolicitudEnvio;
import com.rabbit.seguridad.negocio.IContextoUsuario;
import com.rabbit.transportistas.datos.TransportistaRepository;
import com.rabbit.transportistas.datos.model.Envio;
import com.rabbit.transportistas.datos.model.EstadoEnvio;
import com.rabbit.transportistas.datos.model.Transportista;
import com.rabbit.transportistas.dto.CotizacionDTO;
import com.rabbit.transportistas.dto.DatosEnvioDTO;
import com.rabbit.transportistas.dto.DatosTransportistaDTO;
import com.rabbit.transportistas.dto.EnvioDTO;
import com.rabbit.transportistas.dto.TransportistaDTO;
import jakarta.annotation.security.DeclareRoles;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.EJBException;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import com.rabbit.transportistas.datos.model.TipoIntegracion;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Stateless
@DeclareRoles({"ADMINISTRADOR", "OPERADOR", "COMERCIO"})
@PermitAll
public class TransportistaService implements IGestionTransportistas, IEnvios, ISeguimientoEnvios {

    private static final Logger LOG = Logger.getLogger(TransportistaService.class.getName());
    private static final SecureRandom AZAR = new SecureRandom();

    @Inject
    private TransportistaRepository repository;

    @Inject
    private AdaptadoresTransportista adaptadores;

    @Inject
    private IContextoUsuario contextoUsuario;

    @Inject
    private Event<EnvioSolicitado> envioSolicitado;

    @Inject
    private Event<EnvioCancelado> envioCancelado;

    @Inject
    private Event<EstadoEnvioCambiado> estadoEnvioCambiado;

    // ===============================================================
    // IGestionTransportistas
    // ===============================================================

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    public Long registrarTransportista(DatosTransportistaDTO datos) {
        if (datos.nombre == null || datos.nombre.isBlank()) {
            throw new ValidacionException("El nombre del transportista es obligatorio");
        }
        if (datos.tipoIntegracion == null) {
            throw new ValidacionException("Elegí cómo se integra el transportista");
        }
        validarEndpoint(datos.endpoint);
        Transportista t = new Transportista();
        t.setNombre(datos.nombre.trim());
        t.setTipoIntegracion(datos.tipoIntegracion);
        t.setEndpoint(datos.endpoint.trim());
        t.setActivo(true);
        return repository.guardar(t).getId();
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    public void darDeBajaTransportista(Long idTransportista) {
        Transportista t = obtenerOFallar(idTransportista);
        // Los envíos en curso siguen su seguimiento: la baja solo impide
        // derivarle pedidos nuevos.
        t.setActivo(false);
        repository.actualizar(t);
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    public void reactivarTransportista(Long idTransportista) {
        Transportista t = obtenerOFallar(idTransportista);
        t.setActivo(true);
        repository.actualizar(t);
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    public String generarClaveWebhook(Long idTransportista) {
        Transportista t = obtenerOFallar(idTransportista);
        if (t.getTipoIntegracion() != TipoIntegracion.REST) {
            throw new ValidacionException(t.getNombre() + " es un sistema legado: no avisa novedades, se le consulta");
        }
        byte[] azar = new byte[32];
        AZAR.nextBytes(azar);
        String clave = HexFormat.of().formatHex(azar);
        t.setClaveWebhook(clave);
        repository.actualizar(t);
        LOG.info("[Transportistas] Nueva clave de webhook para " + t.getNombre());
        return clave;
    }

    @Override
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    public List<TransportistaDTO> listarTodos() {
        return repository.listarTodos().stream().map(TransportistaDTO::desde).collect(Collectors.toList());
    }

    @Override
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    public List<TransportistaDTO> listarActivos() {
        return repository.listarTodos().stream().filter(Transportista::isActivo)
                .map(TransportistaDTO::desde).collect(Collectors.toList());
    }

    // ===============================================================
    // IEnvios
    // ===============================================================

    // Solo consultas a los transportistas: no escribe nada, así que no abre
    // transacción (y no la retiene mientras espera respuestas de afuera).
    // Se pregunta a uno por vez, cada uno con su timeout de 5 s.
    @Override
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    public List<CotizacionDTO> cotizarEnvio(Long idPedido, DatosEnvioDTO datos) {
        SolicitudEnvio solicitud = new SolicitudEnvio("PEDIDO-" + idPedido, datos.direccionRetiro,
                datos.direccionEntrega, datos.bultos, datos.cobrarAlEntregar);
        List<CotizacionDTO> cotizaciones = new ArrayList<>();
        for (Transportista t : repository.listarTodos()) {
            if (!t.isActivo()) {
                continue;
            }
            // Uno que falla de forma inesperada figura como "no respondió":
            // no puede hacer perder las cotizaciones de los demás.
            ResultadoCotizacion r;
            try {
                r = adaptadores.para(t.getTipoIntegracion()).cotizarEnvio(t.getEndpoint(), solicitud);
            } catch (RuntimeException e) {
                LOG.warning("[Transportistas] Error al cotizar con " + t.getNombre() + ": " + e);
                r = ResultadoCotizacion.noDisponible();
            }
            CotizacionDTO c = new CotizacionDTO();
            c.idTransportista = t.getId();
            c.transportista = t.getNombre();
            c.tipoIntegracion = t.getTipoIntegracion().name();
            c.estado = r.getEstado().name();
            c.precio = r.getPrecio();
            c.plazoHoras = r.getPlazoHoras();
            c.motivo = r.getMotivo();
            cotizaciones.add(c);
        }
        cotizaciones.sort(Comparator.comparing((CotizacionDTO c) -> !c.isCotizado())
                .thenComparing(c -> c.precio, Comparator.nullsLast(Comparator.naturalOrder())));
        LOG.info("[Transportistas] Pedido " + idPedido + " cotizado con " + cotizaciones.size() + " transportista(s)");
        return cotizaciones;
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    public EnvioDTO solicitarEnvio(Long idPedido, Long idComercio, Long idTransportista, DatosEnvioDTO datos) {
        Transportista t = obtenerOFallar(idTransportista);
        if (!t.isActivo()) {
            throw new ValidacionException("El transportista " + t.getNombre() + " está dado de baja");
        }
        if (repository.buscarEnvioDePedido(idPedido) != null) {
            throw new ValidacionException("El pedido " + idPedido + " ya fue derivado a un transportista");
        }

        SolicitudEnvio solicitud = new SolicitudEnvio("PEDIDO-" + idPedido, datos.direccionRetiro,
                datos.direccionEntrega, datos.bultos, datos.cobrarAlEntregar);
        ResultadoSolicitud resultado = adaptadores.para(t.getTipoIntegracion()).solicitarEnvio(t.getEndpoint(), solicitud);
        switch (resultado.getEstado()) {
            case RECHAZADO:
                throw new ValidacionException(t.getNombre() + " rechazó el envío: " + resultado.getMotivo());
            case NO_DISPONIBLE:
                throw new ValidacionException(t.getNombre() + " no respondió: el pedido no se derivó. "
                        + "Probá de nuevo o con otro transportista.");
            default:
                break;
        }

        LocalDateTime ahora = LocalDateTime.now();
        Envio envio = new Envio();
        envio.setIdPedido(idPedido);
        envio.setIdComercio(idComercio);
        envio.setTransportista(t);
        envio.setCodigoSeguimiento(resultado.getCodigoSeguimiento());
        envio.setEstado(EstadoEnvio.SOLICITADO);
        envio.setFechaSolicitud(ahora);
        envio.setFechaActualizacion(ahora);
        repository.guardarEnvio(envio);
        // Desde acá el transportista ya tomó el envío: si esta transacción
        // se deshace, CancelacionesDeEnvios se lo cancela (AFTER_FAILURE).
        envioSolicitado.fire(new EnvioSolicitado(idPedido, t.getTipoIntegracion(), t.getEndpoint(),
                resultado.getCodigoSeguimiento()));
        LOG.info("[Transportistas] Pedido " + idPedido + " derivado a " + t.getNombre()
                + ": seguimiento " + resultado.getCodigoSeguimiento());
        return EnvioDTO.desde(envio);
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    public void cancelarEnvioDePedido(Long idPedido) {
        // Bloqueado y releído: una novedad del transportista pudo darlo por
        // ENTREGADO justo antes, y un envío entregado no se cancela. El
        // pedido ya viene bloqueado por cancelarPedido — pedido y después
        // envío, el mismo orden que aplicarNovedad, para no cruzarse en un
        // deadlock.
        Envio envio = repository.buscarEnvioDePedidoParaActualizar(idPedido);
        if (envio == null || !envio.getEstado().isActivo()) {
            return;
        }
        envio.setEstado(EstadoEnvio.CANCELADO);
        envio.setFechaActualizacion(LocalDateTime.now());
        repository.actualizarEnvio(envio);
        Transportista t = envio.getTransportista();
        // Se le avisa al transportista recién cuando la cancelación del
        // pedido queda confirmada (AFTER_SUCCESS).
        envioCancelado.fire(new EnvioCancelado(idPedido, t.getTipoIntegracion(), t.getEndpoint(),
                envio.getCodigoSeguimiento()));
    }

    @Override
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    public List<EnvioDTO> listarEnvios() {
        return repository.listarEnvios().stream().map(EnvioDTO::desde).collect(Collectors.toList());
    }

    @Override
    @RolesAllowed("COMERCIO")
    public List<EnvioDTO> listarEnviosDelComercioActual() {
        return repository.listarEnviosDeComercio(contextoUsuario.idComercioActual()).stream()
                .map(EnvioDTO::desde).collect(Collectors.toList());
    }

    // ===============================================================
    // ISeguimientoEnvios
    // ===============================================================

    @Override
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    public List<EnvioDTO> listarEnviosActivos() {
        return repository.listarEnviosActivos().stream().map(EnvioDTO::desde).collect(Collectors.toList());
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    public void registrarNovedad(Long idEnvio, EstadoEnvio nuevo) {
        aplicarNovedad(repository.buscarEnvioPorId(idEnvio), nuevo);
    }

    // Sin @RolesAllowed: lo llama el transportista, no una persona. Lo que
    // lo protege es la clave (comparada en tiempo constante) y que el envío
    // sea de ese transportista.
    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public boolean recibirNovedad(Long idTransportista, String clave, String codigoSeguimiento, String estado) {
        Transportista t = idTransportista != null ? repository.buscarPorId(idTransportista) : null;
        if (t == null || t.getClaveWebhook() == null || clave == null
                || !MessageDigest.isEqual(t.getClaveWebhook().getBytes(StandardCharsets.UTF_8), clave.getBytes(StandardCharsets.UTF_8))) {
            throw new NovedadRechazadaException(NovedadRechazadaException.Motivo.CLAVE_INVALIDA,
                    "La clave del webhook no corresponde a ese transportista");
        }
        EstadoEnvio nuevo = traducirEstadoExterno(estado);
        if (nuevo == null) {
            throw new NovedadRechazadaException(NovedadRechazadaException.Motivo.ESTADO_DESCONOCIDO,
                    "Estado desconocido: " + estado + " (se esperaba SOLICITADO, EN_TRANSITO, ENTREGADO o CANCELADO)");
        }
        Envio envio = codigoSeguimiento == null ? null : repository.buscarEnvioPorCodigo(idTransportista, codigoSeguimiento);
        if (envio == null) {
            throw new NovedadRechazadaException(NovedadRechazadaException.Motivo.ENVIO_DESCONOCIDO,
                    "No hay ningún envío " + codigoSeguimiento + " de " + t.getNombre());
        }
        LOG.info("[Transportistas][Webhook] " + t.getNombre() + " avisa: " + codigoSeguimiento + " -> " + estado);
        return aplicarNovedad(envio, nuevo);
    }

    private static EstadoEnvio traducirEstadoExterno(String estado) {
        if (estado == null) {
            return null;
        }
        switch (estado.trim().toUpperCase()) {
            case "SOLICITADO": return EstadoEnvio.SOLICITADO;
            case "EN_TRANSITO": return EstadoEnvio.EN_TRANSITO;
            case "ENTREGADO": return EstadoEnvio.ENTREGADO;
            case "CANCELADO": return EstadoEnvio.CANCELADO;
            default: return null;
        }
    }

    // Lo mismo para el polling y el webhook. Una cancelación pudo ganarle al
    // seguimiento: un envío que ya no está activo no se toca.
    //
    // ORDEN DE BLOQUEO: primero el pedido, después el envío. El evento es
    // sincrónico y Pedidos bloquea la fila del pedido al moverlo; recién
    // después se bloquea el envío para escribirlo. cancelarPedido hace lo
    // mismo (bloquea el pedido y llama a cancelarEnvioDePedido): con el
    // mismo orden en los dos caminos no se cruzan en un deadlock.
    private boolean aplicarNovedad(Envio envio, EstadoEnvio nuevo) {
        if (envio == null || !envio.getEstado().isActivo() || envio.getEstado() == nuevo) {
            return false;
        }
        Long idEnvio = envio.getId();
        Long idPedido = envio.getIdPedido();
        String codigo = envio.getCodigoSeguimiento();
        EstadoEnvio anterior = envio.getEstado();

        // Sincrónico: Pedidos mueve el pedido en esta misma transacción. Si
        // falla (por ejemplo, el personal cambió el pedido justo en ese
        // momento), se deshace todo: el webhook responde 409 para que el
        // transportista reintente, y el polling la vuelve a tomar en la
        // próxima pasada. Se envuelve acá, y no en el recurso REST, porque
        // Transportistas no conoce las excepciones de Pedidos; el detalle
        // queda en el log y no se le cuenta al transportista.
        try {
            estadoEnvioCambiado.fire(new EstadoEnvioCambiado(idPedido, anterior, nuevo));
        } catch (EJBException e) {
            // Falla del sistema en Pedidos (la base no responde, un error de
            // programación): no es un conflicto, se deja pasar como error
            // interno para que se note.
            throw e;
        } catch (RuntimeException e) {
            // Excepción de aplicación de Pedidos: una regla de negocio no
            // dejó mover el pedido (cambió de estado justo ahora).
            LOG.log(Level.WARNING, "[Transportistas] El pedido " + idPedido + " no se pudo mover a " + nuevo
                    + " con la novedad del envío " + codigo, e);
            throw new NovedadRechazadaException(NovedadRechazadaException.Motivo.PEDIDO_EN_CONFLICTO,
                    "El pedido del envío " + codigo + " cambió al mismo tiempo y la novedad no se aplicó."
                    + " Reintentá en unos segundos.");
        }

        // Ahora sí el envío, bloqueado y releído.
        envio = repository.buscarEnvioParaActualizar(idEnvio);
        if (envio == null) {
            throw new NovedadRechazadaException(NovedadRechazadaException.Motivo.ENVIO_DESCONOCIDO,
                    "El envío " + codigo + " ya no existe");
        }
        if (envio.getEstado() == nuevo) {
            // El polling y el webhook trajeron la misma novedad a la vez y
            // el otro ya la aplicó: no cambió nada que haga falta escribir.
            // (No se marca rollback-only: con CMT, una transacción REQUIRES_NEW
            // que termina rollback-only sin que la propia aplicación haya
            // lanzado una excepción hace que el contenedor le tire
            // EJBTransactionRolledbackException al llamador — convertiría
            // este caso, que es el idempotente y debería responder 204, en
            // un error.)
            return false;
        }
        if (envio.getEstado() != anterior) {
            // Lo cancelaron mientras tanto: el pedido ya se movió por una
            // novedad que no corresponde, así que se deshace todo.
            LOG.warning("[Transportistas] El envío " + codigo + " cambió mientras se aplicaba " + nuevo
                    + " (está " + envio.getEstado() + "): no se aplica");
            throw new NovedadRechazadaException(NovedadRechazadaException.Motivo.PEDIDO_EN_CONFLICTO,
                    "El envío " + codigo + " cambió al mismo tiempo y la novedad no se aplicó."
                    + " Reintentá en unos segundos.");
        }
        envio.setEstado(nuevo);
        envio.setFechaActualizacion(LocalDateTime.now());
        repository.actualizarEnvio(envio);
        LOG.info("[Transportistas] Envío " + codigo + " (pedido " + idPedido + "): " + anterior + " -> " + nuevo);
        return true;
    }

    // ===============================================================

    private Transportista obtenerOFallar(Long id) {
        Transportista t = id != null ? repository.buscarPorId(id) : null;
        if (t == null) {
            throw new ValidacionException("Transportista no encontrado: " + id);
        }
        return t;
    }

    private static void validarEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new ValidacionException("El endpoint del transportista es obligatorio");
        }
        try {
            URI uri = new URI(endpoint.trim());
            if (!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme())) {
                throw new URISyntaxException(endpoint, "no es http ni https");
            }
            uri.toURL();
        } catch (URISyntaxException | MalformedURLException | IllegalArgumentException e) {
            throw new ValidacionException("El endpoint tiene que ser una URL http o https válida");
        }
        if (endpoint.trim().length() > 300) {
            throw new ValidacionException("El endpoint no puede superar los 300 caracteres");
        }
    }
}
