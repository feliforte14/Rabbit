package com.rabbit.infraestructura;

/**
 * CAPA DE PRESENTACIÓN (compartida) — cómo se muestran en pantalla los
 * valores de los enums: texto legible y color de la etiqueta.
 *
 * Antes cada vista mostraba el nombre crudo del enum (EN_CAMINO,
 * CONTRA_ENTREGA) o armaba su propio texto con un ternario, y la misma
 * cosa se veía distinta en cada pantalla. Acá queda en un solo lugar.
 *
 * Uso en las vistas:
 *   <h:outputText value="#{etiquetas.estadoPedido(p.estado)}"
 *                 styleClass="#{etiquetas.claseEstadoPedido(p.estado)}" />
 */

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

@Named("etiquetas")
@ApplicationScoped
public class EtiquetasBean {

    public String estadoPedido(Object estado) {
        switch (texto(estado)) {
            case "PENDIENTE": return "Pendiente";
            case "CONFIRMADO": return "Confirmado";
            case "EN_CAMINO": return "En camino";
            case "ENTREGADO": return "Entregado";
            case "CANCELADO": return "Cancelado";
            default: return "—";
        }
    }

    public String claseEstadoPedido(Object estado) {
        switch (texto(estado)) {
            case "PENDIENTE": return "badge badge-naranja";
            case "CONFIRMADO": return "badge badge-azul";
            case "EN_CAMINO": return "badge badge-azul";
            case "ENTREGADO": return "badge badge-verde";
            case "CANCELADO": return "badge badge-gris";
            default: return "";
        }
    }

    public String estadoCobro(Object estado) {
        switch (texto(estado)) {
            case "PENDIENTE": return "A cobrar";
            case "ACREDITADO": return "Cobrado";
            case "ANULADO": return "Anulado";
            default: return "—";
        }
    }

    public String claseEstadoCobro(Object estado) {
        switch (texto(estado)) {
            case "PENDIENTE": return "badge badge-naranja";
            case "ACREDITADO": return "badge badge-verde";
            case "ANULADO": return "badge badge-gris";
            default: return "";
        }
    }

    public String estadoReserva(Object estado) {
        switch (texto(estado)) {
            case "VIGENTE": return "Vigente";
            case "CONFIRMADA": return "Confirmada";
            case "LIBERADA": return "Liberada";
            case "EXPIRADA": return "Expirada";
            case "DEVUELTA": return "Devuelta";
            default: return "—";
        }
    }

    // Las reservas tienen su propia paleta (.estado-*, ver estilos.css).
    public String claseEstadoReserva(Object estado) {
        switch (texto(estado)) {
            case "VIGENTE": return "estado-vigente";
            case "CONFIRMADA": return "estado-confirmada";
            case "LIBERADA": return "estado-liberada";
            case "EXPIRADA": return "estado-expirada";
            case "DEVUELTA": return "estado-devuelta";
            default: return "";
        }
    }

    // Cómo terminó un pedido que llegó del ERP (ver PedidoExternoDTO.resultado).
    public String claseResultadoErp(Object resultado) {
        switch (texto(resultado)) {
            case "Sincronizado": return "badge badge-verde";
            case "Descartado": return "badge badge-rojo";
            case "Pendiente": return "badge badge-naranja";
            default: return "";
        }
    }

    public String estadoEnvio(Object estado) {
        switch (texto(estado)) {
            case "SOLICITADO": return "Solicitado";
            case "EN_TRANSITO": return "En tránsito";
            case "ENTREGADO": return "Entregado";
            case "CANCELADO": return "Cancelado";
            default: return "—";
        }
    }

    public String claseEstadoEnvio(Object estado) {
        switch (texto(estado)) {
            case "SOLICITADO": return "badge badge-naranja";
            case "EN_TRANSITO": return "badge badge-azul";
            case "ENTREGADO": return "badge badge-verde";
            case "CANCELADO": return "badge badge-gris";
            default: return "";
        }
    }

    public String tipoIntegracion(Object tipo) {
        switch (texto(tipo)) {
            case "REST": return "API REST";
            case "SOAP_LEGADO": return "SOAP (legado)";
            default: return "—";
        }
    }

    public String cobertura(Object cobertura) {
        switch (texto(cobertura)) {
            case "PROPIA": return "Repartidores propios";
            case "TRANSPORTISTA": return "Transportista";
            default: return "—";
        }
    }

    public String resultadoDespacho(Object resultado) {
        switch (texto(resultado)) {
            case "REPARTIDOR": return "Con repartidor";
            case "DERIVADO": return "Derivado";
            case "SIN_ZONA": return "Sin zona";
            case "ERROR": return "No se despachó";
            default: return "—";
        }
    }

    public String claseResultadoDespacho(Object resultado) {
        switch (texto(resultado)) {
            case "REPARTIDOR": return "badge badge-verde";
            case "DERIVADO": return "badge badge-azul";
            case "SIN_ZONA": return "badge badge-naranja";
            case "ERROR": return "badge badge-rojo";
            default: return "";
        }
    }

    public String estadoRepartidor(Object estado) {
        switch (texto(estado)) {
            case "DISPONIBLE": return "Disponible";
            case "OCUPADO": return "En reparto";
            default: return "—";
        }
    }

    public String claseEstadoRepartidor(Object estado) {
        switch (texto(estado)) {
            case "DISPONIBLE": return "badge badge-verde";
            case "OCUPADO": return "badge badge-azul";
            default: return "";
        }
    }

    public String medioPago(Object medio) {
        switch (texto(medio)) {
            case "PREPAGO": return "Prepago";
            case "CONTRA_ENTREGA": return "Contra entrega";
            default: return "—";
        }
    }

    public String origen(Object origen) {
        switch (texto(origen)) {
            case "PUNTO_PICKING": return "Punto de picking";
            case "STOCK_CONSIGNADO": return "Stock consignado";
            default: return "—";
        }
    }

    public String rol(Object rol) {
        switch (texto(rol)) {
            case "ADMINISTRADOR": return "Administrador";
            case "OPERADOR": return "Operador";
            case "COMERCIO": return "Comercio";
            case "REPARTIDOR": return "Repartidor";
            default: return "—";
        }
    }

    private static String texto(Object valor) {
        return valor != null ? valor.toString() : "";
    }
}
