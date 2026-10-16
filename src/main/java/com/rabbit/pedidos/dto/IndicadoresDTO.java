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
        public Integer getPorcentajeATiempo() { return porcentaje(aTiempo, conPromesa); }
        public Integer getPorcentajePrimerIntento() { return porcentaje(alPrimerIntento, terminados); }
    }

    /** Redondeado a entero; null si no hay base. */
    public static Integer porcentaje(int parte, int total) {
        return total == 0 ? null : (int) Math.round(parte * 100.0 / total);
    }

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
    public long getEnEsperaAhora() { return enEsperaAhora; }
    public BigDecimal getCostoPorEnvio() { return costoPorEnvio; }
    public List<Motivo> getMotivosFallidos() { return motivosFallidos; }
    public List<PorRepartidor> getPorRepartidor() { return porRepartidor; }
}
