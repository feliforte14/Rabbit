package com.rabbit.notificaciones.negocio;

/**
 * CAPA DE NEGOCIO — avisos a quien recibe el pedido (EJB @Stateless,
 * componente ServicioDeNotificaciones, ADR-040).
 *
 * Hasta ahora Rabbit solo le avisaba al comercio: el cliente final no se
 * enteraba de nada salvo que entrara al seguimiento. Los líderes (Onfleet,
 * Bringg, Andreani, DHL) le avisan a quien recibe en cada cambio. Acá se
 * arma ese aviso con el link al seguimiento y se manda por mail.
 *
 * Lo dispara SuscriptorDestinatarioEstadoPedido, el tercer suscriptor de
 * topico.pedidos.estado: sumar este canal no tocó Pedidos ni a los otros
 * suscriptores, que es justo lo que promete un tópico (ADR-006).
 *
 * ORDEN Y DUPLICADOS: mismo criterio que NotificacionService, con la
 * fechaCambio del último aviso al destinatario de ese pedido.
 *
 * El link al seguimiento se arma con la system property
 *   rabbit.url-publica   por ejemplo https://rabbit.example.com/Rabbit
 * (https://localhost:8443/Rabbit por defecto, el WildFly local).
 */

import com.rabbit.comercios.dto.ComercioDTO;
import com.rabbit.comercios.negocio.IConsultaComercios;
import com.rabbit.notificaciones.datos.NotificacionRepository;
import com.rabbit.notificaciones.datos.model.AvisoDestinatario;
import com.rabbit.notificaciones.dto.AvisoDestinatarioDTO;
import com.rabbit.pedidos.dto.PedidoDTO;
import com.rabbit.pedidos.negocio.ISeguimientoPedido;
import jakarta.annotation.security.DeclareRoles;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

// SEGURIDAD: @PermitAll de clase porque el aviso lo genera el suscriptor
// del tópico, sin usuario (mismo esquema que NotificacionService).
@Stateless
@DeclareRoles({"ADMINISTRADOR", "OPERADOR"})
@PermitAll
public class AvisoDestinatarioService implements IAvisosAlDestinatario {

    private static final Logger LOG = Logger.getLogger(AvisoDestinatarioService.class.getName());

    public static final String PROPIEDAD_URL = "rabbit.url-publica";
    private static final String URL_POR_DEFECTO = "https://localhost:8443/Rabbit";

    @Inject
    private NotificacionRepository repository;

    @Inject
    private ISeguimientoPedido pedidos;

    @Inject
    private IConsultaComercios comercios;

    // Para el canal de mail (AvisosPorMail), que se manda después del commit.
    @Inject
    private Event<AvisoDestinatarioRegistrado> avisoRegistrado;

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public boolean avisar(Long idPedido, Long idComercio, String estado, LocalDateTime fechaCambio) {
        LocalDateTime ultima = repository.ultimaFechaCambioAvisadaAlDestinatario(idPedido);
        if (ultima != null && !fechaCambio.isAfter(ultima)) {
            LOG.info("[Notificaciones] Descartado " + estado + " del pedido " + idPedido
                    + " para el destinatario: ya se avisó un cambio más nuevo");
            return false;
        }
        PedidoDTO pedido;
        try {
            pedido = pedidos.consultarEstadoPedido(idPedido);
        } catch (com.rabbit.pedidos.negocio.ValidacionException e) {
            // El pedido ya no existe (se eliminó su comercio): no hay a quién avisar.
            return false;
        }
        if (pedido.emailDestinatario == null) {
            return false;
        }

        // Una visita fallida vuelve a PENDIENTE con su motivo (ADR-041).
        String motivo = "PENDIENTE".equals(estado) || "DEVUELTO_AL_COMERCIO".equals(estado)
                ? pedido.motivoDevolucion : null;
        LocalDateTime ahora = LocalDateTime.now();
        TextosAlDestinatario.Datos datos = new TextosAlDestinatario.Datos(estado, pedido.nombreDestinatario,
                nombreComercio(idComercio), pedido.codigoSeguimiento, enlaceSeguimiento(pedido.codigoSeguimiento),
                pedido.fechaPrometida, ahora.toLocalDate(), motivo);
        String asunto = TextosAlDestinatario.asunto(datos);
        String texto = TextosAlDestinatario.cuerpo(datos);

        AvisoDestinatario aviso = new AvisoDestinatario();
        aviso.setIdPedido(idPedido);
        aviso.setIdComercio(idComercio);
        aviso.setEstadoPedido(estado);
        aviso.setFechaCambio(fechaCambio);
        aviso.setFechaAviso(ahora);
        aviso.setEmail(pedido.emailDestinatario);
        aviso.setAsunto(recortar(asunto, 200));
        aviso.setMensaje(recortar(texto, 1000));
        repository.guardarAvisoDestinatario(aviso);
        LOG.info("[Notificaciones] Aviso al destinatario del pedido " + idPedido + ": " + asunto);
        avisoRegistrado.fire(new AvisoDestinatarioRegistrado(idPedido, pedido.emailDestinatario, asunto, texto));
        return true;
    }

    @Override
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    public List<AvisoDestinatarioDTO> listarRecientes(int cantidad) {
        return repository.listarAvisosDestinatarioRecientes(cantidad).stream()
                .map(AvisoDestinatarioDTO::desde).collect(Collectors.toList());
    }

    /** El link público al seguimiento de un pedido. */
    static String enlaceSeguimiento(String codigo) {
        String base = System.getProperty(PROPIEDAD_URL, URL_POR_DEFECTO).trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/seguimiento.xhtml?codigo=" + codigo;
    }

    // En el aviso va el nombre del comercio, que es como lo conoce el cliente.
    private String nombreComercio(Long idComercio) {
        try {
            ComercioDTO comercio = comercios.obtenerComercio(idComercio);
            return comercio != null && comercio.nombre != null ? comercio.nombre : "la tienda";
        } catch (RuntimeException e) {
            LOG.warning("[Notificaciones] No se pudo leer el comercio " + idComercio + ": " + e.getMessage());
            return "la tienda";
        }
    }

    private static String recortar(String texto, int maximo) {
        return texto.length() <= maximo ? texto : texto.substring(0, maximo - 3) + "...";
    }
}
