package com.rabbit.pedidos.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO de salida: el tablero de indicadores de entrega (ADR-044) de un
 * período, para el personal (toda la red o un comercio) o para un comercio
 * (lo suyo). Son los que mide la industria de última milla: entregas a
 * tiempo, al primer intento y cuánto tarda un pedido en llegar.
 *
 * Los porcentajes son null cuando no hay con qué calcularlos (por ejemplo,
 * ningún pedido entregado en el período): mostrar 0 % sería mentir.
 */
public class IndicadoresDTO {

    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public LocalDate desde;
    public LocalDate hasta;

    // Pedidos recibidos en el período, según cómo están hoy.
    public int recibidos;
    public int pendientes;
    public int enCurso;
    public int entregados;
    public int cancelados;
    public int devueltos;

    // A tiempo: entregados el día prometido o antes (ADR-042). Solo cuentan
    // los que tenían fecha prometida.
    public int entregadosConPromesa;
    public int entregadosATiempo;

    // Primer intento: de los que terminaron el reparto (entregados o
    // devueltos al comercio), cuántos se entregaron sin visitas fallidas.
    public int alPrimerIntento;

    // Horas promedio de recibido a entregado; null sin entregas.
    public BigDecimal horasPromedioEntrega;

    // En la lista de espera ahora (no depende del período).
    public long enEsperaAhora;

    // Lo que se le cobra al comercio por envío en el mes en curso (abono +
    // cargos / envíos). Lo completa la vista con Pagos; null si no hay envíos.
    public BigDecimal costoPorEnvio;

    public List<Motivo> motivosFallidos = new ArrayList<>();
    public List<PorRepartidor> porRepartidor = new ArrayList<>();
    // Los mismos indicadores en el período anterior de igual largo (los 30
    // días antes de estos 30), para mostrar si se mejoró o empeoró. Null en
    // el propio período anterior.
    public IndicadoresDTO anterior;

    // Entregas de cada día del período, para el gráfico de columnas (un
    // elemento por día, también los que no tuvieron entregas).
    public List<Dia> porDia = new ArrayList<>();

    /** Entregas de un día, según si cumplieron la fecha prometida. */
    public static class Dia {
        public LocalDate fecha;
        public int aTiempo;
        public int fueraDeTermino;
        public int sinPromesa;

        public Dia(LocalDate fecha) {
            this.fecha = fecha;
        }

        public LocalDate getFecha() { return fecha; }
        public int getATiempo() { return aTiempo; }
        public int getFueraDeTermino() { return fueraDeTermino; }
        public int getSinPromesa() { return sinPromesa; }
    }

    /** Cuántas visitas fallaron por cada motivo (el último de cada pedido). */
    public static class Motivo {
        public String descripcion;
        public int cantidad;

        public Motivo(String descripcion, int cantidad) {
            this.descripcion = descripcion;
            this.cantidad = cantidad;
        }

        public String getDescripcion() { return descripcion; }
        public int getCantidad() { return cantidad; }
    }

    /** Los mismos indicadores, por repartidor propio (solo el personal). */
    public static class PorRepartidor {
        public Long idRepartidor;
        public String nombre;
        public int entregas;
        public int conPromesa;
        public int aTiempo;
        public int terminados;
        public int alPrimerIntento;
        public BigDecimal horasPromedio;

        public Long getIdRepartidor() { return idRepartidor; }
        public String getNombre() { return nombre; }
        public int getEntregas() { return entregas; }
        public BigDecimal getHorasPromedio() { return horasPromedio; }
        public String getHorasPromedioTexto() { return horas(horasPromedio); }
        public Integer getPorcentajeATiempo() { return porcentaje(aTiempo, conPromesa); }
        public Integer getPorcentajePrimerIntento() { return porcentaje(alPrimerIntento, terminados); }
    }

    /** "6,5 h" o "—". */
    public static String horas(BigDecimal horas) {
        if (horas == null) {
            return "—";
        }
        return new java.text.DecimalFormat("0.0", java.text.DecimalFormatSymbols.getInstance(
                java.util.Locale.forLanguageTag("es-AR"))).format(horas) + " h";
    }

    /** Redondeado a entero; null si no hay base. */
    public static Integer porcentaje(int parte, int total) {
        return total == 0 ? null : (int) Math.round(parte * 100.0 / total);
    }

    // --- Diferencias contra el período anterior (null si falta un dato) ---

    /** Puntos porcentuales de diferencia en "a tiempo". */
    public Integer getDeltaATiempo() {
        return anterior == null ? null : resta(getPorcentajeATiempo(), anterior.getPorcentajeATiempo());
    }

    /** Puntos porcentuales de diferencia en "al primer intento". */
    public Integer getDeltaPrimerIntento() {
        return anterior == null ? null : resta(getPorcentajePrimerIntento(), anterior.getPorcentajePrimerIntento());
    }

    /** Horas de diferencia en el promedio de recibido a entregado. */
    public BigDecimal getDeltaHoras() {
        if (anterior == null || horasPromedioEntrega == null || anterior.horasPromedioEntrega == null) {
            return null;
        }
        return horasPromedioEntrega.subtract(anterior.horasPromedioEntrega);
    }

    /** Variación porcentual de los pedidos recibidos; null si antes no hubo ninguno. */
    public Integer getDeltaRecibidos() {
        if (anterior == null || anterior.recibidos == 0) {
            return null;
        }
        return (int) Math.round((recibidos - anterior.recibidos) * 100.0 / anterior.recibidos);
    }

    private static Integer resta(Integer actual, Integer previo) {
        return actual == null || previo == null ? null : actual - previo;
    }

    public IndicadoresDTO getAnterior() { return anterior; }
    public int getTerminados() { return entregados + devueltos; }
    public Integer getPorcentajeATiempo() { return porcentaje(entregadosATiempo, entregadosConPromesa); }
    public Integer getPorcentajePrimerIntento() { return porcentaje(alPrimerIntento, getTerminados()); }
    public String getDesdeTexto() { return desde.format(DIA); }
    public String getHastaTexto() { return hasta.format(DIA); }

    public int getRecibidos() { return recibidos; }
    public int getPendientes() { return pendientes; }
    public int getEnCurso() { return enCurso; }
    public int getEntregados() { return entregados; }
    public int getCancelados() { return cancelados; }
    public int getDevueltos() { return devueltos; }
    public int getEntregadosConPromesa() { return entregadosConPromesa; }
    public int getEntregadosATiempo() { return entregadosATiempo; }
    public int getAlPrimerIntento() { return alPrimerIntento; }
    public BigDecimal getHorasPromedioEntrega() { return horasPromedioEntrega; }
    public String getHorasPromedioEntregaTexto() { return horas(horasPromedioEntrega); }
    public long getEnEsperaAhora() { return enEsperaAhora; }
    public BigDecimal getCostoPorEnvio() { return costoPorEnvio; }
    public List<Motivo> getMotivosFallidos() { return motivosFallidos; }
    public List<PorRepartidor> getPorRepartidor() { return porRepartidor; }
    public List<Dia> getPorDia() { return porDia; }
}
