package com.rabbit.pagos.presentacion;

/**
 * CAPA DE PRESENTACIÓN (Managed Bean - JSF) — liquidaciones.xhtml: la
 * liquidación mensual a comercios (ADR-034), para el personal.
 *
 * Se elige un mes: si ya terminó y está liquidado, se ve lo cobrado; si
 * terminó y no está liquidado, un administrador lo liquida (lo mismo que
 * hace solo el timer el día 1); si es el mes en curso, se ve una vista
 * previa de lo que se va a cobrar, sin guardar nada.
 */

import com.rabbit.comercios.dto.ComercioDTO;
import com.rabbit.comercios.negocio.IConsultaComercios;
import com.rabbit.infraestructura.Mensajes;
import com.rabbit.pagos.dto.LiquidacionDTO;
import com.rabbit.pagos.negocio.ILiquidaciones;
import com.rabbit.pagos.negocio.TarifaMensual;
import com.rabbit.pagos.negocio.ValidacionException;
import jakarta.annotation.PostConstruct;
import jakarta.ejb.EJBAccessException;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Named
@ViewScoped
public class LiquidacionBean implements Serializable {

    private static final Locale ES_AR = Locale.forLanguageTag("es-AR");

    @Inject
    private ILiquidaciones liquidaciones;

    @Inject
    private IConsultaComercios comercios;

    // "2026-09"
    private String periodo;
    private List<LiquidacionDTO> filas;
    private boolean vistaPrevia;
    private Map<Long, String> nombresComercio;
    private TarifaMensual tarifa;

    @PostConstruct
    public void iniciar() {
        periodo = YearMonth.now().minusMonths(1).toString();
        nombresComercio = comercios.listarTodos().stream()
                .collect(Collectors.toMap(ComercioDTO::getId, ComercioDTO::getNombre));
        cargar();
    }

    /** Lo que se ve del mes elegido: lo liquidado, o la vista previa si no hay nada. */
    public void cargar() {
        tarifa = liquidaciones.tarifaVigente();
        YearMonth mes = YearMonth.parse(periodo);
        filas = liquidaciones.listarDelPeriodo(mes);
        vistaPrevia = filas.isEmpty();
        if (vistaPrevia) {
            filas = liquidaciones.previsualizar(mes);
        }
    }

    public void liquidar() {
        try {
            filas = liquidaciones.liquidarMes(YearMonth.parse(periodo));
            vistaPrevia = false;
            long cobradas = filas.stream().filter(f -> "COBRADA".equals(f.estado)).count();
            Mensajes.info("Mes liquidado: " + filas.size() + " comercio(s), " + cobradas + " cobrado(s) por el banco");
        } catch (ValidacionException e) {
            Mensajes.error(e.getMessage());
        } catch (EJBAccessException e) {
            Mensajes.error("Solo un administrador puede liquidar el mes.");
        }
    }

    public void reintentar(Long idLiquidacion) {
        try {
            LiquidacionDTO resultado = liquidaciones.reintentarCobro(idLiquidacion);
            if ("COBRADA".equals(resultado.estado)) {
                Mensajes.info("Liquidación cobrada");
            } else {
                Mensajes.error(resultado.motivo);
            }
            cargar();
        } catch (ValidacionException e) {
            Mensajes.error(e.getMessage());
        } catch (EJBAccessException e) {
            Mensajes.error("Solo un administrador puede reintentar el cobro.");
        }
    }

    /** Los últimos 12 meses, del más nuevo al más viejo, más los que ya tengan liquidaciones. */
    public List<String> getPeriodos() {
        List<String> lista = new ArrayList<>();
        YearMonth mes = YearMonth.now();
        for (int i = 0; i < 12; i++) {
            lista.add(mes.minusMonths(i).toString());
        }
        for (String p : liquidaciones.listarPeriodos()) {
            if (!lista.contains(p)) {
                lista.add(p);
            }
        }
        return lista;
    }

    /** "septiembre 2026" a partir de "2026-09". */
    public String nombreMes(String periodo) {
        YearMonth mes = YearMonth.parse(periodo);
        return mes.getMonth().getDisplayName(TextStyle.FULL, ES_AR) + " " + mes.getYear()
                + (mes.equals(YearMonth.now()) ? " (en curso)" : "");
    }

    public boolean isMesCerrado() {
        return YearMonth.parse(periodo).isBefore(YearMonth.now());
    }

    public String nombreComercio(Long idComercio) {
        return nombresComercio.getOrDefault(idComercio, "Comercio " + idComercio);
    }

    public BigDecimal getTotalGeneral() {
        return filas.stream().map(LiquidacionDTO::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Lo ya cobrado por el banco. */
    public BigDecimal getTotalCobrado() {
        return sumar("COBRADA");
    }

    /** Lo que falta cobrar: con cobro pendiente o rechazado por el banco. */
    public BigDecimal getTotalPorCobrar() {
        return sumar("PENDIENTE").add(sumar("RECHAZADA"));
    }

    public int getCantidadPorCobrar() {
        return (int) filas.stream().filter(l -> "PENDIENTE".equals(l.getEstado()) || "RECHAZADA".equals(l.getEstado())).count();
    }

    public int getTotalEntregas() {
        return filas.stream().mapToInt(LiquidacionDTO::getCantidadEnvios).sum();
    }

    public BigDecimal getTotalKilos() {
        return filas.stream().map(LiquidacionDTO::getKilos).filter(k -> k != null).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumar(String estado) {
        return filas.stream().filter(l -> estado.equals(l.getEstado()))
                .map(LiquidacionDTO::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public String getPeriodo() { return periodo; }
    public void setPeriodo(String periodo) { this.periodo = periodo; }
    public List<LiquidacionDTO> getFilas() { return filas; }

    // La columna Acciones solo aparece si hay algún cobro para reintentar.
    public boolean isHayCobrosParaReintentar() {
        return filas != null && filas.stream().anyMatch(l ->
                "PENDIENTE".equals(l.getEstado()) || "RECHAZADA".equals(l.getEstado()));
    }
    public boolean isVistaPrevia() { return vistaPrevia; }
    public TarifaMensual getTarifa() { return tarifa; }
}
