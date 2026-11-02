package com.rabbit.inventario.negocio;

import static com.rabbit.inventario.negocio.ConsultaStockServiceTest.deposito;
import static com.rabbit.inventario.negocio.ConsultaStockServiceTest.item;
import static com.rabbit.pruebas.Ids.conId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbit.comercios.negocio.EliminacionDeComercio;
import com.rabbit.comercios.negocio.IConsultaComercios;
import com.rabbit.inventario.datos.InventarioRepository;
import com.rabbit.inventario.datos.model.CategoriaDeTamano;
import com.rabbit.inventario.datos.model.Deposito;
import com.rabbit.inventario.datos.model.EstadoOrdenDeRecepcion;
import com.rabbit.inventario.datos.model.EstadoReserva;
import com.rabbit.inventario.datos.model.ItemInventario;
import com.rabbit.inventario.datos.model.LineaDeRecepcion;
import com.rabbit.inventario.datos.model.OcupacionDiaria;
import com.rabbit.inventario.datos.model.OrdenDeRecepcion;
import com.rabbit.inventario.datos.model.ReservaStock;
import com.rabbit.inventario.dto.DatosLineaRecepcionDTO;
import com.rabbit.inventario.dto.DatosOrdenDeRecepcionDTO;
import com.rabbit.inventario.dto.LineaRecibidaDTO;
import com.rabbit.inventario.dto.OcupacionMensualDTO;
import com.rabbit.inventario.dto.OrdenDeRecepcionDTO;
import com.rabbit.inventario.dto.ReservaStockDTO;
import com.rabbit.seguridad.negocio.IContextoUsuario;
import jakarta.ejb.EJBException;
import jakarta.ejb.SessionContext;
import jakarta.persistence.OptimisticLockException;
import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
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
class MovimientosDeStockTest {

    @Mock private InventarioRepository repository;

    private static ReservaStock reserva(Long id, ItemInventario item, int cantidad, EstadoReserva estado, LocalDateTime vence) {
        ReservaStock r = conId(new ReservaStock(), id);
        r.setItem(item);
        r.setProducto(item != null ? item.getProducto() : "Perdido");
        r.setCantidad(cantidad);
        r.setIdComercio(3L);
        r.setEstado(estado);
        r.setFechaCreacion(LocalDateTime.now().minusMinutes(1));
        r.setFechaExpiracion(vence);
        return r;
    }

    /** La conversación con el bean @Stateful: reservar y después confirmar, liberar o extender. */
    @Nested
    class ReservaConversacional {

        @Mock private IConsultaComercios comercios;
        @InjectMocks private InventarioService conversacion;

        private ItemInventario stock;
        private final List<ReservaStock> guardadas = new ArrayList<>();

        @BeforeEach
        void preparar() throws Exception {
            var campo = InventarioService.class.getDeclaredField("repository");
            campo.setAccessible(true);
            campo.set(conversacion, repository);
            stock = item(10L, deposito(1L), 50, 5);
            when(repository.buscarItemPorId(10L)).thenReturn(stock);
            when(comercios.validarComercioActivo(3L)).thenReturn(true);
            doAnswer(inv -> {
                ReservaStock r = conId(inv.getArgument(0), 100L + guardadas.size());
                guardadas.add(r);
                when(repository.buscarReservaPorId(r.getId())).thenReturn(r);
                when(repository.buscarReservaParaActualizar(r.getId())).thenReturn(r);
                return r;
            }).when(repository).guardarReserva(any());
        }

        @Test
        void reservarApartaElStockPorCincoMinutos() {
            ReservaStockDTO dto = conversacion.reservarStock(3L, 10L, 3);

            assertEquals(8, stock.getCantidadReservada());
            assertEquals(50, stock.getCantidadDisponible());
            ReservaStock r = guardadas.get(0);
            assertEquals(EstadoReserva.VIGENTE, r.getEstado());
            assertEquals(3, r.getCantidad());
            assertTrue(r.getFechaExpiracion().isAfter(LocalDateTime.now().plusMinutes(4)));
            assertTrue(r.getFechaExpiracion().isBefore(LocalDateTime.now().plusMinutes(6)));
            assertEquals(3, dto.cantidad);
            assertTrue(conversacion.hayReservaVigente());
            assertEquals(r.getId(), conversacion.obtenerReservaActual().id);
            verify(repository).sincronizar();
        }

        @Test
        void confirmarDescuentaElStockYCierraLaConversacion() {
            conversacion.reservarStock(3L, 10L, 3);

            conversacion.confirmarReserva();

            assertEquals(47, stock.getCantidadDisponible());
            assertEquals(5, stock.getCantidadReservada());
            assertEquals(EstadoReserva.CONFIRMADA, guardadas.get(0).getEstado());
            assertNotNull(guardadas.get(0).getFechaCierre());
            assertFalse(conversacion.hayReservaVigente());
            assertNull(conversacion.obtenerReservaActual());
        }

        @Test
        void liberarDevuelveLoReservado() {
            conversacion.reservarStock(3L, 10L, 3);

            conversacion.liberarReserva();

            assertEquals(5, stock.getCantidadReservada());
            assertEquals(EstadoReserva.LIBERADA, guardadas.get(0).getEstado());
            assertFalse(conversacion.hayReservaVigente());
        }

        @Test
        void extenderCorreElVencimiento() {
            conversacion.reservarStock(3L, 10L, 3);
            ReservaStock r = guardadas.get(0);
            r.setFechaExpiracion(LocalDateTime.now().plusMinutes(1));

            conversacion.extenderReserva();

            assertTrue(r.getFechaExpiracion().isAfter(LocalDateTime.now().plusMinutes(4)));
            verify(repository).actualizarReserva(r);
        }

        @Test
        void unaReservaVencidaNoSeConfirmaNiSeExtiende() {
            conversacion.reservarStock(3L, 10L, 3);
            guardadas.get(0).setFechaExpiracion(LocalDateTime.now().minusSeconds(1));

            assertEquals("La reserva venció antes de confirmarse. Volvé a reservar.",
                    assertThrows(ValidacionException.class, () -> conversacion.confirmarReserva()).getMessage());
            assertEquals(50, stock.getCantidadDisponible());

            conversacion.reservarStock(3L, 10L, 1);
            guardadas.get(1).setFechaExpiracion(LocalDateTime.now().minusSeconds(1));
            assertEquals("La reserva ya venció: no se puede extender.",
                    assertThrows(ValidacionException.class, () -> conversacion.extenderReserva()).getMessage());
        }

        @Test
        void sinReservaEnCursoNoHayNadaQueConfirmar() {
            assertEquals("No hay ninguna reserva en curso en esta sesión.",
                    assertThrows(ValidacionException.class, () -> conversacion.confirmarReserva()).getMessage());
            assertThrows(ValidacionException.class, () -> conversacion.liberarReserva());
            assertThrows(ValidacionException.class, () -> conversacion.extenderReserva());
            assertFalse(conversacion.hayReservaVigente());
        }

        @Test
        void siLaReservaDesaparecioLoDice() {
            conversacion.reservarStock(3L, 10L, 3);
            when(repository.buscarReservaParaActualizar(100L)).thenReturn(null);

            assertEquals("La reserva en curso ya no existe.",
                    assertThrows(ValidacionException.class, () -> conversacion.confirmarReserva()).getMessage());
        }

        @Test
        void soloUnaReservaVigentePorConversacion() {
            conversacion.reservarStock(3L, 10L, 1);

            assertTrue(assertThrows(ValidacionException.class, () -> conversacion.reservarStock(3L, 10L, 1))
                    .getMessage().startsWith("Ya tenés una reserva vigente"));
        }

        @Test
        void validaComercioCantidadYDuenoDelStock() {
            assertEquals("El comercio no existe o está dado de baja: no puede comprometer stock.",
                    assertThrows(ValidacionException.class, () -> conversacion.reservarStock(9L, 10L, 1)).getMessage());
            assertThrows(ValidacionException.class, () -> conversacion.reservarStock(null, 10L, 1));
            assertEquals("La cantidad a reservar debe ser mayor a cero",
                    assertThrows(ValidacionException.class, () -> conversacion.reservarStock(3L, 10L, 0)).getMessage());
            assertEquals("Ítem de stock no encontrado: 11",
                    assertThrows(ValidacionException.class, () -> conversacion.reservarStock(3L, 11L, 1)).getMessage());

            when(comercios.validarComercioActivo(4L)).thenReturn(true);
            assertTrue(assertThrows(ValidacionException.class, () -> conversacion.reservarStock(4L, 10L, 1))
                    .getMessage().endsWith("pertenece a otro comercio: no se puede reservar."));
            stock.setIdComercio(null);
            assertTrue(assertThrows(ValidacionException.class, () -> conversacion.reservarStock(3L, 10L, 1))
                    .getMessage().contains("no tiene comercio asignado"));
            verify(repository, never()).guardarReserva(any());
        }

        @Test
        void noReservaMasDeLoLibre() {
            ValidacionException e = assertThrows(ValidacionException.class, () -> conversacion.reservarStock(3L, 10L, 46));
            assertEquals("No hay stock suficiente de \"Producto 10\": se pidieron 46 y hay 45 libres.", e.getMessage());
            conversacion.reservarStock(3L, 10L, 45);
            assertEquals(50, stock.getCantidadReservada());
        }

        @Test
        void siOtroModificoElStockAlMismoTiempoLoAvisa() {
            doThrow(new OptimisticLockException()).when(repository).sincronizar();

            StockModificadoException e = assertThrows(StockModificadoException.class,
                    () -> conversacion.reservarStock(3L, 10L, 1));
            assertTrue(e.getMessage().startsWith("Otro usuario acaba de modificar el stock"));
        }

        @Test
        void laDevolucionDeUnPedidoVuelveAlDisponible() {
            ReservaStock confirmada = reserva(7L, stock, 4, EstadoReserva.CONFIRMADA, LocalDateTime.now());
            when(repository.buscarReservaParaActualizar(7L)).thenReturn(confirmada);

            conversacion.registrarDevolucion(7L);

            assertEquals(54, stock.getCantidadDisponible());
            assertEquals(EstadoReserva.DEVUELTA, confirmada.getEstado());
            assertNotNull(confirmada.getFechaCierre());
        }

        @Test
        void laDevolucionDeUnItemQueYaNoExisteSoloCierraLaReserva() {
            ReservaStock sinItem = reserva(8L, null, 4, EstadoReserva.CONFIRMADA, LocalDateTime.now());
            when(repository.buscarReservaParaActualizar(8L)).thenReturn(sinItem);

            conversacion.registrarDevolucion(8L);

            assertEquals(EstadoReserva.DEVUELTA, sinItem.getEstado());
            verify(repository, never()).actualizarItem(any());
        }

        @Test
        void soloSeDevuelvenReservasConfirmadas() {
            when(repository.buscarReservaParaActualizar(9L))
                    .thenReturn(reserva(9L, stock, 1, EstadoReserva.LIBERADA, LocalDateTime.now()));

            assertEquals("Falta el identificador de la reserva a devolver",
                    assertThrows(ValidacionException.class, () -> conversacion.registrarDevolucion(null)).getMessage());
            assertEquals("La reserva 99 no existe",
                    assertThrows(ValidacionException.class, () -> conversacion.registrarDevolucion(99L)).getMessage());
            assertTrue(assertThrows(ValidacionException.class, () -> conversacion.registrarDevolucion(9L))
                    .getMessage().startsWith("Solo se puede devolver una reserva CONFIRMADA"));
        }

        @Test
        void alCerrarLaConversacionLiberaLaReservaAbierta() {
            conversacion.reservarStock(3L, 10L, 3);
            conversacion.alCrear();
            conversacion.alPasivar();
            conversacion.alActivar();

            conversacion.alDestruir();
            conversacion.finalizar();

            assertEquals(EstadoReserva.LIBERADA, guardadas.get(0).getEstado());
            assertEquals(5, stock.getCantidadReservada());
        }
    }

    @Nested
    class Barredor {

        @Mock private SessionContext contexto;
        @InjectMocks private BarredorDeReservas barredor;

        @BeforeEach
        void preparar() throws Exception {
            var campo = BarredorDeReservas.class.getDeclaredField("repository");
            campo.setAccessible(true);
            campo.set(barredor, repository);
            when(contexto.getBusinessObject(BarredorDeReservas.class)).thenReturn(barredor);
        }

        @Test
        void expiraLasReservasVencidasYDevuelveElStock() {
            ItemInventario i = item(10L, deposito(1L), 50, 5);
            ReservaStock vencida = reserva(1L, i, 3, EstadoReserva.VIGENTE, LocalDateTime.now().minusMinutes(1));
            ReservaStock yaConfirmada = reserva(2L, i, 2, EstadoReserva.CONFIRMADA, LocalDateTime.now().minusMinutes(1));
            when(repository.listarIdsReservasVencidas(any())).thenReturn(List.of(1L, 2L, 3L));
            when(repository.buscarReservaParaActualizar(1L)).thenReturn(vencida);
            when(repository.buscarReservaParaActualizar(2L)).thenReturn(yaConfirmada);

            barredor.alArrancar();
            barredor.liberarReservasVencidas();

            assertEquals(EstadoReserva.EXPIRADA, vencida.getEstado());
            assertEquals(2, i.getCantidadReservada());
            assertEquals(EstadoReserva.CONFIRMADA, yaConfirmada.getEstado());
            verify(repository).actualizarReserva(vencida);
            verify(repository, never()).actualizarReserva(yaConfirmada);
        }

        @Test
        void sinVencidasNoHaceNada() {
            barredor.liberarReservasVencidas();

            verify(contexto, never()).getBusinessObject(any());
        }

        @Test
        void unaFallaEnUnaReservaNoFrenaALasDemas() {
            ReservaStock vencida = reserva(2L, null, 3, EstadoReserva.VIGENTE, LocalDateTime.now().minusMinutes(1));
            when(repository.listarIdsReservasVencidas(any())).thenReturn(List.of(1L, 2L));
            when(repository.buscarReservaParaActualizar(1L)).thenThrow(new EJBException("bloqueada"));
            when(repository.buscarReservaParaActualizar(2L)).thenReturn(vencida);

            barredor.liberarReservasVencidas();

            assertEquals(EstadoReserva.EXPIRADA, vencida.getEstado());
            verify(repository, never()).actualizarItem(any());
        }
    }

    @Nested
    class Recepcion {

        @Mock private IConsultaComercios comercios;
        @Mock private IContextoUsuario contextoUsuario;
        @Mock private SessionContext contexto;
        @InjectMocks private RecepcionService recepcion;

        private Deposito deposito;

        @BeforeEach
        void preparar() throws Exception {
            var campo = RecepcionService.class.getDeclaredField("repository");
            campo.setAccessible(true);
            campo.set(recepcion, repository);
            deposito = deposito(1L);
            when(repository.buscarDepositoPorId(1L)).thenReturn(deposito);
            when(contextoUsuario.idComercioActual()).thenReturn(3L);
            when(comercios.validarComercioActivo(3L)).thenReturn(true);
            Principal operador = () -> "op1";
            when(contexto.getCallerPrincipal()).thenReturn(operador);
        }

        private DatosOrdenDeRecepcionDTO anuncio(DatosLineaRecepcionDTO... lineas) {
            DatosOrdenDeRecepcionDTO d = new DatosOrdenDeRecepcionDTO();
            d.idDeposito = 1L;
            d.notas = "  Llega el martes  ";
            d.lineas = new ArrayList<>(List.of(lineas));
            return d;
        }

        private OrdenDeRecepcion orden(Long id, EstadoOrdenDeRecepcion estado, int... esperadas) {
            OrdenDeRecepcion o = conId(new OrdenDeRecepcion(), id);
            o.setIdComercio(3L);
            o.setDeposito(deposito);
            o.setEstado(estado);
            for (int i = 0; i < esperadas.length; i++) {
                LineaDeRecepcion l = conId(new LineaDeRecepcion(), 500L + i);
                l.setProducto("P" + i);
                l.setCantidadEsperada(esperadas[i]);
                l.setPesoKg(BigDecimal.ONE);
                o.agregarLinea(l);
            }
            when(repository.buscarOrdenParaActualizar(id)).thenReturn(o);
            when(repository.buscarOrdenPorId(id)).thenReturn(o);
            return o;
        }

        @Test
        void elComercioAnunciaLaMercaderiaQueVaAMandar() {
            when(repository.guardarOrden(any())).thenAnswer(inv -> conId(inv.getArgument(0), 70L));

            Long id = recepcion.anunciar(anuncio(new DatosLineaRecepcionDTO(" Yerba ", 10, new BigDecimal("1")),
                    new DatosLineaRecepcionDTO("", 0, null)));

            assertEquals(70L, id);
            ArgumentCaptor<OrdenDeRecepcion> o = ArgumentCaptor.forClass(OrdenDeRecepcion.class);
            verify(repository).guardarOrden(o.capture());
            assertEquals(3L, o.getValue().getIdComercio());
            assertEquals(EstadoOrdenDeRecepcion.ANUNCIADA, o.getValue().getEstado());
            assertEquals(1, o.getValue().getLineas().size());
            assertEquals("Yerba", o.getValue().getLineas().get(0).getProducto());
            assertEquals(10, o.getValue().getLineas().get(0).getCantidadEsperada());
            assertNotNull(o.getValue().getFechaAnuncio());
        }

        @Test
        void validaDepositoComercioYPeso() {
            DatosOrdenDeRecepcionDTO sinDeposito = anuncio(new DatosLineaRecepcionDTO("Y", 1, BigDecimal.ONE));
            sinDeposito.idDeposito = null;
            DatosOrdenDeRecepcionDTO inexistente = anuncio(new DatosLineaRecepcionDTO("Y", 1, BigDecimal.ONE));
            inexistente.idDeposito = 9L;

            assertEquals("Elegí a qué depósito vas a mandar la mercadería",
                    assertThrows(ValidacionException.class, () -> recepcion.anunciar(sinDeposito)).getMessage());
            assertThrows(ValidacionException.class, () -> recepcion.anunciar(null));
            assertEquals("Depósito no encontrado: 9",
                    assertThrows(ValidacionException.class, () -> recepcion.anunciar(inexistente)).getMessage());
            assertTrue(assertThrows(ValidacionException.class,
                    () -> recepcion.anunciar(anuncio(new DatosLineaRecepcionDTO("Piano", 1, new BigDecimal("300")))))
                    .getMessage().contains("pesa más de lo que se acepta"));
            when(comercios.validarComercioActivo(3L)).thenReturn(false);
            assertTrue(assertThrows(ValidacionException.class,
                    () -> recepcion.anunciar(anuncio(new DatosLineaRecepcionDTO("Y", 1, BigDecimal.ONE))))
                    .getMessage().startsWith("El comercio no existe o está dado de baja"));
            verify(repository, never()).guardarOrden(any());
        }

        @Test
        void recibirTodoSumaAlStockExistenteYCreaLoNuevo() {
            OrdenDeRecepcion o = orden(70L, EstadoOrdenDeRecepcion.ANUNCIADA, 10, 5);
            ItemInventario existente = item(10L, deposito, 2, 0);
            when(repository.buscarItemDelComercio("P0", 1L, 3L)).thenReturn(existente);

            OrdenDeRecepcionDTO dto = recepcion.registrarRecepcion(70L,
                    List.of(new LineaRecibidaDTO(500L, 10), new LineaRecibidaDTO(501L, 5)));

            assertEquals(EstadoOrdenDeRecepcion.RECIBIDA, o.getEstado());
            assertEquals(12, existente.getCantidadDisponible());
            ArgumentCaptor<ItemInventario> nuevo = ArgumentCaptor.forClass(ItemInventario.class);
            verify(repository).guardarItem(nuevo.capture());
            assertEquals("P1", nuevo.getValue().getProducto());
            assertEquals(5, nuevo.getValue().getCantidadDisponible());
            assertEquals(3L, nuevo.getValue().getIdComercio());
            assertEquals("op1", o.getRecibidaPor());
            assertNotNull(o.getFechaRecepcion());
            assertEquals(70L, dto.id);
        }

        @Test
        void siFaltaAlgoQuedaParcialYLoQueNoLlegoNoSeSuma() {
            OrdenDeRecepcion o = orden(70L, EstadoOrdenDeRecepcion.ANUNCIADA, 10, 5);

            recepcion.registrarRecepcion(70L, List.of(new LineaRecibidaDTO(500L, 7), new LineaRecibidaDTO(501L, 0)));

            assertEquals(EstadoOrdenDeRecepcion.PARCIAL, o.getEstado());
            assertEquals(0, o.getLineas().get(1).getCantidadRecibida());
            verify(repository, times(1)).guardarItem(any());
        }

        @Test
        void rechazaLineasRepetidasAjenasOFaltantes() {
            orden(70L, EstadoOrdenDeRecepcion.ANUNCIADA, 10);

            assertEquals("La recepción tiene líneas repetidas o inválidas",
                    assertThrows(ValidacionException.class, () -> recepcion.registrarRecepcion(70L,
                            List.of(new LineaRecibidaDTO(500L, 1), new LineaRecibidaDTO(500L, 2)))).getMessage());
            assertEquals("La recepción incluye líneas que no son de esta orden",
                    assertThrows(ValidacionException.class, () -> recepcion.registrarRecepcion(70L,
                            List.of(new LineaRecibidaDTO(500L, 1), new LineaRecibidaDTO(999L, 2)))).getMessage());
            assertThrows(ValidacionException.class, () -> recepcion.registrarRecepcion(70L, null));
            verify(repository, never()).guardarItem(any());
        }

        @Test
        void soloSeOperaSobreOrdenesAnunciadas() {
            orden(71L, EstadoOrdenDeRecepcion.RECIBIDA, 1);

            assertTrue(assertThrows(ValidacionException.class,
                    () -> recepcion.registrarRecepcion(71L, List.of(new LineaRecibidaDTO(500L, 1))))
                    .getMessage().contains("solo se opera sobre las ANUNCIADAS"));
            assertThrows(ValidacionException.class, () -> recepcion.cancelar(71L));
            assertEquals("Orden de recepción no encontrada: 99",
                    assertThrows(ValidacionException.class, () -> recepcion.cancelar(99L)).getMessage());
        }

        @Test
        void siElStockCambiaMientrasSeRecibePideReintentar() {
            orden(70L, EstadoOrdenDeRecepcion.ANUNCIADA, 1);
            doThrow(new OptimisticLockException()).when(repository).sincronizar();

            assertEquals("El stock cambió mientras se registraba la recepción. Volvé a intentarlo.",
                    assertThrows(ValidacionException.class,
                            () -> recepcion.registrarRecepcion(70L, List.of(new LineaRecibidaDTO(500L, 1)))).getMessage());
        }

        @Test
        void elComercioCancelaSoloSusOrdenes() {
            OrdenDeRecepcion propia = orden(70L, EstadoOrdenDeRecepcion.ANUNCIADA, 1);
            OrdenDeRecepcion ajena = orden(72L, EstadoOrdenDeRecepcion.ANUNCIADA, 1);
            ajena.setIdComercio(4L);
            when(contexto.isCallerInRole("COMERCIO")).thenReturn(true);

            recepcion.cancelar(70L);

            assertEquals(EstadoOrdenDeRecepcion.CANCELADA, propia.getEstado());
            assertEquals("Orden de recepción no encontrada: 72",
                    assertThrows(ValidacionException.class, () -> recepcion.cancelar(72L)).getMessage());
            assertEquals(EstadoOrdenDeRecepcion.ANUNCIADA, ajena.getEstado());
        }

        @Test
        void consultasDeOrdenes() {
            OrdenDeRecepcion o = orden(70L, EstadoOrdenDeRecepcion.ANUNCIADA, 1);
            when(repository.listarOrdenesDeComercio(3L)).thenReturn(List.of(o));
            when(repository.listarOrdenes()).thenReturn(List.of(o, o));
            when(repository.contarOrdenes(EstadoOrdenDeRecepcion.ANUNCIADA, null)).thenReturn(4L);

            assertEquals(1, recepcion.listarDelComercioActual().size());
            assertEquals(2, recepcion.listarTodas().size());
            assertEquals(70L, recepcion.obtener(70L).id);
            assertThrows(ValidacionException.class, () -> recepcion.obtener(99L));
            assertEquals(4L, recepcion.contarAnunciadas());
        }
    }

    @Nested
    class Ocupacion {

        @Mock private IContextoUsuario contextoUsuario;
        @InjectMocks private OcupacionService ocupacion;

        @BeforeEach
        void preparar() throws Exception {
            var campo = OcupacionService.class.getDeclaredField("repository");
            campo.setAccessible(true);
            campo.set(ocupacion, repository);
        }

        private ItemInventario conMedidas(Long id, Long idComercio, int unidades, String lado) {
            ItemInventario i = item(id, deposito(1L), unidades, 0);
            i.setIdComercio(idComercio);
            i.setPesoKg(new BigDecimal("0.5"));
            BigDecimal l = new BigDecimal(lado);
            i.setMedidas(l, l, l);
            return i;
        }

        @Test
        void laFotoDelDiaGuardaUnidadesPorCategoriaDeCadaComercio() {
            ItemInventario chico = conMedidas(1L, 3L, 10, "10");
            ItemInventario mediano = conMedidas(2L, 3L, 4, "30");
            ItemInventario sinComercio = conMedidas(3L, null, 99, "10");
            ItemInventario agotado = conMedidas(4L, 5L, 0, "10");
            when(repository.listarTodosLosItems()).thenReturn(List.of(chico, mediano, sinComercio, agotado));
            when(repository.listarUltimaOcupacionAntesDe(any(), isNull()))
                    .thenReturn(List.of(new OcupacionDiaria(6L, LocalDate.now().minusDays(1), CategoriaDeTamano.GRANDE, 2)));
            LocalDate hoy = LocalDate.now();

            ocupacion.tomarFoto(hoy);

            verify(repository).borrarOcupacionDelDia(hoy);
            ArgumentCaptor<OcupacionDiaria> filas = ArgumentCaptor.forClass(OcupacionDiaria.class);
            verify(repository, times(6)).guardarOcupacion(filas.capture());
            Map<String, Long> porClave = new java.util.HashMap<>();
            filas.getAllValues().forEach(f -> porClave.put(f.getIdComercio() + "-" + f.getCategoria(), f.getUnidades()));
            assertEquals(10L, porClave.get("3-PEQUENO"));
            assertEquals(4L, porClave.get("3-MEDIANO"));
            assertEquals(0L, porClave.get("3-GRANDE"));
            assertEquals(0L, porClave.get("6-GRANDE"));
        }

        @Test
        void noSeSacaFotoDeUnDiaFuturo() {
            assertThrows(ValidacionException.class, () -> ocupacion.tomarFoto(LocalDate.now().plusDays(1)));
            assertThrows(ValidacionException.class, () -> ocupacion.tomarFoto(null));
            verify(repository, never()).borrarOcupacionDelDia(any());
        }

        @Test
        void unMesCerradoSumaLasFotosYCompletaLosDiasSinFotoConLaAnterior() {
            YearMonth septiembre = YearMonth.of(2026, 9);
            when(repository.listarTodosLosItems()).thenReturn(List.of());
            when(repository.listarUltimaOcupacionAntesDe(eq(septiembre.atDay(1)), isNull()))
                    .thenReturn(List.of(new OcupacionDiaria(3L, LocalDate.of(2026, 8, 31), CategoriaDeTamano.PEQUENO, 10)));
            when(repository.listarOcupacion(septiembre.atDay(1), septiembre.atEndOfMonth(), null))
                    .thenReturn(List.of(new OcupacionDiaria(3L, LocalDate.of(2026, 9, 16), CategoriaDeTamano.PEQUENO, 20)));

            Map<Long, OcupacionMensualDTO> mes = ocupacion.ocupacionDelMes(septiembre);

            // 15 días con 10 unidades + 15 días con 20 unidades
            assertEquals(450L, mes.get(3L).pequeno);
            assertEquals(0L, mes.get(3L).grande);
        }

        @Test
        void cuentaLosProductosSinMedidasDelComercio() {
            ItemInventario sinMedidas = item(1L, deposito(1L), 5, 0);
            sinMedidas.setMedidas(null, null, null);
            when(contextoUsuario.idComercioActual()).thenReturn(3L);
            when(repository.listarItemsPorComercio(3L)).thenReturn(List.of(sinMedidas));

            OcupacionMensualDTO delMes = ocupacion.ocupacionDelMesDelComercioActual(YearMonth.now());

            assertEquals(1, delMes.productosSinMedidas);
            assertTrue(delMes.mediano > 0, "sin medidas cuenta como mediano: " + delMes.mediano);
        }

        @Test
        void laFotoDiariaAutomaticaNoPropagaFallas() {
            IOcupacionDeDeposito servicio = org.mockito.Mockito.mock(IOcupacionDeDeposito.class);
            FotoDiariaDeOcupacion timer = new FotoDiariaDeOcupacion();
            try {
                var campo = FotoDiariaDeOcupacion.class.getDeclaredField("ocupacion");
                campo.setAccessible(true);
                campo.set(timer, servicio);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
            doThrow(new ValidacionException("x")).when(servicio).tomarFoto(any());

            timer.sacarFoto();

            verify(servicio).tomarFoto(LocalDate.now());
            when(repository.fechaDeLaUltimaOcupacion()).thenReturn(LocalDate.of(2026, 10, 1));
            assertEquals(LocalDate.of(2026, 10, 1), ocupacion.fechaDeLaUltimaFoto());
        }
    }

    @Test
    void unComercioConStockUOrdenesAnunciadasNoSeElimina() throws Exception {
        ReferenciasAComercioEnInventario referencias = new ReferenciasAComercioEnInventario();
        var campo = ReferenciasAComercioEnInventario.class.getDeclaredField("repository");
        campo.setAccessible(true);
        campo.set(referencias, repository);
        when(repository.contarItemsDeComercio(3L)).thenReturn(2L);
        when(repository.contarOrdenes(EstadoOrdenDeRecepcion.ANUNCIADA, 3L)).thenReturn(1L);

        EliminacionDeComercio conStock = new EliminacionDeComercio(3L);
        referencias.alEliminarComercio(conStock);
        EliminacionDeComercio limpio = new EliminacionDeComercio(4L);
        referencias.alEliminarComercio(limpio);

        assertEquals(List.of("2 ítem(s) de stock consignado", "1 orden(es) de recepción anunciada(s)"),
                conStock.getImpedimentos());
        assertTrue(limpio.getImpedimentos().isEmpty());
    }
}
