package com.rabbit.pedidos.negocio;

/**
 * Otra sesión tocó el mismo stock al mismo tiempo (Inventario lo detecta
 * con su bloqueo optimista) mientras se sincronizaba o cancelaba un
 * pedido. Es una ValidacionException para que las pantallas y la API la
 * muestren como cualquier otro mensaje, pero con tipo propio porque es
 * pasajera: SincronizadorDePedidos y PedidoExternoListener la distinguen
 * para reintentar en vez de descartar el pedido del ERP como si fuera una
 * regla de negocio.
 */
public class ConflictoDeStockException extends ValidacionException {
    public ConflictoDeStockException(String mensaje) {
        super(mensaje);
    }
}
