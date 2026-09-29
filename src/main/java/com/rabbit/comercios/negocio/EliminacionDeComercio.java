package com.rabbit.comercios.negocio;

import java.util.ArrayList;
import java.util.List;

/**
 * Evento CDI sincrónico: "se va a eliminar este comercio". Lo dispara
 * ComercioService.eliminarComercio antes de borrar; cada componente que
 * guarda datos de comercios (Pedidos, Inventario, Seguridad) lo observa y
 * anota un impedimento si todavía tiene algo que lo referencia.
 *
 * Así Comercios no depende de esos componentes (que ya dependen de él): un
 * pedido o una cuenta nunca quedan apuntando a un comercio que ya no
 * existe. Mismo patrón Observer que los eventos de Pedidos.
 */
public class EliminacionDeComercio {

    private final Long idComercio;
    private final List<String> impedimentos = new ArrayList<>();

    public EliminacionDeComercio(Long idComercio) {
        this.idComercio = idComercio;
    }

    public Long getIdComercio() { return idComercio; }

    /** Lo llama un observador cuando algo suyo todavía referencia al comercio. */
    public void impedir(String motivo) {
        impedimentos.add(motivo);
    }

    public List<String> getImpedimentos() { return impedimentos; }
}
