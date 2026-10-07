package com.rabbit.pedidos.presentacion;

/**
 * CAPA DE PRESENTACIÓN (Managed Bean - JSF) — página pública de
 * seguimiento (seguimiento.xhtml): el cliente final pone el código que le
 * pasó la tienda y ve en qué está su pedido, sin login.
 *
 * Es la versión para personas de GET /api/v1/seguimiento/{codigo}: usa la
 * misma operación de negocio (ISeguimientoPedido.consultarSeguimiento) y
 * muestra lo mismo, solo el estado. Nada de importes, direcciones ni datos
 * del comercio: la puede abrir cualquiera que tenga el código.
 *
 * @RequestScoped: cada consulta es independiente; el código llega por la
 * URL (?codigo=RB-...), así el enlace se puede compartir tal cual.
 */

import com.rabbit.pedidos.dto.PedidoDTO;
import com.rabbit.pedidos.negocio.ISeguimientoPedido;
import com.rabbit.pedidos.negocio.ValidacionException;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.List;

@Named
@RequestScoped
public class SeguimientoBean {

    /** Los pasos normales de un pedido, en orden (CANCELADO va aparte). */
    private static final List<String> PASOS = List.of("PENDIENTE", "CONFIRMADO", "EN_CAMINO", "ENTREGADO");

    @Inject
    private ISeguimientoPedido seguimiento;

    private String codigo;
    private PedidoDTO pedido;
    private boolean noEncontrado;

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
        }
    }

    public boolean isCancelado() {
        return pedido != null && "CANCELADO".equals(pedido.estado);
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
            default: return "Estado: " + pedido.estado;
        }
    }

    public String getDetalle() {
        switch (pedido.estado) {
            case "PENDIENTE": return "La tienda ya nos pasó tu pedido. En breve lo confirmamos y le asignamos quién lo lleva.";
            case "CONFIRMADO": return "Ya tiene quién lo lleve. Lo estamos por retirar.";
            case "EN_CAMINO": return "Ya salió y va hacia la dirección de entrega.";
            case "ENTREGADO": return "Llegó a destino. ¡Gracias por elegirnos!";
            case "CANCELADO": return "Si no lo esperabas, consultá con la tienda donde compraste.";
            default: return "";
        }
    }

    public List<String> getPasos() { return PASOS; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public PedidoDTO getPedido() { return pedido; }
    public boolean isNoEncontrado() { return noEncontrado; }
}
