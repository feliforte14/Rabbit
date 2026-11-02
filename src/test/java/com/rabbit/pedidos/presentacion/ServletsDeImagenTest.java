package com.rabbit.pedidos.presentacion;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbit.pedidos.dto.FotoDeVisitaDTO;
import com.rabbit.pedidos.dto.RemitoDTO;
import com.rabbit.pedidos.negocio.IGestionPedidos;
import com.rabbit.pedidos.negocio.PedidoNoEncontradoException;
import jakarta.ejb.EJBAccessException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.Principal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** El remito y la foto de una visita: sin sesión, sin permiso, con datos y con la conexión cortada. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServletsDeImagenTest {

    @Mock private IGestionPedidos gestion;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private ServletOutputStream salida;
    @InjectMocks private RemitoServlet remito;
    @InjectMocks private FotoVisitaServlet foto;

    @BeforeEach
    void preparar() throws IOException {
        when(request.getContextPath()).thenReturn("/Rabbit");
        when(request.getUserPrincipal()).thenReturn(mock(Principal.class));
        when(response.getOutputStream()).thenReturn(salida);
    }

    private static Principal mock(Class<Principal> tipo) {
        return org.mockito.Mockito.mock(tipo);
    }

    @Test
    void elRemitoSinSesionMandaAlLogin() throws IOException {
        when(request.getUserPrincipal()).thenReturn(null);

        remito.doGet(request, response);

        verify(response).sendRedirect("/Rabbit/login.xhtml");
        verify(gestion, never()).obtenerRemito(any());
    }

    @Test
    void elRemitoDeOtroOInexistenteEs404() throws IOException {
        when(request.getParameter("pedido")).thenReturn("7");
        when(gestion.obtenerRemito(7L)).thenThrow(new PedidoNoEncontradoException("no existe"));
        remito.doGet(request, response);
        verify(response).sendError(HttpServletResponse.SC_NOT_FOUND);

        when(gestion.obtenerRemito(8L)).thenThrow(new EJBAccessException("sin permiso"));
        when(request.getParameter("pedido")).thenReturn("8");
        remito.doGet(request, response);

        when(request.getParameter("pedido")).thenReturn("no-es-un-numero");
        remito.doGet(request, response);
        verify(response, org.mockito.Mockito.times(3)).sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    @Test
    void elRemitoSeEntregaSinCacheYSinOlfatear() throws IOException {
        RemitoDTO dto = new RemitoDTO(7L, "image/jpeg", new byte[] {1, 2, 3});
        when(request.getParameter("pedido")).thenReturn("7");
        when(gestion.obtenerRemito(7L)).thenReturn(dto);

        remito.doGet(request, response);

        verify(response).setContentType("image/jpeg");
        verify(response).setContentLength(3);
        verify(response).setHeader("Cache-Control", "private, no-store");
        verify(response).setHeader("X-Content-Type-Options", "nosniff");
        verify(salida).write(dto.imagen);
    }

    @Test
    void siSeCortaLaConexionAlEscribirElRemitoNoSePropagaLaExcepcion() throws IOException {
        RemitoDTO dto = new RemitoDTO(7L, "image/png", new byte[] {9});
        when(request.getParameter("pedido")).thenReturn("7");
        when(gestion.obtenerRemito(7L)).thenReturn(dto);
        org.mockito.Mockito.doThrow(new IOException("Broken pipe")).when(salida).write(any(byte[].class));

        assertDoesNotThrow(() -> remito.doGet(request, response));
    }

    @Test
    void laFotoSinSesionMandaAlLogin() throws IOException {
        when(request.getUserPrincipal()).thenReturn(null);

        foto.doGet(request, response);

        verify(response).sendRedirect("/Rabbit/login.xhtml");
        verify(gestion, never()).obtenerFotoDeVisita(any());
    }

    @Test
    void laFotoDeOtroOInexistenteEs404() throws IOException {
        when(request.getParameter("id")).thenReturn("5");
        when(gestion.obtenerFotoDeVisita(5L)).thenThrow(new PedidoNoEncontradoException("no existe"));

        foto.doGet(request, response);

        verify(response).sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    @Test
    void laFotoSeEntregaSinCacheYSiSeCortaLaConexionNoSePropaga() throws IOException {
        FotoDeVisitaDTO dto = new FotoDeVisitaDTO(5L, 7L, "image/webp", new byte[] {4, 5});
        when(request.getParameter("id")).thenReturn("5");
        when(gestion.obtenerFotoDeVisita(5L)).thenReturn(dto);

        foto.doGet(request, response);
        verify(response).setContentType("image/webp");
        verify(response).setHeader("Cache-Control", "private, no-store");
        verify(salida).write(dto.imagen);

        org.mockito.Mockito.doThrow(new IOException("Broken pipe")).when(salida).write(any(byte[].class));
        assertDoesNotThrow(() -> foto.doGet(request, response));
    }
}
