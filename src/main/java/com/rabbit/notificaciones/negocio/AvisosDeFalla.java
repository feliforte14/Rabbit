package com.rabbit.notificaciones.negocio;

/**
 * Reconoce el aviso de una visita fallida (ADR-045) entre los cambios de
 * estado del tópico.
 *
 * Después de una visita fallida el pedido vuelve a PENDIENTE y, si hay
 * repartidor libre, AsignadorDeEspera lo confirma con otro en menos de un
 * segundo. Los mensajes del tópico pueden llegar en otro orden, y la regla
 * general de Notificaciones descarta un cambio más viejo que el último
 * avisado. Con la falla eso no sirve: el comercio y el cliente tienen que
 * enterarse de que no se pudo entregar aunque el aviso llegue tarde.
 *
 * Pedidos guarda el momento exacto de ese cambio (fechaUltimaFalla): el
 * PENDIENTE con esa fecha es el de la falla. Un PENDIENTE con otra fecha
 * (el del alta, que llegó tarde) no lo es, aunque el pedido ya tenga motivo.
 *
 * Sin estado ni contenedor: se prueba con tests unitarios.
 */

import java.time.Duration;
import java.time.LocalDateTime;

final class AvisosDeFalla {

    // La fecha viaja por JMS y la base puede recortar los nanosegundos.
    private static final Duration TOLERANCIA = Duration.ofMillis(1);

    private AvisosDeFalla() {
    }

    static boolean esDeLaFalla(LocalDateTime fechaCambio, LocalDateTime fechaUltimaFalla) {
        return fechaCambio != null && fechaUltimaFalla != null
                && Duration.between(fechaUltimaFalla, fechaCambio).abs().compareTo(TOLERANCIA) <= 0;
    }
}
