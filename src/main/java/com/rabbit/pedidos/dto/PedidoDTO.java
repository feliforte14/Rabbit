package com.rabbit.pedidos.dto;

/**
 * DTO de salida: representa un pedido tal como se muestra en la vista.
 * Nunca se persiste (ver Pedido para la entidad).
 */

import com.rabbit.pedidos.datos.model.MotivoEntregaFallida;
import com.rabbit.pedidos.datos.model.OrigenPedido;
import com.rabbit.pedidos.datos.model.Pedido;
import com.rabbit.pagos.dto.MedioPago;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

public class PedidoDTO {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM HH:mm");
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM");

    public Long id;
    public Long idComercio;
    public OrigenPedido origen;
    public Long idPuntoPicking;
    public List<LineaPedidoDTO> lineas;
    public BigDecimal importe;
    public BigDecimal pesoKg;
    public MedioPago medioPago;
    public Long idRepartidor;
    public String estado;
    public String fechaCreacion;
    public String fechaActualizacion;
    public String direccionEntrega;
    public String codigoPostalEntrega;
    public String codigoSeguimiento;
    public String fechaEntrega;
    public String entregadoPor;
    public boolean conRemito;
    // Lista de espera y devolución al depósito (ADR-037).
    public boolean enEspera;
    // Si alguna vez salió a reparto: ya no se puede cancelar (ReglaDeCancelacion).
    public boolean salioAReparto;
    public String enEsperaDesde;
    public String motivoEspera;
    public String motivoDevolucion;
    // Quién recibe (ADR-039). Para el personal, el comercio y el repartidor:
    // el seguimiento público no los muestra.
    public String nombreDestinatario;
    public String telefonoDestinatario;
    public String emailDestinatario;
    // Día prometido (ADR-042); null si no se pudo prometer.
    public LocalDate fechaPrometida;
    // Visitas fallidas y el motivo de la última (ADR-041).
    public int intentosFallidos;
    public MotivoEntregaFallida motivoEntregaFallida;
    // Si la última falla le contó una visita al cliente (ADR-045).
    public boolean ultimaFallaContoComoVisita;
    // Cuándo cambió de estado por esa falla: identifica su aviso (ADR-045).
    public java.time.LocalDateTime fechaUltimaFalla;

    // Convierte una entidad Pedido en un DTO listo para mostrar en la vista.
    public static PedidoDTO desde(Pedido p) {
        PedidoDTO dto = new PedidoDTO();
        dto.id = p.getId();
        dto.idComercio = p.getIdComercio();
        dto.origen = p.getOrigen();
        dto.idPuntoPicking = p.getIdPuntoPicking();
        dto.importe = p.getImporte();
        dto.pesoKg = p.getPesoKg();
        dto.medioPago = p.getMedioPago();
        dto.idRepartidor = p.getIdRepartidor();
        dto.lineas = p.getLineas().stream().map(LineaPedidoDTO::desde).collect(Collectors.toList());
        dto.estado = p.getEstado() != null ? p.getEstado().name() : null;
        dto.fechaCreacion = p.getFechaCreacion() != null ? p.getFechaCreacion().format(FORMATO) : null;
        dto.fechaActualizacion = p.getFechaActualizacion() != null ? p.getFechaActualizacion().format(FORMATO) : null;
        dto.direccionEntrega = p.getDireccionEntrega();
        dto.codigoPostalEntrega = p.getCodigoPostalEntrega();
        dto.codigoSeguimiento = p.getCodigoSeguimiento();
        dto.fechaEntrega = p.getFechaEntrega() != null ? p.getFechaEntrega().format(FORMATO) : null;
        dto.entregadoPor = p.getEntregadoPor();
        dto.conRemito = p.isConRemito();
        dto.enEspera = p.isEnEspera();
        dto.salioAReparto = p.isSalioAReparto();
        dto.enEsperaDesde = p.getEnEsperaDesde() != null ? p.getEnEsperaDesde().format(FORMATO) : null;
        dto.motivoEspera = p.getMotivoEspera();
        dto.motivoDevolucion = p.getMotivoDevolucion();
        dto.nombreDestinatario = p.getNombreDestinatario();
        dto.telefonoDestinatario = p.getTelefonoDestinatario();
        dto.emailDestinatario = p.getEmailDestinatario();
        dto.fechaPrometida = p.getFechaPrometida();
        dto.intentosFallidos = p.getIntentosFallidos();
        dto.motivoEntregaFallida = p.getMotivoEntregaFallida();
        dto.ultimaFallaContoComoVisita = p.isUltimaFallaContoComoVisita();
        dto.fechaUltimaFalla = p.getFechaUltimaFalla();
        return dto;
    }

    /** La fecha prometida como "16/10", o null. */
    public String getFechaPrometidaTexto() {
        return fechaPrometida != null ? fechaPrometida.format(DIA) : null;
    }

    // Cantidad total de unidades del pedido, sumando todas las líneas.
    public int getCantidadTotal() {
        return lineas.stream().mapToInt(LineaPedidoDTO::getCantidad).sum();
    }

    // Descripción de productos separados por coma, para las tablas de
    // listado que hoy muestran una sola columna "Producto".
    public String getProductos() {
        return lineas.stream().map(LineaPedidoDTO::getProducto).collect(Collectors.joining(", "));
    }

    // Getters JavaBean: los requiere Expression Language (JSF).
    public Long getId() { return id; }
    public Long getIdComercio() { return idComercio; }
    public OrigenPedido getOrigen() { return origen; }
    public Long getIdPuntoPicking() { return idPuntoPicking; }
    public List<LineaPedidoDTO> getLineas() { return lineas; }
    public BigDecimal getImporte() { return importe; }
    public BigDecimal getPesoKg() { return pesoKg; }
    public MedioPago getMedioPago() { return medioPago; }
    public Long getIdRepartidor() { return idRepartidor; }
    public String getEstado() { return estado; }
    public String getFechaCreacion() { return fechaCreacion; }
    public String getFechaActualizacion() { return fechaActualizacion; }
    public String getDireccionEntrega() { return direccionEntrega; }
    public String getCodigoPostalEntrega() { return codigoPostalEntrega; }
    public String getCodigoSeguimiento() { return codigoSeguimiento; }
    public String getFechaEntrega() { return fechaEntrega; }
    public String getEntregadoPor() { return entregadoPor; }
    public boolean isConRemito() { return conRemito; }
    public boolean isEnEspera() { return enEspera; }
    public boolean isSalioAReparto() { return salioAReparto; }
    public String getEnEsperaDesde() { return enEsperaDesde; }
    public String getMotivoEspera() { return motivoEspera; }
    public String getMotivoDevolucion() { return motivoDevolucion; }
    public String getNombreDestinatario() { return nombreDestinatario; }
    public String getTelefonoDestinatario() { return telefonoDestinatario; }
    public String getEmailDestinatario() { return emailDestinatario; }
    public LocalDate getFechaPrometida() { return fechaPrometida; }
    public int getIntentosFallidos() { return intentosFallidos; }
    public MotivoEntregaFallida getMotivoEntregaFallida() { return motivoEntregaFallida; }
}
