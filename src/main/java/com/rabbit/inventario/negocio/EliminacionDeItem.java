package com.rabbit.inventario.negocio;

import java.util.ArrayList;
import java.util.List;

/**
 * Evento CDI sincrónico: "se va a eliminar este ítem de stock". Lo dispara
 * ConsultaStockService.eliminarItem antes de borrar; Pedidos lo observa y
 * anota un impedimento si un pedido en curso todavía usa el ítem (si se
 * cancela, el stock tiene que poder volver).
 *
 * Así Inventario no depende de Pedidos (que ya depende de él). Mismo
 * patrón que EliminacionDeComercio.
 */
public class EliminacionDeItem {

    private final Long idItem;
    private final List<String> impedimentos = new ArrayList<>();

    public EliminacionDeItem(Long idItem) {
        this.idItem = idItem;
    }

    public Long getIdItem() { return idItem; }

    /** Lo llama un observador cuando algo suyo todavía usa el ítem. */
    public void impedir(String motivo) {
        impedimentos.add(motivo);
    }

    public List<String> getImpedimentos() { return impedimentos; }
}
