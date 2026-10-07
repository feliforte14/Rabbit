package com.rabbit.pedidos.negocio;

/**
 * El ERP mandó una clave de idempotencia que ya usó, pero con OTRO pedido.
 * Un reintento legítimo repite el mismo contenido; esto es un error del ERP
 * (reutiliza claves), y aceptarlo como reintento perdería el pedido nuevo.
 */
public class ClaveIdempotenciaReutilizadaException extends ValidacionException {
    public ClaveIdempotenciaReutilizadaException(String clave) {
        super("La clave de idempotencia \"" + clave + "\" ya se usó con otro pedido");
    }
}
