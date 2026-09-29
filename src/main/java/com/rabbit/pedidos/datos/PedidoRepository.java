package com.rabbit.pedidos.datos;

/**
 * CAPA DE DATOS (Patrón DAO, sobre JPA) — ver ComercioRepository para la
 * explicación completa del patrón, se aplica igual acá.
 */

import com.rabbit.pedidos.datos.model.EstadoPedido;
import com.rabbit.pedidos.datos.model.Pedido;
import com.rabbit.pedidos.datos.model.PedidoExterno;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.util.List;

@ApplicationScoped
public class PedidoRepository {

    // Puente hacia la base de datos: sabe traducir entidades @Entity a filas
    // y viceversa. Lo administra el contenedor (WildFly), no se instancia a mano.
    @PersistenceContext(unitName = "comerciosPU")
    private EntityManager em;

    // --- Pedido (modelo real de Rabbit) ---

    /**
     * Persiste un pedido nuevo (sin ID) en la BD.
     *
     * @param pedido entidad transitoria (recién creada con new, sin ID)
     * @return el mismo objeto, ya con el ID asignado por la BD
     */
    public Pedido guardarPedido(Pedido pedido) {
        em.persist(pedido);
        return pedido;
    }

    /**
     * Actualiza un pedido existente (típicamente, su estado — ver EstadoPedido).
     *
     * @param pedido entidad con el ID de un registro existente y los campos actualizados
     * @return la entidad managed con los cambios ya aplicados
     */
    public Pedido actualizarPedido(Pedido pedido) {
        return em.merge(pedido);
    }

    /**
     * Busca un pedido por su ID.
     *
     * @param id identificador del pedido
     * @return el pedido encontrado, o null si no existe
     */
    public Pedido buscarPedidoPorId(Long id) {
        return em.find(Pedido.class, id);
    }

    /**
     * Lista todos los pedidos reales, del más reciente al más viejo —
     * usada por la vista de listado.
     *
     * @return todos los pedidos persistidos
     */
    // Las consultas de listado traen las líneas con JOIN FETCH: PedidoDTO y
    // PedidoExternoDTO las recorren, y como la colección es LAZY cada pedido
    // disparaba otra consulta (con 400 pedidos, más de 700 consultas y casi
    // 40 s para cargar la pantalla de Pedidos). Hibernate descarta solo los
    // pedidos repetidos que produce el join.
    public List<Pedido> listarTodos() {
        return em.createQuery("SELECT p FROM Pedido p LEFT JOIN FETCH p.lineas ORDER BY p.fechaCreacion DESC", Pedido.class)
                .getResultList();
    }

    /**
     * Lista los pedidos de un comercio puntual, del más reciente al más viejo.
     *
     * @param idComercio ID del comercio dueño de los pedidos
     * @return los pedidos de ese comercio
     */
    public List<Pedido> listarPedidosDeComercio(Long idComercio) {
        return em.createQuery(
                "SELECT p FROM Pedido p LEFT JOIN FETCH p.lineas WHERE p.idComercio = :idComercio ORDER BY p.fechaCreacion DESC",
                Pedido.class)
                .setParameter("idComercio", idComercio)
                .getResultList();
    }

    // Pedidos de un repartidor, los más recientes primero.
    public List<Pedido> listarPedidosDeRepartidor(Long idRepartidor) {
        return em.createQuery(
                "SELECT p FROM Pedido p LEFT JOIN FETCH p.lineas WHERE p.idRepartidor = :idRepartidor ORDER BY p.fechaActualizacion DESC",
                Pedido.class)
                .setParameter("idRepartidor", idRepartidor)
                .getResultList();
    }

    // Pedidos en viaje o por salir (CONFIRMADO o EN_CAMINO): el tablero de entregas.
    public List<Pedido> listarEntregasEnCurso() {
        return em.createQuery(
                "SELECT p FROM Pedido p LEFT JOIN FETCH p.lineas WHERE p.estado IN :estados ORDER BY p.fechaActualizacion",
                Pedido.class)
                .setParameter("estados", List.of(EstadoPedido.CONFIRMADO, EstadoPedido.EN_CAMINO))
                .getResultList();
    }

    // --- PedidoExterno (mock del ERP del comercio) ---

    /**
     * Persiste una fila nueva del mock del ERP (sin ID) en la BD.
     *
     * @param pedidoExterno entidad transitoria (recién creada con new, sin ID)
     * @return el mismo objeto, ya con el ID asignado por la BD
     */
    public PedidoExterno guardarPedidoExterno(PedidoExterno pedidoExterno) {
        em.persist(pedidoExterno);
        return pedidoExterno;
    }

    /**
     * Busca una fila del mock del ERP por su ID.
     *
     * @param id identificador del pedido externo
     * @return el pedido externo encontrado, o null si no existe
     */
    public PedidoExterno buscarPedidoExternoPorId(Long id) {
        return em.find(PedidoExterno.class, id);
    }

    /**
     * Igual que buscarPedidoExternoPorId, pero bloquea la fila
     * (SELECT ... FOR UPDATE) hasta que termine la transacción actual.
     *
     * La usan sincronizarPedidoExterno y descartarPedidoExterno: con dos
     * disparadores concurrentes (PedidoExternoListener por JMS y
     * SincronizadorDePedidos por polling) sobre la misma fila, el segundo
     * espera a que el primero confirme y recién ahí lee sincronizado=true,
     * en vez de que los dos lean false y generen dos pedidos con doble
     * reserva de stock.
     *
     * @param id identificador del pedido externo
     * @return el pedido externo bloqueado, o null si no existe
     */
    public PedidoExterno buscarPedidoExternoParaActualizar(Long id) {
        return em.find(PedidoExterno.class, id, LockModeType.PESSIMISTIC_WRITE);
    }

    /**
     * Actualiza una fila del mock del ERP (típicamente, al marcarla
     * sincronizada o descartada — ver PedidoExterno).
     *
     * @param pedidoExterno entidad con el ID de un registro existente y los campos actualizados
     * @return la entidad managed con los cambios ya aplicados
     */
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

    /**
     * Lista todas las filas del mock del ERP (sincronizadas y pendientes),
     * de la más reciente a la más vieja — usada por la vista de listado.
     *
     * @return todos los pedidos externos persistidos
     */
    public List<PedidoExterno> listarTodosLosExternos() {
        return em.createQuery(
                "SELECT pe FROM PedidoExterno pe LEFT JOIN FETCH pe.lineas ORDER BY pe.fechaPedido DESC", PedidoExterno.class)
                .getResultList();
    }
}
