package com.rabbit.pedidos.negocio;

/**
 * Llama un usuario con rol ERP que no representa a ningún comercio (por
 * ejemplo, uno creado a mano con add-user.sh, sin cuenta en la app). Sin
 * comercio no hay pedidos que pueda cargar ni ver: la API responde 403.
 */
public class CuentaErpSinComercioException extends ValidacionException {
    public CuentaErpSinComercioException(String mensaje) {
        super(mensaje);
    }
}
