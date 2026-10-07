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
     * Igual que buscarPedidoPorId, pero bloquea la fila (SELECT ... FOR
     * UPDATE) hasta que termine la transacción. Lo usan todas las
     * operaciones que cambian el estado del pedido: si dos llegan a la vez
     * (el ERP cancela mientras el personal confirma), la segunda espera y
     * después ve el estado real, en vez de pisar el resultado de la primera.
     */
    public Pedido buscarPedidoParaActualizar(Long id) {
        // flush primero: si esta transacción ya cambió el pedido y todavía no
        // lo escribió (el seguimiento de envíos lo pasa a EN_CAMINO y enseguida
        // a ENTREGADO en la misma transacción), ese cambio no se pierde.
        em.flush();
        // Si ya estaba cargado, find con bloqueo devolvería esa copia sin
        // volver a leerla (con un estado que otro pudo cambiar antes del
        // bloqueo). Se la saca del contexto y se lee de nuevo, bloqueada.
        // (No se usa refresh con bloqueo: en Hibernate 7.4.5 falla con un
        // NullPointerException interno sobre un pedido con sus líneas cargadas.)
        Pedido cargado = em.find(Pedido.class, id);
        if (cargado == null) {
            return null;
        }
        em.detach(cargado);
        return em.find(Pedido.class, id, LockModeType.PESSIMISTIC_WRITE);
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
     * Los pedidos en un estado, del más viejo al más nuevo (el orden en que
     * se despachan). La usa el Ruteo para los pendientes, en vez de traer
     * toda la historia y filtrar en memoria.
     */
    public List<Pedido> listarPorEstado(EstadoPedido estado) {
        return em.createQuery("SELECT DISTINCT p FROM Pedido p LEFT JOIN FETCH p.lineas WHERE p.estado = :estado ORDER BY p.fechaCreacion",
                        Pedido.class)
                .setParameter("estado", estado)
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

    // Cuántos pedidos y pedidos del ERP referencian a un comercio (para no
    // dejarlos huérfanos al eliminarlo, ver EliminacionDeComercio).
    public long contarPedidosDeComercio(Long idComercio) {
        return em.createQuery("SELECT COUNT(p) FROM Pedido p WHERE p.idComercio = :id", Long.class)
                .setParameter("id", idComercio).getSingleResult();
    }

    public long contarPedidosExternosDeComercio(Long idComercio) {
        return em.createQuery("SELECT COUNT(pe) FROM PedidoExterno pe WHERE pe.idComercio = :id", Long.class)
                .setParameter("id", idComercio).getSingleResult();
    }

    /**
     * Un pedido con sus líneas ya cargadas, en una sola consulta. Para
     * leerlo fuera de una transacción (ver PedidoService.cotizarDerivacion):
     * sin transacción, las líneas no se podrían cargar después.
     */
    public Pedido buscarPedidoConLineas(Long id) {
        return em.createQuery("SELECT p FROM Pedido p LEFT JOIN FETCH p.lineas WHERE p.id = :id", Pedido.class)
                .setParameter("id", id)
                .getResultStream().findFirst().orElse(null);
    }

    /**
     * Busca un pedido por su código público de seguimiento.
     *
     * @return el pedido, o null si ningún pedido tiene ese código
     */
    public Pedido buscarPedidoPorCodigoSeguimiento(String codigo) {
        return em.createQuery("SELECT p FROM Pedido p WHERE p.codigoSeguimiento = :codigo", Pedido.class)
                .setParameter("codigo", codigo)
                .getResultStream().findFirst().orElse(null);
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
     * El pedido externo que un comercio ya mandó con esta clave de
     * idempotencia (ver PedidoService.registrarPedidoExterno).
     *
     * @return el pedido externo, o null si la clave no se usó todavía
     */
    public PedidoExterno buscarPedidoExternoPorClave(Long idComercio, String claveIdempotencia) {
        return em.createQuery(
                "SELECT pe FROM PedidoExterno pe WHERE pe.idComercio = :comercio AND pe.claveIdempotencia = :clave",
                PedidoExterno.class)
                .setParameter("comercio", idComercio)
                .setParameter("clave", claveIdempotencia)
                .getResultStream().findFirst().orElse(null);
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
