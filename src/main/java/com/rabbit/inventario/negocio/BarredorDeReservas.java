package com.rabbit.inventario.negocio;

/**
 * CAPA DE NEGOCIO — mantenimiento del inventario (EJB @Singleton)
 *
 * QUE PROBLEMA RESUELVE
 *
 * Cuando InventarioService abre una reserva, sube la cantidadReservada del
 * item: ese stock queda comprometido y deja de estar libre para los demas.
 * Si el cliente confirma o libera, la cuenta se ajusta sola. Pero si
 * simplemente deja pasar los 5 minutos sin hacer nada, la reserva vence y
 * nadie devuelve esa cantidad: el stock queda comprometido para siempre,
 * bajandole la disponibilidad a todo el mundo.
 *
 * El @PreDestroy de InventarioService cubre solo una parte del problema —
 * el caso en que el contenedor descarta la instancia (cierre explicito o
 * @StatefulTimeout a los 30 minutos). No cubre el caso mas comun: la
 * reserva vencio a los 5 minutos pero la conversacion sigue viva, con el
 * usuario mirando la pantalla sin decidir. Ahi el stock queda retenido
 * 25 minutos de mas.
 *
 * Este barredor cierra ese hueco: cada minuto busca las reservas que
 * siguen marcadas VIGENTE pero cuyo plazo ya paso, las marca EXPIRADA y
 * devuelve la cantidad al stock libre.
 *
 * UNA TRANSACCION POR RESERVA
 * Cada reserva vencida se expira en su propia transaccion (REQUIRES_NEW),
 * con la fila bloqueada y el estado vuelto a chequear: si el usuario la
 * confirmo o libero entre la lista y el bloqueo, se saltea. Y si otra
 * sesion toco el mismo item al mismo tiempo (OptimisticLockException por
 * el @Version de ItemInventario), falla solo esa reserva, queda en el log
 * y se reintenta en la proxima pasada — antes, un solo choque deshacia la
 * pasada entera y ninguna de las demas reservas vencidas se liberaba.
 *
 * POR QUE @Singleton
 * Tiene que haber UNA sola instancia haciendo la limpieza. Si hubiera
 * varias corriendo a la vez sobre las mismas filas, podrian descontar dos
 * veces la misma cantidad y dejar el contador en negativo. @Singleton
 * garantiza instancia unica, y su concurrencia gestionada por el
 * contenedor (LockType.WRITE por defecto) serializa las invocaciones.
 *
 * @Startup fuerza a crearlo al desplegar, sin esperar a que alguien lo
 * invoque: un barredor que arranca recien cuando lo llaman no sirve.
 *
 * El @Schedule usa el TimerService del contenedor — es la tercera forma de
 * ciclo de vida gestionado que muestra este componente, junto con el pool
 * de @Stateless y la instancia por cliente de @Stateful.
 */

import com.rabbit.inventario.datos.InventarioRepository;
import com.rabbit.inventario.datos.model.EstadoReserva;
import com.rabbit.inventario.datos.model.ItemInventario;
import com.rabbit.inventario.datos.model.ReservaStock;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.ejb.EJBException;
import jakarta.ejb.Schedule;
import jakarta.ejb.SessionContext;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.inject.Inject;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@Singleton
@Startup
public class BarredorDeReservas {

    private static final Logger LOG = Logger.getLogger(BarredorDeReservas.class.getName());

    @Inject
    private InventarioRepository repository;

    // Para llamar a expirarReserva a traves del contenedor (y que aplique
    // su REQUIRES_NEW); una llamada directa a this.expirarReserva no abre
    // transaccion nueva.
    @Resource
    private SessionContext contexto;

    // @Startup fuerza a crearlo al desplegar (ver comentario de clase);
    // esto solo deja constancia en el log de que ya está activo.
    @PostConstruct
    public void alArrancar() {
        LOG.info("[Barredor] Activo — revisa reservas vencidas cada 1 minuto");
    }

    /**
     * Corre una vez por minuto. persistent = false: el timer vive mientras
     * viva el servidor y no se guarda en la base; si WildFly se reinicia no
     * hace falta recuperar ejecuciones perdidas, alcanza con la proxima
     * pasada.
     *
     * Sin transaccion propia: solo lista los IDs de las vencidas; cada una
     * se expira en la transaccion de expirarReserva.
     */
    @Schedule(hour = "*", minute = "*", second = "0", persistent = false)
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void liberarReservasVencidas() {
        List<Long> vencidas = repository.listarIdsReservasVencidas(LocalDateTime.now());
        if (vencidas.isEmpty()) {
            return;
        }

        BarredorDeReservas barredor = contexto.getBusinessObject(BarredorDeReservas.class);
        int liberadas = 0;
        for (Long idReserva : vencidas) {
            try {
                if (barredor.expirarReserva(idReserva)) {
                    liberadas++;
                }
            } catch (EJBException e) {
                // Tipicamente una OptimisticLockException al confirmar la
                // transaccion: otra sesion toco el mismo item justo ahora.
                LOG.log(Level.WARNING, "[Barredor] No se pudo expirar la reserva " + idReserva
                        + ": se reintenta en la próxima pasada", e);
            }
        }

        LOG.info("[Barredor] " + liberadas + " de " + vencidas.size() + " reserva(s) vencida(s) liberada(s)");
    }

    /**
     * Expira una reserva en su propia transaccion. Vuelve a leerla con la
     * fila bloqueada y la saltea si ya no esta vencida (la confirmaron,
     * liberaron o extendieron mientras tanto).
     *
     * @return true si la expiro, false si ya no habia nada que hacer
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public boolean expirarReserva(Long idReserva) {
        ReservaStock reserva = repository.buscarReservaParaActualizar(idReserva);
        if (reserva == null || !reserva.estaVencida()) {
            return false;
        }

        ItemInventario item = reserva.getItem();
        if (item != null) {
            // Devolver la cantidad comprometida al stock libre.
            item.setCantidadReservada(item.getCantidadReservada() - reserva.getCantidad());
            repository.actualizarItem(item);
        }
        reserva.setEstado(EstadoReserva.EXPIRADA);
        reserva.setFechaCierre(LocalDateTime.now());
        repository.actualizarReserva(reserva);

        LOG.info("[Barredor] Reserva " + reserva.getId() + " expirada: se devolvieron "
                + reserva.getCantidad() + " x " + reserva.getProducto());
        return true;
    }
}
