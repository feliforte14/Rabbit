package com.rabbit.transportistas.negocio;

import static com.rabbit.pruebas.Ids.conId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.rabbit.integracion.transportistas.IAdaptadorTransportista;
import com.rabbit.integracion.transportistas.ResultadoCotizacion;
import com.rabbit.integracion.transportistas.ResultadoSolicitud;
import com.rabbit.integracion.transportistas.SolicitudEnvio;
import com.rabbit.seguridad.negocio.IContextoUsuario;
import com.rabbit.transportistas.datos.TransportistaRepository;
import com.rabbit.transportistas.datos.model.Envio;
import com.rabbit.transportistas.datos.model.EstadoEnvio;
import com.rabbit.transportistas.datos.model.TipoIntegracion;
import com.rabbit.transportistas.datos.model.Transportista;
import com.rabbit.transportistas.dto.CotizacionDTO;
import com.rabbit.transportistas.dto.DatosEnvioDTO;
import com.rabbit.transportistas.dto.DatosTransportistaDTO;
import com.rabbit.transportistas.dto.EnvioDTO;
import com.rabbit.transportistas.dto.TransportistaDTO;
import jakarta.ejb.EJBException;
import jakarta.enterprise.event.Event;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
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
class TransportistaServiceTest {

    @Mock private TransportistaRepository repository;
    @Mock private AdaptadoresTransportista adaptadores;
    @Mock private IContextoUsuario contextoUsuario;
    @Mock private Event<EnvioSolicitado> envioSolicitado;
    @Mock private Event<EnvioCancelado> envioCancelado;
    @Mock private Event<EstadoEnvioCambiado> estadoEnvioCambiado;
    @Mock private IAdaptadorTransportista rest;
    @Mock private IAdaptadorTransportista soap;

    @InjectMocks private TransportistaService servicio;

    @BeforeEach
    void adaptadoresPorTipo() {
        when(adaptadores.para(TipoIntegracion.REST)).thenReturn(rest);
        when(adaptadores.para(TipoIntegracion.SOAP_LEGADO)).thenReturn(soap);
    }

    private static Transportista transportista(Long id, String nombre, TipoIntegracion tipo, boolean activo) {
        Transportista t = conId(new Transportista(), id);
        t.setNombre(nombre);
        t.setTipoIntegracion(tipo);
        t.setEndpoint("https://" + nombre.toLowerCase() + ".example/api");
        t.setActivo(activo);
        return t;
    }

    private static Envio envio(Long id, Long idPedido, EstadoEnvio estado, Transportista t) {
        Envio e = conId(new Envio(), id);
        e.setIdPedido(idPedido);
        e.setIdComercio(3L);
        e.setTransportista(t);
        e.setCodigoSeguimiento("TL-" + id);
        e.setEstado(estado);
        return e;
    }

    private static DatosEnvioDTO datosEnvio() {
        DatosEnvioDTO d = new DatosEnvioDTO();
        d.direccionRetiro = "Depósito Rabbit, Av. Siempreviva 742";
        d.direccionEntrega = "Corrientes 1234";
        d.bultos = 2;
        d.cobrarAlEntregar = new BigDecimal("2500");
        return d;
    }

    @Nested
    class Alta {

        private DatosTransportistaDTO datos(String nombre, TipoIntegracion tipo, String endpoint) {
            DatosTransportistaDTO d = new DatosTransportistaDTO();
            d.nombre = nombre;
            d.tipoIntegracion = tipo;
            d.endpoint = endpoint;
            return d;
        }

        @Test
        void registraUnTransportistaActivoConLosDatosRecortados() {
            when(repository.guardar(any())).thenAnswer(inv -> conId(inv.getArgument(0), 9L));

            Long id = servicio.registrarTransportista(datos("  Andreani  ", TipoIntegracion.SOAP_LEGADO,
                    "  http://localhost:8080/Rabbit/TransportistaLegadoService?wsdl "));

            assertEquals(9L, id);
            ArgumentCaptor<Transportista> guardado = ArgumentCaptor.forClass(Transportista.class);
            verify(repository).guardar(guardado.capture());
            assertEquals("Andreani", guardado.getValue().getNombre());
            assertEquals(TipoIntegracion.SOAP_LEGADO, guardado.getValue().getTipoIntegracion());
            assertEquals("http://localhost:8080/Rabbit/TransportistaLegadoService?wsdl", guardado.getValue().getEndpoint());
            assertTrue(guardado.getValue().isActivo());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = "  ")
        void rechazaSinNombre(String nombre) {
            ValidacionException e = assertThrows(ValidacionException.class,
                    () -> servicio.registrarTransportista(datos(nombre, TipoIntegracion.REST, "https://x.example")));
            assertEquals("El nombre de la empresa de logística es obligatorio", e.getMessage());
            verify(repository, never()).guardar(any());
        }

        @Test
        void rechazaSinTipoDeIntegracion() {
            ValidacionException e = assertThrows(ValidacionException.class,
                    () -> servicio.registrarTransportista(datos("X", null, "https://x.example")));
            assertEquals("Elegí cómo se integra la empresa de logística", e.getMessage());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = " ")
        void rechazaSinEndpoint(String endpoint) {
            ValidacionException e = assertThrows(ValidacionException.class,
                    () -> servicio.registrarTransportista(datos("X", TipoIntegracion.REST, endpoint)));
            assertEquals("El endpoint de la empresa de logística es obligatorio", e.getMessage());
        }

        @ParameterizedTest
        @ValueSource(strings = {"ftp://x.example", "no es una url", "javascript:alert(1)", "x.example/api", "http://"})
        void rechazaUnEndpointQueNoEsHttpNiHttps(String endpoint) {
            ValidacionException e = assertThrows(ValidacionException.class,
                    () -> servicio.registrarTransportista(datos("X", TipoIntegracion.REST, endpoint)));
            assertEquals("El endpoint tiene que ser una URL http o https válida", e.getMessage());
        }

        @Test
        void rechazaUnEndpointDeMasDe300Caracteres() {
            String largo = "https://x.example/" + "a".repeat(283);
            ValidacionException e = assertThrows(ValidacionException.class,
                    () -> servicio.registrarTransportista(datos("X", TipoIntegracion.REST, largo)));
            assertEquals("El endpoint no puede superar los 300 caracteres", e.getMessage());
        }

        @Test
        void aceptaUnEndpointDeExactamente300Caracteres() {
            String justo = "https://x.example/" + "a".repeat(282);
            assertEquals(300, justo.length());
            when(repository.guardar(any())).thenAnswer(inv -> conId(inv.getArgument(0), 1L));

            assertEquals(1L, servicio.registrarTransportista(datos("X", TipoIntegracion.REST, justo)));
        }
    }

    @Nested
    class BajaReactivacionYListados {

        @Test
        void laBajaSoloLoMarcaInactivo() {
            Transportista t = transportista(1L, "Rapido", TipoIntegracion.REST, true);
            when(repository.buscarPorId(1L)).thenReturn(t);

            servicio.darDeBajaTransportista(1L);

            assertFalse(t.isActivo());
            verify(repository).actualizar(t);
        }

        @Test
        void reactivarLoVuelveAActivar() {
            Transportista t = transportista(1L, "Rapido", TipoIntegracion.REST, false);
            when(repository.buscarPorId(1L)).thenReturn(t);

            servicio.reactivarTransportista(1L);

            assertTrue(t.isActivo());
            verify(repository).actualizar(t);
        }

        @Test
        void unTransportistaInexistenteONuloFalla() {
            ValidacionException e = assertThrows(ValidacionException.class, () -> servicio.darDeBajaTransportista(4L));
            assertEquals("Empresa de logística no encontrada: 4", e.getMessage());
            assertThrows(ValidacionException.class, () -> servicio.reactivarTransportista(null));
            verify(repository, never()).buscarPorId(null);
        }

        @Test
        void listaTodosOSoloLosActivos() {
            when(repository.listarTodos()).thenReturn(List.of(
                    transportista(1L, "Rapido", TipoIntegracion.REST, true),
                    transportista(2L, "Lento", TipoIntegracion.SOAP_LEGADO, false)));

            List<TransportistaDTO> todos = servicio.listarTodos();
            List<TransportistaDTO> activos = servicio.listarActivos();

            assertEquals(2, todos.size());
            assertEquals(1, activos.size());
            assertEquals("Rapido", activos.get(0).getNombre());
        }

        @Test
        void listaLosEnviosDelComercioDeQuienLlama() {
            Transportista t = transportista(1L, "Rapido", TipoIntegracion.REST, true);
            when(contextoUsuario.idComercioActual()).thenReturn(3L);
            when(repository.listarEnviosDeComercio(3L)).thenReturn(List.of(envio(10L, 100L, EstadoEnvio.SOLICITADO, t)));

            List<EnvioDTO> envios = servicio.listarEnviosDelComercioActual();

            assertEquals(1, envios.size());
            assertEquals(100L, envios.get(0).getIdPedido());
            verify(repository, never()).listarEnvios();
        }

        @Test
        void listaTodosLosEnviosYLosActivos() {
            Transportista t = transportista(1L, "Rapido", TipoIntegracion.REST, true);
            when(repository.listarEnvios()).thenReturn(List.of(envio(10L, 100L, EstadoEnvio.ENTREGADO, t),
                    envio(11L, 101L, EstadoEnvio.EN_TRANSITO, t)));
            when(repository.listarEnviosActivos()).thenReturn(List.of(envio(11L, 101L, EstadoEnvio.EN_TRANSITO, t)));

            assertEquals(2, servicio.listarEnvios().size());
            assertEquals(1, servicio.listarEnviosActivos().size());
        }
    }

    @Nested
    class Cotizacion {

        @Test
        void cotizaConLosActivosYOrdenaPrimeroLosCotizadosDelMasBaratoAlMasCaro() {
            Transportista caro = transportista(1L, "Caro", TipoIntegracion.REST, true);
            Transportista barato = transportista(2L, "Barato", TipoIntegracion.REST, true);
            Transportista legado = transportista(3L, "Legado", TipoIntegracion.SOAP_LEGADO, true);
            Transportista deBaja = transportista(4L, "DeBaja", TipoIntegracion.REST, false);
            when(repository.listarTodos()).thenReturn(List.of(legado, caro, deBaja, barato));
            when(rest.cotizarEnvio(eq(caro.getEndpoint()), any())).thenReturn(ResultadoCotizacion.cotizado(new BigDecimal("5000"), 24));
            when(rest.cotizarEnvio(eq(barato.getEndpoint()), any())).thenReturn(ResultadoCotizacion.cotizado(new BigDecimal("3200"), 48));
            when(soap.cotizarEnvio(any(), any())).thenReturn(ResultadoCotizacion.noCotiza());

            List<CotizacionDTO> cotizaciones = servicio.cotizarEnvio(100L, datosEnvio());

            assertEquals(3, cotizaciones.size());
            assertEquals("Barato", cotizaciones.get(0).getTransportista());
            assertEquals(new BigDecimal("3200"), cotizaciones.get(0).getPrecio());
            assertEquals(48, cotizaciones.get(0).getPlazoHoras());
            assertEquals("Caro", cotizaciones.get(1).getTransportista());
            assertEquals("Legado", cotizaciones.get(2).getTransportista());
            assertEquals("NO_COTIZA", cotizaciones.get(2).getEstado());
            assertEquals("SOAP_LEGADO", cotizaciones.get(2).getTipoIntegracion());
            assertFalse(cotizaciones.get(2).isCotizado());
            verify(rest, never()).cotizarEnvio(eq(deBaja.getEndpoint()), any());
        }

        @Test
        void armaLaSolicitudConLaReferenciaDelPedidoYLosDatosDelEnvio() {
            Transportista t = transportista(1L, "Rapido", TipoIntegracion.REST, true);
            when(repository.listarTodos()).thenReturn(List.of(t));
            when(rest.cotizarEnvio(any(), any())).thenReturn(ResultadoCotizacion.cotizado(BigDecimal.TEN, 24));

            servicio.cotizarEnvio(77L, datosEnvio());

            ArgumentCaptor<SolicitudEnvio> solicitud = ArgumentCaptor.forClass(SolicitudEnvio.class);
            verify(rest).cotizarEnvio(eq(t.getEndpoint()), solicitud.capture());
            assertEquals("PEDIDO-77", solicitud.getValue().referencia());
            assertEquals("Corrientes 1234", solicitud.getValue().direccionEntrega());
            assertEquals(2, solicitud.getValue().bultos());
        }

        @Test
        void unTransportistaQueFallaFiguraComoNoDisponibleSinPerderLosDemas() {
            Transportista roto = transportista(1L, "Roto", TipoIntegracion.REST, true);
            Transportista sano = transportista(2L, "Sano", TipoIntegracion.REST, true);
            when(repository.listarTodos()).thenReturn(List.of(roto, sano));
            when(rest.cotizarEnvio(eq(roto.getEndpoint()), any())).thenThrow(new IllegalStateException("timeout"));
            when(rest.cotizarEnvio(eq(sano.getEndpoint()), any())).thenReturn(ResultadoCotizacion.cotizado(BigDecimal.ONE, 1));

            List<CotizacionDTO> cotizaciones = servicio.cotizarEnvio(100L, datosEnvio());

            assertEquals("Sano", cotizaciones.get(0).getTransportista());
            assertEquals("Roto", cotizaciones.get(1).getTransportista());
            assertEquals("NO_DISPONIBLE", cotizaciones.get(1).getEstado());
            assertNull(cotizaciones.get(1).getPrecio());
        }

        @Test
        void unRechazoConservaElMotivo() {
            Transportista t = transportista(1L, "Chico", TipoIntegracion.REST, true);
            when(repository.listarTodos()).thenReturn(List.of(t));
            when(rest.cotizarEnvio(any(), any())).thenReturn(ResultadoCotizacion.rechazado("Más de 50 bultos"));

            CotizacionDTO c = servicio.cotizarEnvio(100L, datosEnvio()).get(0);

            assertEquals("RECHAZADO", c.getEstado());
            assertEquals("Más de 50 bultos", c.getMotivo());
            assertEquals(1L, c.getIdTransportista());
        }

        @Test
        void sinTransportistasDevuelveUnaListaVacia() {
            when(repository.listarTodos()).thenReturn(List.of());

            assertTrue(servicio.cotizarEnvio(100L, datosEnvio()).isEmpty());
        }
    }

    @Nested
    class SolicitudDeEnvio {

        @Test
        void guardaElEnvioSolicitadoYAvisaParaPoderCompensar() {
            Transportista t = transportista(1L, "Rapido", TipoIntegracion.REST, true);
            when(repository.buscarPorId(1L)).thenReturn(t);
            when(rest.solicitarEnvio(eq(t.getEndpoint()), any())).thenReturn(ResultadoSolicitud.aceptado("RP-555"));

            EnvioDTO dto = servicio.solicitarEnvio(100L, 3L, 1L, datosEnvio());

            ArgumentCaptor<Envio> guardado = ArgumentCaptor.forClass(Envio.class);
            verify(repository).guardarEnvio(guardado.capture());
            Envio e = guardado.getValue();
            assertEquals(100L, e.getIdPedido());
            assertEquals(3L, e.getIdComercio());
            assertSame(t, e.getTransportista());
            assertEquals("RP-555", e.getCodigoSeguimiento());
            assertEquals(EstadoEnvio.SOLICITADO, e.getEstado());
            assertNotNull(e.getFechaSolicitud());
            assertEquals(e.getFechaSolicitud(), e.getFechaActualizacion());
            assertEquals("RP-555", dto.codigoSeguimiento);
            verify(envioSolicitado).fire(new EnvioSolicitado(100L, TipoIntegracion.REST, t.getEndpoint(), "RP-555"));
        }

        @Test
        void usaElAdaptadorSoapParaElLegado() {
            Transportista t = transportista(2L, "Legado", TipoIntegracion.SOAP_LEGADO, true);
            when(repository.buscarPorId(2L)).thenReturn(t);
            when(soap.solicitarEnvio(any(), any())).thenReturn(ResultadoSolicitud.aceptado("TL-1"));

            servicio.solicitarEnvio(100L, 3L, 2L, datosEnvio());

            verify(soap).solicitarEnvio(eq(t.getEndpoint()), any());
            verifyNoInteractions(rest);
        }

        @Test
        void noDerivaAUnTransportistaDadoDeBaja() {
            when(repository.buscarPorId(1L)).thenReturn(transportista(1L, "Rapido", TipoIntegracion.REST, false));

            ValidacionException e = assertThrows(ValidacionException.class,
                    () -> servicio.solicitarEnvio(100L, 3L, 1L, datosEnvio()));
            assertEquals("La empresa de logística Rapido está dada de baja", e.getMessage());
            verifyNoInteractions(rest);
        }

        @Test
        void noDerivaDosVecesElMismoPedido() {
            Transportista t = transportista(1L, "Rapido", TipoIntegracion.REST, true);
            when(repository.buscarPorId(1L)).thenReturn(t);
            when(repository.buscarEnvioDePedido(100L)).thenReturn(envio(10L, 100L, EstadoEnvio.SOLICITADO, t));

            ValidacionException e = assertThrows(ValidacionException.class,
                    () -> servicio.solicitarEnvio(100L, 3L, 1L, datosEnvio()));
            assertEquals("El pedido 100 ya fue derivado a logística tercerizada", e.getMessage());
            verifyNoInteractions(rest);
        }

        @Test
        void unRechazoDelTransportistaNoGuardaNada() {
            when(repository.buscarPorId(1L)).thenReturn(transportista(1L, "Rapido", TipoIntegracion.REST, true));
            when(rest.solicitarEnvio(any(), any())).thenReturn(ResultadoSolicitud.rechazado("Zona sin cobertura"));

            ValidacionException e = assertThrows(ValidacionException.class,
                    () -> servicio.solicitarEnvio(100L, 3L, 1L, datosEnvio()));
            assertEquals("Rapido rechazó el envío: Zona sin cobertura", e.getMessage());
            verify(repository, never()).guardarEnvio(any());
            verify(envioSolicitado, never()).fire(any());
        }

        @Test
        void siElTransportistaNoRespondeNoSeDeriva() {
            when(repository.buscarPorId(1L)).thenReturn(transportista(1L, "Rapido", TipoIntegracion.REST, true));
            when(rest.solicitarEnvio(any(), any())).thenReturn(ResultadoSolicitud.noDisponible());

            ValidacionException e = assertThrows(ValidacionException.class,
                    () -> servicio.solicitarEnvio(100L, 3L, 1L, datosEnvio()));
            assertTrue(e.getMessage().startsWith("Rapido no respondió: el pedido no se derivó."), e.getMessage());
            verify(repository, never()).guardarEnvio(any());
        }
    }

    @Nested
    class CancelacionDelEnvio {

        @Test
        void cancelaUnEnvioActivoYAvisaAlTransportistaDespuesDelCommit() {
            Transportista t = transportista(1L, "Rapido", TipoIntegracion.REST, true);
            Envio e = envio(10L, 100L, EstadoEnvio.EN_TRANSITO, t);
            when(repository.buscarEnvioDePedidoParaActualizar(100L)).thenReturn(e);

            servicio.cancelarEnvioDePedido(100L);

            assertEquals(EstadoEnvio.CANCELADO, e.getEstado());
            assertNotNull(e.getFechaActualizacion());
            verify(repository).actualizarEnvio(e);
            verify(envioCancelado).fire(new EnvioCancelado(100L, TipoIntegracion.REST, t.getEndpoint(), "TL-10"));
        }

        @Test
        void sinEnvioNoHaceNada() {
            servicio.cancelarEnvioDePedido(100L);

            verify(repository, never()).actualizarEnvio(any());
            verify(envioCancelado, never()).fire(any());
        }

        @Test
        void unEnvioYaEntregadoNoSeCancela() {
            Envio e = envio(10L, 100L, EstadoEnvio.ENTREGADO, transportista(1L, "R", TipoIntegracion.REST, true));
            when(repository.buscarEnvioDePedidoParaActualizar(100L)).thenReturn(e);

            servicio.cancelarEnvioDePedido(100L);

            assertEquals(EstadoEnvio.ENTREGADO, e.getEstado());
            verify(envioCancelado, never()).fire(any());
        }
    }

    @Nested
    class NovedadesDelSeguimiento {

        private Transportista t;

        @BeforeEach
        void transportista() {
            t = TransportistaServiceTest.transportista(1L, "Rapido", TipoIntegracion.REST, true);
        }

        @Test
        void aplicaLaNovedadMoviendoPrimeroElPedidoYDespuesElEnvio() {
            Envio leido = envio(10L, 100L, EstadoEnvio.SOLICITADO, t);
            Envio bloqueado = envio(10L, 100L, EstadoEnvio.SOLICITADO, t);
            when(repository.buscarEnvioPorId(10L)).thenReturn(leido);
            when(repository.buscarEnvioParaActualizar(10L)).thenReturn(bloqueado);

            servicio.registrarNovedad(10L, EstadoEnvio.EN_TRANSITO);

            verify(estadoEnvioCambiado).fire(new EstadoEnvioCambiado(100L, EstadoEnvio.SOLICITADO, EstadoEnvio.EN_TRANSITO));
            assertEquals(EstadoEnvio.EN_TRANSITO, bloqueado.getEstado());
            assertNotNull(bloqueado.getFechaActualizacion());
            verify(repository).actualizarEnvio(bloqueado);
        }

        @Test
        void ignoraUnEnvioInexistenteTerminadoOSinCambios() {
            when(repository.buscarEnvioPorId(11L)).thenReturn(envio(11L, 101L, EstadoEnvio.ENTREGADO, t));
            when(repository.buscarEnvioPorId(12L)).thenReturn(envio(12L, 102L, EstadoEnvio.EN_TRANSITO, t));

            servicio.registrarNovedad(10L, EstadoEnvio.EN_TRANSITO);
            servicio.registrarNovedad(11L, EstadoEnvio.CANCELADO);
            servicio.registrarNovedad(12L, EstadoEnvio.EN_TRANSITO);

            verify(estadoEnvioCambiado, never()).fire(any());
            verify(repository, never()).actualizarEnvio(any());
        }

        @Test
        void siPedidosRechazaElCambioLaNovedadNoSeAplica() {
            when(repository.buscarEnvioPorId(10L)).thenReturn(envio(10L, 100L, EstadoEnvio.SOLICITADO, t));
            doThrow(new IllegalStateException("el pedido cambió")).when(estadoEnvioCambiado).fire(any());

            NovedadRechazadaException e = assertThrows(NovedadRechazadaException.class,
                    () -> servicio.registrarNovedad(10L, EstadoEnvio.EN_TRANSITO));
            assertTrue(e.getMessage().contains("TL-10"), e.getMessage());
            verify(repository, never()).actualizarEnvio(any());
        }

        @Test
        void unaFallaDelSistemaEnPedidosSePropagaTalCual() {
            when(repository.buscarEnvioPorId(10L)).thenReturn(envio(10L, 100L, EstadoEnvio.SOLICITADO, t));
            EJBException falla = new EJBException("la base no responde");
            doThrow(falla).when(estadoEnvioCambiado).fire(any());

            EJBException e = assertThrows(EJBException.class,
                    () -> servicio.registrarNovedad(10L, EstadoEnvio.EN_TRANSITO));
            assertSame(falla, e);
        }

        @Test
        void siElEnvioDesaparecioAlBloquearloSeRechaza() {
            when(repository.buscarEnvioPorId(10L)).thenReturn(envio(10L, 100L, EstadoEnvio.SOLICITADO, t));

            NovedadRechazadaException e = assertThrows(NovedadRechazadaException.class,
                    () -> servicio.registrarNovedad(10L, EstadoEnvio.EN_TRANSITO));
            assertEquals("El envío TL-10 ya no existe", e.getMessage());
        }

        @Test
        void siOtraPasadaYaLaAplicoNoEscribeNada() {
            when(repository.buscarEnvioPorId(10L)).thenReturn(envio(10L, 100L, EstadoEnvio.SOLICITADO, t));
            when(repository.buscarEnvioParaActualizar(10L)).thenReturn(envio(10L, 100L, EstadoEnvio.EN_TRANSITO, t));

            servicio.registrarNovedad(10L, EstadoEnvio.EN_TRANSITO);

            verify(repository, never()).actualizarEnvio(any());
        }

        @Test
        void siLoCancelaronMientrasTantoSeRechaza() {
            when(repository.buscarEnvioPorId(10L)).thenReturn(envio(10L, 100L, EstadoEnvio.SOLICITADO, t));
            when(repository.buscarEnvioParaActualizar(10L)).thenReturn(envio(10L, 100L, EstadoEnvio.CANCELADO, t));

            assertThrows(NovedadRechazadaException.class,
                    () -> servicio.registrarNovedad(10L, EstadoEnvio.EN_TRANSITO));
            verify(repository, never()).actualizarEnvio(any());
        }
    }

    @Test
    void elEstadoDelEnvioSabeSiSigueActivo() {
        assertTrue(EstadoEnvio.SOLICITADO.isActivo());
        assertTrue(EstadoEnvio.EN_TRANSITO.isActivo());
        assertFalse(EstadoEnvio.ENTREGADO.isActivo());
        assertFalse(EstadoEnvio.CANCELADO.isActivo());
        verifyNoInteractions(repository);
        verify(rest, never()).consultarEstado(anyString(), anyString());
    }
}
