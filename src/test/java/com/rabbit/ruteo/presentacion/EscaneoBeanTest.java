package com.rabbit.ruteo.presentacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbit.pedidos.negocio.IGestionPedidos;
import com.rabbit.pedidos.negocio.ValidacionException;
import com.rabbit.ruteo.dto.HojaDeRutaDTO;
import com.rabbit.ruteo.negocio.IRuteo;
import jakarta.ejb.EJBException;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Escaneo de la etiqueta por el repartidor (ADR-056). El QR lleva el código público del pedido
 * (el RB-...): el bean tiene que buscarlo por ese código y no por el del transportista externo, que en un
 * repartidor propio viene vacío. Con el campo equivocado el escaneo nunca encontraba el pedido.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EscaneoBeanTest {

    /** FacesContext.setCurrentInstance es protegido: se llega desde una subclase. */
    private abstract static class ContextoDePrueba extends FacesContext {
        static void usar(FacesContext contexto) {
            setCurrentInstance(contexto);
        }
    }

    @Mock private FacesContext faces;
    @Mock private IRuteo ruteo;
    @Mock private IGestionPedidos gestion;

    private final EscaneoBean bean = new EscaneoBean();

    @BeforeEach
    void preparar() throws Exception {
        ContextoDePrueba.usar(faces);
        inyectar("ruteo", ruteo);
        inyectar("gestion", gestion);
    }

    @AfterEach
    void limpiar() {
        ContextoDePrueba.usar(null);
    }

    private void inyectar(String campo, Object valor) throws Exception {
        var f = EscaneoBean.class.getDeclaredField(campo);
        f.setAccessible(true);
        f.set(bean, valor);
    }

    /** Un pedido de un repartidor propio: sin código de transportista, con el público del cliente. */
    private HojaDeRutaDTO entrega(Long id, String codigoCliente, String estado) {
        HojaDeRutaDTO h = new HojaDeRutaDTO();
        h.idPedido = id;
        h.codigoCliente = codigoCliente;
        h.codigoSeguimiento = null;
        h.estado = estado;
        return h;
    }

    @Test
    @DisplayName("Encuentra el pedido por el código público del QR, sin importar mayúsculas ni espacios")
    void encuentraPorElCodigoDelCliente() {
        HojaDeRutaDTO suya = entrega(7L, "RB-ABC", "CONFIRMADO");
        when(ruteo.entregasEnCursoDelRepartidor()).thenReturn(List.of(entrega(6L, "RB-OTRO", "CONFIRMADO"), suya));
        bean.setCodigo("  rb-abc ");

        bean.buscar();

        assertSame(suya, bean.getPedido());
        assertEquals("RB-ABC", bean.getCodigo());
        assertTrue(bean.isPorRetirar());
        assertFalse(bean.isEnCamino());
    }

    @Test
    @DisplayName("Un código que no es de sus entregas no encuentra nada")
    void codigoAjeno() {
        when(ruteo.entregasEnCursoDelRepartidor()).thenReturn(List.of(entrega(6L, "RB-OTRO", "CONFIRMADO")));
        bean.setCodigo("RB-ABC");

        bean.buscar();

        assertNull(bean.getPedido());
        assertFalse(bean.isPorRetirar());
    }

    @Test
    @DisplayName("Sin código, o con una cuenta sin repartidor asociado, no es suyo nada")
    void sinCodigoOSinRepartidor() {
        bean.setCodigo("   ");
        bean.buscar();
        assertNull(bean.getPedido());
        verify(ruteo, never()).entregasEnCursoDelRepartidor();

        when(ruteo.entregasEnCursoDelRepartidor()).thenThrow(new IllegalStateException("sin repartidor"));
        bean.setCodigo("RB-ABC");
        bean.buscar();
        assertNull(bean.getPedido());
    }

    @Test
    @DisplayName("Un pedido ya en camino no ofrece marcar el retiro")
    void yaEnCamino() {
        when(ruteo.entregasEnCursoDelRepartidor()).thenReturn(List.of(entrega(7L, "RB-ABC", "EN_CAMINO")));
        bean.setCodigo("RB-ABC");

        bean.buscar();

        assertTrue(bean.isEnCamino());
        assertFalse(bean.isPorRetirar());
    }

    @Test
    @DisplayName("Marcar como retirado despacha el pedido y avisa")
    void retirar() {
        HojaDeRutaDTO suya = entrega(7L, "RB-ABC", "CONFIRMADO");
        when(ruteo.entregasEnCursoDelRepartidor()).thenReturn(List.of(suya));
        bean.setCodigo("RB-ABC");
        bean.buscar();

        bean.retirar();

        verify(gestion).despacharPedido(7L);
        ArgumentCaptor<FacesMessage> mensaje = ArgumentCaptor.forClass(FacesMessage.class);
        verify(faces).addMessage(any(), mensaje.capture());
        assertEquals(FacesMessage.SEVERITY_INFO, mensaje.getValue().getSeverity());
        assertTrue(mensaje.getValue().getSummary().contains("ya está en camino"));
    }

    @Test
    @DisplayName("Sin pedido encontrado, retirar no hace nada")
    void retirarSinPedido() {
        bean.retirar();

        verify(gestion, never()).despacharPedido(any());
    }

    @Test
    @DisplayName("Si el negocio rechaza el retiro o falla la base, se avisa como error")
    void retirarConError() {
        when(ruteo.entregasEnCursoDelRepartidor()).thenReturn(List.of(entrega(7L, "RB-ABC", "CONFIRMADO")));
        bean.setCodigo("RB-ABC");
        bean.buscar();

        doThrow(new ValidacionException("El pedido ya salió")).when(gestion).despacharPedido(7L);
        bean.retirar();
        doThrow(new EJBException("se deshizo")).when(gestion).despacharPedido(7L);
        bean.retirar();

        ArgumentCaptor<FacesMessage> mensajes = ArgumentCaptor.forClass(FacesMessage.class);
        verify(faces, org.mockito.Mockito.times(2)).addMessage(any(), mensajes.capture());
        assertTrue(mensajes.getAllValues().stream().allMatch(m -> m.getSeverity() == FacesMessage.SEVERITY_ERROR));
        assertEquals("El pedido ya salió", mensajes.getAllValues().get(0).getSummary());
    }
}
