package com.rabbit.pedidos.presentacion.rest;

/**
 * Cuerpo de POST /api/v1/pedidos-externos: el contrato público de la API
 * (ver docs/openapi.yaml), separado de DatosPedidoExternoDTO, que es el
 * DTO del formulario JSF.
 *
 * Separarlos permite dos cosas: que la API no tenga idComercio (el
 * comercio sale de la cuenta del ERP, ver PedidoService) y que lleve las
 * reglas de formato de Bean Validation sin que JSF las aplique también a
 * la pantalla. Las reglas de negocio siguen en PedidoService, que valida
 * igual: esto solo corta antes, con un 400 que dice qué campo está mal.
 */

import com.rabbit.pagos.dto.MedioPago;
import com.rabbit.pedidos.datos.model.OrigenPedido;
import com.rabbit.pedidos.dto.DatosLineaPedidoDTO;
import com.rabbit.pedidos.dto.DatosPedidoExternoDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class PedidoExternoRequest {

    public OrigenPedido origen = OrigenPedido.STOCK_CONSIGNADO;

    // Obligatorio con origen PUNTO_PICKING (lo valida PedidoService).
    public Long idPuntoPicking;

    @NotEmpty(message = "El pedido debe tener al menos un producto")
    @Size(max = 100, message = "Un pedido admite hasta 100 líneas")
    public List<@Valid @NotNull(message = "Una línea del pedido está vacía") Linea> lineas = new ArrayList<>();

    @NotNull(message = "Falta el importe del pedido")
    @DecimalMin(value = "0.01", message = "El importe del pedido debe ser mayor a cero")
    @Digits(integer = 10, fraction = 2, message = "El importe admite hasta 10 enteros y 2 decimales")
    public BigDecimal importe;

    @NotNull(message = "Falta el medio de pago (PREPAGO o CONTRA_ENTREGA)")
    public MedioPago medioPago;

    @NotBlank(message = "Falta la dirección de entrega")
    @Size(max = 200, message = "La dirección de entrega no puede superar los 200 caracteres")
    public String direccionEntrega;

    // Opcional: si no viene, se busca en la dirección.
    @Size(max = 8, message = "El código postal tiene 4 dígitos o es un CPA de 8 caracteres")
    public String codigoPostalEntrega;

    public static class Linea {
        // STOCK_CONSIGNADO: el ítem consignado en Rabbit.
        public Long idItem;

        // PUNTO_PICKING: qué se retira (descripción libre).
        @Size(max = 200, message = "La descripción del producto no puede superar los 200 caracteres")
        public String producto;

        @Positive(message = "La cantidad debe ser mayor a cero")
        public int cantidad;
    }

    DatosPedidoExternoDTO aDatos() {
        DatosPedidoExternoDTO datos = new DatosPedidoExternoDTO();
        datos.origen = origen;
        datos.idPuntoPicking = idPuntoPicking;
        datos.importe = importe;
        datos.medioPago = medioPago;
        datos.direccionEntrega = direccionEntrega;
        datos.codigoPostalEntrega = codigoPostalEntrega;
        for (Linea linea : lineas) {
            DatosLineaPedidoDTO dato = new DatosLineaPedidoDTO();
            dato.idItem = linea.idItem;
            dato.producto = linea.producto;
            dato.cantidad = linea.cantidad;
            datos.lineas.add(dato);
        }
        return datos;
    }
}
