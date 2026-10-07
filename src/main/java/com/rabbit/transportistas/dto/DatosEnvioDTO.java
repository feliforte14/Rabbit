package com.rabbit.transportistas.dto;

import java.math.BigDecimal;

/**
 * DTO de entrada: lo que Pedidos le pasa a Transportistas para derivar un
 * pedido. Transportistas lo traduce a la solicitud de cada transportista.
 */
public class DatosEnvioDTO {

    public String direccionRetiro;
    public String direccionEntrega;
    public int bultos;
    // Importe a cobrar al entregar; null si el pedido ya está pagado.
    public BigDecimal cobrarAlEntregar;
}
