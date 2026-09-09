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
import jakarta.ejb.Schedule;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;

@Singleton
@Startup
public class BarredorDeReservas {

    private static final Logger LOG = Logger.getLogger(BarredorDeReservas.class.getName());

    @Inject
    private InventarioRepository repository;

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
     */
    @Schedule(hour = "*", minute = "*", second = "0", persistent = false)
    @Transactional
    public void liberarReservasVencidas() {
        List<ReservaStock> vencidas = repository.listarReservasVencidas(LocalDateTime.now());
        if (vencidas.isEmpty()) {
            return;
        }

        for (ReservaStock reserva : vencidas) {
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
        }

        LOG.info("[Barredor] " + vencidas.size() + " reserva(s) vencida(s) liberada(s)");
    }
}
