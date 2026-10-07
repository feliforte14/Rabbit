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
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Stateless
@DeclareRoles({"ADMINISTRADOR", "OPERADOR", "COMERCIO"})
@PermitAll
public class TransportistaService implements IGestionTransportistas, IEnvios, ISeguimientoEnvios {

    private static final Logger LOG = Logger.getLogger(TransportistaService.class.getName());

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
        Envio envio = repository.buscarEnvioDePedido(idPedido);
        if (envio == null || !envio.getEstado().isActivo()) {
            return;
        }
        envio = repository.buscarEnvioParaActualizar(envio.getId());
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
        Envio envio = repository.buscarEnvioParaActualizar(idEnvio);
        // Una cancelación pudo ganarle al seguimiento: un envío que ya no
        // está activo no se toca.
        if (envio == null || !envio.getEstado().isActivo() || envio.getEstado() == nuevo) {
            return;
        }
        EstadoEnvio anterior = envio.getEstado();
        envio.setEstado(nuevo);
        envio.setFechaActualizacion(LocalDateTime.now());
        repository.actualizarEnvio(envio);
        LOG.info("[Transportistas] Envío " + envio.getCodigoSeguimiento() + " (pedido " + envio.getIdPedido()
                + "): " + anterior + " -> " + nuevo);
        // Sincrónico: Pedidos mueve el pedido en esta misma transacción; si
        // falla, se deshace también la novedad y se reintenta en la próxima
        // pasada del seguimiento.
        estadoEnvioCambiado.fire(new EstadoEnvioCambiado(envio.getIdPedido(), anterior, nuevo));
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
