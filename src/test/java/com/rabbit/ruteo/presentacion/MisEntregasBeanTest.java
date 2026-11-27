package com.rabbit.ruteo.presentacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbit.pedidos.datos.model.MotivoEntregaFallida;
import com.rabbit.pedidos.dto.DatosEntregaDTO;
import com.rabbit.pedidos.dto.EntregaFallidaDTO;
import com.rabbit.pedidos.negocio.IGestionPedidos;
import com.rabbit.pedidos.negocio.ValidacionException;
import com.rabbit.repartidores.negocio.IGestionRepartidores;
import com.rabbit.ruteo.dto.HojaDeRutaDTO;
import com.rabbit.ruteo.dto.RecorridoDTO;
import com.rabbit.ruteo.negocio.IRuteo;
import jakarta.ejb.EJBException;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.servlet.http.Part;
import java.io.ByteArrayInputStream;
import java.io.IOException;
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
 * Pantalla del repartidor (mis-entregas.xhtml): retiro, entrega con foto del remito, devolución con
 * evidencia y ubicación del celular. Se prueban los caminos felices y los que el repartidor ve como error.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MisEntregasBeanTest {

    private abstract static class ContextoDePrueba extends FacesContext {
        static void usar(FacesContext contexto) {
            setCurrentInstance(contexto);
        }
    }

    @Mock private FacesContext faces;
    @Mock private IRuteo ruteo;
    @Mock private IGestionPedidos gestion;
    @Mock private IGestionRepartidores repartidores;

    private final MisEntregasBean bean = new MisEntregasBean();

    @BeforeEach
    void preparar() throws Exception {
        ContextoDePrueba.usar(faces);
        inyectar("ruteo", ruteo);
        inyectar("gestion", gestion);
        inyectar("repartidores", repartidores);
    }

    @AfterEach
    void limpiar() {
        ContextoDePrueba.usar(null);
    }

    private void inyectar(String campo, Object valor) throws Exception {
        var f = MisEntregasBean.class.getDeclaredField(campo);
        f.setAccessible(true);
        f.set(bean, valor);
    }

    private FacesMessage ultimoMensaje() {
        ArgumentCaptor<FacesMessage> captor = ArgumentCaptor.forClass(FacesMessage.class);
        verify(faces).addMessage(any(), captor.capture());
        return captor.getValue();
    }

    private static Part archivo(byte[] contenido) throws IOException {
        Part p = org.mockito.Mockito.mock(Part.class);
        when(p.getSize()).thenReturn((long) contenido.length);
        when(p.getInputStream()).thenReturn(new ByteArrayInputStream(contenido));
        return p;
    }

    @Test
    @DisplayName("Al abrir la pantalla carga entregas, historial y recorrido")
    void cargar() {
        HojaDeRutaDTO h = new HojaDeRutaDTO();
        RecorridoDTO r = new RecorridoDTO();
        when(ruteo.entregasEnCursoDelRepartidor()).thenReturn(List.of(h));
        when(ruteo.historialDelRepartidor()).thenReturn(List.of());
        when(ruteo.recorridoRecomendado(List.of(h))).thenReturn(r);

        bean.cargar();

        assertEquals(List.of(h), bean.getEntregas());
        assertTrue(bean.getHistorial().isEmpty());
        assertEquals(r, bean.getRecorrido());
        assertTrue(bean.isCompartirUbicacion());
    }

    @Test
    @DisplayName("Una cuenta de repartidor sin repartidor asociado ve la pantalla vacía y un aviso")
    void cargarSinRepartidor() {
        when(ruteo.entregasEnCursoDelRepartidor())
                .thenThrow(new com.rabbit.seguridad.negocio.ValidacionException("Tu cuenta no tiene repartidor"));

        bean.cargar();

        assertTrue(bean.getEntregas().isEmpty());
        assertTrue(bean.getHistorial().isEmpty());
        assertTrue(bean.getRecorrido().isVacio());
        assertFalse(bean.isCompartirUbicacion());
        assertEquals(FacesMessage.SEVERITY_ERROR, ultimoMensaje().getSeverity());
    }

    @Test
    @DisplayName("Retirar despacha el pedido y avisa")
    void retirar() {
        bean.retirar(7L);

        verify(gestion).despacharPedido(7L);
        assertEquals(FacesMessage.SEVERITY_INFO, ultimoMensaje().getSeverity());
    }

    @Test
    @DisplayName("Si el negocio rechaza el retiro, el repartidor ve el motivo")
    void retirarRechazado() {
        doThrow(new ValidacionException("El pedido ya está en camino")).when(gestion).despacharPedido(7L);

        bean.retirar(7L);

        FacesMessage m = ultimoMensaje();
        assertEquals(FacesMessage.SEVERITY_ERROR, m.getSeverity());
        assertEquals("El pedido ya está en camino", m.getSummary());
    }

    @Test
    @DisplayName("Un error técnico se muestra sin detalles internos")
    void retirarConErrorTecnico() {
        doThrow(new EJBException("boom")).when(gestion).despacharPedido(7L);

        bean.retirar(7L);

        FacesMessage m = ultimoMensaje();
        assertEquals(FacesMessage.SEVERITY_ERROR, m.getSeverity());
        assertFalse(m.getSummary().contains("boom"));
    }

    @Test
    @DisplayName("Entregar registra la entrega con foto, código y ubicación, y limpia el formulario")
    void entregar() throws Exception {
        byte[] foto = { 1, 2, 3 };
        bean.setIdPedidoAEntregar(7L);
        bean.setFotoRemito(archivo(foto));
        bean.setCodigoEntrega("1234");
        bean.setLatitudEntrega(-34.6);
        bean.setLongitudEntrega(-58.4);
        bean.setPrecisionEntrega(12.0);

        bean.entregar();

        ArgumentCaptor<DatosEntregaDTO> datos = ArgumentCaptor.forClass(DatosEntregaDTO.class);
        verify(gestion).registrarEntrega(any(), any(byte[].class), datos.capture());
        assertNull(bean.getIdPedidoAEntregar());
        assertNull(bean.getCodigoEntrega());
        assertNull(bean.getFotoRemito());
    }

    @Test
    @DisplayName("Entregar sin elegir un pedido avisa y no llama al negocio")
    void entregarSinPedido() throws Exception {
        bean.setFotoRemito(archivo(new byte[] { 1 }));

        bean.entregar();

        assertEquals(FacesMessage.SEVERITY_ERROR, ultimoMensaje().getSeverity());
        verify(gestion, never()).registrarEntrega(any(), any(), any());
    }

    @Test
    @DisplayName("Una foto de más de 5 MB se rechaza antes de llegar al negocio")
    void entregarConFotoGrande() throws Exception {
        Part grande = org.mockito.Mockito.mock(Part.class);
        when(grande.getSize()).thenReturn(6L * 1024 * 1024);
        bean.setIdPedidoAEntregar(7L);
        bean.setFotoRemito(grande);

        bean.entregar();

        assertEquals(FacesMessage.SEVERITY_ERROR, ultimoMensaje().getSeverity());
        verify(gestion, never()).registrarEntrega(any(), any(), any());
    }

    @Test
    @DisplayName("Si no se puede leer la foto, avisa y descarta el archivo")
    void entregarConFotoIlegible() throws Exception {
        Part rota = org.mockito.Mockito.mock(Part.class);
        when(rota.getSize()).thenReturn(10L);
        when(rota.getInputStream()).thenThrow(new IOException("disco"));
        bean.setIdPedidoAEntregar(7L);
        bean.setFotoRemito(rota);

        bean.entregar();

        assertEquals(FacesMessage.SEVERITY_ERROR, ultimoMensaje().getSeverity());
        assertNull(bean.getFotoRemito());
        verify(gestion, never()).registrarEntrega(any(), any(), any());
    }

    @Test
    @DisplayName("Devolver registra la visita fallida con motivo, evidencia y ubicación, y limpia la ventana")
    void devolver() throws Exception {
        bean.setIdPedidoADevolver(7L);
        bean.setMotivoDevolucion(MotivoEntregaFallida.values()[0]);
        bean.setDetalleDevolucion("Nadie atendió");
        bean.setFoto1(archivo(new byte[] { 1 }));
        bean.setLatitudVisita(-34.6);
        bean.setLongitudVisita(-58.4);
        bean.setPrecisionVisita(8.0);

        bean.devolver();

        ArgumentCaptor<EntregaFallidaDTO> datos = ArgumentCaptor.forClass(EntregaFallidaDTO.class);
        verify(gestion).devolverADeposito(any(), datos.capture());
        assertEquals("Nadie atendió", datos.getValue().comentario);
        assertEquals(3, datos.getValue().fotos.size());
        assertEquals(-34.6, datos.getValue().latitud);
        assertNull(bean.getIdPedidoADevolver());
        assertNull(bean.getMotivoDevolucion());
        assertNull(bean.getDetalleDevolucion());
        assertNull(bean.getLatitudVisita());
        assertNull(bean.getFoto1());
    }

    @Test
    @DisplayName("Devolver sin pedido o sin motivo avisa y no llama al negocio")
    void devolverIncompleto() {
        bean.devolver();
        assertEquals(FacesMessage.SEVERITY_ERROR, ultimoMensaje().getSeverity());

        bean.setIdPedidoADevolver(7L);
        bean.devolver();
        verify(gestion, never()).devolverADeposito(any(), any());
    }

    @Test
    @DisplayName("Si el negocio rechaza la devolución, la ventana queda abierta para corregir")
    void devolverRechazado() {
        bean.setIdPedidoADevolver(7L);
        bean.setMotivoDevolucion(MotivoEntregaFallida.values()[0]);
        doThrow(new ValidacionException("Falta el detalle")).when(gestion).devolverADeposito(any(), any());

        bean.devolver();

        assertEquals(7L, bean.getIdPedidoADevolver());
    }

    @Test
    @DisplayName("Una foto de la devolución de más de 5 MB se rechaza")
    void devolverConFotoGrande() {
        Part grande = org.mockito.Mockito.mock(Part.class);
        when(grande.getSize()).thenReturn(6L * 1024 * 1024);
        bean.setIdPedidoADevolver(7L);
        bean.setMotivoDevolucion(MotivoEntregaFallida.values()[0]);
        bean.setFoto2(grande);

        bean.devolver();

        verify(gestion, never()).devolverADeposito(any(), any());
        assertNull(bean.getFoto2());
    }

    @Test
    @DisplayName("Si no se pueden leer las fotos de la devolución, avisa")
    void devolverConFotosIlegibles() throws Exception {
        Part rota = org.mockito.Mockito.mock(Part.class);
        when(rota.getSize()).thenReturn(10L);
        when(rota.getInputStream()).thenThrow(new IOException("disco"));
        bean.setIdPedidoADevolver(7L);
        bean.setMotivoDevolucion(MotivoEntregaFallida.values()[0]);
        bean.setFoto3(rota);

        bean.devolver();

        verify(gestion, never()).devolverADeposito(any(), any());
        assertNull(bean.getFoto3());
    }

    @Test
    @DisplayName("La ubicación se manda solo si llegaron latitud y longitud, y un fallo no se avisa")
    void compartirUbicacion() {
        bean.compartirUbicacion();
        verify(repartidores, never()).registrarUbicacion(anyDouble(), anyDouble());

        bean.setLatitud(-34.6);
        bean.setLongitud(-58.4);
        bean.compartirUbicacion();
        verify(repartidores).registrarUbicacion(-34.6, -58.4);

        doThrow(new EJBException("sin entregas")).when(repartidores).registrarUbicacion(anyDouble(), anyDouble());
        bean.compartirUbicacion();
        verify(faces, never()).addMessage(any(), any());
    }

    @Test
    @DisplayName("Los accesores guardan lo que manda la pantalla")
    void accesores() {
        bean.setLatitud(1.0);
        bean.setLongitud(2.0);
        bean.setLatitudEntrega(3.0);
        bean.setLongitudEntrega(4.0);
        bean.setPrecisionEntrega(5.0);
        bean.setLongitudVisita(6.0);
        bean.setPrecisionVisita(7.0);

        assertEquals(1.0, bean.getLatitud());
        assertEquals(2.0, bean.getLongitud());
        assertEquals(3.0, bean.getLatitudEntrega());
        assertEquals(4.0, bean.getLongitudEntrega());
        assertEquals(5.0, bean.getPrecisionEntrega());
        assertEquals(6.0, bean.getLongitudVisita());
        assertEquals(7.0, bean.getPrecisionVisita());
        assertFalse(bean.getMotivosDevolucion().isEmpty());
        assertTrue(bean.getMaximoIntentos() > 0);
    }
}
