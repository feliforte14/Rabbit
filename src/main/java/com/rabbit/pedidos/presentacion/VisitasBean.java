package com.rabbit.pedidos.presentacion;

/**
 * CAPA DE PRESENTACIÓN (Managed Bean - JSF) — las visitas fallidas de un
 * pedido con su evidencia (visitas.xhtml, ADR-045): motivo, comentario,
 * fotos y dónde estaba el repartidor.
 *
 * El pedido llega por la URL (?pedido=ID). Quién lo puede ver lo decide
 * IGestionPedidos con la identidad de la sesión: el personal, cualquiera;
 * un COMERCIO, solo los suyos. Si no puede, la página dice que no existe.
 *
 * @RequestScoped: es una página de solo lectura.
 */

import com.rabbit.infraestructura.Mensajes;
import com.rabbit.pedidos.dto.IntentoEntregaDTO;
import com.rabbit.pedidos.negocio.IGestionPedidos;
import com.rabbit.pedidos.negocio.PedidoNoEncontradoException;
import jakarta.ejb.EJBAccessException;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.List;

@Named
@RequestScoped
public class VisitasBean {

    @Inject
    private IGestionPedidos gestion;

    private Long idPedido;
    private List<IntentoEntregaDTO> visitas = List.of();
    private boolean encontrado;

    public void cargar() {
        if (idPedido == null) {
            Mensajes.error("Falta el número de pedido");
            return;
        }
        try {
            visitas = gestion.listarIntentosDeEntrega(idPedido);
            encontrado = true;
        } catch (PedidoNoEncontradoException | EJBAccessException e) {
            Mensajes.error("No existe el pedido " + idPedido);
        }
    }

    public Long getIdPedido() { return idPedido; }
    public void setIdPedido(Long idPedido) { this.idPedido = idPedido; }
    public List<IntentoEntregaDTO> getVisitas() { return visitas; }
    public boolean isEncontrado() { return encontrado; }
}
