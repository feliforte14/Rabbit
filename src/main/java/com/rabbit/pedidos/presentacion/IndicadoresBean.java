package com.rabbit.pedidos.presentacion;

/**
 * CAPA DE PRESENTACIÓN (Managed Bean - JSF) — tablero de indicadores de
 * entrega (ADR-044): personal/indicadores.xhtml (toda la red o un comercio,
 * con el detalle por repartidor) y comercio/mis-indicadores.xhtml (lo suyo).
 *
 * Junta dos componentes sin que uno dependa del otro: los indicadores de
 * entrega salen de Pedidos (IIndicadores) y el costo por envío, de la
 * liquidación del mes en curso de Pagos (ILiquidaciones).
 *
 * @RequestScoped: el período y el comercio llegan por la URL
 * (?dias=30&comercio=3), así el enlace se puede compartir tal cual.
 */

import com.rabbit.comercios.dto.ComercioDTO;
import com.rabbit.comercios.negocio.IConsultaComercios;
import com.rabbit.infraestructura.Mensajes;
import com.rabbit.pagos.dto.LiquidacionDTO;
import com.rabbit.pagos.negocio.ILiquidaciones;
import com.rabbit.pedidos.dto.IndicadoresDTO;
import com.rabbit.pedidos.negocio.IIndicadores;
import com.rabbit.repartidores.dto.RepartidorDTO;
import com.rabbit.repartidores.negocio.IGestionRepartidores;
import jakarta.enterprise.context.RequestScoped;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Named
@RequestScoped
public class IndicadoresBean {

    private static final int DIAS_POR_DEFECTO = 30;

    @Inject
    private IIndicadores indicadores;

    @Inject
    private ILiquidaciones liquidaciones;

    @Inject
    private IConsultaComercios comercios;

    @Inject
    private IGestionRepartidores repartidores;

    private Integer dias;
    private Long idComercio;
    private IndicadoresDTO tablero;
    private List<ComercioDTO> listaComercios;

    // f:viewAction de personal/indicadores.xhtml.
    public void cargarPersonal() {
        if (yaRedirigido()) {
            return;
        }
        normalizarDias();
        listaComercios = comercios.listarTodos();
        tablero = indicadores.calcular(dias, idComercio);
        Map<Long, String> nombres = repartidores.listarTodos().stream()
                .collect(Collectors.toMap(RepartidorDTO::getId, RepartidorDTO::getNombre));
        tablero.porRepartidor.forEach(f -> f.nombre = nombres.getOrDefault(f.idRepartidor, "Repartidor " + f.idRepartidor));
        tablero.costoPorEnvio = costoPorEnvio(liquidaciones.previsualizar(YearMonth.now()).stream()
                .filter(l -> idComercio == null || idComercio.equals(l.idComercio))
                .collect(Collectors.toList()));
    }

    // f:viewAction de comercio/mis-indicadores.xhtml.
    public void cargarComercio() {
        if (yaRedirigido()) {
            return;
        }
        normalizarDias();
        try {
            tablero = indicadores.calcularDelComercioActual(dias);
            LiquidacionDTO mes = liquidaciones.previsualizarMesEnCursoDelComercioActual();
            tablero.costoPorEnvio = mes == null ? null : costoPorEnvio(List.of(mes));
        } catch (com.rabbit.seguridad.negocio.ValidacionException e) {
            // Cuenta COMERCIO sin comercio asociado.
            Mensajes.error(e.getMessage());
        }
    }

    // Lo que se paga en el mes por todo el servicio, repartido entre los envíos.
    private static BigDecimal costoPorEnvio(List<LiquidacionDTO> liquidacionesDelMes) {
        int envios = liquidacionesDelMes.stream().mapToInt(LiquidacionDTO::getCantidadEnvios).sum();
        if (envios == 0) {
            return null;
        }
        BigDecimal total = liquidacionesDelMes.stream().map(LiquidacionDTO::getTotal)
                .filter(t -> t != null).reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(envios), 2, RoundingMode.HALF_UP);
    }

    // El guardián de la página (sesionBean.exigir...) corre antes y, si el
    // usuario no puede verla, ya lo mandó a otra: no se consulta nada.
    private static boolean yaRedirigido() {
        return FacesContext.getCurrentInstance().getResponseComplete();
    }

    // Un valor que no está en la lista (la URL se puede tocar a mano) vuelve al de por defecto.
    private void normalizarDias() {
        if (dias == null || !IIndicadores.PERIODOS.contains(dias)) {
            dias = DIAS_POR_DEFECTO;
        }
    }

    /** "87 %" o "—" si no hay con qué calcularlo. */
    public String porcentaje(Integer valor) {
        return valor == null ? "—" : valor + " %";
    }

    public String nombreComercio(Long id) {
        if (id == null || listaComercios == null) {
            return "Toda la red";
        }
        return listaComercios.stream().filter(c -> id.equals(c.id)).map(c -> c.nombre).findFirst().orElse("Comercio " + id);
    }

    public List<Integer> getPeriodos() { return IIndicadores.PERIODOS; }
    public Integer getDias() { return dias; }
    public void setDias(Integer dias) { this.dias = dias; }
    public Long getIdComercio() { return idComercio; }
    public void setIdComercio(Long idComercio) { this.idComercio = idComercio; }
    public IndicadoresDTO getTablero() { return tablero; }
    public List<ComercioDTO> getListaComercios() { return listaComercios; }
}
