package com.rabbit.pagos.negocio;

/**
 * CAPA DE NEGOCIO — el timer de la liquidación mensual (ADR-034), EJB
 * @Singleton.
 *
 * Corre todos los días a las 6: liquida el mes anterior a los comercios que
 * no la tengan (el día 1 hace todo el trabajo; los demás días no encuentra
 * nada nuevo, salvo que WildFly haya estado apagado el 1) y reintenta los
 * cobros que quedaron PENDIENTE porque el banco no respondió.
 *
 * @RunAs("ADMINISTRADOR"): el timer no tiene usuario, y liquidar y cobrar
 * son operaciones de administrador (mismo esquema que SeguimientoDeEnvios
 * con OPERADOR).
 */

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RunAs;
import jakarta.ejb.Schedule;
import jakarta.ejb.Singleton;
import jakarta.inject.Inject;
import java.time.YearMonth;
import java.util.logging.Level;
import java.util.logging.Logger;

@Singleton
@RunAs("ADMINISTRADOR")
@PermitAll
public class LiquidacionMensual {

    private static final Logger LOG = Logger.getLogger(LiquidacionMensual.class.getName());

    @Inject
    private ILiquidaciones liquidaciones;

    @Schedule(hour = "6", minute = "0", second = "0", persistent = false)
    public void liquidar() {
        YearMonth anterior = YearMonth.now().minusMonths(1);
        try {
            liquidaciones.liquidarMes(anterior);
            liquidaciones.reintentarPendientes();
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "[Pagos] Falló la liquidación automática de " + anterior, e);
        }
    }
}
