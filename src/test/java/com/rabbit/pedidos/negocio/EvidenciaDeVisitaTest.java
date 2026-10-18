package com.rabbit.pedidos.negocio;

import static com.rabbit.pedidos.datos.model.MotivoEntregaFallida.*;
import static org.junit.jupiter.api.Assertions.*;

import com.rabbit.pedidos.datos.model.OrigenUbicacion;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** El comentario, las fotos y la ubicación de una visita fallida (ADR-045). */
class EvidenciaDeVisitaTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 17, 15, 30);
    private static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0 };

    @Test
    void elComentarioEsOpcionalSalvoConRechazoUOtro() {
        assertNull(EvidenciaDeVisita.comentario(CLIENTE_AUSENTE, "   "));
        assertEquals("Toqué timbre", EvidenciaDeVisita.comentario(CLIENTE_AUSENTE, "  Toqué timbre "));
        ValidacionException rechazo = assertThrows(ValidacionException.class,
                () -> EvidenciaDeVisita.comentario(RECHAZADO, null));
        assertTrue(rechazo.getMessage().contains("por qué lo rechazó"));
        assertThrows(ValidacionException.class, () -> EvidenciaDeVisita.comentario(OTRO, ""));
    }

    @Test
    void elComentarioTieneUnLargoMaximo() {
        String largo = "x".repeat(EvidenciaDeVisita.LARGO_COMENTARIO + 1);
        assertThrows(ValidacionException.class, () -> EvidenciaDeVisita.comentario(CLIENTE_AUSENTE, largo));
    }

    @Test
    void hastaTresFotosYSeIgnoranLosCamposVacios() {
        List<EvidenciaDeVisita.Foto> fotos = EvidenciaDeVisita.fotos(CLIENTE_AUSENTE,
                Arrays.asList(JPEG, new byte[0], null, JPEG));
        assertEquals(2, fotos.size());
        assertEquals("image/jpeg", fotos.get(0).tipo());
        assertTrue(EvidenciaDeVisita.fotos(CLIENTE_AUSENTE, List.of()).isEmpty());
        assertThrows(ValidacionException.class,
                () -> EvidenciaDeVisita.fotos(CLIENTE_AUSENTE, List.of(JPEG, JPEG, JPEG, JPEG)));
    }

    @Test
    void unaFotoQueNoEsImagenSeRechaza() {
        ValidacionException e = assertThrows(ValidacionException.class,
                () -> EvidenciaDeVisita.fotos(CLIENTE_AUSENTE, List.of(JPEG, new byte[] { '%', 'P', 'D', 'F' })));
        assertTrue(e.getMessage().startsWith("La foto 2"));
    }

    @Test
    void unPaqueteDanadoPideAlMenosUnaFoto() {
        assertThrows(ValidacionException.class, () -> EvidenciaDeVisita.fotos(PAQUETE_DANADO, List.of()));
        assertEquals(1, EvidenciaDeVisita.fotos(PAQUETE_DANADO, List.of(JPEG)).size());
    }

    @Test
    void usaLaUbicacionDelCelularSiLaDio() {
        EvidenciaDeVisita.Ubicacion u = EvidenciaDeVisita.ubicacion(-34.6, -58.4, 12.6,
                -34.5, -58.3, AHORA.minusMinutes(1), AHORA);
        assertEquals(OrigenUbicacion.DISPOSITIVO, u.origen());
        assertEquals(-34.6, u.latitud());
        assertEquals(13, u.precisionMetros());
        assertEquals(AHORA, u.fecha());
    }

    @Test
    void sinLaDelCelularUsaLaUltimaCompartidaSiEsReciente() {
        LocalDateTime hace5 = AHORA.minusMinutes(5);
        EvidenciaDeVisita.Ubicacion u = EvidenciaDeVisita.ubicacion(null, null, null, -34.5, -58.3, hace5, AHORA);
        assertEquals(OrigenUbicacion.ULTIMA_COMPARTIDA, u.origen());
        assertEquals(hace5, u.fecha());
        assertNull(u.precisionMetros());
        // Una de hace media hora ya no dice dónde estaba.
        assertNull(EvidenciaDeVisita.ubicacion(null, null, null, -34.5, -58.3, AHORA.minusMinutes(30), AHORA));
    }

    @Test
    void descartaUbicacionesQueNoSirven() {
        // Fuera de rango, 0,0 o con kilómetros de error: cae a la última compartida (no hay).
        assertNull(EvidenciaDeVisita.ubicacion(95.0, -58.4, 10.0, null, null, null, AHORA));
        assertNull(EvidenciaDeVisita.ubicacion(0.0, 0.0, 10.0, null, null, null, AHORA));
        assertNull(EvidenciaDeVisita.ubicacion(-34.6, -58.4, 20000.0, null, null, null, AHORA));
        assertNull(EvidenciaDeVisita.ubicacion(Double.NaN, -58.4, null, null, null, null, AHORA));
        // Sin precisión informada, la del celular sirve igual.
        assertNotNull(EvidenciaDeVisita.ubicacion(-34.6, -58.4, null, null, null, null, AHORA));
    }
}
