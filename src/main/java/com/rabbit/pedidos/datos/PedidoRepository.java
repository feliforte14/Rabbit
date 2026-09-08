package com.rabbit.pedidos.datos;

/**
 * CAPA DE DATOS (Patrón DAO, sobre JPA) — ver ComercioRepository para la
 * explicación completa del patrón, se aplica igual acá.
 */

import com.rabbit.pedidos.datos.model.Pedido;
import com.rabbit.pedidos.datos.model.PedidoExterno;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;

@ApplicationScoped
public class PedidoRepository {

    @PersistenceContext(unitName = "comerciosPU")
    private EntityManager em;

    // --- Pedido (modelo real de Rabbit) ---

    public Pedido guardarPedido(Pedido pedido) {
        em.persist(pedido);
        return pedido;
    }

    public Pedido actualizarPedido(Pedido pedido) {
        return em.merge(pedido);
    }

    public Pedido buscarPedidoPorId(Long id) {
        return em.find(Pedido.class, id);
    }

    public List<Pedido> listarTodos() {
        return em.createQuery("SELECT p FROM Pedido p ORDER BY p.fechaCreacion DESC", Pedido.class)
                .getResultList();
    }

    public List<Pedido> listarPedidosDeComercio(Long idComercio) {
        return em.createQuery(
                "SELECT p FROM Pedido p WHERE p.idComercio = :idComercio ORDER BY p.fechaCreacion DESC",
                Pedido.class)
                .setParameter("idComercio", idComercio)
                .getResultList();
    }

    // --- PedidoExterno (mock del ERP del comercio) ---

    public PedidoExterno guardarPedidoExterno(PedidoExterno pedidoExterno) {
        em.persist(pedidoExterno);
        return pedidoExterno;
    }

    public PedidoExterno buscarPedidoExternoPorId(Long id) {
        return em.find(PedidoExterno.class, id);
    }

    public PedidoExterno actualizarPedidoExterno(PedidoExterno pedidoExterno) {
        return em.merge(pedidoExterno);
    }

    /**
     * Filas del mock del ERP que el sincronizador todavía no procesó —
     * la consulta que corre SincronizadorDePedidos en cada pasada.
     */
    public List<PedidoExterno> listarNoSincronizados() {
        return em.createQuery(
                "SELECT pe FROM PedidoExterno pe WHERE pe.sincronizado = false ORDER BY pe.fechaPedido",
                PedidoExterno.class)
                .getResultList();
    }

    public List<PedidoExterno> listarTodosLosExternos() {
        return em.createQuery(
                "SELECT pe FROM PedidoExterno pe ORDER BY pe.fechaPedido DESC", PedidoExterno.class)
                .getResultList();
    }
}
