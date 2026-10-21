package com.rabbit.pedidos.presentacion;

/**
 * CAPA DE PRESENTACIÓN (Managed Bean - JSF) — página pública de
 * seguimiento (seguimiento.xhtml): el cliente final pone el código que le
 * pasó la tienda y ve en qué está su pedido, sin login.
 *
 * Es la versión para personas de GET /api/v1/seguimiento/{codigo}: usa la
 * misma operación de negocio (ISeguimientoPedido.consultarSeguimiento) y
 * muestra lo mismo: el estado, el día prometido (ADR-042) y, en camino, en
 * qué parada del repartidor está y en qué franja llega (ADR-043). Nada de
 * importes, direcciones ni datos de quien recibe: la puede abrir
 * cualquiera que tenga el código.
 *
 * Cuando el pedido está ENTREGADO, además le pregunta a quien lo recibió cómo
 * fue la entrega: de 1 a 5 estrellas y un comentario opcional (ADR-052). Una
 * sola vez por pedido; después muestra lo que calificó.
 *
 * @RequestScoped: cada consulta es independiente; el código llega por la
 * URL (?codigo=RB-...), así el enlace se puede compartir tal cual.
 */

import com.rabbit.infraestructura.Mensajes;
import com.rabbit.pedidos.dto.CalificacionDTO;
import com.rabbit.pedidos.dto.PedidoDTO;
import com.rabbit.pedidos.negocio.ISeguimientoPedido;
import com.rabbit.pedidos.negocio.ValidacionException;
import jakarta.enterprise.context.RequestScoped;
import jakarta.faces.context.FacesContext;
import com.rabbit.infraestructura.Formatos;
import com.rabbit.repartidores.dto.UbicacionDTO;
import com.rabbit.repartidores.negocio.IGestionRepartidores;
import com.rabbit.ruteo.dto.LlegadaEstimadaDTO;
import com.rabbit.ruteo.negocio.ILlegadaEstimada;
import java.time.LocalDate;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.inject.Named;
import java.util.List;

@Named
@RequestScoped
public class SeguimientoBean {

    /** Los pasos normales de un pedido, en orden (CANCELADO va aparte). */
    private static final List<String> PASOS = List.of("PENDIENTE", "CONFIRMADO", "EN_CAMINO", "ENTREGADO");

    @Inject
    private ISeguimientoPedido seguimiento;

    // Dónde está el repartidor que lleva el pedido (ADR-035).
    @Inject
    private IGestionRepartidores repartidores;

    private UbicacionDTO ubicacion;

    // En qué parada está y cuándo llega (ADR-043).
    @Inject
    private ILlegadaEstimada llegadas;

    private LlegadaEstimadaDTO llegada;

    private String codigo;
    private PedidoDTO pedido;
    private boolean noEncontrado;

    // Calificación de la entrega (ADR-052). El comentario lo escribe un tercero:
    // el negocio lo valida y esta vista lo muestra siempre escapado.
    private CalificacionDTO calificacion;
    private String comentario;

    // f:viewAction: busca recién cuando ya se cargó el parámetro de la URL.
    public void buscar() {
        if (codigo == null || codigo.isBlank()) {
            return;
        }
        codigo = codigo.trim().toUpperCase();
        try {
            pedido = seguimiento.consultarSeguimiento(codigo);
        } catch (ValidacionException e) {
            noEncontrado = true;
            return;
        }
        // Entregado: si ya lo calificaron, se muestra lo que dejó; si no, el formulario.
        if ("ENTREGADO".equals(pedido.estado)) {
            calificacion = seguimiento.consultarCalificacion(codigo);
        }
        // Solo mientras va en camino con un repartidor de Rabbit: antes no
        // salió, y después no hay nada que seguir.
        if ("EN_CAMINO".equals(pedido.estado) && pedido.idRepartidor != null) {
            ubicacion = repartidores.ubicacionParaPedido(pedido.idRepartidor, pedido.id);
            llegada = estimarLlegada(llegadas, pedido);
        }
    }

    /**
     * Envía la calificación (ADR-052). Las estrellas llegan como parámetro del
     * formulario (los radios de la página no son componentes JSF) y el negocio
     * las vuelve a validar. Después se vuelve a buscar el pedido para dibujar el
     * resultado: el agradecimiento, o el formulario con el motivo del rechazo.
     */
    public void calificar() {
        String enviadas = FacesContext.getCurrentInstance().getExternalContext()
                .getRequestParameterMap().get("estrellas");
        int estrellas = 0;
        try {
            estrellas = enviadas == null ? 0 : Integer.parseInt(enviadas.trim());
        } catch (NumberFormatException e) {
            // queda en 0: el negocio responde "Elegí de 1 a 5 estrellas"
        }
        try {
            seguimiento.calificarEntrega(codigo, estrellas, comentario);
            Mensajes.info("¡Gracias por calificar tu entrega!");
            comentario = null;
        } catch (ValidacionException e) {
            Mensajes.error(e.getMessage());
        }
        buscar();
    }

    /** Entregado y todavía sin calificar: la página muestra el formulario. */
    public boolean isPuedeCalificar() {
        return pedido != null && "ENTREGADO".equals(pedido.estado) && calificacion == null;
    }

    public CalificacionDTO getCalificacion() { return calificacion; }
    public String getComentario() { return comentario; }
    public void setComentario(String comentario) { this.comentario = comentario; }

    /** Las estrellas llenas y vacías de una calificación, para dibujarla como texto ("★★★★☆"). */
    public String estrellasEnTexto(int estrellas) {
        return "★".repeat(Math.max(0, Math.min(5, estrellas))) + "☆".repeat(5 - Math.max(0, Math.min(5, estrellas)));
    }

    public LlegadaEstimadaDTO getLlegada() { return llegada; }

    /**
     * La estimación nunca rompe el seguimiento: si falla hasta la entrada al
     * EJB (por ejemplo, la identidad del @RunAs), se muestra sin horario.
     * También la usa SeguimientoResource.
     */
    public static LlegadaEstimadaDTO estimarLlegada(ILlegadaEstimada llegadas, PedidoDTO pedido) {
        try {
            return llegadas.estimar(pedido.idRepartidor, pedido.id);
        } catch (RuntimeException e) {
            java.util.logging.Logger.getLogger(SeguimientoBean.class.getName())
                    .warning("[Pedidos] Seguimiento sin horario estimado del pedido " + pedido.id + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * "Llega hoy", "Llega mañana" o "Llega el viernes 17/10", antes de que
     * salga a reparto (ADR-042). Null si no hay fecha prometida, si ya pasó
     * o si una visita falló (la promesa original ya no vale).
     */
    public String getPromesa() {
        if (pedido == null || pedido.fechaPrometida == null || pedido.intentosFallidos > 0
                || !("PENDIENTE".equals(pedido.estado) || "CONFIRMADO".equals(pedido.estado))) {
            return null;
        }
        LocalDate hoy = LocalDate.now();
        if (pedido.fechaPrometida.isBefore(hoy)) {
            return null;
        }
        return "Llega " + Formatos.diaRelativo(pedido.fechaPrometida, hoy);
    }

    public boolean isDevuelto() {
        return pedido != null && "DEVUELTO_AL_COMERCIO".equals(pedido.estado);
    }

    /** El punto del repartidor para mapa.js, o null si no hay ubicación reciente. */
    public String getPuntosJson() {
        if (ubicacion == null) {
            return null;
        }
        return Json.createArrayBuilder().add(Json.createObjectBuilder()
                .add("lat", ubicacion.getLatitud())
                .add("lng", ubicacion.getLongitud())
                .add("titulo", "Tu pedido")
                .add("detalle", "Ubicación de las " + ubicacion.getHora())).build().toString();
    }

    public UbicacionDTO getUbicacion() { return ubicacion; }

    /** Mientras va en camino, la página se recarga sola para mover el mapa. */
    public boolean isEnCamino() {
        return pedido != null && "EN_CAMINO".equals(pedido.estado);
    }

    public boolean isCancelado() {
        return pedido != null && "CANCELADO".equals(pedido.estado);
    }

    /** Cancelado o devuelto al comercio: no se dibuja la línea de tiempo. */
    public boolean isFueraDeCamino() {
        return isCancelado() || isDevuelto();
    }

    /** "hecho", "actual" o "pendiente", para dibujar cada paso de la línea de tiempo. */
    public String claseDelPaso(String paso) {
        int actual = PASOS.indexOf(pedido.estado);
        int este = PASOS.indexOf(paso);
        if (este < actual) {
            return "hecho";
        }
        return este == actual ? (paso.equals("ENTREGADO") ? "hecho actual" : "actual") : "pendiente";
    }

    public String getTitular() {
        switch (pedido.estado) {
            case "PENDIENTE": return "Recibimos tu pedido";
            case "CONFIRMADO": return "Estamos preparando el envío";
            case "EN_CAMINO": return "Tu pedido está en camino";
            case "ENTREGADO": return "¡Tu pedido fue entregado!";
            case "CANCELADO": return "El pedido fue cancelado";
            case "DEVUELTO_AL_COMERCIO": return "No pudimos entregar tu pedido";
            default: return "Estado: " + pedido.estado;
        }
    }

    public String getDetalle() {
        switch (pedido.estado) {
            case "PENDIENTE": return pedido.intentosFallidos > 0
                    ? "Pasamos y no pudimos entregarlo. Lo vamos a intentar de nuevo con otro repartidor."
                    : "La tienda ya nos pasó tu pedido. En breve lo confirmamos y le asignamos quién lo lleva.";
            case "CONFIRMADO": return "Ya tiene quién lo lleve. Lo estamos por retirar.";
            case "EN_CAMINO": return "Ya salió y va hacia la dirección de entrega.";
            case "ENTREGADO": return "Llegó a destino. ¡Gracias por elegirnos!";
            case "CANCELADO": return "Si no lo esperabas, consultá con la tienda donde compraste.";
            case "DEVUELTO_AL_COMERCIO": return "Después de intentarlo, se lo devolvimos a la tienda. "
                    + "Comunicate con ella para coordinar otra entrega.";
            default: return "";
        }
    }

    public List<String> getPasos() { return PASOS; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public PedidoDTO getPedido() { return pedido; }
    public boolean isNoEncontrado() { return noEncontrado; }
}
