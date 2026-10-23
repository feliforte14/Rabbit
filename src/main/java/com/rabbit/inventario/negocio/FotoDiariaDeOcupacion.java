package com.rabbit.inventario.negocio;

/**
 * CAPA DE NEGOCIO — el timer de la foto diaria de ocupación (ADR-059), EJB @Singleton.
 *
 * Todos los días a las 23:55 saca la foto de cuántas unidades de cada categoría de tamaño guarda cada comercio.
 * Si WildFly está apagado a esa hora, ese día queda sin foto y se completa con la anterior (ver UnidadesDia).
 *
 * @RunAs("ADMINISTRADOR"): el timer no tiene usuario y sacar la foto es una operación de administrador
 * (mismo esquema que LiquidacionMensual en Pagos).
 */

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RunAs;
import jakarta.ejb.Schedule;
import jakarta.ejb.Singleton;
import jakarta.inject.Inject;
import java.time.LocalDate;
import java.util.logging.Level;
import java.util.logging.Logger;

@Singleton
@RunAs("ADMINISTRADOR")
@PermitAll
public class FotoDiariaDeOcupacion {

    private static final Logger LOG = Logger.getLogger(FotoDiariaDeOcupacion.class.getName());

    @Inject
    private IOcupacionDeDeposito ocupacion;

    @Schedule(hour = "23", minute = "55", second = "0", persistent = false)
    public void sacarFoto() {
        try {
            ocupacion.tomarFoto(LocalDate.now());
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "[Inventario] Falló la foto diaria de ocupación", e);
        }
    }
}
