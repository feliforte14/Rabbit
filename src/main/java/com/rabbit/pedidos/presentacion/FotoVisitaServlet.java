package com.rabbit.pedidos.presentacion;

/**
 * Muestra una foto de una visita fallida: /foto-visita?id=ID (ADR-045).
 *
 * Mismo esquema que RemitoServlet: quién puede verla lo decide
 * IGestionPedidos.obtenerFotoDeVisita con la identidad de la sesión (el
 * personal o el comercio dueño del pedido); acá solo se exige que haya
 * sesión. Cualquier otro caso responde 404, sin distinguir "no existe" de
 * "no es tuya".
 */

import com.rabbit.pedidos.dto.FotoDeVisitaDTO;
import com.rabbit.pedidos.negocio.IGestionPedidos;
import com.rabbit.pedidos.negocio.PedidoNoEncontradoException;
import jakarta.ejb.EJBAccessException;
import jakarta.inject.Inject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet("/foto-visita")
public class FotoVisitaServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(FotoVisitaServlet.class.getName());

    @Inject
    private IGestionPedidos gestion;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        try {
            if (request.getUserPrincipal() == null) {
                response.sendRedirect(request.getContextPath() + "/login.xhtml");
                return;
            }
            FotoDeVisitaDTO foto;
            try {
                foto = gestion.obtenerFotoDeVisita(Long.valueOf(request.getParameter("id")));
            } catch (NumberFormatException | PedidoNoEncontradoException | EJBAccessException e) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            response.setContentType(foto.tipoContenido);
            response.setContentLength(foto.imagen.length);
            // Puede mostrar la casa del cliente: que no quede en cachés compartidas.
            response.setHeader("Cache-Control", "private, no-store");
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Content-Disposition", "inline; filename=\"visita-pedido-" + foto.idPedido + "-" + foto.id + "\"");
            response.getOutputStream().write(foto.imagen);
        } catch (IOException e) {
            // El cliente cortó la conexión o no se pudo escribir: no hay a quién responderle.
            LOG.log(Level.FINE, "[FotoVisita] No se pudo escribir la respuesta", e);
        }
    }
}
