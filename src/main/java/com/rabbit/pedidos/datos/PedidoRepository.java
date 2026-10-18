package com.rabbit.pedidos.datos;

/**
 * CAPA DE DATOS (Patrón DAO, sobre JPA) — ver ComercioRepository para la
 * explicación completa del patrón, se aplica igual acá.
 */

import com.rabbit.pedidos.datos.model.EstadoPedido;
import com.rabbit.pedidos.datos.model.Pedido;
import com.rabbit.pedidos.datos.model.PedidoExterno;
import com.rabbit.pedidos.datos.model.RemitoEntrega;
import com.rabbit.pedidos.datos.model.FotoIntentoEntrega;
import com.rabbit.pedidos.datos.model.IntentoEntrega;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.time.LocalDateTime;
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

    // La lista de espera (ADR-037): pendientes que esperan repartidor, por
    // orden de llegada a la fila.
    public List<Long> listarIdsEnEspera() {
        return em.createQuery("SELECT p.id FROM Pedido p WHERE p.estado = :pendiente AND p.enEsperaDesde IS NOT NULL "
                        + "ORDER BY p.enEsperaDesde, p.id", Long.class)
                .setParameter("pendiente", EstadoPedido.PENDIENTE)
                .getResultList();
    }

    // Cuántos esperan repartidor ahora, de un comercio o de toda la red
    // (idComercio null): el tablero de indicadores (ADR-044).
    // El filtro por comercio se agrega solo si hace falta: un parámetro null
    // en "(:c IS NULL OR ...)" PostgreSQL no siempre sabe de qué tipo es.
    public long contarEnEspera(Long idComercio) {
        TypedQuery<Long> consulta = em.createQuery("SELECT COUNT(p) FROM Pedido p WHERE p.estado = :pendiente"
                        + " AND p.enEsperaDesde IS NOT NULL" + (idComercio != null ? " AND p.idComercio = :comercio" : ""),
                Long.class).setParameter("pendiente", EstadoPedido.PENDIENTE);
        if (idComercio != null) {
            consulta.setParameter("comercio", idComercio);
        }
        return consulta.getSingleResult();
    }

    // Pedidos recibidos en [desde, hasta), de un comercio o de toda la red
    // (idComercio null), sin las líneas: el tablero de indicadores no las usa.
    public List<Pedido> listarRecibidosEntre(LocalDateTime desde, LocalDateTime hasta, Long idComercio) {
        TypedQuery<Pedido> consulta = em.createQuery("SELECT p FROM Pedido p WHERE p.fechaCreacion >= :desde"
                        + " AND p.fechaCreacion < :hasta"
                        + (idComercio != null ? " AND p.idComercio = :comercio" : ""), Pedido.class)
                .setParameter("desde", desde)
                .setParameter("hasta", hasta);
        if (idComercio != null) {
            consulta.setParameter("comercio", idComercio);
        }
        return consulta.getResultList();
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
    // Pedidos del comercio que no están cancelados: en curso o entregados.
    // Pedidos que el repartidor todavía tiene que llevar (ver
    // EliminacionDeRepartidor).
    public long contarPedidosEnCursoDeRepartidor(Long idRepartidor) {
        return em.createQuery("SELECT COUNT(p) FROM Pedido p WHERE p.idRepartidor = :id AND p.estado IN :enCurso", Long.class)
                .setParameter("id", idRepartidor)
                .setParameter("enCurso", List.of(EstadoPedido.CONFIRMADO, EstadoPedido.EN_CAMINO))
                .getSingleResult();
    }

    /** Les quita el repartidor a sus pedidos (al eliminarlo). Devuelve cuántos cambió. */
    public int desvincularRepartidor(Long idRepartidor) {
        return em.createQuery("UPDATE Pedido p SET p.idRepartidor = NULL WHERE p.idRepartidor = :id")
                .setParameter("id", idRepartidor)
                .executeUpdate();
    }

    public long contarPedidosNoCanceladosDeComercio(Long idComercio) {
        return em.createQuery("SELECT COUNT(p) FROM Pedido p WHERE p.idComercio = :id AND p.estado <> :cancelado", Long.class)
                .setParameter("id", idComercio)
                .setParameter("cancelado", EstadoPedido.CANCELADO)
                .getSingleResult();
    }

    // IDs de los pedidos que todavía no terminaron (ni entregados, ni
    // cancelados, ni devueltos al comercio) con una línea de ese ítem de
    // stock: si se cancelan, el stock vuelve a ese ítem.
    public List<Long> listarIdsEnCursoConItem(Long idItem) {
        return em.createQuery("SELECT DISTINCT l.pedido.id FROM LineaPedido l WHERE l.idItem = :idItem"
                        + " AND l.pedido.estado NOT IN :terminados ORDER BY l.pedido.id", Long.class)
                .setParameter("idItem", idItem)
                .setParameter("terminados", List.of(EstadoPedido.ENTREGADO, EstadoPedido.CANCELADO,
                        EstadoPedido.DEVUELTO_AL_COMERCIO))
                .getResultList();
    }

    // Pedidos del ERP sin sincronizar que piden ese ítem: al convertirse
    // en pedido, lo reservan.
    public long contarPedidosExternosPendientesConItem(Long idItem) {
        return em.createQuery("SELECT COUNT(DISTINCT pe) FROM PedidoExterno pe JOIN pe.lineas l"
                        + " WHERE l.idItem = :idItem AND pe.sincronizado = false"
                        + " AND (pe.cancelado IS NULL OR pe.cancelado = false)", Long.class)
                .setParameter("idItem", idItem).getSingleResult();
    }

    // Pedidos del ERP del comercio que todavía esperan sincronizarse (ni
    // sincronizados, ni descartados, ni cancelados).
    public long contarPedidosExternosPendientesDeComercio(Long idComercio) {
        return em.createQuery("SELECT COUNT(pe) FROM PedidoExterno pe WHERE pe.idComercio = :id"
                        + " AND pe.sincronizado = false AND (pe.cancelado IS NULL OR pe.cancelado = false)", Long.class)
                .setParameter("id", idComercio).getSingleResult();
    }

    /**
     * Borra los pedidos y los pedidos del ERP de un comercio que se elimina
     * (ver ComercioEliminado). Uno por uno con remove, no con un DELETE
     * masivo, para que el cascade borre también sus líneas.
     *
     * @return cuántos pedidos y cuántos pedidos del ERP se borraron
     */
    public long[] eliminarPedidosDeComercio(Long idComercio) {
        List<Pedido> pedidos = em.createQuery("SELECT p FROM Pedido p WHERE p.idComercio = :id", Pedido.class)
                .setParameter("id", idComercio).getResultList();
        if (!pedidos.isEmpty()) {
            List<Long> ids = pedidos.stream().map(Pedido::getId).toList();
            for (String entidad : List.of("RemitoEntrega", "FotoIntentoEntrega", "IntentoEntrega")) {
                em.createQuery("DELETE FROM " + entidad + " x WHERE x.idPedido IN :ids")
                        .setParameter("ids", ids)
                        .executeUpdate();
            }
        }
        pedidos.forEach(em::remove);
        List<PedidoExterno> externos = em.createQuery("SELECT pe FROM PedidoExterno pe WHERE pe.idComercio = :id", PedidoExterno.class)
                .setParameter("id", idComercio).getResultList();
        externos.forEach(em::remove);
        return new long[] { pedidos.size(), externos.size() };
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

    // --- Remitos de entrega (ADR-033) ---

    public void guardarRemito(RemitoEntrega remito) {
        em.persist(remito);
    }

    /** El remito del pedido, o null si no tiene. */
    public RemitoEntrega buscarRemitoDePedido(Long idPedido) {
        return em.createQuery("SELECT r FROM RemitoEntrega r WHERE r.idPedido = :p", RemitoEntrega.class)
                .setParameter("p", idPedido)
                .getResultStream().findFirst().orElse(null);
    }

    // --- Visitas fallidas y sus fotos (ADR-045) ---

    public void guardarIntento(IntentoEntrega intento) {
        em.persist(intento);
    }

    public void guardarFotoDeIntento(FotoIntentoEntrega foto) {
        em.persist(foto);
    }

    /** Las visitas fallidas del pedido, de la más vieja a la más nueva. */
    public List<IntentoEntrega> listarIntentosDePedido(Long idPedido) {
        return em.createQuery("SELECT i FROM IntentoEntrega i WHERE i.idPedido = :p ORDER BY i.fecha, i.id",
                        IntentoEntrega.class)
                .setParameter("p", idPedido)
                .getResultList();
    }

    /**
     * Los IDs de las fotos de esas visitas, en orden: [idIntento, idFoto].
     * Sin la imagen, que se lee recién al mostrarla.
     */
    public List<Object[]> listarIdsDeFotos(List<Long> idsIntento) {
        if (idsIntento.isEmpty()) {
            return List.of();
        }
        return em.createQuery("SELECT f.idIntento, f.id FROM FotoIntentoEntrega f WHERE f.idIntento IN :ids"
                        + " ORDER BY f.idIntento, f.orden", Object[].class)
                .setParameter("ids", idsIntento)
                .getResultList();
    }

    /** La foto, o null si no existe. */
    public FotoIntentoEntrega buscarFotoDeIntento(Long idFoto) {
        return em.find(FotoIntentoEntrega.class, idFoto);
    }

    // --- Para la liquidación mensual (ADR-034) ---

    /**
     * Pedidos entregados entre dos momentos, por comercio: cantidad y kilos
     * (los de peso desconocido no suman). Los entregados antes de que
     * existiera fechaEntrega se cuentan por su última actualización, que en
     * un pedido ENTREGADO es la de la entrega.
     *
     * @return filas {idComercio, cantidad (Long), kilos (BigDecimal o null)}
     */
    public List<Object[]> resumenEntregas(LocalDateTime desde, LocalDateTime hasta) {
        return em.createQuery("SELECT p.idComercio, COUNT(p), SUM(p.pesoKg) FROM Pedido p "
                        + "WHERE p.estado = :entregado "
                        + "AND COALESCE(p.fechaEntrega, p.fechaActualizacion) >= :desde "
                        + "AND COALESCE(p.fechaEntrega, p.fechaActualizacion) < :hasta "
                        + "GROUP BY p.idComercio", Object[].class)
                .setParameter("entregado", EstadoPedido.ENTREGADO)
                .setParameter("desde", desde)
                .setParameter("hasta", hasta)
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
