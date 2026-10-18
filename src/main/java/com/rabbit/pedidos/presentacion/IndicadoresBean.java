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
 *
 * GRÁFICOS: cada uno recibe sus datos en un atributo data-datos, como JSON
 * con un formato común que dibuja indicadores.js:
 *   {"series": ["A tiempo", ...], "filas": [{"etiqueta", "detalle", "valores": [..]}],
 *    "formato": "entero" | "porcentaje", "columnasExtra": [..] (solo tabla)}
 * Los nombres (comercios, repartidores, motivos) los pone el script como
 * texto, nunca como HTML.
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
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObjectBuilder;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
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

    // ===============================================================
    // Datos de los gráficos
    // ===============================================================

    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter DIA_LARGO =
            DateTimeFormatter.ofPattern("EEEE dd/MM", Locale.forLanguageTag("es-AR"));

    /** Entregas de cada día: a tiempo, fuera de término y sin fecha prometida. */
    public String getJsonPorDia() {
        JsonArrayBuilder filas = Json.createArrayBuilder();
        for (IndicadoresDTO.Dia d : tablero.porDia) {
            filas.add(fila(d.fecha.format(DIA), d.fecha.format(DIA_LARGO), d.aTiempo, d.fueraDeTermino, d.sinPromesa));
        }
        return grafico(List.of("A tiempo", "Fuera de término", "Sin fecha prometida"), filas, "entero", null);
    }

    /** Pedidos recibidos en el período, según cómo están hoy. */
    public String getJsonEstados() {
        JsonArrayBuilder filas = Json.createArrayBuilder()
                .add(fila("Entregados", null, tablero.entregados))
                .add(fila("En reparto", null, tablero.enCurso))
                .add(fila("Pendientes", null, tablero.pendientes))
                .add(fila("Devueltos al comercio", null, tablero.devueltos))
                .add(fila("Cancelados", null, tablero.cancelados));
        return grafico(List.of("Pedidos"), filas, "entero", null);
    }

    /** Por qué fallaron las visitas (el último motivo de cada pedido). */
    public String getJsonMotivos() {
        JsonArrayBuilder filas = Json.createArrayBuilder();
        tablero.motivosFallidos.forEach(m -> filas.add(fila(m.descripcion, null, m.cantidad)));
        return grafico(List.of("Pedidos"), filas, "entero", null);
    }

    /** % a tiempo y % al primer intento de cada repartidor; entregas y horas, solo en la tabla. */
    public String getJsonRepartidores() {
        JsonArrayBuilder filas = Json.createArrayBuilder();
        for (IndicadoresDTO.PorRepartidor r : tablero.porRepartidor) {
            JsonArrayBuilder valores = Json.createArrayBuilder();
            agregar(valores, r.getPorcentajeATiempo());
            agregar(valores, r.getPorcentajePrimerIntento());
            // Números, no textos: así el CSV se puede sumar en Excel.
            JsonArrayBuilder extra = Json.createArrayBuilder().add(r.entregas);
            if (r.horasPromedio == null) {
                extra.addNull();
            } else {
                extra.add(r.horasPromedio);
            }
            filas.add(Json.createObjectBuilder()
                    .add("etiqueta", r.nombre)
                    .add("valores", valores)
                    .add("extra", extra));
        }
        return grafico(List.of("A tiempo", "Al primer intento"), filas, "porcentaje",
                List.of("Entregas", "Horas de recibido a entregado"));
    }

    private static JsonObjectBuilder fila(String etiqueta, String detalle, int... valores) {
        JsonArrayBuilder lista = Json.createArrayBuilder();
        for (int v : valores) {
            lista.add(v);
        }
        JsonObjectBuilder fila = Json.createObjectBuilder().add("etiqueta", etiqueta).add("valores", lista);
        if (detalle != null) {
            fila.add("detalle", detalle);
        }
        return fila;
    }

    private static void agregar(JsonArrayBuilder valores, Integer valor) {
        if (valor == null) {
            valores.addNull();
        } else {
            valores.add(valor);
        }
    }

    private static String grafico(List<String> series, JsonArrayBuilder filas, String formato, List<String> columnasExtra) {
        JsonObjectBuilder json = Json.createObjectBuilder()
                .add("series", Json.createArrayBuilder(series))
                .add("filas", filas)
                .add("formato", formato);
        if (columnasExtra != null) {
            json.add("columnasExtra", Json.createArrayBuilder(columnasExtra));
        }
        return json.build().toString();
    }

    /**
     * La diferencia contra el período anterior, como la muestra la tarjeta:
     * "▲ 6 pts vs. período anterior", "▼ 1,5 h ...", "= sin cambios". Flecha
     * y texto, así no depende del
     * color. Vacío si no hay con qué comparar.
     */
    public String delta(Number valor, String unidad) {
        if (valor == null) {
            return "";
        }
        double v = valor.doubleValue();
        if (Math.abs(v) < 0.05) {
            return "= sin cambios";
        }
        String numero = valor instanceof BigDecimal b
                ? new java.text.DecimalFormat("0.0", java.text.DecimalFormatSymbols.getInstance(
                        Locale.forLanguageTag("es-AR"))).format(b.abs())
                : String.valueOf(Math.abs(valor.intValue()));
        return (v > 0 ? "▲ " : "▼ ") + numero + " " + unidad + " vs. período anterior";
    }

    /** Verde si mejoró, rojo si empeoró: depende de si subir es bueno. */
    public String claseDelta(Number valor, boolean subirEsBueno) {
        if (valor == null || Math.abs(valor.doubleValue()) < 0.05) {
            return "viz-delta";
        }
        boolean mejoro = valor.doubleValue() > 0 == subirEsBueno;
        return "viz-delta " + (mejoro ? "viz-delta-mejor" : "viz-delta-peor");
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
