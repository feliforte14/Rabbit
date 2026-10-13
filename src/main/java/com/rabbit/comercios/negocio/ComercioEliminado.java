package com.rabbit.comercios.negocio;

/**
 * Evento CDI sincrónico: "este comercio se elimina ahora". Lo dispara
 * ComercioService.eliminarComercio después de que ningún componente
 * impidió la eliminación (ver EliminacionDeComercio) y antes de borrar el
 * comercio, dentro de la misma transacción.
 *
 * Cada componente que guardó historial terminado del comercio (pedidos
 * cancelados, sus pedidos del ERP, avisos, cobros anulados, envíos
 * cancelados) lo borra al recibirlo: así nada queda apuntando a un
 * comercio inexistente. Si cualquier borrado falla, la transacción se
 * deshace entera y el comercio sigue existiendo.
 *
 * Son dos eventos y no uno porque un componente no puede borrar lo suyo
 * mientras otro todavía puede impedir la eliminación.
 */
public class ComercioEliminado {

    private final Long idComercio;

    public ComercioEliminado(Long idComercio) {
        this.idComercio = idComercio;
    }

    public Long getIdComercio() { return idComercio; }
}
