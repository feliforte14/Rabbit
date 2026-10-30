package com.rabbit.notificaciones.negocio;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.rabbit.comercios.dto.ComercioDTO;
import com.rabbit.comercios.negocio.ComercioEliminado;
import com.rabbit.comercios.negocio.IConsultaComercios;
import com.rabbit.notificaciones.datos.NotificacionRepository;
import com.rabbit.notificaciones.datos.model.AvisoDestinatario;
import com.rabbit.notificaciones.datos.model.Notificacion;
import com.rabbit.pedidos.datos.model.MotivoEntregaFallida;
import com.rabbit.pedidos.datos.model.OrigenPedido;
import com.rabbit.pedidos.dto.PedidoDTO;
import com.rabbit.pedidos.negocio.ISeguimientoPedido;
import com.rabbit.seguridad.negocio.IContextoUsuario;
import jakarta.enterprise.event.Event;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.TextMessage;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificacionesServiciosTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 20, 10, 0);

    private static PedidoDTO pedido(Long id) {
        PedidoDTO p = new PedidoDTO();
        p.id = id;
        p.codigoSeguimiento = "RB-ABCDEFGHJK";
        p.origen = OrigenPedido.STOCK_CONSIGNADO;
        p.emailDestinatario = "ana@ejemplo.com";
        p.nombreDestinatario = "Ana";
        p.fechaPrometida = LocalDate.of(2026, 10, 20);
        return p;
    }

    private static ComercioDTO comercio(String nombre, String email) {
        ComercioDTO c = new ComercioDTO();
        c.id = 3L;
        c.nombre = nombre;
        c.email = email;
        return c;
    }

    @Nested
    class AvisoAlComercio {

        @Mock private IContextoUsuario contextoUsuario;
        @Mock private ISeguimientoPedido pedidos;
        @Mock private NotificacionRepository repository;
        @Mock private Event<AvisoRegistrado> avisoRegistrado;
        @Mock private IConsultaComercios comercios;
        @InjectMocks private NotificacionService servicio;

        private Notificacion guardada() {
            ArgumentCaptor<Notificacion> n = ArgumentCaptor.forClass(Notificacion.class);
            verify(repository).guardar(n.capture());
            return n.getValue();
        }

        @ParameterizedTest
        @CsvSource({
                "PENDIENTE, fue recibido por Rabbit",
                "CONFIRMADO, fue confirmado y ya tiene quién lo lleve",
                "CANCELADO, fue cancelado",
                "OTRO_ESTADO, cambió de estado a OTRO_ESTADO"})
        void guardaElAvisoConElTextoDeCadaEstadoYLoMandaPorMail(String estado, String texto) {
            when(comercios.obtenerComercio(3L)).thenReturn(comercio("Tienda", " ventas@tienda.com "));
            when(pedidos.consultarEstadoPedido(100L)).thenReturn(pedido(100L));

            assertTrue(servicio.avisarCambioDeEstado(100L, 3L, estado, AHORA));

            Notificacion n = guardada();
            assertEquals("Tu pedido #100 " + texto, n.getMensaje());
            assertEquals(100L, n.getIdPedido());
            assertEquals(3L, n.getIdComercio());
            assertEquals(estado, n.getEstadoPedido());
            assertEquals(AHORA, n.getFechaCambio());
            verify(avisoRegistrado).fire(new AvisoRegistrado(3L, "ventas@tienda.com", 100L, "Tu pedido #100 " + texto));
        }

        @Test
        void enCaminoSumaElCodigoDeSeguimiento() {
            when(pedidos.consultarEstadoPedido(100L)).thenReturn(pedido(100L));

            servicio.avisarCambioDeEstado(100L, 3L, "EN_CAMINO", AHORA);

            assertEquals("Tu pedido #100 está en camino. Tu cliente lo puede seguir en vivo con el código RB-ABCDEFGHJK"
                    + " en la página de seguimiento de Rabbit", guardada().getMensaje());
        }

        @Test
        void entregadoConRemitoAvisaDondeVerlo() {
            PedidoDTO p = pedido(100L);
            p.conRemito = true;
            when(pedidos.consultarEstadoPedido(100L)).thenReturn(p);

            servicio.avisarCambioDeEstado(100L, 3L, "ENTREGADO", AHORA);

            assertEquals("Tu pedido #100 fue entregado. El remito firmado está en tu portal, en Mis pedidos",
                    guardada().getMensaje());
        }

        @Test
        void siNoSePuedeLeerElPedidoAvisaIgualSinElExtra() {
            when(pedidos.consultarEstadoPedido(100L)).thenThrow(new IllegalStateException("caído"));
            when(comercios.obtenerComercio(3L)).thenThrow(new IllegalStateException("caído"));

            assertTrue(servicio.avisarCambioDeEstado(100L, 3L, "EN_CAMINO", AHORA));

            assertEquals("Tu pedido #100 está en camino", guardada().getMensaje());
            verify(avisoRegistrado).fire(new AvisoRegistrado(3L, null, 100L, "Tu pedido #100 está en camino"));
        }

        @Test
        void descartaUnCambioMasViejoQueElUltimoAvisado() {
            when(repository.ultimaFechaCambio(100L)).thenReturn(AHORA);

            assertFalse(servicio.avisarCambioDeEstado(100L, 3L, "CONFIRMADO", AHORA.minusSeconds(1)));
            assertFalse(servicio.avisarCambioDeEstado(100L, 3L, "CONFIRMADO", AHORA));

            verify(repository, never()).guardar(any());
            verify(avisoRegistrado, never()).fire(any());
        }

        @Test
        void unaEntregaFallidaUsaElTextoDeLaFallaAunqueLlegueConLaMismaFecha() {
            PedidoDTO p = pedido(100L);
            p.motivoEntregaFallida = MotivoEntregaFallida.CLIENTE_AUSENTE;
            p.fechaUltimaFalla = AHORA;
            p.intentosFallidos = 1;
            p.ultimaFallaContoComoVisita = true;
            when(pedidos.consultarEstadoPedido(100L)).thenReturn(p);
            when(repository.ultimaFechaCambio(100L)).thenReturn(AHORA);

            assertTrue(servicio.avisarCambioDeEstado(100L, 3L, "PENDIENTE", AHORA));

            String texto = guardada().getMensaje();
            assertTrue(texto.startsWith("Tu pedido #100"), texto);
            assertTrue(texto.contains("Cliente ausente"), texto);
            assertNotEquals("Tu pedido #100 fue recibido por Rabbit", texto);
        }

        @Test
        void unPendienteQueNoEsDeLaFallaUsaElTextoNormal() {
            PedidoDTO p = pedido(100L);
            p.motivoEntregaFallida = MotivoEntregaFallida.CLIENTE_AUSENTE;
            p.fechaUltimaFalla = AHORA.minusHours(1);
            when(pedidos.consultarEstadoPedido(100L)).thenReturn(p);

            servicio.avisarCambioDeEstado(100L, 3L, "PENDIENTE", AHORA);

            assertEquals("Tu pedido #100 fue recibido por Rabbit", guardada().getMensaje());
        }

        @Test
        void laDevolucionAlComercioExplicaElMotivo() {
            PedidoDTO p = pedido(100L);
            p.motivoEntregaFallida = MotivoEntregaFallida.RECHAZADO;
            p.motivoDevolucion = "El cliente no quiso el producto";
            when(pedidos.consultarEstadoPedido(100L)).thenReturn(p);

            servicio.avisarCambioDeEstado(100L, 3L, "DEVUELTO_AL_COMERCIO", AHORA);

            assertTrue(guardada().getMensaje().contains("El cliente no quiso el producto"));
        }

        @Test
        void recortaMensajesDeMasDe255Caracteres() {
            PedidoDTO p = pedido(100L);
            p.motivoEntregaFallida = MotivoEntregaFallida.OTRO;
            p.motivoDevolucion = "x".repeat(400);
            when(pedidos.consultarEstadoPedido(100L)).thenReturn(p);

            servicio.avisarCambioDeEstado(100L, 3L, "DEVUELTO_AL_COMERCIO", AHORA);

            String mensaje = guardada().getMensaje();
            assertEquals(255, mensaje.length());
            assertTrue(mensaje.endsWith("..."));
        }

        @Test
        void listaLosRecientesYLosDelComercioDeQuienLlama() {
            Notificacion n = new Notificacion();
            n.setIdPedido(100L);
            n.setMensaje("hola");
            n.setFechaAviso(AHORA);
            when(repository.listarRecientes(5)).thenReturn(List.of(n));
            when(contextoUsuario.idComercioActual()).thenReturn(3L);
            when(repository.listarDeComercio(3L, 10)).thenReturn(List.of(n, n));

            assertEquals(1, servicio.listarRecientes(5).size());
            assertEquals(2, servicio.listarDelComercioActual(10).size());
            assertEquals("hola", servicio.listarRecientes(5).get(0).mensaje);
        }
    }

    @Nested
    class AvisoAQuienRecibe {

        @Mock private NotificacionRepository repository;
        @Mock private ISeguimientoPedido pedidos;
        @Mock private IConsultaComercios comercios;
        @Mock private Event<AvisoDestinatarioRegistrado> avisoRegistrado;
        @InjectMocks private AvisoDestinatarioService servicio;

        @AfterEach
        void limpiar() {
            System.clearProperty(AvisoDestinatarioService.PROPIEDAD_URL);
        }

        private AvisoDestinatario guardado() {
            ArgumentCaptor<AvisoDestinatario> a = ArgumentCaptor.forClass(AvisoDestinatario.class);
            verify(repository).guardarAvisoDestinatario(a.capture());
            return a.getValue();
        }

        @Test
        void mandaElMailConElLinkAlSeguimiento() {
            when(pedidos.consultarEstadoPedido(100L)).thenReturn(pedido(100L));
            when(comercios.obtenerComercio(3L)).thenReturn(comercio("Tienda Feliz", null));

            assertTrue(servicio.avisar(100L, 3L, "CONFIRMADO", AHORA));

            AvisoDestinatario a = guardado();
            assertEquals("ana@ejemplo.com", a.getEmail());
            assertEquals("CONFIRMADO", a.getEstadoPedido());
            assertTrue(a.getMensaje().contains("https://localhost:8443/Rabbit/seguimiento.xhtml?codigo=RB-ABCDEFGHJK"),
                    a.getMensaje());
            ArgumentCaptor<AvisoDestinatarioRegistrado> evento = ArgumentCaptor.forClass(AvisoDestinatarioRegistrado.class);
            verify(avisoRegistrado).fire(evento.capture());
            assertEquals("ana@ejemplo.com", evento.getValue().email());
            assertEquals(a.getAsunto(), evento.getValue().asunto());
        }

        @Test
        void enCaminoElMailLlevaElCodigoDeEntregaPeroLaBaseNo() {
            when(pedidos.consultarEstadoPedido(100L)).thenReturn(pedido(100L));
            when(pedidos.consultarCodigoDeEntrega(100L)).thenReturn("4821");

            servicio.avisar(100L, 3L, "EN_CAMINO", AHORA);

            ArgumentCaptor<AvisoDestinatarioRegistrado> evento = ArgumentCaptor.forClass(AvisoDestinatarioRegistrado.class);
            verify(avisoRegistrado).fire(evento.capture());
            assertTrue(evento.getValue().texto().contains("4821"), evento.getValue().texto());
            assertFalse(guardado().getMensaje().contains("4821"));
        }

        @Test
        void sinEmailNoSeAvisa() {
            PedidoDTO p = pedido(100L);
            p.emailDestinatario = null;
            when(pedidos.consultarEstadoPedido(100L)).thenReturn(p);

            assertFalse(servicio.avisar(100L, 3L, "CONFIRMADO", AHORA));
            verify(repository, never()).guardarAvisoDestinatario(any());
        }

        @Test
        void unPedidoInexistenteNoSeAvisa() {
            when(pedidos.consultarEstadoPedido(100L)).thenThrow(new com.rabbit.pedidos.negocio.ValidacionException("no existe"));

            assertFalse(servicio.avisar(100L, 3L, "CONFIRMADO", AHORA));
            verifyNoInteractions(avisoRegistrado);
        }

        @Test
        void descartaUnCambioViejoSalvoQueSeaElAvisoDeLaFalla() {
            PedidoDTO p = pedido(100L);
            p.motivoEntregaFallida = MotivoEntregaFallida.CLIENTE_AUSENTE;
            p.fechaUltimaFalla = AHORA;
            when(pedidos.consultarEstadoPedido(100L)).thenReturn(p);
            when(repository.ultimaFechaCambioAvisadaAlDestinatario(100L)).thenReturn(AHORA);

            assertFalse(servicio.avisar(100L, 3L, "CONFIRMADO", AHORA));
            assertTrue(servicio.avisar(100L, 3L, "PENDIENTE", AHORA));
        }

        @Test
        void unRetiroEnDepositoUsaElNombreDelDepositoYUnComercioCaidoNoFrenaElAviso() {
            PedidoDTO p = pedido(100L);
            p.tipoEntrega = com.rabbit.pedidos.datos.model.TipoEntrega.RETIRO_EN_DEPOSITO;
            p.depositoRetiro = "Depósito Norte";
            when(pedidos.consultarEstadoPedido(100L)).thenReturn(p);
            when(comercios.obtenerComercio(3L)).thenThrow(new IllegalStateException("caído"));

            assertTrue(servicio.avisar(100L, 3L, "CONFIRMADO", AHORA));
            assertTrue(guardado().getMensaje().contains("la tienda") || guardado().getAsunto().contains("la tienda"));
        }

        @Test
        void elLinkUsaLaUrlPublicaConfiguradaSinBarraFinal() {
            System.setProperty(AvisoDestinatarioService.PROPIEDAD_URL, " https://rabbit.example/app/ ");

            assertEquals("https://rabbit.example/app/seguimiento.xhtml?codigo=RB-1",
                    AvisoDestinatarioService.enlaceSeguimiento("RB-1"));
        }

        @Test
        void listaLosAvisosRecientes() {
            AvisoDestinatario a = new AvisoDestinatario();
            a.setIdPedido(100L);
            a.setEmail("ana@ejemplo.com");
            a.setFechaAviso(AHORA);
            when(repository.listarAvisosDestinatarioRecientes(3)).thenReturn(List.of(a));

            assertEquals(1, servicio.listarRecientes(3).size());
        }
    }

    @Nested
    class SuscriptoresDelTopico {

        @Mock private INotificaciones notificaciones;
        @Mock private IAvisosAlDestinatario avisos;
        @InjectMocks private SuscriptorNotificacionesEstadoPedido alComercio;
        @InjectMocks private SuscriptorDestinatarioEstadoPedido aQuienRecibe;

        private TextMessage mensaje(String json) throws JMSException {
            TextMessage m = mock(TextMessage.class);
            when(m.getText()).thenReturn(json);
            return m;
        }

        @Test
        void unMensajeValidoLlegaALosDosSuscriptores() throws JMSException {
            TextMessage m = mensaje("{\"idPedido\":100,\"idComercio\":3,\"estado\":\"ENTREGADO\",\"fechaCambio\":\"2026-10-20T10:00\"}");

            alComercio.onMessage(m);
            aQuienRecibe.onMessage(m);

            verify(notificaciones).avisarCambioDeEstado(100L, 3L, "ENTREGADO", AHORA);
            verify(avisos).avisar(100L, 3L, "ENTREGADO", AHORA);
        }

        @ParameterizedTest
        @ValueSource(strings = {"no es json", "{\"idPedido\":100}", "{\"idPedido\":\"x\",\"idComercio\":3,\"estado\":\"E\",\"fechaCambio\":\"2026-10-20T10:00\"}",
                "{\"idPedido\":1,\"idComercio\":3,\"estado\":\"E\",\"fechaCambio\":\"ayer\"}"})
        void unMensajeIlegibleSeConsumeSinAvisar(String json) throws JMSException {
            alComercio.onMessage(mensaje(json));
            aQuienRecibe.onMessage(mensaje(json));

            verifyNoInteractions(notificaciones, avisos);
        }

        @Test
        void unMensajeQueNoEsDeTextoSeDescarta() throws JMSException {
            Message otro = mock(Message.class);

            alComercio.onMessage(otro);
            aQuienRecibe.onMessage(otro);

            verifyNoInteractions(notificaciones, avisos);
        }

        @Test
        void siNoSePuedeLeerElTextoSeConsume() throws JMSException {
            TextMessage m = mock(TextMessage.class);
            when(m.getText()).thenThrow(new JMSException("roto"));

            alComercio.onMessage(m);
            aQuienRecibe.onMessage(m);

            verifyNoInteractions(notificaciones, avisos);
        }
    }

    @Nested
    class MailYLimpieza {

        @Mock private NotificacionRepository repository;
        @InjectMocks private ReferenciasAComercioEnNotificaciones referencias;

        @AfterEach
        void limpiar() {
            System.clearProperty("rabbit.mail.smtp.host");
            System.clearProperty("rabbit.mail.smtp.port");
        }

        @Test
        void alEliminarUnComercioSeBorranSusAvisos() {
            when(repository.eliminarNotificacionesDeComercio(3L)).thenReturn(4);

            referencias.alConfirmarEliminacion(new ComercioEliminado(3L));

            verify(repository).eliminarNotificacionesDeComercio(3L);
        }

        @Test
        void sinServidorDeCorreoNoSeMandaNadaYNoFalla() {
            AvisosPorMail mail = new AvisosPorMail();

            assertDoesNotThrow(() -> mail.alRegistrarAviso(new AvisoRegistrado(3L, "a@b.com", 100L, "texto")));
            assertDoesNotThrow(() -> mail.alRegistrarAvisoDestinatario(
                    new AvisoDestinatarioRegistrado(100L, "a@b.com", "asunto", "texto")));
            assertNull(ConfiguracionSmtp.desde(System.getProperties()));
        }

        @Test
        void unServidorDeCorreoCaidoNoFrenaElAviso() {
            System.setProperty("rabbit.mail.smtp.host", "127.0.0.1");
            System.setProperty("rabbit.mail.smtp.port", "1");
            AvisosPorMail mail = new AvisosPorMail();

            assertDoesNotThrow(() -> mail.alRegistrarAviso(new AvisoRegistrado(3L, "a@b.com", 100L, "texto")));
            assertDoesNotThrow(() -> mail.alRegistrarAviso(new AvisoRegistrado(3L, null, 100L, "sin email")));
        }
    }
}
