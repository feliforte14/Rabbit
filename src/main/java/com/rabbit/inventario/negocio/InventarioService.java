package com.rabbit.inventario.negocio;

/**
 * CAPA DE NEGOCIO — componente ServicioDeInventario (EJB @Stateful)
 *
 * POR QUE ESTE COMPONENTE ES STATEFUL
 *
 * ServicioDeComercios es @Stateless porque cada una de sus operaciones es
 * autocontenida: recibe por parametro todo lo que necesita y no depende de
 * ninguna llamada anterior. El estado de un comercio vive en la base, no
 * en memoria.
 *
 * Acá pasa lo contrario. La reserva de stock es una CONVERSACION:
 *
 *     reservarStock()  ->  [el cliente decide]  ->  confirmarReserva()
 *                                                o  liberarReserva()
 *                                                o  extenderReserva()
 *
 * Entre esas llamadas el componente tiene que recordar cual es la reserva
 * en curso. Por eso las tres operaciones de cierre NO reciben el ID de la
 * reserva: operan sobre la que esta conversacion dejo abierta. Ese estado
 * conversacional es parte del contrato del componente, y es lo que obliga
 * al contenedor a asociar una instancia dedicada por cliente.
 *
 * CICLO DE VIDA GESTIONADO POR EL CONTENEDOR
 *
 * La instancia no se crea nunca con new: la administra WildFly, que ademas
 * invoca los callbacks de abajo. @StatefulTimeout hace que el contenedor
 * la descarte sola si el cliente abandona la conversacion, y @PreDestroy
 * aprovecha ese momento para no dejar stock comprometido colgado.
 *
 * NOTA SOBRE EL ESTADO CONVERSACIONAL
 * El campo que se conserva entre llamadas es el ID de la reserva, no la
 * entidad. Una entidad JPA guardada entre transacciones queda detached, y
 * reusarla despues es una fuente clasica de bugs (LazyInitializationException,
 * merges que pisan cambios). Guardando el ID, cada operacion vuelve a
 * cargar una instancia managed. El estado conversacional —"cual reserva
 * tiene abierta este cliente"— se conserva igual.
 */

import com.rabbit.comercios.negocio.IConsultaComercios;
import com.rabbit.inventario.datos.InventarioRepository;
import com.rabbit.inventario.datos.model.Deposito;
import com.rabbit.inventario.datos.model.EstadoReserva;
import com.rabbit.inventario.datos.model.ItemInventario;
import com.rabbit.inventario.datos.model.ReservaStock;
import com.rabbit.inventario.dto.*;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.ejb.PostActivate;
import jakarta.ejb.PrePassivate;
import jakarta.ejb.Remove;
import jakarta.ejb.Stateful;
import jakarta.ejb.StatefulTimeout;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Stateful
@StatefulTimeout(value = 30, unit = TimeUnit.MINUTES)
public class InventarioService implements IConsultaStock, IReservaStock, Serializable {

    private static final Logger LOG = Logger.getLogger(InventarioService.class.getName());

    /** Cuanto dura un hold antes de vencer. */
    private static final int MINUTOS_DE_HOLD = 5;

    @Inject
    private InventarioRepository repository;

    // Dependencia hacia OTRO componente, tomada por su interfaz de solo
    // lectura: Inventario necesita preguntar si un comercio esta habilitado,
    // pero no debe poder darlo de baja. Ver ADR 001.
    @Inject
    private IConsultaComercios comercios;

    // ---------------------------------------------------------------
    // EL ESTADO CONVERSACIONAL
    // Esto es lo que hace stateful al componente: sobrevive entre
    // llamadas del mismo cliente.
    // ---------------------------------------------------------------
    private Long idReservaActual;

    // ---------------------------------------------------------------
    // Callbacks de ciclo de vida
    // Evidencia de que el contenedor administra la instancia.
    // ---------------------------------------------------------------

    /** El contenedor creo la instancia y ya inyecto sus dependencias. */
    @PostConstruct
    public void alCrear() {
        LOG.info("[Inventario] Conversacion iniciada — instancia " + hashCode());
    }

    /**
     * El contenedor esta por descartar la instancia: porque el cliente la
     * cerro, o porque vencio el @StatefulTimeout.
     *
     * Si quedo una reserva abierta se libera acá, para no dejar stock
     * comprometido por una conversacion que ya no existe. Es la primera
     * de las dos redes de seguridad; la otra es BarredorDeReservas, que
     * ademas cubre el caso de la reserva vencida con la instancia todavia
     * viva.
     */
    @PreDestroy
    public void alDestruir() {
        if (idReservaActual != null) {
            LOG.warning("[Inventario] Conversacion terminada con reserva " + idReservaActual
                    + " abierta — liberando");
            try {
                liberarReserva();
            } catch (RuntimeException e) {
                LOG.warning("[Inventario] No se pudo liberar la reserva: " + e.getMessage());
            }
        }
        LOG.info("[Inventario] Conversacion cerrada — instancia " + hashCode());
    }

    /** El contenedor manda la instancia a disco por inactividad. */
    @PrePassivate
    public void alPasivar() {
        LOG.info("[Inventario] Pasivando — reserva en curso: " + idReservaActual);
    }

    /** El contenedor la trae de vuelta a memoria. */
    @PostActivate
    public void alActivar() {
        LOG.info("[Inventario] Activando — reserva en curso: " + idReservaActual);
    }

    /**
     * Cierre explicito de la conversacion. @Remove le dice al contenedor
     * que despues de este metodo destruya la instancia — dispara @PreDestroy.
     */
    @Remove
    public void finalizar() {
        LOG.info("[Inventario] Cierre explicito de la conversacion");
    }

    // ===============================================================
    // IReservaStock — la conversacion
    // ===============================================================

    @Override
    @Transactional
    public ReservaStockDTO reservarStock(Long idComercio, Long idItem, int cantidad) {
        if (hayReservaVigente()) {
            throw new ValidacionException(
                    "Ya tenés una reserva vigente. Confirmala o liberala antes de reservar de nuevo.");
        }
        if (idComercio == null || !comercios.validarComercioActivo(idComercio)) {
            throw new ValidacionException(
                    "El comercio no existe o está dado de baja: no puede comprometer stock.");
        }
        if (cantidad <= 0) {
            throw new ValidacionException("La cantidad a reservar debe ser mayor a cero");
        }

        ItemInventario item = obtenerItemOFallar(idItem);

        // El deposito es de Rabbit, pero la mercaderia adentro es del
        // comercio que la consigno: solo el puede comprometerla. Sin esto,
        // cualquier comercio podria reservar stock ajeno.
        if (item.getIdComercio() == null) {
            throw new ValidacionException("El stock de \"" + item.getProducto()
                    + "\" no tiene comercio asignado: no se puede reservar hasta que se le asigne uno.");
        }
        if (!item.getIdComercio().equals(idComercio)) {
            throw new ValidacionException("El stock de \"" + item.getProducto()
                    + "\" pertenece a otro comercio: no se puede reservar.");
        }

        int libre = item.getCantidadDisponible() - item.getCantidadReservada();
        if (cantidad > libre) {
            throw new ValidacionException("No hay stock suficiente de \"" + item.getProducto()
                    + "\": se pidieron " + cantidad + " y hay " + libre + " libres.");
        }

        // Comprometer, sin descontar: sube lo reservado y queda igual lo
        // disponible. El stock recien sale del deposito al confirmar.
        item.setCantidadReservada(item.getCantidadReservada() + cantidad);
        repository.actualizarItem(item);

        LocalDateTime ahora = LocalDateTime.now();
        ReservaStock reserva = new ReservaStock();
        reserva.setProducto(item.getProducto());
        reserva.setCantidad(cantidad);
        reserva.setIdComercio(idComercio);
        reserva.setEstado(EstadoReserva.VIGENTE);
        reserva.setFechaCreacion(ahora);
        reserva.setFechaExpiracion(ahora.plusMinutes(MINUTOS_DE_HOLD));
        reserva.setItem(item);
        repository.guardarReserva(reserva);

        // A partir de acá la conversacion tiene una reserva abierta.
        idReservaActual = reserva.getId();
        LOG.info("[Inventario] Reserva " + idReservaActual + " abierta: " + cantidad
                + " x " + item.getProducto());

        return ReservaStockDTO.desde(reserva);
    }

    @Override
    @Transactional
    public void confirmarReserva() {
        ReservaStock reserva = reservaEnCursoOFallar();
        if (!reserva.estaVigente()) {
            // Vencio antes de que el cliente confirmara. El stock ya lo
            // libero (o lo va a liberar) el barredor: no se puede confirmar.
            idReservaActual = null;
            throw new ValidacionException(
                    "La reserva venció antes de confirmarse. Volvé a reservar.");
        }

        ItemInventario item = reserva.getItem();
        // Ahora si sale del deposito: baja lo disponible y se descompromete.
        item.setCantidadDisponible(item.getCantidadDisponible() - reserva.getCantidad());
        item.setCantidadReservada(item.getCantidadReservada() - reserva.getCantidad());
        repository.actualizarItem(item);

        reserva.setEstado(EstadoReserva.CONFIRMADA);
        repository.actualizarReserva(reserva);

        LOG.info("[Inventario] Reserva " + idReservaActual + " confirmada");
        idReservaActual = null;
    }

    @Override
    @Transactional
    public void liberarReserva() {
        ReservaStock reserva = reservaEnCursoOFallar();

        // Solo se descompromete si seguia contando como reservada. Si el
        // barredor ya la marco EXPIRADA, tambien ya devolvio la cantidad:
        // descontarla de nuevo dejaria el contador en negativo.
        if (reserva.getEstado() == EstadoReserva.VIGENTE) {
            ItemInventario item = reserva.getItem();
            item.setCantidadReservada(item.getCantidadReservada() - reserva.getCantidad());
            repository.actualizarItem(item);

            reserva.setEstado(EstadoReserva.LIBERADA);
            repository.actualizarReserva(reserva);
        }

        LOG.info("[Inventario] Reserva " + idReservaActual + " liberada");
        idReservaActual = null;
    }

    @Override
    @Transactional
    public void extenderReserva() {
        ReservaStock reserva = reservaEnCursoOFallar();
        if (!reserva.estaVigente()) {
            idReservaActual = null;
            throw new ValidacionException("La reserva ya venció: no se puede extender.");
        }
        reserva.setFechaExpiracion(LocalDateTime.now().plusMinutes(MINUTOS_DE_HOLD));
        repository.actualizarReserva(reserva);
        LOG.info("[Inventario] Reserva " + idReservaActual + " extendida");
    }

    @Override
    public ReservaStockDTO obtenerReservaActual() {
        if (idReservaActual == null) {
            return null;
        }
        ReservaStock reserva = repository.buscarReservaPorId(idReservaActual);
        return reserva != null ? ReservaStockDTO.desde(reserva) : null;
    }

    @Override
    public boolean hayReservaVigente() {
        if (idReservaActual == null) {
            return false;
        }
        ReservaStock reserva = repository.buscarReservaPorId(idReservaActual);
        return reserva != null && reserva.estaVigente();
    }

    @Override
    @Transactional
    public void registrarDevolucion(Long idReserva) {
        if (idReserva == null) {
            throw new ValidacionException("Falta el identificador de la reserva a devolver");
        }
        ReservaStock reserva = repository.buscarReservaPorId(idReserva);
        if (reserva == null) {
            throw new ValidacionException("La reserva " + idReserva + " no existe");
        }
        if (reserva.getEstado() != EstadoReserva.CONFIRMADA) {
            // Solo se devuelve stock que efectivamente se habia descontado.
            // LIBERADA / EXPIRADA / DEVUELTA ya devolvieron la cantidad en su
            // momento; sumarla otra vez dejaria cantidadDisponible inflado.
            throw new ValidacionException("Solo se puede devolver una reserva CONFIRMADA (la "
                    + idReserva + " esta " + reserva.getEstado() + ")");
        }

        ItemInventario item = reserva.getItem();
        item.setCantidadDisponible(item.getCantidadDisponible() + reserva.getCantidad());
        repository.actualizarItem(item);

        reserva.setEstado(EstadoReserva.DEVUELTA);
        repository.actualizarReserva(reserva);

        // Si justo era la reserva en curso de esta conversacion, ya no lo es.
        if (idReserva.equals(idReservaActual)) {
            idReservaActual = null;
        }

        LOG.info("[Inventario] Devolucion de reserva " + idReserva + ": +" + reserva.getCantidad()
                + " x " + reserva.getProducto() + " al stock disponible");
    }

    /** La reserva que esta conversacion dejo abierta, o error si no hay. */
    private ReservaStock reservaEnCursoOFallar() {
        if (idReservaActual == null) {
            throw new ValidacionException("No hay ninguna reserva en curso en esta sesión.");
        }
        ReservaStock reserva = repository.buscarReservaPorId(idReservaActual);
        if (reserva == null) {
            idReservaActual = null;
            throw new ValidacionException("La reserva en curso ya no existe.");
        }
        return reserva;
    }

    // ===============================================================
    // IConsultaStock — operaciones autocontenidas
    // No usan el estado conversacional. Que existan en un componente
    // stateful no lo hace stateless: lo que define al componente es que
    // su contrato INCLUYE mantener una conversacion, no que todas sus
    // operaciones la usen.
    // ===============================================================

    @Override
    @Transactional
    public Long registrarDeposito(DatosDepositoDTO datos) {
        validarNombreDeposito(datos.nombre);
        validarDireccionDeposito(datos.direccion);

        Deposito deposito = new Deposito();
        deposito.setNombre(datos.nombre.trim());
        deposito.setDireccion(datos.direccion.trim());
        return repository.guardarDeposito(deposito).getId();
    }

    @Override
    public DepositoDTO obtenerDeposito(Long idDeposito) {
        return DepositoDTO.desde(obtenerDepositoOFallar(idDeposito));
    }

    @Override
    public List<DepositoDTO> listarDepositos() {
        return repository.listarDepositos()
                .stream()
                .map(DepositoDTO::desde)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public Long registrarItem(Long idDeposito, DatosItemInventarioDTO datos) {
        Deposito deposito = obtenerDepositoOFallar(idDeposito);
        validarProducto(datos.producto);
        validarCantidad(datos.cantidadDisponible);

        // Cargar stock es registrar una consignacion: sin comercio dueño,
        // la mercaderia no seria de nadie y nadie podria reservarla.
        if (datos.idComercio == null) {
            throw new ValidacionException("Hay que indicar de qué comercio es el stock que se carga");
        }
        if (!comercios.validarComercioActivo(datos.idComercio)) {
            throw new ValidacionException(
                    "El comercio no existe o está dado de baja: no puede consignar stock.");
        }
        if (repository.existeProductoEnDeposito(datos.producto.trim(), idDeposito, datos.idComercio, null)) {
            throw new ValidacionException("El producto \"" + datos.producto
                    + "\" ya tiene stock de este comercio cargado en este depósito");
        }

        ItemInventario item = new ItemInventario();
        item.setProducto(datos.producto.trim());
        item.setCantidadDisponible(datos.cantidadDisponible);
        item.setCantidadReservada(0);
        item.setDeposito(deposito);
        item.setIdComercio(datos.idComercio);
        return repository.guardarItem(item).getId();
    }

    @Override
    public List<ItemInventarioDTO> listarItemsPorDeposito(Long idDeposito) {
        obtenerDepositoOFallar(idDeposito);
        return repository.listarItemsPorDeposito(idDeposito)
                .stream()
                .map(ItemInventarioDTO::desde)
                .collect(Collectors.toList());
    }

    @Override
    public List<ItemInventarioDTO> listarItemsPorComercio(Long idComercio) {
        if (idComercio == null) {
            return List.of();
        }
        return repository.listarItemsPorComercio(idComercio)
                .stream()
                .map(ItemInventarioDTO::desde)
                .collect(Collectors.toList());
    }

    @Override
    public List<ItemInventarioDTO> listarItemsPorComercioYDeposito(Long idComercio, Long idDeposito) {
        if (idComercio == null || idDeposito == null) {
            return List.of();
        }
        obtenerDepositoOFallar(idDeposito);
        return repository.listarItemsPorComercioYDeposito(idComercio, idDeposito)
                .stream()
                .map(ItemInventarioDTO::desde)
                .collect(Collectors.toList());
    }

    // Cantidad libre para comprometer (disponible - ya reservada)
    @Override
    public int consultarDisponibilidad(Long idItem) {
        ItemInventario item = obtenerItemOFallar(idItem);
        return item.getCantidadDisponible() - item.getCantidadReservada();
    }

    // Depósitos que tienen stock libre de un producto puntual
    @Override
    public List<DepositoDTO> listarDepositosConStock(String producto) {
        return repository.listarDepositosConStock(producto)
                .stream()
                .map(DepositoDTO::desde)
                .collect(Collectors.toList());
    }

    // --- Validaciones de negocio ---

    private void validarNombreDeposito(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new ValidacionException("El nombre del depósito es obligatorio");
        }
    }

    private void validarDireccionDeposito(String direccion) {
        if (direccion == null || direccion.isBlank()) {
            throw new ValidacionException("La dirección del depósito es obligatoria");
        }
    }

    private void validarProducto(String producto) {
        if (producto == null || producto.isBlank()) {
            throw new ValidacionException("El nombre del producto es obligatorio");
        }
    }

    private void validarCantidad(int cantidad) {
        if (cantidad < 0) {
            throw new ValidacionException("La cantidad no puede ser negativa");
        }
    }

    private Deposito obtenerDepositoOFallar(Long id) {
        Deposito deposito = repository.buscarDepositoPorId(id);
        if (deposito == null) {
            throw new ValidacionException("Depósito no encontrado: " + id);
        }
        return deposito;
    }

    private ItemInventario obtenerItemOFallar(Long id) {
        ItemInventario item = repository.buscarItemPorId(id);
        if (item == null) {
            throw new ValidacionException("Ítem de stock no encontrado: " + id);
        }
        return item;
    }
}
