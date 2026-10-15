package com.rabbit.pagos.presentacion;

/**
 * CAPA DE PRESENTACIÓN (Managed Bean - JSF) — mis-liquidaciones.xhtml: lo
 * que Rabbit le cobró al comercio cada mes (ADR-034). El comercio sale de
 * la identidad autenticada (ILiquidaciones.listarDelComercioActual).
 */

import com.rabbit.infraestructura.Mensajes;
import com.rabbit.pagos.dto.LiquidacionDTO;
import com.rabbit.pagos.negocio.ILiquidaciones;
import com.rabbit.pagos.negocio.TarifaMensual;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

@Named
@RequestScoped
public class MisLiquidacionesBean {

    @Inject
    private ILiquidaciones liquidaciones;

    private List<LiquidacionDTO> filas;
    private TarifaMensual tarifa;

    @PostConstruct
    public void cargar() {
        tarifa = liquidaciones.tarifaVigente();
        try {
            filas = liquidaciones.listarDelComercioActual();
        } catch (com.rabbit.seguridad.negocio.ValidacionException e) {
            // Cuenta COMERCIO sin comercio asociado.
            filas = List.of();
            Mensajes.error(e.getMessage());
        }
    }

    public String nombreMes(String periodo) {
        YearMonth mes = YearMonth.parse(periodo);
        return mes.getMonth().getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es-AR")) + " " + mes.getYear();
    }

    public List<LiquidacionDTO> getFilas() { return filas; }
    public TarifaMensual getTarifa() { return tarifa; }
}
