package com.rabbit.pagos.datos;

/**
 * CAPA DE DATOS (Patrón DAO, sobre JPA) — ver ComercioRepository para la
 * explicación completa del patrón, se aplica igual acá.
 */

import com.rabbit.pagos.datos.model.Cobro;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.util.List;

@ApplicationScoped
public class CobroRepository {

    @PersistenceContext(unitName = "comerciosPU")
    private EntityManager em;

    public Cobro guardar(Cobro cobro) {
        em.persist(cobro);
        return cobro;
    }

    public Cobro buscarPorPedido(Long idPedido) {
        List<Cobro> cobros = em.createQuery("SELECT c FROM Cobro c WHERE c.idPedido = :idPedido", Cobro.class)
                .setParameter("idPedido", idPedido)
                .getResultList();
        return cobros.isEmpty() ? null : cobros.get(0);
    }

    /**
     * Mismo que buscarPorPedido pero bloqueando la fila hasta el fin de la
     * transacción: si el evento ENTREGADO llega dos veces a la vez, el
     * segundo espera y encuentra el cobro ya ACREDITADO.
     */
    public Cobro buscarPorPedidoParaActualizar(Long idPedido) {
        List<Cobro> cobros = em.createQuery("SELECT c FROM Cobro c WHERE c.idPedido = :idPedido", Cobro.class)
                .setParameter("idPedido", idPedido)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList();
        return cobros.isEmpty() ? null : cobros.get(0);
    }

    public List<Cobro> listarTodos() {
        return em.createQuery("SELECT c FROM Cobro c ORDER BY c.id", Cobro.class).getResultList();
    }

    public Cobro actualizar(Cobro cobro) {
        return em.merge(cobro);
    }
}
