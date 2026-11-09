package com.rabbit.pedidos.presentacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbit.pedidos.dto.PedidoDTO;
import com.rabbit.pedidos.negocio.ISeguimientoPedido;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Seguimiento público: al enviar un formulario (calificar, cambiar la entrega) el bean nace vacío y
 * tiene que recuperar el pedido, o JSF no procesa el envío (la calificación no se guardaba).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SeguimientoBeanTest {

    /** FacesContext.setCurrentInstance es protegido: se llega desde una subclase. */
    private abstract static class ContextoDePrueba extends FacesContext {
        static void usar(FacesContext contexto) {
            setCurrentInstance(contexto);
        }
    }

    @Mock private FacesContext faces;
    @Mock private ExternalContext externo;
    @Mock private ISeguimientoPedido seguimiento;

    private final SeguimientoBean bean = new SeguimientoBean();

    @BeforeEach
    void preparar() throws Exception {
        ContextoDePrueba.usar(faces);
        when(faces.getExternalContext()).thenReturn(externo);
        var campo = SeguimientoBean.class.getDeclaredField("seguimiento");
        campo.setAccessible(true);
        campo.set(bean, seguimiento);
    }

    @AfterEach
    void limpiar() {
        ContextoDePrueba.usar(null);
    }

    private PedidoDTO entregado() {
        PedidoDTO pedido = new PedidoDTO();
        pedido.estado = "ENTREGADO";
        return pedido;
    }

    @Test
    @DisplayName("En un reenvío vuelve a buscar el pedido con el código del campo oculto")
    void reenvioRecuperaElPedido() {
        when(faces.isPostback()).thenReturn(true);
        when(externo.getRequestParameterMap()).thenReturn(Map.of("codigo", " rb-abc "));
        when(seguimiento.consultarSeguimiento("RB-ABC")).thenReturn(entregado());

        bean.alReenviarFormulario();

        assertEquals("RB-ABC", bean.getCodigo());
        // Sin esto el panel del formulario daba falso al leer el envío y JSF lo ignoraba.
        assertTrue(bean.isPuedeCalificar());
    }

    @Test
    @DisplayName("Ya calificado, el reenvío no ofrece el formulario otra vez")
    void yaCalificado() {
        when(faces.isPostback()).thenReturn(true);
        when(externo.getRequestParameterMap()).thenReturn(Map.of("codigo", "RB-ABC"));
        when(seguimiento.consultarSeguimiento("RB-ABC")).thenReturn(entregado());
        when(seguimiento.consultarCalificacion("RB-ABC")).thenReturn(new com.rabbit.pedidos.dto.CalificacionDTO());

        bean.alReenviarFormulario();

        assertFalse(bean.isPuedeCalificar());
    }

    @Test
    @DisplayName("En una visita normal (GET) no busca nada: lo hace f:viewAction")
    void getNoBuscaAntesDeTiempo() {
        when(faces.isPostback()).thenReturn(false);

        bean.alReenviarFormulario();

        assertNull(bean.getCodigo());
        verify(seguimiento, never()).consultarSeguimiento(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("Un reenvío sin código no busca ni falla")
    void reenvioSinCodigo() {
        when(faces.isPostback()).thenReturn(true);
        when(externo.getRequestParameterMap()).thenReturn(Map.of());

        bean.alReenviarFormulario();

        assertFalse(bean.isPuedeCalificar());
        verify(seguimiento, never()).consultarSeguimiento(org.mockito.ArgumentMatchers.anyString());
    }
}
