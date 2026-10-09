package com.rabbit.inventario.negocio;

/**
 * Otra sesión escribió el mismo item entre que este lo leyó y lo quiso
 * escribir (bloqueo optimista, ver @Version en ItemInventario). Es una
 * ValidacionException para que las pantallas la muestren como cualquier
 * otro mensaje, pero con tipo propio porque no es una regla de negocio:
 * con reintentar alcanza. Pedidos la distingue para no descartar un pedido
 * del ERP por un choque pasajero.
 */
public class StockModificadoException extends ValidacionException {
    public StockModificadoException(String mensaje) {
        super(mensaje);
    }
}
