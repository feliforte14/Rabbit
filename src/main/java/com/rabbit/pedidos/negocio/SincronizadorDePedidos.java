package com.rabbit.pedidos.negocio;

/**
 * CAPA DE NEGOCIO — mantenimiento del componente Pedidos (EJB @Singleton)
 *
 * QUE PROBLEMA RESUELVE
 * Rabbit no origina pedidos (ver Sección 1.1 del documento técnico): un
 * pedido nace en el ERP del comercio, mockeado acá como la tabla
 * "pedidos_externos" (ver PedidoExterno). Por eso no hay un
 * "crearPedido()" que un usuario dispare a mano — la primera operación
 * es una sincronización periódica, igual que en producción correspondería
 * a un polling contra IERPComercioAdapter (ver Sección 1.6).
 *
 * Este componente cierra ese hueco: cada minuto revisa qué filas del mock
 * todavía no se procesaron y le pide a PedidoService (el Facade) que las
 * convierta en pedidos reales.
 *
 * POR QUÉ @Singleton
 * Mismo argumento que BarredorDeReservas: tiene que haber UNA sola
 * instancia sincronizando. Si hubiera varias corriendo a la vez sobre las
 * mismas filas, podrían sincronizar el mismo PedidoExterno dos veces antes
 * de que la primera transacción marque sincronizado=true, duplicando el
 * pedido y comprometiendo stock de más. @Singleton + la concurrencia
 * gestionada por el contenedor (LockType.WRITE por defecto) serializa las
 * invocaciones.
 *
 * @Startup fuerza a crearlo al desplegar, sin esperar a que alguien lo
 * invoque.
 */

import com.rabbit.pedidos.datos.PedidoRepository;
import com.rabbit.pedidos.datos.model.PedidoExterno;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.Schedule;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.inject.Inject;

import java.util.List;
import java.util.logging.Logger;

@Singleton
@Startup
public class SincronizadorDePedidos {

    private static final Logger LOG = Logger.getLogger(SincronizadorDePedidos.class.getName());

    @Inject
    private PedidoRepository repository;

    @Inject
    private IGestionPedidos gestionPedidos;

    @PostConstruct
    public void alArrancar() {
        LOG.info("[Sincronizador] Activo — revisa pedidos externos sin sincronizar cada 1 minuto");
    }

    /**
     * Corre una vez por minuto. persistent = false: igual que
     * BarredorDeReservas, el timer vive mientras vive el servidor.
     */
    @Schedule(hour = "*", minute = "*", second = "30", persistent = false)
    public void sincronizarPendientes() {
        List<PedidoExterno> pendientes = repository.listarNoSincronizados();
        if (pendientes.isEmpty()) {
            return;
        }

        for (PedidoExterno externo : pendientes) {
            try {
                Long idPedido = gestionPedidos.sincronizarPedidoExterno(externo.getId());
                LOG.info("[Sincronizador] Pedido externo " + externo.getId() + " -> pedido " + idPedido);
            } catch (RuntimeException e) {
                // Una fila con datos inválidos (comercio dado de baja, sin
                // stock) no debe trabar el resto de la pasada — se loguea
                // y se sigue con la próxima. Queda sin sincronizar: no se
                // marca sincronizado=true dentro de PedidoService salvo
                // que la transacción haya llegado a confirmar.
                LOG.warning("[Sincronizador] No se pudo sincronizar el pedido externo "
                        + externo.getId() + ": " + e.getMessage());
            }
        }
    }
}
