package com.rabbit.pedidos.negocio;

/**
 * CAPA DE NEGOCIO — mantenimiento del componente Pedidos (EJB @Singleton): una vez por día devuelve al
 * comercio los retiros en depósito que nadie fue a buscar en PlazoDeRetiro.dias() (21 por defecto).
 *
 * Sin esto, un retiro que nadie busca quedaba CONFIRMADO para siempre, con el stock del comercio y su cobro
 * trabados. Cada pedido se devuelve en su propia transacción (IGestionPedidos.vencerRetiro): si uno falla, los
 * demás igual se procesan, y el que falló se reintenta al día siguiente.
 *
 * @Singleton: con la concurrencia por defecto las pasadas no se pisan dentro del mismo servidor. Entre
 * servidores, vencerRetiro vuelve a leer el pedido con bloqueo y no hace nada si ya no es un retiro CONFIRMADO.
 *
 * El timer no tiene usuario: los métodos que llama no piden rol (ver el @PermitAll de PedidoService).
 */

import jakarta.annotation.PostConstruct;
import jakarta.ejb.Schedule;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@Singleton
@Startup
public class VencimientoDeRetiros {

    private static final Logger LOG = Logger.getLogger(VencimientoDeRetiros.class.getName());

    @Inject
    private IGestionPedidos gestion;

    @PostConstruct
    public void alArrancar() {
        LOG.info("[Pedidos] Vencimiento de retiros activo: cada día a las 4:10 devuelve al comercio los que nadie buscó en "
                + PlazoDeRetiro.dias() + " días");
    }

    @Schedule(hour = "4", minute = "10", persistent = false)
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void vencerRetirosDelDia() {
        vencerRetiros();
    }

    /** Devuelve al comercio los retiros vencidos. Devuelve cuántos devolvió. */
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public int vencerRetiros() {
        List<Long> vencidos;
        try {
            vencidos = gestion.idsDeRetirosVencidos();
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "[Pedidos] No se pudo buscar los retiros vencidos", e);
            return 0;
        }
        int devueltos = 0;
        for (Long idPedido : vencidos) {
            try {
                if (gestion.vencerRetiro(idPedido)) {
                    devueltos++;
                }
            } catch (RuntimeException e) {
                // Uno que falla no frena a los demás; mañana se vuelve a intentar.
                LOG.log(Level.WARNING, "[Pedidos] No se pudo vencer el retiro del pedido " + idPedido
                        + " (se reintenta mañana)", e);
            }
        }
        if (!vencidos.isEmpty()) {
            LOG.info("[Pedidos] Retiros vencidos: " + devueltos + " devueltos al comercio de " + vencidos.size());
        }
        return devueltos;
    }
}
