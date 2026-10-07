package com.rabbit.pedidos.negocio;

import com.rabbit.pedidos.datos.model.OrigenPedido;
import com.rabbit.pedidos.dto.DatosLineaPedidoDTO;
import com.rabbit.pedidos.dto.DatosPedidoExternoDTO;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Resumen (SHA-256) del contenido de un pedido externo, para la
 * idempotencia del alta: distingue un reintento (mismo pedido, misma clave)
 * de una clave reutilizada con OTRO pedido (ver PedidoService).
 *
 * Se arma con los datos ya normalizados, así lo que es el mismo pedido da
 * la misma huella: un espacio de más en la dirección, "1500" o "1500.00"
 * en el importe, o el código postal como "C1414ABC" o "1414".
 */
final class HuellaDePedido {

    private HuellaDePedido() {
    }

    static String de(DatosPedidoExternoDTO datos, OrigenPedido origen) {
        StringBuilder texto = new StringBuilder()
                .append(origen).append('|').append(datos.idPuntoPicking).append('|')
                .append(datos.importe.stripTrailingZeros().toPlainString()).append('|')
                .append(datos.medioPago).append('|')
                .append(datos.direccionEntrega.trim()).append('|')
                .append(CodigosPostales.resolver(datos.codigoPostalEntrega, datos.direccionEntrega));
        for (DatosLineaPedidoDTO linea : datos.lineas) {
            texto.append('|').append(linea.idItem).append(':')
                    .append(linea.producto != null ? linea.producto.trim() : null).append(':')
                    .append(linea.cantidad);
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(texto.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // Todo JRE trae SHA-256 (lo exige la especificación de Java).
            throw new IllegalStateException(e);
        }
    }
}
