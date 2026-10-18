package com.rabbit.pedidos.dto;

/**
 * DTO de salida: una visita fallida con su evidencia (ADR-045), para el
 * personal y el comercio dueño del pedido. Las fotos van por ID: la imagen
 * la sirve FotoVisitaServlet, que vuelve a verificar quién la pide.
 */

import com.rabbit.pedidos.datos.model.IntentoEntrega;
import com.rabbit.pedidos.datos.model.OrigenUbicacion;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class IntentoEntregaDTO {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    public Long id;
    public Long idPedido;
    public LocalDateTime fecha;
    public String motivo;
    public String comentario;
    public int numeroVisita;
    public boolean devueltoAlComercio;
    public String registradoPor;
    public Double latitud;
    public Double longitud;
    public Integer precisionMetros;
    public OrigenUbicacion origenUbicacion;
    public LocalDateTime fechaUbicacion;
    public List<Long> idsFotos = new ArrayList<>();

    public static IntentoEntregaDTO desde(IntentoEntrega i) {
        IntentoEntregaDTO dto = new IntentoEntregaDTO();
        dto.id = i.getId();
        dto.idPedido = i.getIdPedido();
        dto.fecha = i.getFecha();
        dto.motivo = i.getMotivo().getDescripcion();
        dto.comentario = i.getComentario();
        dto.numeroVisita = i.getNumeroVisita();
        dto.devueltoAlComercio = i.isDevueltoAlComercio();
        dto.registradoPor = i.getRegistradoPor();
        dto.latitud = i.getLatitud();
        dto.longitud = i.getLongitud();
        dto.precisionMetros = i.getPrecisionMetros();
        dto.origenUbicacion = i.getOrigenUbicacion();
        dto.fechaUbicacion = i.getFechaUbicacion();
        return dto;
    }

    public Long getId() { return id; }
    public String getFechaTexto() { return fecha != null ? fecha.format(FECHA) : ""; }
    public String getMotivo() { return motivo; }
    public String getComentario() { return comentario; }
    public int getNumeroVisita() { return numeroVisita; }
    public boolean isDevueltoAlComercio() { return devueltoAlComercio; }
    public String getRegistradoPor() { return registradoPor; }
    public List<Long> getIdsFotos() { return idsFotos; }
    public boolean isConUbicacion() { return latitud != null && longitud != null; }

    /** El punto en Google Maps, o null sin ubicación. */
    public String getEnlaceMapa() {
        return isConUbicacion()
                ? String.format(Locale.ROOT, "https://www.google.com/maps/search/?api=1&query=%.6f,%.6f", latitud, longitud)
                : null;
    }

    /** "Del celular, ±15 m" o "Última compartida, 14:32 (aproximada)". */
    public String getUbicacionTexto() {
        if (!isConUbicacion()) {
            return "Sin ubicación";
        }
        if (origenUbicacion == OrigenUbicacion.ULTIMA_COMPARTIDA) {
            return "Última que compartió"
                    + (fechaUbicacion != null ? ", " + fechaUbicacion.format(HORA) : "") + " (aproximada)";
        }
        return "Del celular, en el momento" + (precisionMetros != null ? ", ±" + precisionMetros + " m" : "");
    }
}
