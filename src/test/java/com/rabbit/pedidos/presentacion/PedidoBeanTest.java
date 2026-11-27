package com.rabbit.pedidos.presentacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbit.comercios.dto.ComercioDTO;
import com.rabbit.comercios.dto.PuntoPickingDTO;
import com.rabbit.comercios.negocio.IConsultaComercios;
import com.rabbit.inventario.dto.DepositoDTO;
import com.rabbit.inventario.dto.ItemInventarioDTO;
import com.rabbit.inventario.negocio.IConsultaStock;
import com.rabbit.pagos.dto.CobroDTO;
import com.rabbit.pagos.negocio.IConsultaCobros;
import com.rabbit.pedidos.datos.model.OrigenPedido;
import com.rabbit.pedidos.dto.DatosLineaPedidoDTO;
import com.rabbit.pedidos.dto.LineaPedidoDTO;
import com.rabbit.pedidos.dto.PedidoDTO;
import com.rabbit.pedidos.dto.PedidoExternoDTO;
import com.rabbit.pedidos.dto.UbicacionEntregaDTO;
import com.rabbit.pedidos.negocio.IGestionPedidos;
import com.rabbit.pedidos.negocio.ISeguimientoPedido;
import com.rabbit.pedidos.negocio.ValidacionException;
import com.rabbit.repartidores.dto.RepartidorDTO;
import com.rabbit.repartidores.negocio.IGestionRepartidores;
import com.rabbit.transportistas.dto.CotizacionDTO;
import com.rabbit.transportistas.dto.EnvioDTO;
import com.rabbit.transportistas.dto.TransportistaDTO;
import com.rabbit.transportistas.negocio.IEnvios;
import com.rabbit.transportistas.negocio.IGestionTransportistas;
import jakarta.ejb.EJBAccessException;
import jakarta.ejb.EJBException;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.servlet.http.Part;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
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
 * Pantalla de pedidos del personal (pedidos.xhtml): carga de la lista con sus datos relacionados y las
 * acciones (cotizar y derivar, confirmar, despachar, entregar, cancelar, cargar un pedido externo). Cada
 * acción tiene que mostrar el motivo cuando el negocio la rechaza y un mensaje genérico cuando falla algo técnico.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PedidoBeanTest {

    private abstract static class ContextoDePrueba extends FacesContext {
        static void usar(FacesContext contexto) {
            setCurrentInstance(contexto);
        }
    }

    @Mock private FacesContext faces;
    @Mock private IGestionPedidos gestion;
    @Mock private ISeguimientoPedido seguimiento;
    @Mock private IConsultaComercios comercios;
    @Mock private IConsultaStock stock;
    @Mock private IGestionRepartidores repartidores;
    @Mock private IConsultaCobros cobros;
    @Mock private IGestionTransportistas transportistas;
    @Mock private IEnvios envios;

    private final PedidoBean bean = new PedidoBean();

    @BeforeEach
    void preparar() throws Exception {
        ContextoDePrueba.usar(faces);
        inyectar("gestion", gestion);
        inyectar("seguimiento", seguimiento);
        inyectar("comercios", comercios);
        inyectar("stock", stock);
        inyectar("repartidores", repartidores);
        inyectar("cobros", cobros);
        inyectar("transportistas", transportistas);
        inyectar("envios", envios);

        when(seguimiento.listarTodos()).thenReturn(new ArrayList<>());
        when(seguimiento.listarPedidosExternos()).thenReturn(new ArrayList<>());
        when(comercios.listarTodos()).thenReturn(new ArrayList<>());
        when(stock.listarDepositos()).thenReturn(new ArrayList<>());
        when(repartidores.listarTodos()).thenReturn(new ArrayList<>());
        when(cobros.listarTodos()).thenReturn(new ArrayList<>());
        when(envios.listarEnvios()).thenReturn(new ArrayList<>());
        when(transportistas.listarActivos()).thenReturn(new ArrayList<>());
    }

    @AfterEach
    void limpiar() {
        ContextoDePrueba.usar(null);
    }

    private void inyectar(String campo, Object valor) throws Exception {
        var f = PedidoBean.class.getDeclaredField(campo);
        f.setAccessible(true);
        f.set(bean, valor);
    }

    private FacesMessage ultimoMensaje() {
        ArgumentCaptor<FacesMessage> captor = ArgumentCaptor.forClass(FacesMessage.class);
        verify(faces, atLeastOnce()).addMessage(any(), captor.capture());
        return captor.getAllValues().get(captor.getAllValues().size() - 1);
    }

    private static PedidoDTO pedido(Long id, String estado, OrigenPedido origen, Long idRepartidor) {
        PedidoDTO p = new PedidoDTO();
        p.id = id;
        p.estado = estado;
        p.origen = origen;
        p.idRepartidor = idRepartidor;
        return p;
    }

    private static TransportistaDTO transportista(Long id, String nombre) {
        TransportistaDTO t = new TransportistaDTO();
        t.id = id;
        t.nombre = nombre;
        return t;
    }

    private static Part archivo(byte[] contenido) throws IOException {
        Part p = org.mockito.Mockito.mock(Part.class);
        when(p.getSize()).thenReturn((long) contenido.length);
        when(p.getInputStream()).thenReturn(new ByteArrayInputStream(contenido));
        return p;
    }

    // ----- carga y consultas -----

    @Test
    @DisplayName("Al cargar arma las tablas de apoyo: productos, repartidores, cobros y envíos")
    void cargar() {
        LineaPedidoDTO linea = new LineaPedidoDTO();
        linea.idItem = 101L;
        PedidoExternoDTO externo = new PedidoExternoDTO();
        externo.lineas = List.of(linea);
        when(seguimiento.listarPedidosExternos()).thenReturn(List.of(externo));
        ItemInventarioDTO item = new ItemInventarioDTO();
        item.id = 101L;
        item.producto = "Yerba mate 1 kg";
        when(stock.listarItemsPorIds(any())).thenReturn(List.of(item));
        RepartidorDTO r = new RepartidorDTO();
        r.id = 5L;
        r.nombre = "Ana";
        when(repartidores.listarTodos()).thenReturn(List.of(r));
        CobroDTO cobro = new CobroDTO();
        cobro.idPedido = 7L;
        cobro.estado = "APROBADO";
        when(cobros.listarTodos()).thenReturn(List.of(cobro));
        EnvioDTO envio = new EnvioDTO();
        envio.idPedido = 7L;
        when(envios.listarEnvios()).thenReturn(List.of(envio));

        bean.cargar();

        assertEquals("Yerba mate 1 kg", bean.descripcionLineas(externo));
        assertEquals("Ana", bean.nombreRepartidor(5L));
        assertEquals("#9", bean.nombreRepartidor(9L));
        assertEquals("—", bean.nombreRepartidor(null));
        assertEquals("APROBADO", bean.estadoCobro(7L));
        assertSame(envio, bean.envio(7L));
    }

    @Test
    @DisplayName("Sin pedidos externos no consulta productos")
    void cargarSinPedidosExternos() {
        bean.cargar();

        verify(stock, never()).listarItemsPorIds(any());
        assertTrue(bean.getPedidosExternos().isEmpty());
    }

    @Test
    @DisplayName("Si la línea no trae producto ni se encuentra el ítem, muestra 'Ítem N'")
    void descripcionConItemDesconocido() {
        LineaPedidoDTO conNombre = new LineaPedidoDTO();
        conNombre.producto = "Café";
        LineaPedidoDTO desconocida = new LineaPedidoDTO();
        desconocida.idItem = 99L;
        PedidoExternoDTO externo = new PedidoExternoDTO();
        externo.lineas = List.of(conNombre, desconocida);
        when(seguimiento.listarPedidosExternos()).thenReturn(List.of(externo));
        when(stock.listarItemsPorIds(any())).thenReturn(List.of());

        bean.cargar();

        assertEquals("Café, Ítem 99", bean.descripcionLineas(externo));
    }

    @Test
    @DisplayName("Cuenta y filtra los pedidos por estado")
    void contarYFiltrar() {
        when(seguimiento.listarTodos()).thenReturn(List.of(
                pedido(1L, "PENDIENTE", OrigenPedido.STOCK_CONSIGNADO, null),
                pedido(2L, "PENDIENTE", OrigenPedido.STOCK_CONSIGNADO, null),
                pedido(3L, "EN_CAMINO", OrigenPedido.STOCK_CONSIGNADO, 5L),
                pedido(4L, "EN_CAMINO", OrigenPedido.STOCK_CONSIGNADO, null)));
        bean.cargar();

        assertEquals(2, bean.contarPedidos("PENDIENTE"));
        assertEquals(2, bean.getPedidosPendientes().size());
        assertEquals(1, bean.getEnCaminoConRepartidor().size());
        assertEquals(4, bean.getPedidos().size());
    }

    @Test
    @DisplayName("Los nombres de comercio, punto de picking y depósito caen a un texto con el número si no existen")
    void nombresConRespaldo() {
        PuntoPickingDTO punto = new PuntoPickingDTO();
        punto.id = 3L;
        punto.nombre = "Mostrador";
        ComercioDTO comercio = new ComercioDTO();
        comercio.id = 1L;
        comercio.nombre = "Almacén Sur";
        comercio.puntosPicking = List.of(punto);
        ComercioDTO sinPuntos = new ComercioDTO();
        sinPuntos.id = 2L;
        sinPuntos.nombre = "Sin puntos";
        DepositoDTO deposito = new DepositoDTO();
        deposito.id = 10L;
        deposito.nombre = "Central";
        when(comercios.listarTodos()).thenReturn(List.of(comercio, sinPuntos));
        when(stock.listarDepositos()).thenReturn(List.of(deposito));
        bean.cargar();

        assertEquals("Almacén Sur", bean.nombreComercio(1L));
        assertEquals("Comercio 8", bean.nombreComercio(8L));
        assertEquals("Mostrador", bean.nombrePuntoPicking(1L, 3L));
        assertEquals("Punto 4", bean.nombrePuntoPicking(1L, 4L));
        assertEquals("Punto 3", bean.nombrePuntoPicking(2L, 3L));
        assertEquals("—", bean.nombrePuntoPicking(1L, null));
        assertEquals("Central", bean.nombreDeposito(10L));
        assertEquals("Depósito 11", bean.nombreDeposito(11L));
        assertEquals("—", bean.nombreDeposito(null));
    }

    @Test
    @DisplayName("La ubicación de la entrega se consulta una sola vez por pedido y se reinicia al recargar")
    void ubicacionEnCache() {
        UbicacionEntregaDTO ubicacion = new UbicacionEntregaDTO(-34.6, -58.4, 10);
        when(seguimiento.consultarUbicacionDeEntrega(7L)).thenReturn(ubicacion);
        bean.cargar();

        assertSame(ubicacion, bean.ubicacionEntrega(7L));
        assertSame(ubicacion, bean.ubicacionEntrega(7L));
        assertNull(bean.ubicacionEntrega(8L));
        verify(seguimiento, times(1)).consultarUbicacionDeEntrega(7L);

        bean.cargar();
        bean.ubicacionEntrega(7L);
        verify(seguimiento, times(2)).consultarUbicacionDeEntrega(7L);
    }

    // ----- formulario de pedido externo -----

    @Test
    @DisplayName("El formulario arranca con una línea; se agregan líneas y no se puede quitar la última")
    void lineas() {
        assertEquals(1, bean.getNuevoPedido().getLineas().size());
        bean.agregarLinea();
        assertEquals(2, bean.getNuevoPedido().getLineas().size());

        DatosLineaPedidoDTO primera = bean.getNuevoPedido().getLineas().get(0);
        bean.quitarLinea(primera);
        assertEquals(1, bean.getNuevoPedido().getLineas().size());

        bean.quitarLinea(bean.getNuevoPedido().getLineas().get(0));
        assertEquals(1, bean.getNuevoPedido().getLineas().size());
    }

    @Test
    @DisplayName("Los ítems y los puntos de picking dependen del comercio y del depósito elegidos")
    void listasDependientes() {
        assertTrue(bean.getListaItems().isEmpty());
        assertTrue(bean.getListaPuntosPicking().isEmpty());

        bean.getNuevoPedido().setIdComercio(1L);
        assertTrue(bean.getListaItems().isEmpty());
        ItemInventarioDTO item = new ItemInventarioDTO();
        PuntoPickingDTO punto = new PuntoPickingDTO();
        when(comercios.listarPuntosPicking(1L)).thenReturn(List.of(punto));
        assertEquals(List.of(punto), bean.getListaPuntosPicking());

        bean.setIdDepositoSeleccionado(10L);
        when(stock.listarItemsPorComercioYDeposito(1L, 10L)).thenReturn(List.of(item));
        assertEquals(List.of(item), bean.getListaItems());
    }

    @Test
    @DisplayName("Al cambiar el comercio o el depósito se limpian el punto de picking y los ítems elegidos")
    void contextoCambiado() {
        bean.getNuevoPedido().setIdPuntoPicking(3L);
        bean.getNuevoPedido().getLineas().get(0).setIdItem(101L);

        bean.onContextoCambiado();

        assertNull(bean.getNuevoPedido().getIdPuntoPicking());
        assertNull(bean.getNuevoPedido().getLineas().get(0).getIdItem());
        assertTrue(bean.getOrigenes().length > 0);
        assertTrue(bean.getMediosPago().length > 0);
    }

    @Test
    @DisplayName("Registrar un pedido externo lo manda a la cola y deja el formulario vacío")
    void registrarPedidoExterno() {
        bean.getNuevoPedido().setIdComercio(1L);
        bean.setIdDepositoSeleccionado(10L);

        bean.registrarPedidoExterno();

        verify(gestion).registrarPedidoExterno(any(), eq(null));
        assertNull(bean.getNuevoPedido().getIdComercio());
        assertNull(bean.getIdDepositoSeleccionado());
        assertEquals(FacesMessage.SEVERITY_INFO, ultimoMensaje().getSeverity());
    }

    @Test
    @DisplayName("Un pedido externo inválido conserva lo que se cargó y muestra el motivo")
    void registrarPedidoExternoRechazado() {
        bean.getNuevoPedido().setIdComercio(1L);
        doThrow(new ValidacionException("Falta la dirección")).when(gestion).registrarPedidoExterno(any(), any());

        bean.registrarPedidoExterno();

        assertEquals(1L, bean.getNuevoPedido().getIdComercio());
        assertEquals("Falta la dirección", ultimoMensaje().getSummary());
    }

    @Test
    @DisplayName("Un error técnico al registrar un pedido externo no expone detalles")
    void registrarPedidoExternoConErrorTecnico() {
        doThrow(new EJBException("sql")).when(gestion).registrarPedidoExterno(any(), any());

        bean.registrarPedidoExterno();

        assertFalse(ultimoMensaje().getSummary().contains("sql"));
    }

    // ----- cotizar y derivar -----

    @Test
    @DisplayName("Cotizar guarda las cotizaciones y avisa si no hay empresas activas")
    void cotizar() {
        bean.setIdPedidoADerivar(7L);
        when(gestion.cotizarDerivacion(7L)).thenReturn(List.of(new CotizacionDTO()));

        bean.cotizar();

        assertEquals(1, bean.getCotizaciones().size());
        assertEquals(7L, bean.getIdPedidoCotizado());

        when(gestion.cotizarDerivacion(7L)).thenReturn(List.of());
        bean.cotizar();
        assertEquals(FacesMessage.SEVERITY_ERROR, ultimoMensaje().getSeverity());
    }

    @Test
    @DisplayName("Si la cotización falla, se descartan las anteriores y se avisa")
    void cotizarConErrores() {
        bean.setIdPedidoADerivar(7L);
        doThrow(new ValidacionException("Pedido ya derivado")).when(gestion).cotizarDerivacion(7L);
        bean.cotizar();
        assertNull(bean.getCotizaciones());
        assertEquals("Pedido ya derivado", ultimoMensaje().getSummary());

        doThrow(new EJBException("timeout")).when(gestion).cotizarDerivacion(7L);
        bean.cotizar();
        assertNull(bean.getCotizaciones());
        assertFalse(ultimoMensaje().getSummary().contains("timeout"));
    }

    @Test
    @DisplayName("Derivar con una cotización usa el pedido cotizado, nombra a la empresa y limpia el estado")
    void derivarCon() {
        TransportistaDTO t = transportista(4L, "Andreani");
        when(transportistas.listarActivos()).thenReturn(List.of(t));
        bean.cargar();
        bean.setIdPedidoADerivar(7L);
        when(gestion.cotizarDerivacion(7L)).thenReturn(List.of(new CotizacionDTO()));
        bean.cotizar();
        when(gestion.derivarATransportista(7L, 4L)).thenReturn("SEG-1");

        bean.derivarCon(4L);

        verify(gestion).derivarATransportista(7L, 4L);
        assertTrue(ultimoMensaje().getSummary().contains("Andreani"));
        assertNull(bean.getIdPedidoADerivar());
        assertNull(bean.getIdTransportistaElegido());
        assertNull(bean.getCotizaciones());
        assertNull(bean.getIdPedidoCotizado());
    }

    @Test
    @DisplayName("Si la empresa ya no figura entre las activas, igual avisa con un nombre genérico")
    void derivarEmpresaDesconocida() {
        bean.cargar();
        bean.setIdPedidoADerivar(7L);
        bean.setIdTransportistaElegido(4L);
        when(gestion.derivarATransportista(7L, 4L)).thenReturn("SEG-1");

        bean.derivar();

        assertTrue(ultimoMensaje().getSummary().contains("la empresa de logística"));
    }

    @Test
    @DisplayName("Derivar rechazado o con error técnico avisa y no recarga")
    void derivarConErrores() {
        bean.cargar();
        bean.setIdPedidoADerivar(7L);
        bean.setIdTransportistaElegido(4L);
        doThrow(new ValidacionException("No se puede derivar")).when(gestion).derivarATransportista(7L, 4L);
        bean.derivar();
        assertEquals("No se puede derivar", ultimoMensaje().getSummary());

        doThrow(new EJBException("x")).when(gestion).derivarATransportista(7L, 4L);
        bean.derivar();
        assertEquals(FacesMessage.SEVERITY_ERROR, ultimoMensaje().getSeverity());
        verify(seguimiento, times(1)).listarTodos();
    }

    @Test
    @DisplayName("Si la derivación salió bien pero falla recargar la lista, lo avisa")
    void derivarYFallaLaRecarga() {
        bean.cargar();
        bean.setIdPedidoADerivar(7L);
        bean.setIdTransportistaElegido(4L);
        when(gestion.derivarATransportista(7L, 4L)).thenReturn("SEG-1");
        when(seguimiento.listarTodos()).thenThrow(new EJBException("db"));

        bean.derivar();

        assertTrue(ultimoMensaje().getSummary().contains("recargá la página"));
    }

    // ----- confirmar, retirar, despachar -----

    @Test
    @DisplayName("Confirmar con repartidor asignado avisa que quedó confirmado")
    void confirmarConRepartidor() {
        when(gestion.confirmarPedido(7L)).thenReturn(5L);

        bean.confirmar(7L);

        assertEquals("Pedido confirmado", ultimoMensaje().getSummary());
    }

    @Test
    @DisplayName("Confirmar un retiro en depósito avisa que está listo para retirar")
    void confirmarRetiroEnDeposito() {
        when(gestion.confirmarPedido(7L)).thenReturn(null);
        PedidoDTO estado = new PedidoDTO();
        estado.tipoEntrega = com.rabbit.pedidos.datos.model.TipoEntrega.RETIRO_EN_DEPOSITO;
        when(seguimiento.consultarEstadoPedido(7L)).thenReturn(estado);

        bean.confirmar(7L);

        assertTrue(ultimoMensaje().getSummary().contains("listo para retirar"));
    }

    @Test
    @DisplayName("Confirmar sin repartidor disponible deja el pedido en lista de espera con el motivo")
    void confirmarEnEspera() {
        when(gestion.confirmarPedido(7L)).thenReturn(null);
        PedidoDTO estado = new PedidoDTO();
        estado.motivoEspera = "sin repartidores libres";
        when(seguimiento.consultarEstadoPedido(7L)).thenReturn(estado);

        bean.confirmar(7L);

        String texto = ultimoMensaje().getSummary();
        assertTrue(texto.contains("lista de espera"));
        assertTrue(texto.contains("sin repartidores libres"));
    }

    @Test
    @DisplayName("Confirmar rechazado o con error técnico muestra el aviso correspondiente")
    void confirmarConErrores() {
        doThrow(new ValidacionException("Sin stock")).when(gestion).confirmarPedido(7L);
        bean.confirmar(7L);
        assertEquals("Sin stock", ultimoMensaje().getSummary());

        doThrow(new EJBException("x")).when(gestion).confirmarPedido(7L);
        bean.confirmar(7L);
        assertFalse(ultimoMensaje().getSummary().contains("x."));
    }

    @Test
    @DisplayName("Entregar en el depósito usa el código tipeado y lo borra")
    void entregarEnDeposito() {
        bean.getCodigosDeRetiro().put(7L, "1234");

        bean.entregarEnDeposito(7L);

        verify(gestion).entregarEnDeposito(7L, "1234");
        assertFalse(bean.getCodigosDeRetiro().containsKey(7L));
    }

    @Test
    @DisplayName("Entregar en el depósito con código incorrecto muestra el motivo y conserva el código")
    void entregarEnDepositoRechazado() {
        bean.getCodigosDeRetiro().put(7L, "0000");
        doThrow(new ValidacionException("Código incorrecto")).when(gestion).entregarEnDeposito(7L, "0000");
        bean.entregarEnDeposito(7L);
        assertEquals("Código incorrecto", ultimoMensaje().getSummary());
        assertTrue(bean.getCodigosDeRetiro().containsKey(7L));

        doThrow(new EJBException("x")).when(gestion).entregarEnDeposito(7L, "0000");
        bean.entregarEnDeposito(7L);
        assertEquals(FacesMessage.SEVERITY_ERROR, ultimoMensaje().getSeverity());
    }

    @Test
    @DisplayName("Despachar avisa que el pedido está en camino y muestra los errores")
    void despachar() {
        bean.despachar(7L);
        assertEquals("Pedido en camino", ultimoMensaje().getSummary());

        doThrow(new ValidacionException("Estado inválido")).when(gestion).despacharPedido(8L);
        bean.despachar(8L);
        assertEquals("Estado inválido", ultimoMensaje().getSummary());

        doThrow(new EJBException("x")).when(gestion).despacharPedido(9L);
        bean.despachar(9L);
        assertEquals(FacesMessage.SEVERITY_ERROR, ultimoMensaje().getSeverity());
    }

    // ----- entregar -----

    @Test
    @DisplayName("Entregar registra la entrega con la foto del remito y limpia el formulario")
    void entregar() throws Exception {
        bean.setIdPedidoAEntregar(7L);
        bean.setFotoRemito(archivo(new byte[] { 1, 2 }));

        bean.entregar();

        verify(gestion).registrarEntrega(eq(7L), any(byte[].class), eq(null));
        assertNull(bean.getIdPedidoAEntregar());
        assertNull(bean.getFotoRemito());
    }

    @Test
    @DisplayName("Una foto mayor a 5 MB o ilegible se rechaza antes de llegar al negocio")
    void entregarConFotoInvalida() throws Exception {
        Part grande = org.mockito.Mockito.mock(Part.class);
        when(grande.getSize()).thenReturn(6L * 1024 * 1024);
        bean.setFotoRemito(grande);
        bean.entregar();

        Part rota = org.mockito.Mockito.mock(Part.class);
        when(rota.getSize()).thenReturn(10L);
        when(rota.getInputStream()).thenThrow(new IOException("disco"));
        bean.setFotoRemito(rota);
        bean.entregar();

        verify(gestion, never()).registrarEntrega(anyLong(), any(), any());
        assertEquals(FacesMessage.SEVERITY_ERROR, ultimoMensaje().getSeverity());
    }

    @Test
    @DisplayName("Si el negocio rechaza la entrega, el pedido sigue elegido para corregir")
    void entregarRechazado() throws Exception {
        bean.setIdPedidoAEntregar(7L);
        bean.setFotoRemito(archivo(new byte[] { 1 }));
        doThrow(new ValidacionException("Falta el remito")).when(gestion).registrarEntrega(anyLong(), any(), any());
        bean.entregar();
        assertEquals(7L, bean.getIdPedidoAEntregar());

        bean.setFotoRemito(archivo(new byte[] { 1 }));
        doThrow(new EJBException("x")).when(gestion).registrarEntrega(anyLong(), any(), any());
        bean.entregar();
        assertEquals(FacesMessage.SEVERITY_ERROR, ultimoMensaje().getSeverity());
    }

    // ----- cancelar -----

    @Test
    @DisplayName("Cancelar un pedido con stock consignado avisa que el stock volvió al disponible")
    void cancelarConStock() {
        when(seguimiento.listarTodos()).thenReturn(List.of(pedido(7L, "PENDIENTE", OrigenPedido.STOCK_CONSIGNADO, null)));
        bean.cargar();

        bean.cancelar(7L);

        verify(gestion).cancelarPedido(7L);
        assertTrue(ultimoMensaje().getSummary().contains("el stock volvió"));
    }

    @Test
    @DisplayName("Cancelar un pedido sin stock propio solo avisa que se canceló")
    void cancelarSinStock() {
        when(seguimiento.listarTodos()).thenReturn(List.of(pedido(7L, "PENDIENTE", OrigenPedido.values()[OrigenPedido.values().length - 1], null)));
        bean.cargar();

        bean.cancelar(7L);

        verify(gestion).cancelarPedido(7L);
    }

    @Test
    @DisplayName("Cancelar sin permiso explica que lo hace un administrador; los demás errores se muestran")
    void cancelarConErrores() {
        bean.cargar();
        doThrow(new EJBAccessException("no")).when(gestion).cancelarPedido(7L);
        bean.cancelar(7L);
        assertTrue(ultimoMensaje().getSummary().contains("administrador"));

        doThrow(new ValidacionException("Ya entregado")).when(gestion).cancelarPedido(8L);
        bean.cancelar(8L);
        assertEquals("Ya entregado", ultimoMensaje().getSummary());

        doThrow(new EJBException("x")).when(gestion).cancelarPedido(9L);
        bean.cancelar(9L);
        assertEquals(FacesMessage.SEVERITY_ERROR, ultimoMensaje().getSeverity());
    }

    @Test
    @DisplayName("Los accesores de la pantalla devuelven lo cargado")
    void accesores() {
        bean.cargar();
        bean.setNuevoPedido(bean.getNuevoPedido());

        assertTrue(bean.getListaComercios().isEmpty());
        assertTrue(bean.getListaDepositos().isEmpty());
        assertTrue(bean.getListaTransportistas().isEmpty());
        assertNull(bean.getIdPedidoADerivar());
    }
}
