package com.rabbit.notificaciones.negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.rabbit.notificaciones.negocio.BandejaDeCorreos.Correo;
import com.rabbit.pedidos.negocio.EntregaModificada;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Los dos tiempos del canal de mail: dentro de la transacción del aviso se guarda el mail en la bandeja, y después
 * del commit se intenta mandar lo pendiente. Sin servidor de correo no se guarda nada.
 */
@ExtendWith(MockitoExtension.class)
class AvisosPorMailTest {

    @Mock private BandejaDeCorreos bandeja;
    @Mock private EnvioDeCorreosPendientes envio;
    @InjectMocks private AvisosPorMail canal;

    @BeforeEach
    void conServidorDeCorreo() {
        System.setProperty("rabbit.mail.smtp.host", "smtp.demo.test");
    }

    @AfterEach
    void limpiar() {
        System.clearProperty("rabbit.mail.smtp.host");
    }

    private Correo guardado() {
        ArgumentCaptor<Correo> correo = ArgumentCaptor.forClass(Correo.class);
        verify(bandeja).registrar(correo.capture());
        return correo.getValue();
    }

    @Test
    @DisplayName("El aviso al comercio se guarda con el aviso de que también está en su portal")
    void avisoAlComercio() {
        canal.registrarAviso(new AvisoRegistrado(3L, "ventas@tienda.test", 100L, "Tu pedido #100 está en camino"));

        Correo correo = guardado();
        assertEquals("ventas@tienda.test", correo.destino());
        assertEquals(100L, correo.idPedido());
        assertEquals("Pedido #100", correo.titulo());
        assertEquals("Tu pedido #100 está en camino.\n\nEste aviso también está en tu portal de Rabbit.", correo.texto());
        assertNull(correo.enlace());
        assertNull(correo.codigoEntrega());
    }

    @Test
    @DisplayName("El aviso al cliente final se guarda con el enlace y los códigos")
    void avisoAlDestinatario() {
        canal.registrarAvisoDestinatario(new AvisoDestinatarioRegistrado(100L, "ana@demo.test", "¡Llega hoy!",
                "Hola, Ana.", "https://r.test/s?codigo=RB-1", "RB-1", "4821"));

        Correo correo = guardado();
        assertEquals("ana@demo.test", correo.destino());
        assertEquals("¡Llega hoy!", correo.titulo());
        assertEquals("https://r.test/s?codigo=RB-1", correo.enlace());
        assertEquals("RB-1", correo.codigoSeguimiento());
        assertEquals("4821", correo.codigoEntrega());
    }

    @Test
    @DisplayName("El cambio de entrega se guarda con lo que quedó registrado, sin el código de entrega")
    void cambioDeEntrega() {
        canal.registrarCambioDeEntrega(new EntregaModificada(7L, "ana@demo.test", "Ana", "Tienda Feliz", "RB-1",
                LocalDate.now().plusDays(2), "Dejar en portería", "Marta Gómez"));

        Correo correo = guardado();
        assertEquals("ana@demo.test", correo.destino());
        assertEquals("Guardamos los cambios de tu entrega de Tienda Feliz", correo.titulo());
        assertTrue(correo.texto().contains("Instrucciones para el repartidor: Dejar en portería"), correo.texto());
        assertTrue(correo.texto().contains("Autorizaste a Marta Gómez"), correo.texto());
        assertTrue(correo.enlace().contains("codigo=RB-1"), correo.enlace());
        assertNull(correo.codigoEntrega());
    }

    @Test
    @DisplayName("Sin servidor de correo configurado no se guarda nada: no hay qué reintentar")
    void sinServidorNoSeGuardaNada() {
        System.clearProperty("rabbit.mail.smtp.host");

        canal.registrarAviso(new AvisoRegistrado(3L, "ventas@tienda.test", 100L, "texto"));
        canal.registrarAvisoDestinatario(new AvisoDestinatarioRegistrado(100L, "ana@demo.test", "asunto", "texto"));
        canal.registrarCambioDeEntrega(new EntregaModificada(7L, "ana@demo.test", "Ana", "Tienda", "RB-1", null, null, null));

        verify(bandeja, never()).registrar(any());
    }

    @Test
    @DisplayName("Sin email de destino no se guarda nada")
    void sinEmail() {
        canal.registrarAviso(new AvisoRegistrado(3L, null, 100L, "sin email"));
        canal.registrarAvisoDestinatario(new AvisoDestinatarioRegistrado(100L, null, "asunto", "texto"));
        canal.registrarCambioDeEntrega(new EntregaModificada(7L, null, "Ana", "Tienda", "RB-1", null, null, null));

        verify(bandeja, never()).registrar(any());
    }

    @Test
    @DisplayName("Después del commit de cada aviso se intenta mandar lo pendiente")
    void despuesDelCommitSeIntentaMandar() {
        canal.enviarTrasAviso(new AvisoRegistrado(3L, "a@b.test", 100L, "texto"));
        canal.enviarTrasAvisoDestinatario(new AvisoDestinatarioRegistrado(100L, "a@b.test", "asunto", "texto"));
        canal.enviarTrasCambioDeEntrega(new EntregaModificada(7L, "a@b.test", "Ana", "Tienda", "RB-1", null, null, null));

        verify(envio, org.mockito.Mockito.times(3)).enviarPendientes();
    }

    @Test
    @DisplayName("Si el intento de envío falla, el error no sale: el mail queda pendiente y lo reintenta el timer")
    void unFalloNoSePropaga() {
        doThrow(new IllegalStateException("sin base")).when(envio).enviarPendientes();

        canal.enviarTrasAviso(new AvisoRegistrado(3L, "a@b.test", 100L, "texto"));

        verify(envio).enviarPendientes();
    }
}
