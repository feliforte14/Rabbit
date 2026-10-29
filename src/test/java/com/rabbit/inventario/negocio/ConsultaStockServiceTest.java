package com.rabbit.inventario.negocio;

import static com.rabbit.pruebas.Ids.conId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.rabbit.comercios.negocio.IConsultaComercios;
import com.rabbit.inventario.datos.InventarioRepository;
import com.rabbit.inventario.datos.model.Deposito;
import com.rabbit.inventario.datos.model.EstadoReserva;
import com.rabbit.inventario.datos.model.ItemInventario;
import com.rabbit.inventario.datos.model.ReservaStock;
import com.rabbit.inventario.dto.DatosDepositoDTO;
import com.rabbit.inventario.dto.DatosItemInventarioDTO;
import com.rabbit.inventario.dto.DepositoDTO;
import com.rabbit.inventario.dto.FiltroHistorialDTO;
import com.rabbit.inventario.dto.ItemInventarioDTO;
import com.rabbit.inventario.dto.ReservaStockDTO;
import com.rabbit.seguridad.negocio.IContextoUsuario;
import jakarta.enterprise.event.Event;
import jakarta.persistence.OptimisticLockException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConsultaStockServiceTest {

    @Mock private IContextoUsuario contextoUsuario;
    @Mock private InventarioRepository repository;
    @Mock private IConsultaComercios comercios;
    @Mock private Event<EliminacionDeItem> eliminacionDeItem;

    @InjectMocks private ConsultaStockService servicio;

    static Deposito deposito(Long id) {
        Deposito d = conId(new Deposito(), id);
        d.setNombre("Depósito " + id);
        d.setDireccion("Calle " + id);
        d.setProvincia("Buenos Aires");
        d.setLocalidad("CABA");
        d.setCodigoPostal("1000");
        return d;
    }

    static ItemInventario item(Long id, Deposito deposito, int disponible, int reservada) {
        ItemInventario i = conId(new ItemInventario(), id);
        i.setProducto("Producto " + id);
        i.setCantidadDisponible(disponible);
        i.setCantidadReservada(reservada);
        i.setDeposito(deposito);
        i.setIdComercio(3L);
        i.setPesoKg(new BigDecimal("2"));
        i.setMedidas(BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN);
        return i;
    }

    private static DatosItemInventarioDTO datosItem(String producto, int cantidad, String peso) {
        DatosItemInventarioDTO d = new DatosItemInventarioDTO();
        d.producto = producto;
        d.cantidadDisponible = cantidad;
        d.idComercio = 3L;
        d.pesoKg = peso != null ? new BigDecimal(peso) : null;
        d.altoCm = new BigDecimal("10");
        d.anchoCm = new BigDecimal("20");
        d.largoCm = new BigDecimal("30.5");
        return d;
    }

    @Nested
    class Depositos {

        private DatosDepositoDTO datos() {
            DatosDepositoDTO d = new DatosDepositoDTO();
            d.nombre = " Norte ";
            d.direccion = " Av. Siempreviva 742 ";
            d.provincia = " Buenos Aires ";
            d.localidad = " Martínez ";
            d.codigoPostal = " 1640 ";
            return d;
        }

        @Test
        void registraElDepositoConLosDatosRecortados() {
            when(repository.guardarDeposito(any())).thenAnswer(inv -> conId(inv.getArgument(0), 4L));

            assertEquals(4L, servicio.registrarDeposito(datos()));

            ArgumentCaptor<Deposito> d = ArgumentCaptor.forClass(Deposito.class);
            verify(repository).guardarDeposito(d.capture());
            assertEquals("Norte", d.getValue().getNombre());
            assertEquals("Av. Siempreviva 742", d.getValue().getDireccion());
            assertEquals("Buenos Aires", d.getValue().getProvincia());
            assertEquals("Martínez", d.getValue().getLocalidad());
            assertEquals("1640", d.getValue().getCodigoPostal());
        }

        @ParameterizedTest
        @CsvSource({
                "nombre, El nombre del depósito es obligatorio",
                "direccion, La dirección del depósito es obligatoria",
                "provincia, La provincia del depósito es obligatoria",
                "localidad, La localidad del depósito es obligatoria",
                "codigoPostal, El código postal del depósito es obligatorio"})
        void cadaDatoEsObligatorio(String campo, String mensaje) throws Exception {
            for (String valor : new String[] {null, "", "  "}) {
                DatosDepositoDTO d = datos();
                DatosDepositoDTO.class.getField(campo).set(d, valor);
                assertEquals(mensaje, assertThrows(ValidacionException.class, () -> servicio.registrarDeposito(d)).getMessage());
            }
            verify(repository, never()).guardarDeposito(any());
        }

        @Test
        void soloUnDepositoEsLaBaseDeSalida() {
            Deposito a = deposito(1L);
            Deposito b = deposito(2L);
            a.setBaseDeSalida(true);
            when(repository.buscarDepositoPorId(2L)).thenReturn(b);
            when(repository.listarDepositos()).thenReturn(List.of(a, b));

            servicio.marcarBaseDeSalida(2L);

            assertFalse(a.isBaseDeSalida());
            assertTrue(b.isBaseDeSalida());
        }

        @Test
        void obtieneYListaDepositos() {
            when(repository.buscarDepositoPorId(1L)).thenReturn(deposito(1L));
            when(repository.listarDepositos()).thenReturn(List.of(deposito(1L), deposito(2L)));
            when(repository.listarDepositosConStock("Yerba")).thenReturn(List.of(deposito(2L)));

            DepositoDTO d = servicio.obtenerDeposito(1L);
            assertEquals("Depósito 1", d.nombre);
            assertEquals(2, servicio.listarDepositos().size());
            assertEquals(2L, servicio.listarDepositosConStock("Yerba").get(0).id);
        }

        @Test
        void unDepositoInexistenteFalla() {
            assertEquals("Depósito no encontrado: 9",
                    assertThrows(ValidacionException.class, () -> servicio.obtenerDeposito(9L)).getMessage());
            assertThrows(ValidacionException.class, () -> servicio.marcarBaseDeSalida(9L));
            assertThrows(ValidacionException.class, () -> servicio.listarItemsPorDeposito(9L));
        }
    }

    @Nested
    class AltaDeStock {

        @Test
        void cargaElStockSinReservasConPesoYMedidas() {
            Deposito d = deposito(1L);
            when(repository.buscarDepositoPorId(1L)).thenReturn(d);
            when(comercios.validarComercioActivo(3L)).thenReturn(true);
            when(repository.guardarItem(any())).thenAnswer(inv -> conId(inv.getArgument(0), 10L));

            assertEquals(10L, servicio.registrarItem(1L, datosItem("  Yerba 1 kg  ", 50, "1.25")));

            ArgumentCaptor<ItemInventario> i = ArgumentCaptor.forClass(ItemInventario.class);
            verify(repository).guardarItem(i.capture());
            assertEquals("Yerba 1 kg", i.getValue().getProducto());
            assertEquals(50, i.getValue().getCantidadDisponible());
            assertEquals(0, i.getValue().getCantidadReservada());
            assertEquals(d, i.getValue().getDeposito());
            assertEquals(3L, i.getValue().getIdComercio());
            assertEquals(new BigDecimal("1.25"), i.getValue().getPesoKg());
            assertEquals(new BigDecimal("30.5"), i.getValue().getLargoCm());
        }

        @Test
        void validaElProductoLaCantidadYElPeso() {
            when(repository.buscarDepositoPorId(1L)).thenReturn(deposito(1L));

            assertEquals("El nombre del producto es obligatorio",
                    assertThrows(ValidacionException.class, () -> servicio.registrarItem(1L, datosItem(" ", 1, "1"))).getMessage());
            assertEquals("La cantidad no puede ser negativa",
                    assertThrows(ValidacionException.class, () -> servicio.registrarItem(1L, datosItem("X", -1, "1"))).getMessage());
            assertEquals("Ingresá el peso por unidad, en kilos",
                    assertThrows(ValidacionException.class, () -> servicio.registrarItem(1L, datosItem("X", 1, null))).getMessage());
            verify(repository, never()).guardarItem(any());
        }

        @ParameterizedTest
        @ValueSource(strings = {"0", "-1", "1000.001"})
        void rechazaPesosFueraDeRango(String peso) {
            when(repository.buscarDepositoPorId(1L)).thenReturn(deposito(1L));

            assertEquals("El peso por unidad tiene que ser mayor a 0 y de hasta 1000 kg",
                    assertThrows(ValidacionException.class, () -> servicio.registrarItem(1L, datosItem("X", 1, peso))).getMessage());
        }

        @Test
        void elPesoAdmiteHastaTresDecimales() {
            when(repository.buscarDepositoPorId(1L)).thenReturn(deposito(1L));

            assertEquals("El peso admite hasta 3 decimales (gramos)",
                    assertThrows(ValidacionException.class, () -> servicio.registrarItem(1L, datosItem("X", 1, "1.0005"))).getMessage());
        }

        @Test
        void lasMedidasSonObligatoriasAlCargar() {
            when(repository.buscarDepositoPorId(1L)).thenReturn(deposito(1L));
            DatosItemInventarioDTO sinMedidas = datosItem("X", 1, "1");
            sinMedidas.altoCm = null;
            sinMedidas.anchoCm = null;
            sinMedidas.largoCm = null;
            DatosItemInventarioDTO incompletas = datosItem("X", 1, "1");
            incompletas.largoCm = null;

            assertEquals("Ingresá las medidas de una unidad: alto, ancho y largo, en centímetros",
                    assertThrows(ValidacionException.class, () -> servicio.registrarItem(1L, sinMedidas)).getMessage());
            assertEquals("Cargá las tres medidas (alto, ancho y largo) o ninguna",
                    assertThrows(ValidacionException.class, () -> servicio.registrarItem(1L, incompletas)).getMessage());
        }

        @ParameterizedTest
        @CsvSource({"0, Cada medida tiene que ser mayor a 0 y de hasta 1000 cm",
                "1000.1, Cada medida tiene que ser mayor a 0 y de hasta 1000 cm",
                "10.25, Las medidas admiten hasta 1 decimal (milímetros)"})
        void validaCadaMedida(String alto, String mensaje) {
            when(repository.buscarDepositoPorId(1L)).thenReturn(deposito(1L));
            DatosItemInventarioDTO d = datosItem("X", 1, "1");
            d.altoCm = new BigDecimal(alto);

            assertEquals(mensaje, assertThrows(ValidacionException.class, () -> servicio.registrarItem(1L, d)).getMessage());
        }

        @Test
        void rechazaProductosMasGrandesQueLaCategoriaGrande() {
            when(repository.buscarDepositoPorId(1L)).thenReturn(deposito(1L));
            DatosItemInventarioDTO enorme = datosItem("Heladera", 1, "80");

            assertTrue(assertThrows(ValidacionException.class, () -> servicio.registrarItem(1L, enorme))
                    .getMessage().startsWith("El producto supera el tamaño máximo"));
        }

        @Test
        void exigeUnComercioActivo() {
            when(repository.buscarDepositoPorId(1L)).thenReturn(deposito(1L));
            DatosItemInventarioDTO sinComercio = datosItem("X", 1, "1");
            sinComercio.idComercio = null;

            assertEquals("Hay que indicar de qué comercio es el stock que se carga",
                    assertThrows(ValidacionException.class, () -> servicio.registrarItem(1L, sinComercio)).getMessage());
            assertEquals("El comercio no existe o está dado de baja: no puede consignar stock.",
                    assertThrows(ValidacionException.class, () -> servicio.registrarItem(1L, datosItem("X", 1, "1"))).getMessage());
        }

        @Test
        void noRepiteElMismoProductoDelMismoComercioEnElDeposito() {
            when(repository.buscarDepositoPorId(1L)).thenReturn(deposito(1L));
            when(comercios.validarComercioActivo(3L)).thenReturn(true);
            when(repository.existeProductoEnDeposito("Yerba", 1L, 3L, null)).thenReturn(true);

            ValidacionException e = assertThrows(ValidacionException.class,
                    () -> servicio.registrarItem(1L, datosItem("Yerba", 1, "1")));
            assertEquals("El producto \"Yerba\" ya tiene stock de este comercio cargado en este depósito", e.getMessage());
        }

        @Test
        void unDepositoInexistenteFalla() {
            assertThrows(ValidacionException.class, () -> servicio.registrarItem(9L, datosItem("X", 1, "1")));
            verifyNoInteractions(comercios);
        }
    }

    @Nested
    class EdicionYEliminacion {

        @Test
        void editaProductoCantidadPesoYMedidas() {
            ItemInventario i = item(10L, deposito(1L), 50, 5);
            when(repository.buscarItemPorId(10L)).thenReturn(i);

            servicio.actualizarItem(10L, datosItem(" Yerba 500 g ", 40, "0.5"));

            assertEquals("Yerba 500 g", i.getProducto());
            assertEquals(40, i.getCantidadDisponible());
            assertEquals(new BigDecimal("0.5"), i.getPesoKg());
            assertEquals(new BigDecimal("30.5"), i.getLargoCm());
            verify(repository).actualizarItem(i);
            verify(repository).sincronizar();
            verify(repository).existeProductoEnDeposito("Yerba 500 g", 1L, 3L, 10L);
        }

        @Test
        void sinMedidasNuevasConservaLasAnteriores() {
            ItemInventario i = item(10L, deposito(1L), 50, 0);
            when(repository.buscarItemPorId(10L)).thenReturn(i);
            DatosItemInventarioDTO d = datosItem("X", 50, "1");
            d.altoCm = null;
            d.anchoCm = null;
            d.largoCm = null;

            servicio.actualizarItem(10L, d);

            assertEquals(BigDecimal.TEN, i.getAltoCm());
        }

        @Test
        void laCantidadNoQuedaDebajoDeLoReservado() {
            when(repository.buscarItemPorId(10L)).thenReturn(item(10L, deposito(1L), 50, 5));

            ValidacionException e = assertThrows(ValidacionException.class,
                    () -> servicio.actualizarItem(10L, datosItem("X", 4, "1")));
            assertTrue(e.getMessage().startsWith("No se puede dejar en 4: hay 5 unidades reservadas"), e.getMessage());
            verify(repository, never()).actualizarItem(any());
        }

        @Test
        void unNombreQueYaUsaOtroItemSeRechaza() {
            when(repository.buscarItemPorId(10L)).thenReturn(item(10L, deposito(1L), 50, 0));
            when(repository.existeProductoEnDeposito("Otro", 1L, 3L, 10L)).thenReturn(true);

            assertThrows(ValidacionException.class, () -> servicio.actualizarItem(10L, datosItem("Otro", 50, "1")));
        }

        @Test
        void siOtroLoModificoAlMismoTiempoPideVolverAAbrirlo() {
            when(repository.buscarItemPorId(10L)).thenReturn(item(10L, deposito(1L), 50, 0));
            doThrow(new OptimisticLockException()).when(repository).sincronizar();

            ValidacionException e = assertThrows(ValidacionException.class,
                    () -> servicio.actualizarItem(10L, datosItem("Yerba", 50, "1")));
            assertTrue(e.getMessage().startsWith("El stock de \"Yerba\" cambió recién"), e.getMessage());
        }

        @Test
        void eliminaUnItemSinReservasYConservaElHistorial() {
            ItemInventario i = item(10L, deposito(1L), 50, 0);
            when(repository.buscarItemPorId(10L)).thenReturn(i);
            when(repository.desvincularReservasDeItem(10L)).thenReturn(3);

            servicio.eliminarItem(10L);

            verify(eliminacionDeItem).fire(any());
            verify(repository).desvincularReservasDeItem(10L);
            verify(repository).eliminarItem(i);
        }

        @Test
        void noEliminaConUnidadesReservadas() {
            when(repository.buscarItemPorId(10L)).thenReturn(item(10L, deposito(1L), 50, 1));

            ValidacionException e = assertThrows(ValidacionException.class, () -> servicio.eliminarItem(10L));
            assertTrue(e.getMessage().startsWith("No se puede eliminar: hay 1 unidad reservada"), e.getMessage());
            verifyNoInteractions(eliminacionDeItem);
        }

        @Test
        void noEliminaSiUnPedidoEnCursoLoUsa() {
            when(repository.buscarItemPorId(10L)).thenReturn(item(10L, deposito(1L), 50, 0));
            doAnswer(inv -> {
                ((EliminacionDeItem) inv.getArgument(0)).impedir("lo usa el pedido 100");
                return null;
            }).when(eliminacionDeItem).fire(any());

            ValidacionException e = assertThrows(ValidacionException.class, () -> servicio.eliminarItem(10L));
            assertTrue(e.getMessage().contains("lo usa el pedido 100"), e.getMessage());
            verify(repository, never()).eliminarItem(any());
        }

        @Test
        void unItemInexistenteFalla() {
            assertEquals("Ítem de stock no encontrado: 77",
                    assertThrows(ValidacionException.class, () -> servicio.eliminarItem(77L)).getMessage());
            assertThrows(ValidacionException.class, () -> servicio.consultarDisponibilidad(77L));
        }
    }

    @Nested
    class Consultas {

        @Test
        void laDisponibilidadEsLoQueNoEstaReservado() {
            when(repository.buscarItemPorId(10L)).thenReturn(item(10L, deposito(1L), 50, 12));

            assertEquals(38, servicio.consultarDisponibilidad(10L));
        }

        @Test
        void listaPorDepositoPorIdsYConPesosYDepositos() {
            Deposito d = deposito(1L);
            when(repository.buscarDepositoPorId(1L)).thenReturn(d);
            when(repository.listarItemsPorDeposito(1L)).thenReturn(List.of(item(10L, d, 5, 1)));
            ItemInventario sinDeposito = item(11L, null, 1, 0);
            when(repository.listarItemsPorIds(List.of(10L, 11L))).thenReturn(List.of(item(10L, d, 5, 1), sinDeposito));

            List<ItemInventarioDTO> delDeposito = servicio.listarItemsPorDeposito(1L);
            assertEquals(1, delDeposito.size());
            assertEquals(4, delDeposito.get(0).cantidadLibre);
            assertEquals(2, servicio.listarItemsPorIds(List.of(10L, 11L)).size());
            Map<Long, BigDecimal> pesos = servicio.pesosPorItem(List.of(10L, 11L));
            assertEquals(new BigDecimal("2"), pesos.get(10L));
            Map<Long, Long> depositos = servicio.depositosPorItem(List.of(10L, 11L));
            assertEquals(1L, depositos.get(10L));
            assertTrue(depositos.containsKey(11L));
            assertNull(depositos.get(11L));
        }

        @Test
        void elComercioVeSoloSuStock() {
            when(contextoUsuario.idComercioActual()).thenReturn(3L);
            when(repository.listarItemsPorComercio(3L)).thenReturn(List.of(item(10L, deposito(1L), 5, 0)));

            assertEquals(1, servicio.listarStockDelComercioActual().size());
            assertTrue(servicio.listarItemsPorComercio(null).isEmpty());
        }

        @Test
        void listaPorComercioYDeposito() {
            when(repository.buscarDepositoPorId(1L)).thenReturn(deposito(1L));
            when(repository.listarItemsPorComercioYDeposito(3L, 1L)).thenReturn(List.of(item(10L, deposito(1L), 5, 0)));

            assertEquals(1, servicio.listarItemsPorComercioYDeposito(3L, 1L).size());
            assertTrue(servicio.listarItemsPorComercioYDeposito(null, 1L).isEmpty());
            assertTrue(servicio.listarItemsPorComercioYDeposito(3L, null).isEmpty());
            verify(repository, never()).listarItemsPorComercioYDeposito(isNull(), anyLong());
        }

        @Test
        void elHistorialSinFiltroUsaUnFiltroVacio() {
            ReservaStock r = conId(new ReservaStock(), 5L);
            r.setProducto("Yerba");
            r.setCantidad(2);
            r.setEstado(EstadoReserva.CONFIRMADA);
            r.setFechaCreacion(LocalDateTime.now());
            r.setFechaExpiracion(LocalDateTime.now().plusMinutes(5));
            when(repository.listarHistorial(any(FiltroHistorialDTO.class))).thenReturn(List.of(r));

            List<ReservaStockDTO> historial = servicio.listarHistorialReservas(null);

            assertEquals(1, historial.size());
            assertEquals("Yerba", historial.get(0).producto);
            verify(repository).listarHistorial(any(FiltroHistorialDTO.class));
            verify(repository, never()).existeProductoEnDeposito(anyString(), any(), any(), eq(null));
        }
    }
}
