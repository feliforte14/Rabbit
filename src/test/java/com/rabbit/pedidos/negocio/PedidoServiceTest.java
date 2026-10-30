package com.rabbit.pedidos.negocio;

import static com.rabbit.pruebas.Ids.conId;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbit.comercios.dto.PuntoPickingDTO;
import com.rabbit.comercios.negocio.IConsultaComercios;
import com.rabbit.inventario.dto.DepositoDTO;
import com.rabbit.inventario.dto.ItemInventarioDTO;
import com.rabbit.inventario.dto.ReservaStockDTO;
import com.rabbit.inventario.negocio.IConsultaStock;
import com.rabbit.inventario.negocio.IReservaStock;
import com.rabbit.inventario.negocio.StockModificadoException;
import com.rabbit.pagos.dto.MedioPago;
import com.rabbit.pagos.negocio.IRegistroCobros;
import com.rabbit.pedidos.datos.PedidoRepository;
import com.rabbit.pedidos.datos.model.CalificacionEntrega;
import com.rabbit.pedidos.datos.model.EstadoPedido;
import com.rabbit.pedidos.datos.model.FotoIntentoEntrega;
import com.rabbit.pedidos.datos.model.IntentoEntrega;
import com.rabbit.pedidos.datos.model.LineaPedido;
import com.rabbit.pedidos.datos.model.LineaPedidoExterno;
import com.rabbit.pedidos.datos.model.MotivoEntregaFallida;
import com.rabbit.pedidos.datos.model.OrigenPedido;
import com.rabbit.pedidos.datos.model.OrigenUbicacion;
import com.rabbit.pedidos.datos.model.Pedido;
import com.rabbit.pedidos.datos.model.PedidoExterno;
import com.rabbit.pedidos.datos.model.RemitoEntrega;
import com.rabbit.pedidos.datos.model.TipoEntrega;
import com.rabbit.pedidos.dto.CalificacionDTO;
import com.rabbit.pedidos.dto.DatosEntregaDTO;
import com.rabbit.pedidos.dto.DatosLineaPedidoDTO;
import com.rabbit.pedidos.dto.DatosPedidoExternoDTO;
import com.rabbit.pedidos.dto.EntregaFallidaDTO;
import com.rabbit.pedidos.dto.IntentoEntregaDTO;
import com.rabbit.pedidos.dto.PedidoDTO;
import com.rabbit.pedidos.dto.PedidoExternoDTO;
import com.rabbit.pedidos.dto.ResumenEntregasDTO;
import com.rabbit.repartidores.dto.UbicacionDTO;
import com.rabbit.repartidores.negocio.IAsignacionRepartidores;
import com.rabbit.repartidores.negocio.SinLugarException;
import com.rabbit.seguridad.negocio.IContextoUsuario;
import com.rabbit.transportistas.dto.CotizacionDTO;
import com.rabbit.transportistas.dto.DatosEnvioDTO;
import com.rabbit.transportistas.dto.EnvioDTO;
import com.rabbit.transportistas.negocio.IEnvios;
import jakarta.ejb.SessionContext;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Instance;
import jakarta.persistence.PersistenceException;
import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PedidoServiceTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};

    @Mock private PedidoRepository repository;
    @Mock private IConsultaComercios comercios;
    @Mock private Instance<IReservaStock> reservaProvider;
    @Mock private Event<PedidoExternoRegistrado> pedidoExternoRegistrado;
    @Mock private Event<EstadoPedidoCambiado> estadoPedidoCambiado;
    @Mock private IAsignacionRepartidores repartidores;
    @Mock private IRegistroCobros cobros;
    @Mock private IEnvios envios;
    @Mock private IConsultaStock stock;
    @Mock private IContextoUsuario contextoUsuario;
    @Mock private SessionContext contexto;
    @Mock private IReservaStock reserva;
    @Mock private Principal usuario;
    @InjectMocks private PedidoService servicio;

    @BeforeEach
    void contexto() {
        when(reservaProvider.get()).thenReturn(reserva);
        when(contexto.getCallerPrincipal()).thenReturn(usuario);
        when(usuario.getName()).thenReturn("moto5");
        when(repartidores.diaDeEntregaPara(any())).thenReturn(LocalDate.of(2026, 11, 2));
        when(repository.guardarPedidoExterno(any())).thenAnswer(inv -> conId(inv.getArgument(0), 77L));
        when(repository.guardarPedido(any())).thenAnswer(inv -> conId(inv.getArgument(0), 100L));
        org.mockito.Mockito.doAnswer(inv -> conId(inv.getArgument(0), 1L)).when(repository).guardarIntento(any());
        DepositoDTO deposito = new DepositoDTO();
        deposito.id = 1L;
        deposito.nombre = "Norte";
        deposito.direccion = "Av 1";
        deposito.localidad = "Martínez";
        when(stock.obtenerDeposito(1L)).thenReturn(deposito);
    }

    private Pedido pedido(EstadoPedido estado) {
        Pedido p = conId(new Pedido(), 100L);
        p.setIdComercio(3L);
        p.setOrigen(OrigenPedido.STOCK_CONSIGNADO);
        p.setImporte(new BigDecimal("1000"));
        p.setMedioPago(MedioPago.PREPAGO);
        p.setDireccionEntrega("Corrientes 1234");
        p.setCodigoPostalEntrega("1043");
        p.setCodigoSeguimiento("RB-ABC");
        p.setPesoKg(new BigDecimal("3"));
        p.setEstado(estado);
        p.setTipoEntrega(TipoEntrega.DOMICILIO);
        LineaPedido linea = new LineaPedido();
        linea.setPedido(p);
        linea.setIdItem(10L);
        linea.setProducto("Yerba");
        linea.setCantidad(2);
        linea.setIdReservaStock(50L);
        p.setLineas(new ArrayList<>(List.of(linea)));
        when(repository.buscarPedidoParaActualizar(100L)).thenReturn(p);
        when(repository.buscarPedidoPorId(100L)).thenReturn(p);
        when(repository.buscarPedidoConLineas(100L)).thenReturn(p);
        when(repository.buscarPedidoPorCodigoSeguimiento("RB-ABC")).thenReturn(p);
        return p;
    }

    private void comoRol(String rol) {
        when(contexto.isCallerInRole(rol)).thenReturn(true);
    }

    @Nested
    class RegistroDePedidosExternos {

        private DatosPedidoExternoDTO datos() {
            DatosPedidoExternoDTO d = new DatosPedidoExternoDTO();
            d.idComercio = 3L;
            d.importe = new BigDecimal("1000");
            d.medioPago = MedioPago.PREPAGO;
            d.direccionEntrega = "  Corrientes 1234 ";
            d.codigoPostalEntrega = "1043";
            d.emailDestinatario = "ana@mail.com";
            d.nombreDestinatario = "Ana";
            DatosLineaPedidoDTO l = new DatosLineaPedidoDTO();
            l.idItem = 10L;
            l.cantidad = 2;
            d.lineas = new ArrayList<>(List.of(l));
            return d;
        }

        private String error(DatosPedidoExternoDTO d) {
            return assertThrows(ValidacionException.class, () -> servicio.registrarPedidoExterno(d, null)).getMessage();
        }

        @Test
        void guardaElPedidoExternoYAvisaQueLlego() {
            assertEquals(77L, servicio.registrarPedidoExterno(datos(), null));

            ArgumentCaptor<PedidoExterno> e = ArgumentCaptor.forClass(PedidoExterno.class);
            verify(repository).guardarPedidoExterno(e.capture());
            PedidoExterno externo = e.getValue();
            assertEquals(3L, externo.getIdComercio());
            assertEquals("Corrientes 1234", externo.getDireccionEntrega());
            assertEquals("1043", externo.getCodigoPostalEntrega());
            assertEquals(LocalDate.of(2026, 11, 2), externo.getFechaPrometida());
            assertEquals(TipoEntrega.DOMICILIO, externo.getTipoEntrega());
            assertEquals(1, externo.getCantidadDeBultos());
            assertEquals("ana@mail.com", externo.getEmailDestinatario());
            assertFalse(externo.isSincronizado());
            assertEquals(10L, externo.getLineas().get(0).getIdItem());
            assertEquals(2, externo.getLineas().get(0).getCantidad());
            ArgumentCaptor<PedidoExternoRegistrado> aviso = ArgumentCaptor.forClass(PedidoExternoRegistrado.class);
            verify(pedidoExternoRegistrado).fire(aviso.capture());
            assertEquals(new PedidoExternoRegistrado(77L, OrigenPedido.STOCK_CONSIGNADO), aviso.getValue());
        }

        @Test
        void elErpSoloCargaPedidosDeSuComercio() {
            comoRol("ERP");
            when(contextoUsuario.idComercioActual()).thenReturn(9L);
            DatosPedidoExternoDTO d = datos();

            servicio.registrarPedidoExterno(d, null);

            assertEquals(9L, d.idComercio);
            when(contextoUsuario.idComercioActual()).thenThrow(new com.rabbit.seguridad.negocio.ValidacionException("sin"));
            assertThrows(CuentaErpSinComercioException.class, () -> servicio.registrarPedidoExterno(datos(), null));
        }

        @Test
        void validaLosDatosObligatorios() {
            DatosPedidoExternoDTO d = datos();
            d.idComercio = null;
            assertEquals("Debe indicar el comercio del pedido", error(d));
            d = datos();
            d.lineas = List.of();
            assertEquals("El pedido debe tener al menos un producto", error(d));
            d.lineas = null;
            assertEquals("El pedido debe tener al menos un producto", error(d));
            d = datos();
            d.importe = BigDecimal.ZERO;
            assertEquals("El importe del pedido debe ser mayor a cero", error(d));
            d.importe = null;
            assertEquals("El importe del pedido debe ser mayor a cero", error(d));
            d = datos();
            d.medioPago = null;
            assertEquals("Debe indicar el medio de pago del pedido", error(d));
            d = datos();
            d.direccionEntrega = " ";
            assertEquals("Debe indicar la dirección de entrega del pedido", error(d));
            d.direccionEntrega = null;
            assertEquals("Debe indicar la dirección de entrega del pedido", error(d));
            d.direccionEntrega = "x".repeat(201);
            assertEquals("La dirección de entrega no puede superar los 200 caracteres", error(d));
            d = datos();
            d.codigoPostalEntrega = "12";
            assertTrue(error(d).startsWith("El código postal de entrega tiene que tener 4 dígitos"));
            d = datos();
            d.lineas.get(0).idItem = null;
            assertEquals("Debe indicar el producto del pedido", error(d));
            d = datos();
            d.lineas.get(0).cantidad = 0;
            assertEquals("La cantidad debe ser mayor a cero", error(d));
            d = datos();
            d.emailDestinatario = null;
            assertEquals(Destinatarios.FALTA_EMAIL, error(d));
            verify(repository, never()).guardarPedidoExterno(any());
        }

        @Test
        void elPesoSoloLoInformaUnPuntoDePickingYTieneLimites() {
            DatosPedidoExternoDTO d = datos();
            d.pesoKg = new BigDecimal("2");
            assertTrue(error(d).startsWith("El peso solo se informa en pedidos de punto de picking"));
            d.origen = OrigenPedido.PUNTO_PICKING;
            d.idPuntoPicking = 30L;
            d.lineas.get(0).producto = "Caja";
            d.pesoKg = BigDecimal.ZERO;
            assertEquals("El peso del pedido debe ser mayor a cero", error(d));
            d.pesoKg = new BigDecimal("5000.001");
            assertEquals("El peso del pedido no puede superar los 5000 kg", error(d));
            d.pesoKg = new BigDecimal("1.2345");
            assertEquals("El peso admite hasta 3 decimales (gramos)", error(d));
            d.pesoKg = new BigDecimal("5000");
            assertEquals(77L, servicio.registrarPedidoExterno(d, null));
        }

        @Test
        void unPedidoDePuntoDePickingIndicaQueRetirarYDonde() {
            DatosPedidoExternoDTO d = datos();
            d.origen = OrigenPedido.PUNTO_PICKING;
            d.pesoKg = new BigDecimal("1.5");
            d.lineas.get(0).idItem = null;
            assertEquals("Debe indicar el punto de picking del pedido", error(d));
            d.idPuntoPicking = 30L;
            assertEquals("Debe indicar qué se va a retirar del punto de picking", error(d));
            d.lineas.get(0).producto = "  Caja de libros ";
            d.lineas.get(0).cantidad = -1;
            assertEquals("La cantidad debe ser mayor a cero", error(d));
            d.lineas.get(0).cantidad = 1;

            servicio.registrarPedidoExterno(d, null);

            ArgumentCaptor<PedidoExterno> e = ArgumentCaptor.forClass(PedidoExterno.class);
            verify(repository).guardarPedidoExterno(e.capture());
            assertEquals(30L, e.getValue().getIdPuntoPicking());
            assertEquals("Caja de libros", e.getValue().getLineas().get(0).getProducto());
            assertEquals(new BigDecimal("1.5"), e.getValue().getPesoKg());
        }

        @Test
        void conRetiroEnDepositoNoHayDireccionNiFechaPrometida() {
            DatosPedidoExternoDTO d = datos();
            d.tipoEntrega = TipoEntrega.RETIRO_EN_DEPOSITO;
            d.direccionEntrega = null;
            d.codigoPostalEntrega = null;
            d.bultos = 3;

            servicio.registrarPedidoExterno(d, null);

            ArgumentCaptor<PedidoExterno> e = ArgumentCaptor.forClass(PedidoExterno.class);
            verify(repository).guardarPedidoExterno(e.capture());
            assertEquals(ReglaDeRetiro.DIRECCION_PROVISORIA, e.getValue().getDireccionEntrega());
            assertNull(e.getValue().getCodigoPostalEntrega());
            assertNull(e.getValue().getFechaPrometida());
            assertEquals(3, e.getValue().getCantidadDeBultos());
            d.origen = OrigenPedido.PUNTO_PICKING;
            assertTrue(error(d).startsWith("El retiro en depósito solo se puede pedir con stock consignado"));
        }

        @Test
        void sinCodigoPostalReconocibleNoPrometeFecha() {
            DatosPedidoExternoDTO d = datos();
            d.codigoPostalEntrega = null;
            d.direccionEntrega = "Calle sin número";

            servicio.registrarPedidoExterno(d, null);

            ArgumentCaptor<PedidoExterno> e = ArgumentCaptor.forClass(PedidoExterno.class);
            verify(repository).guardarPedidoExterno(e.capture());
            assertNull(e.getValue().getCodigoPostalEntrega());
            assertNull(e.getValue().getFechaPrometida());
        }

        @Test
        void conLaMismaClaveYElMismoPedidoDevuelveElQueYaExiste() {
            DatosPedidoExternoDTO d = datos();
            PedidoExterno anterior = conId(new PedidoExterno(), 70L);
            anterior.setHuellaSolicitud(HuellaDePedido.de(d, OrigenPedido.STOCK_CONSIGNADO));
            when(repository.buscarPedidoExternoPorClave(3L, "K1")).thenReturn(anterior);

            assertEquals(70L, servicio.registrarPedidoExterno(d, "K1"));
            verify(repository, never()).guardarPedidoExterno(any());

            anterior.setHuellaSolicitud("otra");
            assertThrows(ClaveIdempotenciaReutilizadaException.class, () -> servicio.registrarPedidoExterno(datos(), "K1"));
        }

        @Test
        void unaClaveNuevaQuedaGuardadaConSuHuella() {
            DatosPedidoExternoDTO d = datos();

            servicio.registrarPedidoExterno(d, "K2");

            ArgumentCaptor<PedidoExterno> e = ArgumentCaptor.forClass(PedidoExterno.class);
            verify(repository).guardarPedidoExterno(e.capture());
            assertEquals("K2", e.getValue().getClaveIdempotencia());
            assertEquals(HuellaDePedido.de(d, OrigenPedido.STOCK_CONSIGNADO), e.getValue().getHuellaSolicitud());
        }
    }

    @Nested
    class Sincronizacion {

        private PedidoExterno externo;

        @BeforeEach
        void externo() {
            externo = conId(new PedidoExterno(), 77L);
            externo.setIdComercio(3L);
            externo.setOrigen(OrigenPedido.STOCK_CONSIGNADO);
            externo.setImporte(new BigDecimal("1000"));
            externo.setMedioPago(MedioPago.PREPAGO);
            externo.setDireccionEntrega("Corrientes 1234");
            externo.setCodigoPostalEntrega("1043");
            externo.setEmailDestinatario("ana@mail.com");
            externo.setTipoEntrega(TipoEntrega.DOMICILIO);
            externo.setCantidadDeBultos(1);
            externo.setSincronizado(false);
            LineaPedidoExterno l = new LineaPedidoExterno();
            l.setPedidoExterno(externo);
            l.setIdItem(10L);
            l.setCantidad(2);
            externo.setLineas(new ArrayList<>(List.of(l)));
            when(repository.buscarPedidoExternoParaActualizar(77L)).thenReturn(externo);
            when(comercios.validarComercioActivo(3L)).thenReturn(true);
            ReservaStockDTO r = new ReservaStockDTO();
            r.id = 50L;
            r.producto = "Yerba";
            when(reserva.reservarStock(3L, 10L, 2)).thenReturn(r);
            when(stock.pesosPorItem(any())).thenReturn(Map.of(10L, new BigDecimal("1.5")));
        }

        private Pedido guardado() {
            ArgumentCaptor<Pedido> p = ArgumentCaptor.forClass(Pedido.class);
            verify(repository).guardarPedido(p.capture());
            return p.getValue();
        }

        @Test
        void creaElPedidoComprometiendoElStockDeCadaLinea() {
            assertEquals(100L, servicio.sincronizarPedidoExterno(77L));

            Pedido p = guardado();
            assertEquals(EstadoPedido.PENDIENTE, p.getEstado());
            assertEquals("Corrientes 1234", p.getDireccionEntrega());
            assertEquals("ana@mail.com", p.getEmailDestinatario());
            assertEquals(0, new BigDecimal("3.0").compareTo(p.getPesoKg()));
            assertEquals(50L, p.getLineas().get(0).getIdReservaStock());
            assertEquals("Yerba", p.getLineas().get(0).getProducto());
            assertNotNull(p.getCodigoSeguimiento());
            verify(reserva).confirmarReserva();
            verify(reservaProvider).destroy(reserva);
            assertTrue(externo.isSincronizado());
            assertEquals(100L, externo.getIdPedido());
            verify(repository).actualizarPedidoExterno(externo);
            verify(estadoPedidoCambiado).fire(any());
        }

        @Test
        void siUnItemNoTienePesoElTotalQuedaSinConocer() {
            when(stock.pesosPorItem(any())).thenReturn(Map.of());

            servicio.sincronizarPedidoExterno(77L);

            assertNull(guardado().getPesoKg());
        }

        @Test
        void noSeSincronizaDosVecesNiUnoQueNoExiste() {
            externo.setSincronizado(true);
            assertThrows(PedidoYaSincronizadoException.class, () -> servicio.sincronizarPedidoExterno(77L));
            assertEquals("Pedido externo no encontrado: 78",
                    assertThrows(PedidoNoEncontradoException.class, () -> servicio.sincronizarPedidoExterno(78L)).getMessage());
        }

        @Test
        void elComercioTieneQueEstarActivo() {
            when(comercios.validarComercioActivo(3L)).thenReturn(false);

            assertEquals("El comercio 3 no existe o está dado de baja",
                    assertThrows(ValidacionException.class, () -> servicio.sincronizarPedidoExterno(77L)).getMessage());
            verify(repository, never()).guardarPedido(any());
        }

        @Test
        void unChoqueDeStockSePuedeReintentarYOtraFallaNo() {
            when(reserva.reservarStock(3L, 10L, 2)).thenThrow(new StockModificadoException("otro lo tocó"));
            assertEquals("No se pudo comprometer el stock del pedido: otro lo tocó",
                    assertThrows(ConflictoDeStockException.class, () -> servicio.sincronizarPedidoExterno(77L)).getMessage());

            doThrow(new IllegalStateException("sin stock")).when(reserva).reservarStock(3L, 10L, 2);
            ValidacionException e = assertThrows(ValidacionException.class, () -> servicio.sincronizarPedidoExterno(77L));
            assertFalse(e instanceof ConflictoDeStockException);
            assertEquals("No se pudo comprometer el stock del pedido: sin stock", e.getMessage());
            verify(reservaProvider, times(2)).destroy(reserva);
            assertFalse(externo.isSincronizado());
        }

        @Test
        void unPedidoDePuntoDePickingNoReservaStockPeroExigeUnPuntoActivo() {
            externo.setOrigen(OrigenPedido.PUNTO_PICKING);
            externo.setIdPuntoPicking(30L);
            externo.setPesoKg(new BigDecimal("4"));
            externo.getLineas().get(0).setIdItem(null);
            externo.getLineas().get(0).setProducto("Caja");
            PuntoPickingDTO punto = new PuntoPickingDTO();
            punto.id = 31L;
            when(comercios.listarPuntosPicking(3L)).thenReturn(List.of(punto));

            assertEquals("El punto de picking 30 no existe, no pertenece a este comercio, o está dado de baja",
                    assertThrows(ValidacionException.class, () -> servicio.sincronizarPedidoExterno(77L)).getMessage());

            punto.id = 30L;
            servicio.sincronizarPedidoExterno(77L);
            Pedido p = guardado();
            assertEquals(30L, p.getIdPuntoPicking());
            assertEquals(new BigDecimal("4"), p.getPesoKg());
            assertEquals("Caja", p.getLineas().get(0).getProducto());
            verify(reservaProvider, never()).get();
        }

        @Test
        void conRetiroEnDepositoSeRetiraDondeEstaElStock() {
            externo.setTipoEntrega(TipoEntrega.RETIRO_EN_DEPOSITO);
            when(stock.depositosPorItem(any())).thenReturn(Map.of(10L, 1L));

            servicio.sincronizarPedidoExterno(77L);

            Pedido p = guardado();
            assertEquals(1L, p.getIdDepositoRetiro());
            assertEquals("Retiro en depósito: Norte (Av 1)", p.getDireccionEntrega());
        }

        @Test
        void descartarloGuardaElMotivoRecortado() {
            servicio.descartarPedidoExterno(77L, "x".repeat(600));
            assertTrue(externo.isSincronizado());
            assertEquals(500, externo.getErrorSincronizacion().length());
            assertTrue(externo.getErrorSincronizacion().endsWith("..."));

            externo.setSincronizado(false);
            servicio.descartarPedidoExterno(77L, null);
            assertEquals("Sin detalle", externo.getErrorSincronizacion());

            externo.setSincronizado(false);
            servicio.descartarPedidoExterno(77L, "corto");
            assertEquals("corto", externo.getErrorSincronizacion());
            verify(repository, times(3)).actualizarPedidoExterno(externo);
        }

        @Test
        void noDescartaLoQueYaSeSincronizo() {
            externo.setSincronizado(true);

            servicio.descartarPedidoExterno(77L, "tarde");

            assertNull(externo.getErrorSincronizacion());
            verify(repository, never()).actualizarPedidoExterno(any());
        }
    }

    @Nested
    class Confirmacion {

        @Test
        void cobraAsignaRepartidorYConfirma() {
            Pedido p = pedido(EstadoPedido.PENDIENTE);
            when(repartidores.asignarRepartidor(100L, null, new BigDecimal("3"), 2, null)).thenReturn(5L);

            assertEquals(5L, servicio.confirmarPedido(100L));

            assertEquals(EstadoPedido.CONFIRMADO, p.getEstado());
            assertEquals(5L, p.getIdRepartidor());
            assertTrue(p.isCobroRegistrado());
            assertNotNull(p.getFechaActualizacion());
            verify(cobros).registrarCobro(100L, 3L, new BigDecimal("1000"), MedioPago.PREPAGO);
            verify(repository).actualizarPedido(p);
            ArgumentCaptor<EstadoPedidoCambiado> e = ArgumentCaptor.forClass(EstadoPedidoCambiado.class);
            verify(estadoPedidoCambiado).fire(e.capture());
            assertEquals(EstadoPedido.CONFIRMADO, e.getValue().estado());
            assertEquals(3L, e.getValue().idComercio());
        }

        @Test
        void enUnaZonaBuscaPrimeroAhiYNoVuelveACobrar() {
            Pedido p = pedido(EstadoPedido.PENDIENTE);
            p.setCobroRegistrado(true);
            when(repartidores.asignarRepartidor(eq(100L), eq(4L), any(), eq(2), isNull())).thenReturn(6L);

            assertEquals(6L, servicio.confirmarPedidoEnZona(100L, 4L));

            verify(cobros, never()).registrarCobro(any(), any(), any(), any());
        }

        @Test
        void sinZonaUsaLaZonaEnLaQueEsperaba() {
            Pedido p = pedido(EstadoPedido.PENDIENTE);
            p.setIdZonaEspera(8L);
            when(repartidores.asignarRepartidor(eq(100L), eq(8L), any(), eq(2), isNull())).thenReturn(6L);

            assertEquals(6L, servicio.confirmarPedido(100L));
        }

        @Test
        void sinLugarQuedaEnListaDeEsperaUnaSolaVez() {
            Pedido p = pedido(EstadoPedido.PENDIENTE);
            when(repartidores.motivoParaEsperar(any(), eq(2), isNull())).thenReturn("Nadie tiene lugar");

            assertNull(servicio.confirmarPedidoEnZona(100L, 4L));
            LocalDateTime desde = p.getEnEsperaDesde();
            assertNotNull(desde);
            assertEquals(4L, p.getIdZonaEspera());
            assertEquals("Nadie tiene lugar", p.getMotivoEspera());
            assertEquals(EstadoPedido.PENDIENTE, p.getEstado());

            assertNull(servicio.confirmarPedidoEnZona(100L, 4L));
            assertNull(servicio.confirmarPedido(100L));
            verify(repository, times(1)).actualizarPedido(p);

            when(repartidores.motivoParaEsperar(any(), eq(2), isNull())).thenReturn("y".repeat(400));
            servicio.confirmarPedido(100L);
            assertEquals(300, p.getMotivoEspera().length());
            assertEquals(desde, p.getEnEsperaDesde());
            assertEquals(4L, p.getIdZonaEspera());
            verify(repartidores, never()).asignarRepartidor(anyLong(), any(), any(), any(Integer.class), any());
        }

        @Test
        void traduceLasFallasDeRepartidoresYPagos() {
            pedido(EstadoPedido.PENDIENTE);
            when(repartidores.asignarRepartidor(anyLong(), any(), any(), any(Integer.class), any()))
                    .thenThrow(new SinLugarException("Todos llenos"))
                    .thenThrow(new com.rabbit.repartidores.negocio.ValidacionException("Zona inválida"));

            assertEquals("Todos llenos. Probá de nuevo en un momento.",
                    assertThrows(SinRepartidorConLugarException.class, () -> servicio.confirmarPedido(100L)).getMessage());
            assertEquals("Zona inválida",
                    assertThrows(ValidacionException.class, () -> servicio.confirmarPedido(100L)).getMessage());

            Pedido otro = pedido(EstadoPedido.PENDIENTE);
            when(cobros.registrarCobro(any(), any(), any(), any()))
                    .thenThrow(new com.rabbit.pagos.negocio.ValidacionException("Medio inválido"));
            assertEquals("Medio inválido",
                    assertThrows(ValidacionException.class, () -> servicio.confirmarPedido(100L)).getMessage());
            assertEquals(EstadoPedido.PENDIENTE, otro.getEstado());
        }

        @Test
        void soloSeConfirmaUnPedidoPendienteQueExiste() {
            pedido(EstadoPedido.CANCELADO);
            assertEquals("El pedido 100 está CANCELADO y no puede pasar a CONFIRMADO",
                    assertThrows(TransicionNoPermitidaException.class, () -> servicio.confirmarPedido(100L)).getMessage());
            assertEquals("Pedido no encontrado: 101",
                    assertThrows(PedidoNoEncontradoException.class, () -> servicio.confirmarPedido(101L)).getMessage());
        }

        @Test
        void unRetiroEnDepositoSeCobraYQuedaListoSinRepartidor() {
            Pedido p = pedido(EstadoPedido.PENDIENTE);
            p.setTipoEntrega(TipoEntrega.RETIRO_EN_DEPOSITO);

            assertNull(servicio.confirmarPedido(100L));

            assertEquals(EstadoPedido.CONFIRMADO, p.getEstado());
            assertTrue(p.isCobroRegistrado());
            verify(repartidores, never()).motivoParaEsperar(any(), any(Integer.class), any());

            Pedido otro = pedido(EstadoPedido.PENDIENTE);
            otro.setTipoEntrega(TipoEntrega.RETIRO_EN_DEPOSITO);
            when(cobros.registrarCobro(any(), any(), any(), any()))
                    .thenThrow(new com.rabbit.pagos.negocio.ValidacionException("Sin medio"));
            assertEquals("Sin medio", assertThrows(ValidacionException.class, () -> servicio.confirmarPedido(100L)).getMessage());
        }

        @Test
        void laListaDeEsperaAsignaSoloLoQueSigueEsperando() {
            Pedido p = pedido(EstadoPedido.PENDIENTE);
            assertNull(servicio.asignarDesdeEspera(100L));
            verify(repartidores, never()).motivoParaEsperar(any(), any(Integer.class), any());

            p.setEnEsperaDesde(LocalDateTime.now().minusHours(1));
            when(repartidores.asignarRepartidor(anyLong(), any(), any(), any(Integer.class), any())).thenReturn(5L);
            assertEquals(5L, servicio.asignarDesdeEspera(100L));
            assertFalse(p.isEnEspera());

            assertNull(servicio.asignarDesdeEspera(100L));
        }

        @Test
        void sacarDeEsperaDejaElMotivoParaElOperador() {
            Pedido p = pedido(EstadoPedido.PENDIENTE);
            servicio.sacarDeEspera(100L, "no hay repartidores");
            verify(repository, never()).actualizarPedido(any());

            p.setEnEsperaDesde(LocalDateTime.now());
            servicio.sacarDeEspera(100L, "no hay repartidores");

            assertFalse(p.isEnEspera());
            assertEquals("No se pudo asignar solo: no hay repartidores. Confirmalo o derivalo a mano.", p.getMotivoEspera());
            verify(repository).actualizarPedido(p);
        }
    }

    @Nested
    class Derivacion {

        @BeforeEach
        void stock() {
            ItemInventarioDTO item = new ItemInventarioDTO();
            item.id = 10L;
            item.idDeposito = 1L;
            when(stock.listarItemsPorIds(any())).thenReturn(List.of(item));
            EnvioDTO envio = new EnvioDTO();
            envio.codigoSeguimiento = "TL-1";
            when(envios.solicitarEnvio(eq(100L), eq(3L), eq(7L), any())).thenReturn(envio);
        }

        private DatosEnvioDTO datosEnviados() {
            ArgumentCaptor<DatosEnvioDTO> d = ArgumentCaptor.forClass(DatosEnvioDTO.class);
            verify(envios).solicitarEnvio(eq(100L), eq(3L), eq(7L), d.capture());
            return d.getValue();
        }

        @Test
        void derivaAlTransportistaConLosDatosDelEnvio() {
            Pedido p = pedido(EstadoPedido.PENDIENTE);
            p.setIdRepartidor(5L);

            assertEquals("TL-1", servicio.derivarATransportista(100L, 7L));

            assertEquals(EstadoPedido.CONFIRMADO, p.getEstado());
            assertNull(p.getIdRepartidor());
            assertTrue(p.isCobroRegistrado());
            DatosEnvioDTO d = datosEnviados();
            assertEquals("Depósito Norte — Av 1, Martínez", d.direccionRetiro);
            assertEquals("Corrientes 1234", d.direccionEntrega);
            assertEquals(2, d.bultos);
            assertNull(d.cobrarAlEntregar);
        }

        @Test
        void contraEntregaLeCobraElTransportistaYElPickingSeRetiraEnElPunto() {
            Pedido p = pedido(EstadoPedido.PENDIENTE);
            p.setMedioPago(MedioPago.CONTRA_ENTREGA);
            p.setOrigen(OrigenPedido.PUNTO_PICKING);
            p.setIdPuntoPicking(30L);
            PuntoPickingDTO otro = new PuntoPickingDTO();
            otro.id = 29L;
            PuntoPickingDTO punto = new PuntoPickingDTO();
            punto.id = 30L;
            punto.nombre = "Local";
            punto.direccion = "Florida 1";
            when(comercios.listarPuntosPickingDeComercio(3L)).thenReturn(List.of(otro, punto));

            servicio.derivarATransportista(100L, 7L);

            DatosEnvioDTO d = datosEnviados();
            assertEquals("Local — Florida 1", d.direccionRetiro);
            assertEquals(new BigDecimal("1000"), d.cobrarAlEntregar);
        }

        @Test
        void unPuntoDePickingQueNoAparecePorLoMenosSeNombra() {
            Pedido p = pedido(EstadoPedido.PENDIENTE);
            p.setOrigen(OrigenPedido.PUNTO_PICKING);
            p.setIdPuntoPicking(31L);
            when(comercios.listarPuntosPickingDeComercio(3L)).thenReturn(List.of());

            servicio.derivarATransportista(100L, 7L);

            assertEquals("Punto de picking 31", datosEnviados().direccionRetiro);
        }

        @Test
        void validaAntesDeDerivar() {
            pedido(EstadoPedido.PENDIENTE);
            assertEquals("Elegí a qué empresa de logística derivar el pedido",
                    assertThrows(ValidacionException.class, () -> servicio.derivarATransportista(100L, null)).getMessage());

            when(envios.solicitarEnvio(eq(100L), eq(3L), eq(8L), any()))
                    .thenThrow(new com.rabbit.transportistas.negocio.ValidacionException("No cubre la zona"));
            assertEquals("No cubre la zona",
                    assertThrows(ValidacionException.class, () -> servicio.derivarATransportista(100L, 8L)).getMessage());

            pedido(EstadoPedido.PENDIENTE).setTipoEntrega(TipoEntrega.RETIRO_EN_DEPOSITO);
            assertEquals("Este pedido es de retiro en depósito, no sale a reparto: no se puede derivar a un transportista",
                    assertThrows(ValidacionException.class, () -> servicio.derivarATransportista(100L, 7L)).getMessage());
            pedido(EstadoPedido.ENTREGADO);
            assertThrows(TransicionNoPermitidaException.class, () -> servicio.derivarATransportista(100L, 7L));
        }

        @Test
        void cotizaLaDerivacionDeUnPedidoPendiente() {
            pedido(EstadoPedido.PENDIENTE);
            CotizacionDTO c = new CotizacionDTO();
            when(envios.cotizarEnvio(eq(100L), any())).thenReturn(List.of(c));

            assertEquals(List.of(c), servicio.cotizarDerivacion(100L));

            assertEquals("Pedido no encontrado: 9",
                    assertThrows(PedidoNoEncontradoException.class, () -> servicio.cotizarDerivacion(9L)).getMessage());
            pedido(EstadoPedido.CONFIRMADO);
            assertThrows(TransicionNoPermitidaException.class, () -> servicio.cotizarDerivacion(100L));
        }
    }

    @Nested
    class RepartoYEntrega {

        private Pedido enCamino() {
            Pedido p = pedido(EstadoPedido.EN_CAMINO);
            p.setIdRepartidor(5L);
            p.setCodigoEntrega("1234");
            return p;
        }

        @Test
        void despacharGeneraElCodigoDeEntregaSoloParaRepartidoresPropios() {
            Pedido p = pedido(EstadoPedido.CONFIRMADO);
            p.setIdRepartidor(5L);

            servicio.despacharPedido(100L);

            assertEquals(EstadoPedido.EN_CAMINO, p.getEstado());
            assertTrue(p.isSalioAReparto());
            assertTrue(CodigoDeEntrega.tieneFormato(p.getCodigoEntrega()));
            assertEquals(p.getCodigoEntrega(), servicio.consultarCodigoDeEntrega(100L));

            Pedido tercerizado = pedido(EstadoPedido.CONFIRMADO);
            servicio.despacharPedido(100L);
            assertNull(tercerizado.getCodigoEntrega());
        }

        @Test
        void unRepartidorSoloDespachaLoQueTieneAsignado() {
            Pedido p = pedido(EstadoPedido.CONFIRMADO);
            p.setIdRepartidor(5L);
            comoRol("REPARTIDOR");
            when(contextoUsuario.idRepartidorActual()).thenReturn(6L);

            assertEquals("El pedido 100 no está asignado a vos",
                    assertThrows(ValidacionException.class, () -> servicio.despacharPedido(100L)).getMessage());

            when(contextoUsuario.idRepartidorActual()).thenReturn(5L);
            servicio.despacharPedido(100L);
            assertEquals(EstadoPedido.EN_CAMINO, p.getEstado());
        }

        @Test
        void elCodigoDeEntregaSoloSeVeMientrasEstaEnCamino() {
            pedido(EstadoPedido.CONFIRMADO).setCodigoEntrega("1234");
            assertNull(servicio.consultarCodigoDeEntrega(100L));
            assertNull(servicio.consultarCodigoDeEntrega(null));
            assertNull(servicio.consultarCodigoDeEntrega(9L));
        }

        @Test
        void registraLaEntregaConElRemitoElCodigoYLaUbicacion() {
            Pedido p = enCamino();

            servicio.registrarEntrega(100L, JPEG, new DatosEntregaDTO("1234", -34.6, -58.4, 12.4));

            ArgumentCaptor<RemitoEntrega> r = ArgumentCaptor.forClass(RemitoEntrega.class);
            verify(repository).guardarRemito(r.capture());
            assertEquals("image/jpeg", r.getValue().getTipoContenido());
            assertEquals(4, r.getValue().getTamanoBytes());
            assertEquals("moto5", r.getValue().getCargadoPor());
            assertEquals(-34.6, r.getValue().getLatitud());
            assertEquals(12, r.getValue().getPrecisionMetros());
            assertEquals(EstadoPedido.ENTREGADO, p.getEstado());
            assertTrue(p.isConRemito());
            assertEquals("moto5", p.getEntregadoPor());
            assertNotNull(p.getFechaEntrega());
            verify(repartidores).liberarRepartidor(5L, 100L);
        }

        @Test
        void unCodigoIncorrectoSumaUnIntentoYNoEntrega() {
            Pedido p = enCamino();

            CodigoIncorrectoException e = assertThrows(CodigoIncorrectoException.class,
                    () -> servicio.registrarEntrega(100L, JPEG, new DatosEntregaDTO("9999", null, null, null)));

            assertEquals("El código no es correcto (te quedan 4 intentos)", e.getMessage());
            assertEquals(1, p.getIntentosDeCodigo());
            assertEquals(EstadoPedido.EN_CAMINO, p.getEstado());
            verify(repository, never()).guardarRemito(any());
        }

        @Test
        void unAdministradorEntregaSinCodigoYSinUbicacion() {
            Pedido p = enCamino();
            comoRol("ADMINISTRADOR");

            servicio.registrarEntrega(100L, JPEG, null);

            ArgumentCaptor<RemitoEntrega> r = ArgumentCaptor.forClass(RemitoEntrega.class);
            verify(repository).guardarRemito(r.capture());
            assertNull(r.getValue().getLatitud());
            assertEquals(EstadoPedido.ENTREGADO, p.getEstado());
        }

        @Test
        void laEntregaConRemitoEsDeUnRepartidorPropio() {
            pedido(EstadoPedido.EN_CAMINO);
            assertEquals("El pedido 100 lo lleva una empresa de logística tercerizada: su entrega la informa la empresa",
                    assertThrows(ValidacionException.class, () -> servicio.registrarEntrega(100L, JPEG, null)).getMessage());
            assertEquals("Sacale una foto al remito firmado para registrar la entrega",
                    assertThrows(ValidacionException.class, () -> servicio.registrarEntrega(100L, new byte[0], null)).getMessage());
            pedido(EstadoPedido.CONFIRMADO).setTipoEntrega(TipoEntrega.RETIRO_EN_DEPOSITO);
            assertThrows(ValidacionException.class, () -> servicio.registrarEntrega(100L, JPEG, null));
        }

        @Test
        void elTransportistaInformaSuEntrega() {
            Pedido p = pedido(EstadoPedido.EN_CAMINO);

            servicio.registrarEntregaDeTransportista(100L);

            assertEquals(EstadoPedido.ENTREGADO, p.getEstado());
            assertEquals("transportista", p.getEntregadoPor());
            enCamino();
            assertEquals("El pedido 100 lo lleva un repartidor de Rabbit: la entrega se registra con la foto del remito",
                    assertThrows(ValidacionException.class, () -> servicio.registrarEntregaDeTransportista(100L)).getMessage());
        }

        @Test
        void enElDepositoSeEntregaContraElCodigoDeSeguimiento() {
            Pedido p = pedido(EstadoPedido.CONFIRMADO);
            p.setTipoEntrega(TipoEntrega.RETIRO_EN_DEPOSITO);

            assertEquals("El código no coincide con el del pedido 100",
                    assertThrows(ValidacionException.class, () -> servicio.entregarEnDeposito(100L, "RB-XYZ")).getMessage());
            servicio.entregarEnDeposito(100L, " rb-abc ");

            assertEquals(EstadoPedido.ENTREGADO, p.getEstado());
            assertEquals("moto5", p.getEntregadoPor());
        }

        @Test
        void laUbicacionDeLaEntregaSaleDelRemito() {
            RemitoEntrega remito = new RemitoEntrega();
            remito.setUbicacion(-34.6, -58.4, 8);
            when(repository.buscarRemitoDePedido(100L)).thenReturn(remito);
            when(repository.buscarRemitoDePedido(101L)).thenReturn(new RemitoEntrega());

            assertEquals(-58.4, servicio.consultarUbicacionDeEntrega(100L).longitud);
            assertEquals(8, servicio.consultarUbicacionDeEntrega(100L).precisionMetros);
            assertNull(servicio.consultarUbicacionDeEntrega(101L));
            assertNull(servicio.consultarUbicacionDeEntrega(102L));
            assertNull(servicio.consultarUbicacionDeEntrega(null));
        }

        @Test
        void elRemitoLoVenElPersonalElComercioDuenoYSuRepartidor() {
            Pedido p = enCamino();
            RemitoEntrega remito = new RemitoEntrega();
            remito.setTipoContenido("image/png");
            remito.setImagen(JPEG);
            when(repository.buscarRemitoDePedido(100L)).thenReturn(remito);

            comoRol("COMERCIO");
            when(contextoUsuario.idComercioActual()).thenReturn(3L);
            assertEquals("image/png", servicio.obtenerRemito(100L).tipoContenido);
            when(contextoUsuario.idComercioActual()).thenReturn(4L);
            assertEquals("No hay remito para el pedido 100",
                    assertThrows(PedidoNoEncontradoException.class, () -> servicio.obtenerRemito(100L)).getMessage());
            when(contextoUsuario.idComercioActual()).thenThrow(new com.rabbit.seguridad.negocio.ValidacionException("sin"));
            assertThrows(PedidoNoEncontradoException.class, () -> servicio.obtenerRemito(100L));

            when(contexto.isCallerInRole("COMERCIO")).thenReturn(false);
            comoRol("REPARTIDOR");
            when(contextoUsuario.idRepartidorActual()).thenReturn(5L);
            assertArrayEquals(JPEG, servicio.obtenerRemito(100L).imagen);
            p.setIdRepartidor(6L);
            assertThrows(PedidoNoEncontradoException.class, () -> servicio.obtenerRemito(100L));

            when(contexto.isCallerInRole("REPARTIDOR")).thenReturn(false);
            assertThrows(PedidoNoEncontradoException.class, () -> servicio.obtenerRemito(100L));
            comoRol("OPERADOR");
            assertEquals(100L, servicio.obtenerRemito(100L).idPedido);
            assertThrows(PedidoNoEncontradoException.class, () -> servicio.obtenerRemito(null));
        }
    }

    @Nested
    class VisitasFallidas {

        private Pedido enCamino(int intentosPrevios) {
            Pedido p = pedido(EstadoPedido.EN_CAMINO);
            p.setIdRepartidor(5L);
            p.setIntentosFallidos(intentosPrevios);
            return p;
        }

        private IntentoEntrega intentoGuardado() {
            ArgumentCaptor<IntentoEntrega> i = ArgumentCaptor.forClass(IntentoEntrega.class);
            verify(repository).guardarIntento(i.capture());
            return i.getValue();
        }

        @Test
        void laPrimeraVisitaFallidaVuelveAlDepositoParaOtroRepartidor() {
            Pedido p = enCamino(0);
            UbicacionDTO ultima = new UbicacionDTO();
            ultima.latitud = -34.6;
            ultima.longitud = -58.4;
            ultima.fecha = LocalDateTime.now().minusMinutes(2);
            when(repartidores.ultimaUbicacion(5L)).thenReturn(ultima);

            servicio.devolverADeposito(100L, new EntregaFallidaDTO(MotivoEntregaFallida.CLIENTE_AUSENTE, null));

            assertEquals(EstadoPedido.PENDIENTE, p.getEstado());
            assertNull(p.getIdRepartidor());
            assertEquals(5L, p.getIdRepartidorExcluido());
            assertTrue(p.isEnEspera());
            assertEquals("Devuelto al depósito: Cliente ausente", p.getMotivoEspera());
            assertEquals("Cliente ausente", p.getMotivoDevolucion());
            assertEquals(1, p.getIntentosFallidos());
            assertTrue(p.isUltimaFallaContoComoVisita());
            assertTrue(p.isCobroRegistrado());
            assertNotNull(p.getFechaUltimaFalla());
            IntentoEntrega intento = intentoGuardado();
            assertEquals(1, intento.getNumeroVisita());
            assertFalse(intento.isDevueltoAlComercio());
            assertEquals("moto5", intento.getRegistradoPor());
            assertEquals(OrigenUbicacion.ULTIMA_COMPARTIDA, intento.getOrigenUbicacion());
            assertEquals(-34.6, intento.getLatitud());
            verify(repartidores).liberarRepartidor(5L, 100L);
            verify(reserva, never()).registrarDevolucion(any());
        }

        @Test
        void alAgotarLasVisitasVuelveAlComercioConSuStock() {
            Pedido p = enCamino(1);
            EntregaFallidaDTO datos = new EntregaFallidaDTO(MotivoEntregaFallida.DOMICILIO_CERRADO, "Persiana baja");
            datos.latitud = -34.6;
            datos.longitud = -58.4;
            datos.precisionMetros = 20.0;

            servicio.devolverADeposito(100L, datos);

            assertEquals(EstadoPedido.DEVUELTO_AL_COMERCIO, p.getEstado());
            assertEquals("Local o domicilio cerrado: Persiana baja", p.getMotivoDevolucion());
            assertEquals(2, p.getIntentosFallidos());
            verify(reserva).registrarDevolucion(50L);
            IntentoEntrega intento = intentoGuardado();
            assertTrue(intento.isDevueltoAlComercio());
            assertEquals(OrigenUbicacion.DISPOSITIVO, intento.getOrigenUbicacion());
            assertEquals("Persiana baja", intento.getComentario());
        }

        @Test
        void unPaqueteDanadoVuelveAlComercioConLasFotosYSinDevolverStock() {
            Pedido p = enCamino(0);
            EntregaFallidaDTO datos = new EntregaFallidaDTO(MotivoEntregaFallida.PAQUETE_DANADO, null);
            assertEquals("Sacale al menos una foto al paquete dañado: es la prueba para el comercio",
                    assertThrows(ValidacionException.class, () -> servicio.devolverADeposito(100L, datos)).getMessage());
            datos.fotos = List.of(JPEG, new byte[0], JPEG);

            servicio.devolverADeposito(100L, datos);

            assertEquals(EstadoPedido.DEVUELTO_AL_COMERCIO, p.getEstado());
            assertEquals(0, p.getIntentosFallidos());
            verify(reserva, never()).registrarDevolucion(any());
            ArgumentCaptor<FotoIntentoEntrega> f = ArgumentCaptor.forClass(FotoIntentoEntrega.class);
            verify(repository, times(2)).guardarFotoDeIntento(f.capture());
            assertEquals(1L, f.getAllValues().get(1).getIdIntento());
            assertEquals(2, f.getAllValues().get(1).getOrden());
            assertEquals("image/jpeg", f.getAllValues().get(0).getTipoContenido());
            assertEquals(2, intentoGuardado().getCantidadFotos());
        }

        @Test
        void unaFallaAntesDeSalirNoCuentaComoVisita() {
            Pedido p = pedido(EstadoPedido.CONFIRMADO);
            p.setIdRepartidor(5L);

            servicio.devolverADeposito(100L, new EntregaFallidaDTO(MotivoEntregaFallida.NO_ENTRA_EN_VEHICULO, null));

            assertEquals(EstadoPedido.PENDIENTE, p.getEstado());
            assertEquals(0, p.getIntentosFallidos());
            assertFalse(p.isUltimaFallaContoComoVisita());
            assertEquals(0, intentoGuardado().getNumeroVisita());
        }

        @Test
        void conMotivoOtroElComentarioEsLaDescripcion() {
            Pedido p = enCamino(0);
            assertEquals("Contá qué pasó", assertThrows(ValidacionException.class,
                    () -> servicio.devolverADeposito(100L, new EntregaFallidaDTO(MotivoEntregaFallida.OTRO, " "))).getMessage());

            servicio.devolverADeposito(100L, new EntregaFallidaDTO(MotivoEntregaFallida.OTRO, "Perro suelto"));

            assertEquals("Perro suelto", p.getMotivoDevolucion());
        }

        @Test
        void soloSeDevuelveUnPedidoEnRepartoDeUnRepartidorPropio() {
            assertEquals("Elegí por qué no se pudo entregar el pedido",
                    assertThrows(ValidacionException.class, () -> servicio.devolverADeposito(100L, null)).getMessage());
            assertEquals("Elegí por qué no se pudo entregar el pedido", assertThrows(ValidacionException.class,
                    () -> servicio.devolverADeposito(100L, new EntregaFallidaDTO())).getMessage());
            EntregaFallidaDTO ausente = new EntregaFallidaDTO(MotivoEntregaFallida.CLIENTE_AUSENTE, null);
            pedido(EstadoPedido.PENDIENTE);
            assertEquals("El pedido 100 está PENDIENTE: solo se devuelve al depósito un pedido en reparto",
                    assertThrows(ValidacionException.class, () -> servicio.devolverADeposito(100L, ausente)).getMessage());
            pedido(EstadoPedido.EN_CAMINO);
            assertEquals("El pedido 100 lo lleva una empresa de logística tercerizada: no se devuelve desde Rabbit",
                    assertThrows(ValidacionException.class, () -> servicio.devolverADeposito(100L, ausente)).getMessage());
            enCamino(0).setTipoEntrega(TipoEntrega.RETIRO_EN_DEPOSITO);
            assertThrows(ValidacionException.class, () -> servicio.devolverADeposito(100L, ausente));
        }

        @Test
        void siNoSePuedeDevolverElStockSeAvisa() {
            enCamino(1);
            doThrow(new StockModificadoException("otro")).when(reserva).registrarDevolucion(50L);
            EntregaFallidaDTO ausente = new EntregaFallidaDTO(MotivoEntregaFallida.CLIENTE_AUSENTE, null);

            assertEquals("No se pudo devolver el stock del pedido: otro",
                    assertThrows(ConflictoDeStockException.class, () -> servicio.devolverADeposito(100L, ausente)).getMessage());

            enCamino(1);
            doThrow(new IllegalStateException("no existe")).when(reserva).registrarDevolucion(50L);
            assertEquals("No se pudo devolver el stock del pedido: no existe",
                    assertThrows(ValidacionException.class, () -> servicio.devolverADeposito(100L, ausente)).getMessage());
            verify(reservaProvider, times(2)).destroy(reserva);
        }

        @Test
        void listaLasVisitasConSusFotos() {
            pedido(EstadoPedido.PENDIENTE);
            comoRol("ADMINISTRADOR");
            IntentoEntrega intento = conId(new IntentoEntrega(), 1L);
            intento.setIdPedido(100L);
            intento.setMotivo(MotivoEntregaFallida.CLIENTE_AUSENTE);
            when(repository.listarIntentosDePedido(100L)).thenReturn(List.of(intento));
            when(repository.listarIdsDeFotos(List.of(1L))).thenReturn(List.<Object[]>of(new Object[] {1L, 9L}));

            List<IntentoEntregaDTO> intentos = servicio.listarIntentosDeEntrega(100L);

            assertEquals("Cliente ausente", intentos.get(0).motivo);
            assertEquals(List.of(9L), intentos.get(0).idsFotos);
            assertEquals("Pedido no encontrado: null",
                    assertThrows(PedidoNoEncontradoException.class, () -> servicio.listarIntentosDeEntrega(null)).getMessage());
        }

        @Test
        void unaFotoSoloLaVeQuienPuedeVerElPedido() {
            pedido(EstadoPedido.PENDIENTE);
            FotoIntentoEntrega foto = conId(new FotoIntentoEntrega(), 9L);
            foto.setIdPedido(100L);
            foto.setTipoContenido("image/jpeg");
            foto.setImagen(JPEG);
            when(repository.buscarFotoDeIntento(9L)).thenReturn(foto);

            assertEquals("No existe la foto 9",
                    assertThrows(PedidoNoEncontradoException.class, () -> servicio.obtenerFotoDeVisita(9L)).getMessage());
            comoRol("OPERADOR");
            assertEquals(9L, servicio.obtenerFotoDeVisita(9L).id);
            assertThrows(PedidoNoEncontradoException.class, () -> servicio.obtenerFotoDeVisita(10L));
            assertThrows(PedidoNoEncontradoException.class, () -> servicio.obtenerFotoDeVisita(null));
        }
    }

    @Nested
    class Cancelaciones {

        @Test
        void unPendienteDevuelveElStockSinTocarCobros() {
            Pedido p = pedido(EstadoPedido.PENDIENTE);
            LineaPedido sinReserva = new LineaPedido();
            sinReserva.setCantidad(1);
            p.getLineas().add(sinReserva);

            servicio.cancelarPedido(100L);

            assertEquals(EstadoPedido.CANCELADO, p.getEstado());
            verify(reserva, times(1)).registrarDevolucion(50L);
            verify(cobros, never()).anularCobro(any());
            verify(envios, never()).cancelarEnvioDePedido(any());
        }

        @Test
        void unPendienteConCobroLoAnula() {
            pedido(EstadoPedido.PENDIENTE).setCobroRegistrado(true);

            servicio.cancelarPedido(100L);

            verify(cobros).anularCobro(100L);
            verify(repartidores, never()).liberarRepartidor(any(), any());
        }

        @Test
        void unConfirmadoAnulaCobroLiberaRepartidorYCancelaElEnvio() {
            Pedido p = pedido(EstadoPedido.CONFIRMADO);
            p.setIdRepartidor(5L);

            servicio.cancelarPedido(100L);

            verify(cobros).anularCobro(100L);
            verify(repartidores).liberarRepartidor(5L, 100L);
            verify(envios).cancelarEnvioDePedido(100L);
            assertEquals(EstadoPedido.CANCELADO, p.getEstado());
        }

        @Test
        void loQueSalioARepartoNoSeCancela() {
            pedido(EstadoPedido.EN_CAMINO);
            assertTrue(assertThrows(CancelacionNoPermitidaException.class, () -> servicio.cancelarPedido(100L))
                    .getMessage().startsWith("El pedido 100 ya salió a reparto y no se puede cancelar"));
            pedido(EstadoPedido.PENDIENTE).setSalioAReparto(true);
            assertTrue(assertThrows(CancelacionNoPermitidaException.class, () -> servicio.cancelarPedido(100L))
                    .getMessage().startsWith("El pedido 100 ya salió a reparto (volvió al depósito para otra visita)"));
            pedido(EstadoPedido.ENTREGADO);
            assertThrows(ValidacionException.class, () -> servicio.cancelarPedido(100L));
            verify(reserva, never()).registrarDevolucion(any());
        }

        @Nested
        class DesdeElErp {

            private PedidoExterno externo;

            @BeforeEach
            void externo() {
                comoRol("ERP");
                when(contextoUsuario.idComercioActual()).thenReturn(3L);
                externo = conId(new PedidoExterno(), 77L);
                externo.setIdComercio(3L);
                externo.setLineas(new ArrayList<>());
                when(repository.buscarPedidoExternoParaActualizar(77L)).thenReturn(externo);
                when(repository.buscarPedidoExternoPorId(77L)).thenReturn(externo);
            }

            private Pedido sincronizado(EstadoPedido estado) {
                externo.setSincronizado(true);
                externo.setIdPedido(100L);
                return pedido(estado);
            }

            @Test
            void antesDeSincronizarseSoloSeMarcaCancelado() {
                PedidoExternoDTO dto = servicio.cancelarPedidoExterno(77L);

                assertTrue(externo.isCancelado());
                assertTrue(externo.isSincronizado());
                assertEquals("Cancelado por el comercio", externo.getErrorSincronizacion());
                assertEquals("Cancelado", dto.resultado);
                verify(repository).actualizarPedidoExterno(externo);
            }

            @Test
            void unPedidoDeOtroComercioNoExisteParaEseErp() {
                externo.setIdComercio(4L);

                assertEquals("Pedido externo no encontrado: 77",
                        assertThrows(PedidoNoEncontradoException.class, () -> servicio.cancelarPedidoExterno(77L)).getMessage());
                assertThrows(PedidoNoEncontradoException.class, () -> servicio.cancelarPedidoExterno(78L));
                assertThrows(PedidoNoEncontradoException.class, () -> servicio.consultarPedidoExterno(77L));
            }

            @Test
            void unPendienteSinCobroLoCancelaElErp() {
                Pedido p = sincronizado(EstadoPedido.PENDIENTE);

                PedidoExternoDTO dto = servicio.cancelarPedidoExterno(77L);

                assertEquals(EstadoPedido.CANCELADO, p.getEstado());
                assertEquals("CANCELADO", dto.estadoPedido);
                assertEquals("RB-ABC", dto.codigoSeguimiento);
            }

            @Test
            void unoYaCanceladoNoCambia() {
                sincronizado(EstadoPedido.CANCELADO);

                assertEquals("CANCELADO", servicio.cancelarPedidoExterno(77L).estadoPedido);
                verify(repository, never()).actualizarPedido(any());
            }

            @Test
            void loQueYaAvanzoLoCancelaElPersonalDeRabbit() {
                sincronizado(EstadoPedido.PENDIENTE).setCobroRegistrado(true);
                assertEquals("El pedido 100 ya fue confirmado y tiene su cobro: lo puede cancelar el personal de Rabbit.",
                        assertThrows(CancelacionNoPermitidaException.class, () -> servicio.cancelarPedidoExterno(77L)).getMessage());

                sincronizado(EstadoPedido.CONFIRMADO);
                assertEquals("El pedido 100 está CONFIRMADO: el comercio ya no puede cancelarlo por la API. "
                                + "Si todavía no salió, lo puede cancelar el personal de Rabbit.",
                        assertThrows(CancelacionNoPermitidaException.class, () -> servicio.cancelarPedidoExterno(77L)).getMessage());

                sincronizado(EstadoPedido.EN_CAMINO);
                assertTrue(assertThrows(CancelacionNoPermitidaException.class, () -> servicio.cancelarPedidoExterno(77L))
                        .getMessage().contains("ya salió a reparto"));
            }

            @Test
            void sincronizadoSinPedidoSoloDevuelveElEstado() {
                externo.setSincronizado(true);
                externo.setErrorSincronizacion("sin stock");

                assertEquals("Descartado", servicio.cancelarPedidoExterno(77L).resultado);
            }

            @Test
            void consultaSuPedidoExternoConLaSituacionDelPedido() {
                Pedido p = sincronizado(EstadoPedido.PENDIENTE);
                p.setEnEsperaDesde(LocalDateTime.now());
                p.setIntentosFallidos(1);

                PedidoExternoDTO dto = servicio.consultarPedidoExterno(77L);

                assertEquals("Sincronizado", dto.resultado);
                assertTrue(dto.enListaDeEspera);
                assertFalse(dto.devueltoADeposito);
                assertEquals(1, dto.intentosFallidos);
                when(repository.buscarPedidoPorId(100L)).thenReturn(null);
                assertNull(servicio.consultarPedidoExterno(77L).estadoPedido);
            }
        }
    }

    @Nested
    class SeguimientoDelDestinatario {

        @Test
        void buscaPorCodigoSinImportarMayusculasNiEspacios() {
            pedido(EstadoPedido.PENDIENTE);

            PedidoDTO dto = servicio.consultarSeguimiento("  rb-abc ");

            assertEquals(100L, dto.id);
            assertTrue(dto.modificableDesdeSeguimiento);
            assertEquals("No hay ningún pedido con el código null",
                    assertThrows(PedidoNoEncontradoException.class, () -> servicio.consultarSeguimiento(null)).getMessage());
            assertThrows(PedidoNoEncontradoException.class, () -> servicio.consultarSeguimiento("  "));
            assertThrows(PedidoNoEncontradoException.class, () -> servicio.consultarSeguimiento("RB-NO"));
        }

        @Test
        void yaNoEsModificableConElMaximoDeCambiosOEnCamino() {
            Pedido p = pedido(EstadoPedido.PENDIENTE);
            p.setCambiosDelCliente(ReglaDeOpcionesDeEntrega.CAMBIOS_MAXIMOS);
            assertFalse(servicio.consultarSeguimiento("RB-ABC").modificableDesdeSeguimiento);
            p.setCambiosDelCliente(0);
            p.setEstado(EstadoPedido.EN_CAMINO);
            assertFalse(servicio.consultarSeguimiento("RB-ABC").modificableDesdeSeguimiento);
        }

        @Test
        void unRetiroMuestraElDepositoDondeRetirarlo() {
            Pedido p = pedido(EstadoPedido.CONFIRMADO);
            p.setTipoEntrega(TipoEntrega.RETIRO_EN_DEPOSITO);
            p.setIdDepositoRetiro(1L);

            assertEquals("Norte (Av 1)", servicio.consultarSeguimiento("RB-ABC").depositoRetiro);
            p.setIdDepositoRetiro(null);
            assertNull(servicio.consultarSeguimiento("RB-ABC").depositoRetiro);
        }

        @Test
        void calificaUnaSolaVezUnaEntregaHecha() {
            pedido(EstadoPedido.ENTREGADO);

            CalificacionDTO c = servicio.calificarEntrega("RB-ABC", 5, "  Excelente  ");

            assertEquals(5, c.estrellas);
            assertEquals("Excelente", c.comentario);
            ArgumentCaptor<CalificacionEntrega> g = ArgumentCaptor.forClass(CalificacionEntrega.class);
            verify(repository).guardarCalificacion(g.capture());
            assertEquals(100L, g.getValue().getIdPedido());

            when(repository.buscarCalificacionDePedido(100L)).thenReturn(g.getValue());
            assertEquals("Esta entrega ya fue calificada. ¡Gracias!",
                    assertThrows(ValidacionException.class, () -> servicio.calificarEntrega("RB-ABC", 4, null)).getMessage());
            assertEquals(5, servicio.consultarCalificacion("RB-ABC").estrellas);
        }

        @Test
        void siOtraCalificacionGanoLaCarreraSeAvisaIgual() {
            pedido(EstadoPedido.ENTREGADO);
            doThrow(new PersistenceException("duplicada")).when(repository).guardarCalificacion(any());

            assertEquals("Esta entrega ya fue calificada. ¡Gracias!",
                    assertThrows(ValidacionException.class, () -> servicio.calificarEntrega("RB-ABC", 3, null)).getMessage());
            assertNull(servicio.consultarCalificacion("RB-ABC"));
        }

        @Test
        void elDestinatarioReprogramaYDejaInstrucciones() {
            Pedido p = pedido(EstadoPedido.PENDIENTE);
            LocalDate prometida = LocalDate.now().plusDays(1);
            p.setFechaPrometida(prometida);

            PedidoDTO dto = servicio.modificarEntrega("RB-ABC", LocalDate.now().plusDays(3), "Timbre B", "Juan");

            assertEquals(LocalDate.now().plusDays(3), p.getFechaPrometida());
            assertEquals(prometida, p.getFechaPrometidaOriginal());
            assertEquals("Timbre B", p.getInstruccionesEntrega());
            assertEquals("Juan", p.getNombreAutorizado());
            assertEquals(1, p.getCambiosDelCliente());
            assertEquals("Timbre B", dto.instruccionesEntrega);

            servicio.modificarEntrega("RB-ABC", LocalDate.now().plusDays(4), "Timbre B", "Juan");
            assertEquals(prometida, p.getFechaPrometidaOriginal());
            assertEquals(2, p.getCambiosDelCliente());

            assertEquals("No cambiaste nada", assertThrows(ValidacionException.class,
                    () -> servicio.modificarEntrega("RB-ABC", LocalDate.now().plusDays(4), "Timbre B", "Juan")).getMessage());
            assertEquals("No cambiaste nada", assertThrows(ValidacionException.class,
                    () -> servicio.modificarEntrega("RB-ABC", null, "Timbre B", "Juan")).getMessage());

            servicio.modificarEntrega("RB-ABC", null, null, "Juan");
            assertNull(p.getInstruccionesEntrega());
            servicio.modificarEntrega("RB-ABC", null, null, null);
            assertNull(p.getNombreAutorizado());
            assertEquals(4, p.getCambiosDelCliente());
        }
    }

    @Nested
    class Listados {

        @Test
        void consultaUnPedidoPorId() {
            pedido(EstadoPedido.PENDIENTE);
            assertEquals("PENDIENTE", servicio.consultarEstadoPedido(100L).estado);
            assertEquals("Pedido no encontrado: 1",
                    assertThrows(PedidoNoEncontradoException.class, () -> servicio.consultarEstadoPedido(1L)).getMessage());
        }

        @Test
        void losPendientesNoIncluyenLosRetirosEnDeposito() {
            Pedido domicilio = pedido(EstadoPedido.PENDIENTE);
            Pedido retiro = conId(new Pedido(), 101L);
            retiro.setEstado(EstadoPedido.PENDIENTE);
            retiro.setTipoEntrega(TipoEntrega.RETIRO_EN_DEPOSITO);
            retiro.setLineas(List.of());
            when(repository.listarPorEstado(EstadoPedido.PENDIENTE)).thenReturn(List.of(domicilio, retiro));
            when(repository.listarTodos()).thenReturn(List.of(domicilio, retiro));
            when(repository.listarEntregasEnCurso()).thenReturn(List.of(domicilio));

            assertEquals(List.of(100L), servicio.listarPendientes().stream().map(d -> d.id).toList());
            assertEquals(2, servicio.listarTodos().size());
            assertEquals(1, servicio.listarEntregasEnCurso().size());
        }

        @Test
        void losPedidosDeUnRepartidorEnCursoVanOrdenados() {
            Pedido entregado = conId(pedidoSuelto(EstadoPedido.ENTREGADO), 90L);
            Pedido b = conId(pedidoSuelto(EstadoPedido.EN_CAMINO), 120L);
            Pedido a = conId(pedidoSuelto(EstadoPedido.CONFIRMADO), 110L);
            when(repository.listarPedidosDeRepartidor(5L)).thenReturn(List.of(entregado, b, a));
            when(contextoUsuario.idRepartidorActual()).thenReturn(5L);

            assertEquals(List.of(110L, 120L), servicio.listarEntregasEnCursoDeRepartidor(5L).stream().map(d -> d.id).toList());
            assertEquals(3, servicio.listarPedidosDelRepartidorActual().size());
        }

        private Pedido pedidoSuelto(EstadoPedido estado) {
            Pedido p = new Pedido();
            p.setEstado(estado);
            p.setLineas(List.of());
            return p;
        }

        @Test
        void losPedidosDelComercioActual() {
            when(contextoUsuario.idComercioActual()).thenReturn(3L);
            when(repository.listarPedidosDeComercio(3L)).thenReturn(List.of(pedidoSuelto(EstadoPedido.PENDIENTE)));

            assertEquals(1, servicio.listarPedidosDelComercioActual().size());
            assertEquals(1, servicio.listarPedidosDeComercio(3L).size());
        }

        @Test
        void resumeLasEntregasPorComercio() {
            when(repository.resumenEntregas(any(), any())).thenReturn(List.of(
                    new Object[] {3L, 4L, new BigDecimal("12.5")}, new Object[] {4L, 1, null}));
            when(contextoUsuario.idComercioActual()).thenReturn(4L, 9L);
            LocalDateTime desde = LocalDateTime.of(2026, 10, 1, 0, 0);
            LocalDateTime hasta = desde.plusMonths(1);

            List<ResumenEntregasDTO> resumen = servicio.resumenEntregas(desde, hasta);

            assertEquals(new ResumenEntregasDTO(3L, 4, new BigDecimal("12.5")), resumen.get(0));
            assertEquals(new ResumenEntregasDTO(4L, 1, BigDecimal.ZERO), resumen.get(1));
            assertEquals(new ResumenEntregasDTO(4L, 1, BigDecimal.ZERO), servicio.resumenEntregasDelComercioActual(desde, hasta));
            assertEquals(new ResumenEntregasDTO(9L, 0, BigDecimal.ZERO), servicio.resumenEntregasDelComercioActual(desde, hasta));
        }

        @Test
        void listaLosPedidosExternos() {
            PedidoExterno e = conId(new PedidoExterno(), 77L);
            e.setLineas(List.of());
            when(repository.listarTodosLosExternos()).thenReturn(List.of(e));
            when(repository.buscarPedidoExternoPorId(77L)).thenReturn(e);

            assertEquals("Pendiente", servicio.listarPedidosExternos().get(0).resultado);
            assertEquals(77L, servicio.consultarPedidoExterno(77L).id);
        }
    }
}
