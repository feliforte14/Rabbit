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
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;

import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Singleton
@Startup
public class SincronizadorDePedidos {

    private static final Logger LOG = Logger.getLogger(SincronizadorDePedidos.class.getName());

    @Inject
    private PedidoRepository repository;

    @Inject
    private IGestionPedidos gestionPedidos;

    // @Startup fuerza a crearlo al desplegar (ver comentario de clase);
    // esto solo deja constancia en el log de que ya está activo.
    @PostConstruct
    public void alArrancar() {
        LOG.info("[Sincronizador] Activo — revisa pedidos externos sin sincronizar cada 1 minuto");
    }

    /**
     * Corre una vez por minuto. persistent = false: igual que
     * BarredorDeReservas, el timer vive mientras vive el servidor.
     *
     * NOT_SUPPORTED: este método solo lee qué falta y reparte trabajo, no
     * escribe nada. Que no abra transacción propia garantiza que ninguna
     * fila pueda ensuciar una transacción compartida con las demás — cada
     * llamada de abajo abre y cierra la suya (REQUIRES_NEW en
     * PedidoService).
     *
     * Antes no era así: todas las filas de una pasada compartían la
     * transacción del timer, y como ValidacionException esta anotada
     * @ApplicationException(rollback = true), una sola fila invalida la
     * marcaba rollback-only. El catch atrapaba la excepcion pero la
     * transaccion ya estaba condenada, asi que las filas siguientes
     * fallaban con errores fantasma (un em.find() sobre una transaccion
     * marcada devuelve null: se veia "item no encontrado" sobre items que
     * existian) y ni las que habian sincronizado bien quedaban guardadas.
     */
    @Schedule(hour = "*", minute = "*", second = "30", persistent = false)
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void sincronizarPendientes() {
        // Solo los IDs: sin transaccion las entidades vienen detached, y
        // cada llamada de abajo recarga la suya en su propio contexto.
        List<Long> pendientes = repository.listarNoSincronizados()
                .stream()
                .map(PedidoExterno::getId)
                .collect(Collectors.toList());
        if (pendientes.isEmpty()) {
            return;
        }

        for (Long idExterno : pendientes) {
            try {
                Long idPedido = gestionPedidos.sincronizarPedidoExterno(idExterno);
                LOG.info("[Sincronizador] Pedido externo " + idExterno + " -> pedido " + idPedido);
            } catch (ValidacionException e) {
                // Falla de negocio (comercio dado de baja, sin stock, item
                // inexistente): no se arregla sola con el tiempo. Se marca
                // la fila como descartada con el motivo, en vez de
                // reintentarla cada minuto para siempre.
                descartar(idExterno, e.getMessage());
            } catch (RuntimeException e) {
                // Cualquier otra cosa (la base no responde, etc.) si puede
                // ser transitoria: se loguea y la fila queda pendiente
                // para la proxima pasada.
                LOG.warning("[Sincronizador] Error transitorio con el pedido externo "
                        + idExterno + ", se reintenta en la proxima pasada: " + e.getMessage());
            }
        }
    }

    private void descartar(Long idExterno, String motivo) {
        try {
            gestionPedidos.descartarPedidoExterno(idExterno, motivo);
        } catch (RuntimeException e) {
            // Si ni siquiera se pudo marcar, se reintentara en la proxima
            // pasada; peor seria dejar que esto corte el resto del lote.
            LOG.warning("[Sincronizador] No se pudo descartar el pedido externo "
                    + idExterno + ": " + e.getMessage());
        }
    }
}
