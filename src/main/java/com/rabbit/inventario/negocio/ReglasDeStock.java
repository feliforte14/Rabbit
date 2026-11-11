package com.rabbit.inventario.negocio;

/**
 * Reglas puras para corregir o eliminar el stock de un depósito, sin
 * base ni servidor (ver ReglasDeStockTest).
 *
 * La cantidad que se edita es la que hay físicamente en el depósito
 * (cantidadDisponible). Lo reservado ya está comprometido con un pedido
 * que se está armando: no se puede bajar el stock por debajo de eso, ni
 * eliminar un ítem que lo tiene.
 */
public final class ReglasDeStock {

    private ReglasDeStock() {}

    /**
     * @return el motivo por el que no se puede guardar esa cantidad, o
     *         null si se puede
     */
    public static String motivoParaNoCambiarCantidad(int cantidadNueva, int reservada) {
        if (cantidadNueva < 0) {
            return "La cantidad no puede ser negativa";
        }
        if (cantidadNueva < reservada) {
            return "No se puede dejar en " + cantidadNueva + ": hay " + reservada
                    + (reservada == 1 ? " unidad reservada" : " unidades reservadas")
                    + " para pedidos que se están armando. El mínimo es " + reservada + ".";
        }
        return null;
    }

    /**
     * @return el motivo por el que no se puede eliminar el ítem, o null si
     *         se puede (falta además que ningún pedido en curso lo use, ver
     *         EliminacionDeItem)
     */
    public static String motivoParaNoEliminar(int reservada) {
        if (reservada > 0) {
            return "No se puede eliminar: hay " + reservada
                    + (reservada == 1 ? " unidad reservada" : " unidades reservadas")
                    + " para pedidos que se están armando. Esperá a que se confirmen o se liberen.";
        }
        return null;
    }
}
