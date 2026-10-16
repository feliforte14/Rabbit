package com.rabbit.pedidos.presentacion;

/**
 * Muestra la foto del remito de un pedido: /remito?pedido=ID (ADR-033).
 *
 * Una imagen no se puede servir desde una página JSF, por eso un servlet.
 * Quién puede verla lo decide IGestionPedidos.obtenerRemito con la
 * identidad de la sesión (el personal, el comercio dueño del pedido o su
 * repartidor); acá solo se exige que haya sesión. Cualquier otro caso
 * responde 404, sin distinguir "no existe" de "no es tuyo".
 */

import com.rabbit.pedidos.dto.RemitoDTO;
import com.rabbit.pedidos.negocio.IGestionPedidos;
import com.rabbit.pedidos.negocio.PedidoNoEncontradoException;
import jakarta.ejb.EJBAccessException;
import jakarta.inject.Inject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet("/remito")
public class RemitoServlet extends HttpServlet {

    @Inject
    private IGestionPedidos gestion;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (request.getUserPrincipal() == null) {
            response.sendRedirect(request.getContextPath() + "/login.xhtml");
            return;
        }
        RemitoDTO remito;
        try {
            remito = gestion.obtenerRemito(Long.valueOf(request.getParameter("pedido")));
        } catch (NumberFormatException | PedidoNoEncontradoException | EJBAccessException e) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        response.setContentType(remito.tipoContenido);
        response.setContentLength(remito.imagen.length);
        // Es un documento con datos del cliente: que no quede en cachés compartidas.
        response.setHeader("Cache-Control", "private, no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Content-Disposition", "inline; filename=\"remito-pedido-" + remito.idPedido + "\"");
        response.getOutputStream().write(remito.imagen);
    }
}
