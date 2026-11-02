package com.rabbit.ruteo.negocio;

import static com.rabbit.pruebas.Ids.conId;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.rabbit.comercios.dto.ComercioDTO;
import com.rabbit.comercios.dto.PuntoPickingDTO;
import com.rabbit.comercios.negocio.IConsultaComercios;
import com.rabbit.inventario.dto.DepositoDTO;
import com.rabbit.inventario.dto.ItemInventarioDTO;
import com.rabbit.inventario.negocio.IConsultaStock;
import com.rabbit.pagos.dto.MedioPago;
import com.rabbit.pedidos.datos.model.MotivoEntregaFallida;
import com.rabbit.pedidos.datos.model.OrigenPedido;
import com.rabbit.pedidos.dto.LineaPedidoDTO;
import com.rabbit.pedidos.dto.PedidoDTO;
import com.rabbit.pedidos.negocio.IGestionPedidos;
import com.rabbit.pedidos.negocio.ISeguimientoPedido;
import com.rabbit.repartidores.dto.RepartidorDTO;
import com.rabbit.repartidores.negocio.IGestionRepartidores;
import com.rabbit.ruteo.datos.CoordenadaRepository;
import com.rabbit.ruteo.datos.ZonaRepository;
import com.rabbit.ruteo.datos.model.CoberturaZona;
import com.rabbit.ruteo.datos.model.CoordenadaDireccion;
import com.rabbit.ruteo.datos.model.Zona;
import com.rabbit.ruteo.dto.DatosZonaDTO;
import com.rabbit.ruteo.dto.GrupoZonaDTO;
import com.rabbit.ruteo.dto.HojaDeRutaDTO;
import com.rabbit.ruteo.dto.LlegadaEstimadaDTO;
import com.rabbit.ruteo.dto.ParadaDTO;
import com.rabbit.ruteo.dto.RecorridoDTO;
import com.rabbit.ruteo.dto.ResultadoDespachoDTO;
import com.rabbit.ruteo.dto.ResultadoDespachoDTO.Resultado;
import com.rabbit.ruteo.dto.ZonaDTO;
import com.rabbit.transportistas.dto.CotizacionDTO;
import com.rabbit.transportistas.dto.EnvioDTO;
import com.rabbit.transportistas.dto.TransportistaDTO;
import com.rabbit.transportistas.negocio.IEnvios;
import com.rabbit.transportistas.negocio.IGestionTransportistas;
import com.sun.net.httpserver.HttpServer;
import jakarta.ejb.EJBException;
import jakarta.enterprise.event.Event;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RuteoServiciosTest {

    private static PedidoDTO pedido(Long id, String cp, String estado, Long idItem, int cantidad) {
        PedidoDTO p = new PedidoDTO();
        p.id = id;
        p.idComercio = 3L;
        p.origen = OrigenPedido.STOCK_CONSIGNADO;
        p.estado = estado;
        p.codigoPostalEntrega = cp;
        p.direccionEntrega = "Corrientes " + id;
        p.codigoSeguimiento = "RB-" + id;
        p.pesoKg = new BigDecimal("2");
        p.medioPago = MedioPago.PREPAGO;
        LineaPedidoDTO l = new LineaPedidoDTO();
        l.idItem = idItem;
        l.producto = "Yerba";
        l.cantidad = cantidad;
        p.lineas = new ArrayList<>(List.of(l));
        return p;
    }

    private static ZonaDTO zona(Long id, String nombre, int desde, int hasta, CoberturaZona cobertura, Long idTransportista) {
        ZonaDTO z = new ZonaDTO();
        z.id = id;
        z.nombre = nombre;
        z.codigoPostalDesde = desde;
        z.codigoPostalHasta = hasta;
        z.cobertura = cobertura.name();
        z.idTransportista = idTransportista;
        z.activa = true;
        return z;
    }

    private static RepartidorDTO repartidor(Long id, Long idZona, String kg) {
        RepartidorDTO r = new RepartidorDTO();
        r.id = id;
        r.nombre = "Moto " + id;
        r.idZona = idZona;
        r.capacidadKg = kg != null ? new BigDecimal(kg) : null;
        r.capacidadBultos = 100;
        return r;
    }

    private static TransportistaDTO transportista(Long id, String nombre) {
        TransportistaDTO t = new TransportistaDTO();
        t.id = id;
        t.nombre = nombre;
        return t;
    }

    @Nested
    class HojaDeRuta {

        @Mock private ISeguimientoPedido pedidos;
        @Mock private IConsultaComercios comercios;
        @Mock private IConsultaStock stock;
        @Mock private IGestionRepartidores repartidores;
        @Mock private IEnvios envios;
        @Mock private IZonas zonas;
        @Mock private IGestionPedidos gestion;
        @Mock private IGestionTransportistas transportistas;
        @Mock private CacheDeCoordenadas coordenadas;
        @InjectMocks private RuteoService ruteo;

        private DepositoDTO deposito;

        @BeforeEach
        void datos() {
            ComercioDTO comercio = new ComercioDTO();
            comercio.id = 3L;
            comercio.nombre = "Tienda Feliz";
            PuntoPickingDTO punto = new PuntoPickingDTO();
            punto.id = 30L;
            punto.nombre = "Local Centro";
            punto.direccion = "Florida 100";
            comercio.puntosPicking = List.of(punto);
            when(comercios.listarTodos()).thenReturn(List.of(comercio));
            RepartidorDTO r = repartidor(5L, 1L, "100");
            r.telefono = "1155556666";
            when(repartidores.listarTodos()).thenReturn(List.of(r));
            deposito = new DepositoDTO();
            deposito.id = 1L;
            deposito.nombre = "Norte";
            deposito.direccion = "Av. Siempreviva 742";
            deposito.localidad = "Martínez";
            deposito.codigoPostal = "1640";
            deposito.baseDeSalida = true;
            when(stock.listarDepositos()).thenReturn(List.of(deposito));
            ItemInventarioDTO item = new ItemInventarioDTO();
            item.id = 10L;
            item.idDeposito = 1L;
            when(stock.listarItemsPorIds(any())).thenReturn(List.of(item));
            when(coordenadas.encontradas(anyCollection())).thenAnswer(inv -> {
                Collection<String> claves = inv.getArgument(0);
                Map<String, double[]> r2 = new java.util.HashMap<>();
                for (String c : claves) {
                    r2.put(c, new double[] {-34.6, -58.4});
                }
                return r2;
            });
        }

        @Test
        void armaLaHojaConRetiroEnElDepositoEntregaYTransportista() {
            PedidoDTO p = pedido(100L, "1043", "CONFIRMADO", 10L, 2);
            p.idRepartidor = 5L;
            p.medioPago = MedioPago.CONTRA_ENTREGA;
            p.importe = new BigDecimal("2500");
            p.nombreDestinatario = "Ana";
            p.intentosFallidos = 1;
            p.motivoEntregaFallida = MotivoEntregaFallida.CLIENTE_AUSENTE;
            when(pedidos.listarEntregasEnCurso()).thenReturn(List.of(p));
            EnvioDTO envio = new EnvioDTO();
            envio.idPedido = 100L;
            envio.transportista = "Andreani";
            envio.codigoSeguimiento = "TL-1";
            when(envios.listarEnvios()).thenReturn(List.of(envio));

            List<HojaDeRutaDTO> hojas = ruteo.listarEntregasEnCurso();

            HojaDeRutaDTO h = hojas.get(0);
            assertEquals(100L, h.idPedido);
            assertEquals("Tienda Feliz", h.comercio);
            assertEquals("Moto 5", h.repartidor);
            assertEquals("1155556666", h.telefonoRepartidor);
            assertEquals("Andreani", h.transportista);
            assertEquals("TL-1", h.codigoSeguimiento);
            assertEquals(new BigDecimal("2500"), h.cobrarAlEntregar);
            assertEquals("Cliente ausente", h.motivoUltimoIntento);
            assertEquals(2, h.cantidadTotal);
            assertEquals(1, h.paradasRetiro.size());
            assertEquals("Av. Siempreviva 742, Martínez", h.paradasRetiro.get(0).direccion);
            assertEquals(-34.6, h.latitudEntrega);
            assertNotNull(h.urlMapa);
            assertTrue(h.urlMapa.startsWith("https://www.google.com/maps/dir/"), h.urlMapa);
        }

        @Test
        void unPedidoDePuntoDePickingSeRetiraEnElPunto() {
            PedidoDTO p = pedido(101L, "1043", "CONFIRMADO", null, 1);
            p.origen = OrigenPedido.PUNTO_PICKING;
            p.idPuntoPicking = 30L;
            when(pedidos.listarEntregasEnCurso()).thenReturn(List.of(p));

            HojaDeRutaDTO h = ruteo.listarEntregasEnCurso().get(0);

            assertEquals(List.of("Punto de picking Local Centro — Florida 100"), h.retiros);
            assertNull(h.cobrarAlEntregar);
        }

        @Test
        void sinPedidosNoConsultaNada() {
            when(pedidos.listarEntregasEnCursoDeRepartidor(5L)).thenReturn(List.of());

            RecorridoDTO r = ruteo.recorridoDeRepartidor(5L);

            assertNotNull(r);
            verify(comercios, never()).listarTodos();
        }

        @Test
        void elRepartidorVeSusEntregasEnCursoYSuHistorialPorSeparado() {
            when(pedidos.listarPedidosDelRepartidorActual()).thenReturn(List.of(
                    pedido(102L, "1043", "EN_CAMINO", 10L, 1), pedido(101L, "1043", "CONFIRMADO", 10L, 1),
                    pedido(103L, "1043", "ENTREGADO", 10L, 1), pedido(104L, "1043", "CANCELADO", 10L, 1)));

            List<HojaDeRutaDTO> enCurso = ruteo.entregasEnCursoDelRepartidor();
            List<HojaDeRutaDTO> historial = ruteo.historialDelRepartidor();

            assertEquals(List.of(101L, 102L), enCurso.stream().map(h -> h.idPedido).toList());
            assertEquals(2, historial.size());
            RecorridoDTO recorrido = ruteo.recorridoRecomendado(enCurso);
            assertEquals(ParadaDTO.Tipo.SALIDA, recorrido.paradas.get(0).tipo);
        }

        @Test
        void laSalidaEsElDepositoMarcadoComoBaseYSeUbicaEnElMapa() {
            ParadaDTO salida = ruteo.depositoDeSalida();

            assertEquals(ParadaDTO.Tipo.SALIDA, salida.tipo);
            assertEquals("Av. Siempreviva 742, Martínez", salida.direccion);
            assertEquals(-34.6, salida.latitud);
        }

        @Test
        void sinBaseDeSalidaUsaElNombrePorDefectoYUnaCacheCaidaNoFrena() {
            deposito.baseDeSalida = false;
            when(coordenadas.encontradas(anyCollection())).thenThrow(new IllegalStateException("caída"));
            when(pedidos.listarEntregasEnCurso()).thenReturn(List.of(pedido(100L, "1043", "CONFIRMADO", 10L, 1)));

            assertEquals(DepositoRabbit.NOMBRE_POR_DEFECTO, ruteo.depositoDeSalida().descripcion);
            HojaDeRutaDTO h = ruteo.listarEntregasEnCurso().get(0);
            assertNull(h.latitudEntrega);
        }

        @Test
        void agrupaLosPendientesPorZonaConSusRepartidoresLibres() {
            when(zonas.listarTodas()).thenReturn(List.of(zona(1L, "Centro", 1000, 1499, CoberturaZona.PROPIA, 7L)));
            when(transportistas.listarTodos()).thenReturn(List.of(transportista(7L, "Andreani")));
            when(pedidos.listarPendientes()).thenReturn(List.of(pedido(100L, "1043", "PENDIENTE", 10L, 1),
                    pedido(101L, "9999", "PENDIENTE", 10L, 1), pedido(102L, null, "PENDIENTE", 10L, 1)));

            List<GrupoZonaDTO> grupos = ruteo.listarPendientesPorZona();

            assertEquals(2, grupos.size());
            assertEquals("Centro", grupos.get(0).zona.nombre);
            assertEquals("Andreani", grupos.get(0).transportista);
            assertEquals(1, grupos.get(0).repartidoresLibres);
            assertEquals(1, grupos.get(0).pedidos.size());
            assertNull(grupos.get(1).zona);
            assertEquals(2, grupos.get(1).pedidos.size());
        }
    }

    @Nested
    class Despacho {

        @Mock private ISeguimientoPedido pedidos;
        @Mock private IGestionRepartidores repartidores;
        @Mock private IZonas zonas;
        @Mock private IGestionPedidos gestion;
        @Mock private IGestionTransportistas transportistas;
        @InjectMocks private RuteoService ruteo;

        @BeforeEach
        void zonas() {
            when(zonas.listarTodas()).thenReturn(List.of(
                    zona(1L, "Centro", 1000, 1499, CoberturaZona.PROPIA, 7L),
                    zona(2L, "Sur", 1800, 1899, CoberturaZona.TRANSPORTISTA, 8L),
                    zona(3L, "Oeste", 1700, 1799, CoberturaZona.PROPIA, null)));
            when(transportistas.listarTodos()).thenReturn(List.of(transportista(7L, "Respaldo"), transportista(8L, "Lejano")));
            when(repartidores.listarTodos()).thenReturn(List.of(repartidor(5L, 1L, "100"), repartidor(6L, 3L, "1")));
        }

        private ResultadoDespachoDTO despachar(PedidoDTO p) {
            when(pedidos.consultarEstadoPedido(p.id)).thenReturn(p);
            return ruteo.despacharPedido(p.id);
        }

        @Test
        void conLugarEnLaZonaLoLlevaUnRepartidorDeLaZona() {
            when(gestion.confirmarPedidoEnZona(100L, 1L)).thenReturn(5L);

            ResultadoDespachoDTO r = despachar(pedido(100L, "1043", "PENDIENTE", 10L, 1));

            assertEquals(Resultado.REPARTIDOR, r.resultado);
            assertEquals("Moto 5 (zona Centro)", r.detalle);
        }

        @Test
        void siLoAsignaAOtraZonaLoAclara() {
            when(gestion.confirmarPedidoEnZona(100L, 3L)).thenReturn(5L);
            PedidoDTO p = pedido(100L, "1750", "PENDIENTE", 10L, 1);
            p.pesoKg = new BigDecimal("0.5");

            ResultadoDespachoDTO r = despachar(p);

            assertEquals("Moto 5 (de otra zona: en Oeste no le entraba a nadie)", r.detalle);
        }

        @Test
        void unaZonaTercerizadaSeDerivaAlTransportista() {
            when(gestion.derivarATransportista(100L, 8L)).thenReturn("TL-9");

            ResultadoDespachoDTO r = despachar(pedido(100L, "1850", "PENDIENTE", 10L, 1));

            assertEquals(Resultado.DERIVADO, r.resultado);
            assertEquals("Lejano (cubre la zona Sur), seguimiento TL-9", r.detalle);
            verify(gestion, never()).confirmarPedidoEnZona(anyLong(), anyLong());
        }

        @Test
        void sinLugarEnLaZonaVaAlTransportistaDeRespaldo() {
            PedidoDTO pesado = pedido(100L, "1043", "PENDIENTE", 10L, 1);
            pesado.pesoKg = new BigDecimal("500");
            when(gestion.derivarATransportista(100L, 7L)).thenReturn("RP-1");

            ResultadoDespachoDTO r = despachar(pesado);

            assertEquals(Resultado.DERIVADO, r.resultado);
            assertTrue(r.detalle.startsWith("Respaldo (respaldo: a ningún repartidor de la zona Centro le entra el pedido)"));
        }

        @Test
        void siElRespaldoNoLoTomaPruebaConLaAlternativaMasBarata() {
            PedidoDTO pesado = pedido(100L, "1043", "PENDIENTE", 10L, 1);
            pesado.pesoKg = new BigDecimal("500");
            when(gestion.derivarATransportista(100L, 7L)).thenThrow(new com.rabbit.pedidos.negocio.ValidacionException("rechazó"));
            CotizacionDTO mismo = new CotizacionDTO();
            mismo.idTransportista = 7L;
            mismo.estado = "COTIZADO";
            CotizacionDTO sinPrecio = new CotizacionDTO();
            sinPrecio.idTransportista = 9L;
            sinPrecio.estado = "NO_COTIZA";
            CotizacionDTO falla = new CotizacionDTO();
            falla.idTransportista = 10L;
            falla.transportista = "Falla";
            falla.estado = "COTIZADO";
            CotizacionDTO toma = new CotizacionDTO();
            toma.idTransportista = 11L;
            toma.transportista = "Barato";
            toma.estado = "COTIZADO";
            when(gestion.cotizarDerivacion(100L)).thenReturn(List.of(mismo, sinPrecio, falla, toma));
            when(gestion.derivarATransportista(100L, 10L)).thenThrow(new com.rabbit.pedidos.negocio.ValidacionException("no"));
            when(gestion.derivarATransportista(100L, 11L)).thenReturn("B-1");

            ResultadoDespachoDTO r = despachar(pesado);

            assertEquals("Barato (la más barata de las alternativas: Respaldo no lo tomó), seguimiento B-1", r.detalle);
        }

        @Test
        void siNadieLoTomaQuedaComoError() {
            when(gestion.derivarATransportista(100L, 8L)).thenThrow(new com.rabbit.pedidos.negocio.ValidacionException("Lejano rechazó"));
            when(gestion.cotizarDerivacion(100L)).thenReturn(List.of());

            ResultadoDespachoDTO r = despachar(pedido(100L, "1850", "PENDIENTE", 10L, 1));

            assertEquals(Resultado.ERROR, r.resultado);
            assertEquals("Lejano rechazó", r.detalle);
        }

        @Test
        void siNoHayLugarQuedaEnEsperaConElMotivo() {
            PedidoDTO p = pedido(100L, "1750", "PENDIENTE", 10L, 1);
            PedidoDTO conMotivo = pedido(100L, "1750", "PENDIENTE", 10L, 1);
            conMotivo.motivoEspera = "Pasó la hora de corte (07:00): se asigna mañana";
            when(gestion.confirmarPedidoEnZona(100L, 3L)).thenReturn(null);
            when(pedidos.consultarEstadoPedido(100L)).thenReturn(p, conMotivo);

            ResultadoDespachoDTO r = ruteo.despacharPedido(100L);

            assertEquals(Resultado.EN_ESPERA, r.resultado);
            assertEquals("Pasó la hora de corte (07:00): se asigna mañana. Se asigna solo en cuanto se pueda.", r.detalle);
        }

        @ParameterizedTest
        @CsvSource({"5555, El código postal 5555 no está en ninguna zona: hay que despacharlo a mano"})
        void unCodigoPostalSinZonaSeDespachaAMano(String cp, String detalle) {
            ResultadoDespachoDTO r = despachar(pedido(100L, cp, "PENDIENTE", 10L, 1));
            assertEquals(Resultado.SIN_ZONA, r.resultado);
            assertEquals(detalle, r.detalle);

            ResultadoDespachoDTO sinCp = despachar(pedido(101L, null, "PENDIENTE", 10L, 1));
            assertEquals("Sin código postal de entrega: hay que despacharlo a mano", sinCp.detalle);
        }

        @Test
        void soloSeDespachanPedidosPendientesQueExisten() {
            when(pedidos.consultarEstadoPedido(99L)).thenThrow(new com.rabbit.pedidos.negocio.ValidacionException("Pedido 99 no existe"));

            assertEquals("Pedido 99 no existe", ruteo.despacharPedido(99L).detalle);
            assertEquals("El pedido no está pendiente", despachar(pedido(100L, "1043", "CONFIRMADO", 10L, 1)).detalle);
        }

        @Test
        void unaFallaDelSistemaSeInformaSinRomperElDespacho() {
            when(gestion.confirmarPedidoEnZona(100L, 1L)).thenThrow(new EJBException("base"));

            assertEquals("No se pudo despachar el pedido. Intentá de nuevo.",
                    despachar(pedido(100L, "1043", "PENDIENTE", 10L, 1)).detalle);
        }

        @Test
        void despacharLaZonaDespachaSoloSusPendientes() {
            when(pedidos.listarPendientes()).thenReturn(List.of(pedido(100L, "1043", "PENDIENTE", 10L, 1),
                    pedido(101L, "1850", "PENDIENTE", 10L, 1)));
            when(gestion.confirmarPedidoEnZona(100L, 1L)).thenReturn(5L);

            List<ResultadoDespachoDTO> resultados = ruteo.despacharZona(1L);

            assertEquals(1, resultados.size());
            assertEquals(100L, resultados.get(0).idPedido);
            assertTrue(ruteo.despacharZona(42L).isEmpty());
        }
    }

    @Nested
    class Zonas {

        @Mock private ZonaRepository repository;
        @Mock private IGestionTransportistas transportistas;
        @Mock private Event<ZonaEliminada> zonaEliminada;
        @InjectMocks private ZonaService servicio;

        private DatosZonaDTO datos(String nombre, Integer desde, Integer hasta, CoberturaZona cobertura, Long idTransportista) {
            DatosZonaDTO d = new DatosZonaDTO();
            d.nombre = nombre;
            d.codigoPostalDesde = desde;
            d.codigoPostalHasta = hasta;
            d.cobertura = cobertura;
            d.idTransportista = idTransportista;
            return d;
        }

        private Zona zona(Long id, int desde, int hasta, boolean activa) {
            Zona z = conId(new Zona(), id);
            z.setNombre("Z" + id);
            z.setCodigoPostalDesde(desde);
            z.setCodigoPostalHasta(hasta);
            z.setCobertura(CoberturaZona.PROPIA);
            z.setActiva(activa);
            return z;
        }

        @Test
        void registraUnaZonaActiva() {
            when(transportistas.listarActivos()).thenReturn(List.of(transportista(7L, "Andreani")));
            when(repository.guardar(any())).thenAnswer(inv -> conId(inv.getArgument(0), 4L));

            assertEquals(4L, servicio.registrarZona(datos(" Centro ", 1000, 1499, CoberturaZona.TRANSPORTISTA, 7L)));

            ArgumentCaptor<Zona> z = ArgumentCaptor.forClass(Zona.class);
            verify(repository).guardar(z.capture());
            assertEquals("Centro", z.getValue().getNombre());
            assertEquals(1000, z.getValue().getCodigoPostalDesde());
            assertEquals(1499, z.getValue().getCodigoPostalHasta());
            assertTrue(z.getValue().isActiva());
        }

        @Test
        void validaNombreRangoYCobertura() {
            assertEquals("El nombre de la zona es obligatorio",
                    assertThrows(ValidacionException.class, () -> servicio.registrarZona(datos(" ", 1000, 1100, CoberturaZona.PROPIA, null))).getMessage());
            assertEquals("Indicá el rango de códigos postales de la zona",
                    assertThrows(ValidacionException.class, () -> servicio.registrarZona(datos("A", null, 1100, CoberturaZona.PROPIA, null))).getMessage());
            for (int[] r : new int[][] {{999, 1100}, {1000, 10000}, {2000, 1999}}) {
                assertTrue(assertThrows(ValidacionException.class,
                        () -> servicio.registrarZona(datos("A", r[0], r[1], CoberturaZona.PROPIA, null)))
                        .getMessage().startsWith("El rango tiene que ir de 1000 a 9999"));
            }
            assertEquals("Elegí quién reparte en la zona",
                    assertThrows(ValidacionException.class, () -> servicio.registrarZona(datos("A", 1000, 1100, null, null))).getMessage());
            assertEquals("Una zona de logística tercerizada necesita la empresa de logística",
                    assertThrows(ValidacionException.class, () -> servicio.registrarZona(datos("A", 1000, 1100, CoberturaZona.TRANSPORTISTA, null))).getMessage());
            assertEquals("La empresa de logística elegida no existe o está dada de baja",
                    assertThrows(ValidacionException.class, () -> servicio.registrarZona(datos("A", 1000, 1100, CoberturaZona.PROPIA, 99L))).getMessage());
            verify(repository, never()).guardar(any());
        }

        @Test
        void losRangosNoSePuedenSuperponer() {
            when(repository.listarSuperpuestas(1400, 1600, null)).thenReturn(List.of(zona(1L, 1000, 1499, true)));

            assertEquals("El rango se superpone con la zona Z1 (1000-1499)",
                    assertThrows(ValidacionException.class, () -> servicio.registrarZona(datos("B", 1400, 1600, CoberturaZona.PROPIA, null))).getMessage());
        }

        @Test
        void bajaReactivacionYEliminacion() {
            Zona z = zona(1L, 1000, 1499, true);
            when(repository.buscarPorId(1L)).thenReturn(z);

            servicio.darDeBajaZona(1L);
            assertFalse(z.isActiva());
            servicio.reactivarZona(1L);
            assertTrue(z.isActiva());
            verify(repository).listarSuperpuestas(1000, 1499, 1L);
            servicio.eliminarZona(1L);
            ArgumentCaptor<ZonaEliminada> evento = ArgumentCaptor.forClass(ZonaEliminada.class);
            verify(zonaEliminada).fire(evento.capture());
            assertEquals(1L, evento.getValue().getIdZona());
            verify(repository).eliminar(z);
            assertEquals("Zona no encontrada: 9",
                    assertThrows(ValidacionException.class, () -> servicio.darDeBajaZona(9L)).getMessage());
            assertThrows(ValidacionException.class, () -> servicio.eliminarZona(null));
        }

        @Test
        void noReactivaSiAhoraSeSuperponeConOtra() {
            when(repository.buscarPorId(1L)).thenReturn(zona(1L, 1000, 1499, false));
            when(repository.listarSuperpuestas(1000, 1499, 1L)).thenReturn(List.of(zona(2L, 1200, 1300, true)));

            assertThrows(ValidacionException.class, () -> servicio.reactivarZona(1L));
        }

        @Test
        void encuentraLaZonaActivaDeUnCodigoPostal() {
            when(repository.listarTodas()).thenReturn(List.of(zona(1L, 1000, 1499, false), zona(2L, 1000, 1499, true)));

            assertEquals(2L, servicio.zonaDeCodigoPostal("1043").id);
            assertNull(servicio.zonaDeCodigoPostal("5000"));
            assertNull(servicio.zonaDeCodigoPostal(null));
            assertEquals(2, servicio.listarTodas().size());
        }
    }

    @Nested
    class CoordenadasYLlegada {

        @Mock private CoordenadaRepository repository;
        @InjectMocks private CacheDeCoordenadas cache;

        @Mock private IRuteo ruteoMock;
        @InjectMocks private LlegadaEstimadaService llegada;

        private CoordenadaDireccion coordenada(String clave, boolean encontrada, int diasAtras) {
            CoordenadaDireccion c = new CoordenadaDireccion();
            c.setClave(clave);
            c.setEncontrada(encontrada);
            if (encontrada) {
                c.setLatitud(-34.6);
                c.setLongitud(-58.4);
            }
            c.setFechaConsulta(LocalDateTime.now().minusDays(diasAtras));
            return c;
        }

        @Test
        void devuelveSoloLasEncontradas() {
            when(repository.buscarPorClaves(any())).thenReturn(List.of(coordenada("a|1", true, 0), coordenada("b|1", false, 0)));

            Map<String, double[]> encontradas = cache.encontradas(List.of("a|1", "b|1"));

            assertEquals(1, encontradas.size());
            assertArrayEquals(new double[] {-34.6, -58.4}, encontradas.get("a|1"));
        }

        @Test
        void faltanLasNuncaConsultadasYLasNoEncontradasHaceMasDeUnaSemana() {
            when(repository.buscarPorClaves(any())).thenReturn(List.of(coordenada("a|1", true, 30),
                    coordenada("b|1", false, 2), coordenada("c|1", false, 8)));

            Set<String> faltan = cache.faltantes(List.of("a|1", "b|1", "c|1", "d|1"));

            assertEquals(Set.of("c|1", "d|1"), faltan);
        }

        @Test
        void guardaLoEncontradoYTambienLoQueNoSeEncontro() {
            cache.guardar("a|1", new double[] {-34.6, -58.4});
            cache.guardar("b|1", null);

            ArgumentCaptor<CoordenadaDireccion> c = ArgumentCaptor.forClass(CoordenadaDireccion.class);
            verify(repository, org.mockito.Mockito.times(2)).guardar(c.capture());
            assertTrue(c.getAllValues().get(0).isEncontrada());
            assertEquals(-58.4, c.getAllValues().get(0).getLongitud());
            assertFalse(c.getAllValues().get(1).isEncontrada());
            assertNull(c.getAllValues().get(1).getLatitud());
            assertNotNull(c.getAllValues().get(1).getFechaConsulta());
        }

        @Test
        void estimaLaParadaDeUnPedidoEnElRecorrido() throws Exception {
            var campo = LlegadaEstimadaService.class.getDeclaredField("ruteo");
            campo.setAccessible(true);
            campo.set(llegada, ruteoMock);
            RecorridoDTO recorrido = new RecorridoDTO();
            recorrido.paradas.add(new ParadaDTO(ParadaDTO.Tipo.SALIDA, "Depósito", "x", null, null));
            recorrido.paradas.add(new ParadaDTO(ParadaDTO.Tipo.ENTREGA, "A", "a", null, 100L));
            recorrido.paradas.add(new ParadaDTO(ParadaDTO.Tipo.ENTREGA, "B", "b", null, 101L));
            when(ruteoMock.recorridoDeRepartidor(5L)).thenReturn(recorrido);
            when(ruteoMock.recorridoDeRepartidor(6L)).thenThrow(new IllegalStateException("caído"));

            LlegadaEstimadaDTO estimada = llegada.estimar(5L, 101L);

            assertEquals(2, estimada.numeroParada);
            assertEquals(2, estimada.totalParadas);
            assertTrue(estimada.hasta.isAfter(estimada.desde) || estimada.hasta.isBefore(java.time.LocalTime.of(1, 0)));
            assertNull(llegada.estimar(5L, 999L));
            assertNull(llegada.estimar(null, 101L));
            assertNull(llegada.estimar(5L, null));
            assertNull(llegada.estimar(6L, 101L));
        }
    }

    /** El geocodificador contra un Nominatim falso levantado en un puerto local. */
    @Nested
    class GeocodificacionContraUnServidorReal {

        @Mock private IRuteo ruteoMock;
        @Mock private CacheDeCoordenadas cache;
        @InjectMocks private Geocodificador geocodificador;

        private HttpServer servidor;
        private final AtomicInteger consultas = new AtomicInteger();
        private volatile int estado = 200;
        private volatile String respuesta = "[{\"lat\":\"-34.6037\",\"lon\":\"-58.3816\"}]";

        @BeforeEach
        void levantarNominatim() throws IOException {
            servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            servidor.createContext("/search", intercambio -> {
                consultas.incrementAndGet();
                byte[] cuerpo = respuesta.getBytes(StandardCharsets.UTF_8);
                intercambio.sendResponseHeaders(estado, cuerpo.length);
                intercambio.getResponseBody().write(cuerpo);
                intercambio.close();
            });
            servidor.start();
            System.setProperty(Geocodificador.PROPIEDAD_URL,
                    "http://127.0.0.1:" + servidor.getAddress().getPort() + "/search");
            ParadaDTO salida = new ParadaDTO();
            salida.direccion = "Av. Siempreviva 742";
            salida.codigoPostal = "1640";
            when(ruteoMock.depositoDeSalida()).thenReturn(salida);
            HojaDeRutaDTO hoja = new HojaDeRutaDTO();
            hoja.direccionEntrega = "Av. Siempreviva 742";
            hoja.codigoPostalEntrega = "1640";
            hoja.paradasRetiro = List.of();
            when(ruteoMock.listarEntregasEnCurso()).thenReturn(List.of(hoja));
            when(cache.faltantes(any())).thenAnswer(inv -> new java.util.LinkedHashSet<String>(inv.getArgument(0)));
        }

        @AfterEach
        void apagar() {
            servidor.stop(0);
            System.clearProperty(Geocodificador.PROPIEDAD_URL);
            System.clearProperty(Geocodificador.PROPIEDAD);
        }

        @Test
        void consultaLasDireccionesQueFaltanYGuardaLaCoordenada() {
            geocodificador.cadaMinuto();

            assertEquals(1, consultas.get());
            ArgumentCaptor<double[]> c = ArgumentCaptor.forClass(double[].class);
            verify(cache).guardar(eq(Nominatim.clave("Av. Siempreviva 742", "1640")), c.capture());
            assertArrayEquals(new double[] {-34.6037, -58.3816}, c.getValue(), 1e-9);
        }

        @Test
        void unaDireccionNoEncontradaSeGuardaComoTal() {
            respuesta = "[]";

            geocodificador.cadaMinuto();

            verify(cache).guardar(any(), eq((double[]) null));
        }

        @Test
        void siNominatimFallaNoGuardaNadaYReintentaEnLaProxima() {
            estado = 503;

            geocodificador.cadaMinuto();

            assertEquals(1, consultas.get());
            verify(cache, never()).guardar(any(), any());
        }

        @Test
        void sePuedeApagarPorConfiguracion() {
            System.setProperty(Geocodificador.PROPIEDAD, "ninguna");

            geocodificador.cadaMinuto();

            verifyNoInteractions(ruteoMock);
            assertEquals(0, consultas.get());
        }

        @Test
        void unaFallaArmandoLaListaNoRompeElTimer() {
            when(ruteoMock.listarEntregasEnCurso()).thenThrow(new IllegalStateException("caído"));

            geocodificador.cadaMinuto();
            geocodificador.cadaMinuto();

            assertEquals(0, consultas.get());
        }
    }

    @Test
    void elMapaDeGoogleArmaTramosDeHastaNueveParadas() {
        List<String> retiros = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            retiros.add("Depósito " + i);
        }
        String url = EnlaceMapa.recorrido(retiros, "Corrientes 1234");

        assertNotNull(url);
        assertTrue(url.startsWith("https://www.google.com/maps/dir/?api=1"), url);
        assertTrue(url.contains("destination="), url);
        assertTrue(LocalDate.now() != null);
    }
}
