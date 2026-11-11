package com.rabbit.inventario.dto;

import com.rabbit.inventario.datos.model.EstadoOrdenDeRecepcion;
import com.rabbit.inventario.datos.model.LineaDeRecepcion;
import com.rabbit.inventario.datos.model.OrdenDeRecepcion;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/** DTO de salida: una orden de recepción con lo esperado, lo recibido y la diferencia (ADR-058). */
public class OrdenDeRecepcionDTO {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    public Long id;
    public Long idComercio;
    public Long idDeposito;
    public String deposito;
    public String estado;
    public String notas;
    public String fechaAnuncio;
    public String fechaRecepcion;
    public String recibidaPor;
    public List<Linea> lineas;

    /** Una línea: lo anunciado, lo que llegó (null antes de recibirla) y la diferencia. */
    public static class Linea {
        public Long id;
        public String producto;
        public int cantidadEsperada;
        public Integer cantidadRecibida;
        public BigDecimal pesoKg;

        /** Lo que llegó menos lo anunciado: negativo = faltó, positivo = llegó de más; null si todavía no se recibió. */
        public Integer getDiferencia() {
            return cantidadRecibida == null ? null : cantidadRecibida - cantidadEsperada;
        }

        /** "Faltaron 3", "Llegaron 2 de más" o "Completo"; vacío antes de la recepción. */
        public String getDiferenciaTexto() {
            Integer d = getDiferencia();
            if (d == null) {
                return "";
            }
            if (d < 0) {
                return "Faltaron " + (-d);
            }
            return d > 0 ? "Llegaron " + d + " de más" : "Completo";
        }

        public Long getId() { return id; }
        public String getProducto() { return producto; }
        public int getCantidadEsperada() { return cantidadEsperada; }
        public Integer getCantidadRecibida() { return cantidadRecibida; }
        public BigDecimal getPesoKg() { return pesoKg; }
    }

    public static OrdenDeRecepcionDTO desde(OrdenDeRecepcion o) {
        OrdenDeRecepcionDTO dto = new OrdenDeRecepcionDTO();
        dto.id = o.getId();
        dto.idComercio = o.getIdComercio();
        dto.idDeposito = o.getDeposito().getId();
        dto.deposito = o.getDeposito().getNombre();
        dto.estado = o.getEstado().name();
        dto.notas = o.getNotas();
        dto.fechaAnuncio = o.getFechaAnuncio() != null ? o.getFechaAnuncio().format(FORMATO) : null;
        dto.fechaRecepcion = o.getFechaRecepcion() != null ? o.getFechaRecepcion().format(FORMATO) : null;
        dto.recibidaPor = o.getRecibidaPor();
        dto.lineas = o.getLineas().stream().map(OrdenDeRecepcionDTO::linea).collect(Collectors.toList());
        return dto;
    }

    private static Linea linea(LineaDeRecepcion l) {
        Linea dto = new Linea();
        dto.id = l.getId();
        dto.producto = l.getProducto();
        dto.cantidadEsperada = l.getCantidadEsperada();
        dto.cantidadRecibida = l.getCantidadRecibida();
        dto.pesoKg = l.getPesoKg();
        return dto;
    }

    public boolean isAnunciada() { return EstadoOrdenDeRecepcion.ANUNCIADA.name().equals(estado); }
    public int getTotalEsperado() { return lineas.stream().mapToInt(l -> l.cantidadEsperada).sum(); }
    public int getTotalRecibido() { return lineas.stream().mapToInt(l -> l.cantidadRecibida == null ? 0 : l.cantidadRecibida).sum(); }

    /** ¿Alguna línea llegó distinta de lo anunciado? (solo con la recepción registrada). */
    public boolean isHayDiferencia() {
        return lineas.stream().anyMatch(l -> l.getDiferencia() != null && l.getDiferencia() != 0);
    }

    /** "3 productos · 120 unidades" */
    public String getResumen() {
        return lineas.size() + (lineas.size() == 1 ? " producto" : " productos") + " · " + getTotalEsperado() + " u.";
    }

    public Long getId() { return id; }
    public Long getIdComercio() { return idComercio; }
    public Long getIdDeposito() { return idDeposito; }
    public String getDeposito() { return deposito; }
    public String getEstado() { return estado; }
    public String getNotas() { return notas; }
    public String getFechaAnuncio() { return fechaAnuncio; }
    public String getFechaRecepcion() { return fechaRecepcion; }
    public String getRecibidaPor() { return recibidaPor; }
    public List<Linea> getLineas() { return lineas; }
}
