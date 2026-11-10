package com.rabbit.notificaciones.negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbit.notificaciones.datos.CorreoPendienteRepository;
import com.rabbit.notificaciones.datos.model.CorreoPendiente;
import com.rabbit.notificaciones.negocio.BandejaDeCorreos.Correo;
import com.rabbit.notificaciones.negocio.BandejaDeCorreos.Pendiente;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** La bandeja de mails: guarda lo que hay que mandar, lo devuelve sin la entidad y lo marca según salió. */
@ExtendWith(MockitoExtension.class)
class BandejaDeCorreosTest {

    @Mock private CorreoPendienteRepository repository;
    @InjectMocks private BandejaDeCorreos bandeja;

    @Test
    @DisplayName("Registrar guarda el mail PENDIENTE con todo lo que hace falta para mandarlo")
    void registrar() {
        LocalDateTime antes = LocalDateTime.now();

        bandeja.registrar(new Correo(7L, "ana@demo.test", "¡Llega hoy!", "Hola, Ana.", "https://r.test/s", "RB-1", "4821"));

        ArgumentCaptor<CorreoPendiente> fila = ArgumentCaptor.forClass(CorreoPendiente.class);
        verify(repository).guardar(fila.capture());
        CorreoPendiente guardada = fila.getValue();
        assertEquals(7L, guardada.getIdPedido());
        assertEquals("ana@demo.test", guardada.getDestino());
        assertEquals("¡Llega hoy!", guardada.getTitulo());
        assertEquals("Hola, Ana.", guardada.getTexto());
        assertEquals("https://r.test/s", guardada.getEnlace());
        assertEquals("RB-1", guardada.getCodigoSeguimiento());
        assertEquals("4821", guardada.getCodigoEntrega());
        assertEquals(CorreoPendiente.PENDIENTE, guardada.getEstado());
        assertEquals(0, guardada.getIntentosFallidos());
        assertTrue(!guardada.getCreadoEn().isBefore(antes));
        assertNull(guardada.getResueltoEn());
    }

    @Test
    @DisplayName("Los textos más largos que la columna se recortan con puntos suspensivos")
    void recortaLoLargo() {
        bandeja.registrar(new Correo(7L, "ana@demo.test", "t".repeat(300), "x".repeat(5000), null, null, null));

        ArgumentCaptor<CorreoPendiente> fila = ArgumentCaptor.forClass(CorreoPendiente.class);
        verify(repository).guardar(fila.capture());
        assertEquals(200, fila.getValue().getTitulo().length());
        assertTrue(fila.getValue().getTitulo().endsWith("..."));
        assertEquals(4000, fila.getValue().getTexto().length());
        assertNull(fila.getValue().getEnlace());
    }

    @Test
    @DisplayName("Los pendientes se devuelven como datos, sin la entidad")
    void pendientes() {
        CorreoPendiente fila = new CorreoPendiente();
        fila.setIdPedido(7L);
        fila.setDestino("ana@demo.test");
        fila.setTitulo("Titulo");
        fila.setTexto("Texto");
        fila.setEnlace("https://r.test/s");
        fila.setCodigoSeguimiento("RB-1");
        fila.setCodigoEntrega("4821");
        when(repository.listarPendientes(50)).thenReturn(List.of(fila));

        List<Pendiente> pendientes = bandeja.pendientes(50);

        assertEquals(1, pendientes.size());
        assertEquals(new Correo(7L, "ana@demo.test", "Titulo", "Texto", "https://r.test/s", "RB-1", "4821"),
                pendientes.get(0).correo());
        assertNotNull(pendientes.get(0));
    }

    @Test
    @DisplayName("Marcar enviado, registrar un fallo y descartar van al repositorio")
    void marcas() {
        bandeja.marcarEnviado(1L);
        bandeja.registrarFallo(2L);
        bandeja.descartar(3L, "El servidor rechazó el destinatario");

        verify(repository).marcarEnviado(eq(1L), any(LocalDateTime.class));
        verify(repository).sumarIntentoFallido(2L);
        verify(repository).descartar(eq(3L), any(LocalDateTime.class), eq("El servidor rechazó el destinatario"));
    }

    @Test
    @DisplayName("Descartar los vencidos deja el motivo y devuelve cuántos fueron")
    void descartarVencidos() {
        LocalDateTime limite = LocalDateTime.of(2026, 11, 9, 12, 0);
        when(repository.descartarPendientesCreadosAntesDe(eq(limite), any(), eq("No se pudo mandar a tiempo"))).thenReturn(4);

        assertEquals(4, bandeja.descartarVencidos(limite));
    }

    @Test
    @DisplayName("La limpieza suma los enviados y los descartados que borró")
    void limpiar() {
        LocalDateTime enviados = LocalDateTime.of(2026, 11, 9, 4, 0);
        LocalDateTime descartados = LocalDateTime.of(2026, 11, 3, 4, 0);
        when(repository.borrarResueltosAntesDe(CorreoPendiente.ENVIADO, enviados)).thenReturn(5);
        when(repository.borrarResueltosAntesDe(CorreoPendiente.DESCARTADO, descartados)).thenReturn(2);

        assertEquals(7, bandeja.limpiar(enviados, descartados));
    }
}
