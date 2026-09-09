package com.rabbit.pedidos.dto;

/**
 * DTO de salida: representa una fila del mock del ERP tal como se
 * muestra en la vista — deja ver qué está pendiente de sincronizar y qué
 * ya se convirtió en pedido real. Nunca se persiste (ver PedidoExterno).
 */

import com.rabbit.pedidos.datos.model.PedidoExterno;
import java.time.format.DateTimeFormatter;

public class PedidoExternoDTO {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss");

    public Long id;
    public Long idComercio;
    public Long idItem;
    public int cantidad;
    public String fechaPedido;
    public boolean sincronizado;
    public String errorSincronizacion;
    public String resultado;

    // Convierte una entidad PedidoExterno en un DTO listo para la vista.
    public static PedidoExternoDTO desde(PedidoExterno pe) {
        PedidoExternoDTO dto = new PedidoExternoDTO();
        dto.id = pe.getId();
        dto.idComercio = pe.getIdComercio();
        dto.idItem = pe.getIdItem();
        dto.cantidad = pe.getCantidad();
        dto.fechaPedido = pe.getFechaPedido() != null ? pe.getFechaPedido().format(FORMATO) : null;
        dto.sincronizado = pe.isSincronizado();
        dto.errorSincronizacion = pe.getErrorSincronizacion();
        // Los tres desenlaces posibles, ya resueltos acá para que la vista
        // no tenga que combinar dos campos en Expression Language.
        if (!pe.isSincronizado()) {
            dto.resultado = "Pendiente";
        } else if (pe.getErrorSincronizacion() == null) {
            dto.resultado = "Sincronizado";
        } else {
            dto.resultado = "Descartado";
        }
        return dto;
    }

    // Getters JavaBean: los requiere Expression Language (JSF).
    public Long getId() { return id; }
    public Long getIdComercio() { return idComercio; }
    public Long getIdItem() { return idItem; }
    public int getCantidad() { return cantidad; }
    public String getFechaPedido() { return fechaPedido; }
    public boolean isSincronizado() { return sincronizado; }
    public String getErrorSincronizacion() { return errorSincronizacion; }
    public String getResultado() { return resultado; }
}
