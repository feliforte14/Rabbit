package com.rabbit.inventario.negocio;

/**
 * CAPA DE NEGOCIO — componente ServicioDeInventario, parte sin conversación (EJB @Stateless)
 *
 * Depósitos, carga de stock y consultas. Cada operación recibe todo lo que
 * necesita y no depende de ninguna llamada anterior, así que no hay estado
 * conversacional que guardar: es @Stateless, como ComercioService.
 *
 * POR QUE NO VIVE EN InventarioService
 *
 * Antes las dos interfaces del componente las implementaba el mismo EJB
 * @Stateful. Los que solo consultan stock (PedidoService y RuteoService,
 * @Stateless de pool, y los beans de la vista) se inyectaban IConsultaStock
 * como campo y quedaban atados a UNA instancia stateful para siempre.
 * Cuando esa instancia moría —por el @StatefulTimeout o por una excepción
 * de sistema, que hace que el contenedor la descarte— la referencia quedaba
 * apuntando a nada y la próxima llamada fallaba con NoSuchEJBException.
 * La conversación (IReservaStock) sigue en InventarioService, y quien la
 * necesita la pide por Instance<IReservaStock> (patrón Provider).
 */

import com.rabbit.comercios.negocio.IConsultaComercios;
import com.rabbit.inventario.datos.InventarioRepository;
import com.rabbit.inventario.datos.model.Deposito;
import com.rabbit.inventario.datos.model.ItemInventario;
import com.rabbit.inventario.dto.*;

import com.rabbit.seguridad.negocio.IContextoUsuario;
import jakarta.annotation.security.DeclareRoles;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

// SEGURIDAD: @PermitAll de clase porque la vista, el ruteo y la
// sincronización de pedidos (sin usuario) consultan stock. Solo el personal
// de Rabbit da de alta depósitos y carga stock; un COMERCIO consulta
// únicamente su propio stock consignado.
@Stateless
@DeclareRoles({"ADMINISTRADOR", "OPERADOR", "COMERCIO", "REPARTIDOR"})
@PermitAll
public class ConsultaStockService implements IConsultaStock {

    private static final BigDecimal PESO_MAXIMO_KG = new BigDecimal("1000");

    @Inject
    private IContextoUsuario contextoUsuario;

    @Inject
    private InventarioRepository repository;

    @Inject
    private IConsultaComercios comercios;

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    public Long registrarDeposito(DatosDepositoDTO datos) {
        validarNombreDeposito(datos.nombre);
        validarDireccionDeposito(datos.direccion);
        validarProvinciaDeposito(datos.provincia);
        validarLocalidadDeposito(datos.localidad);
        validarCodigoPostalDeposito(datos.codigoPostal);

        Deposito deposito = new Deposito();
        deposito.setNombre(datos.nombre.trim());
        deposito.setDireccion(datos.direccion.trim());
        deposito.setProvincia(datos.provincia.trim());
        deposito.setLocalidad(datos.localidad.trim());
        deposito.setCodigoPostal(datos.codigoPostal.trim());
        return repository.guardarDeposito(deposito).getId();
    }

    // Devuelve el depósito como DTO (nunca expone la entidad directamente)
    @Override
    public DepositoDTO obtenerDeposito(Long idDeposito) {
        return DepositoDTO.desde(obtenerDepositoOFallar(idDeposito));
    }

    // Devuelve todos los depósitos como DTO — usado por la vista de listado (JSF)
    @Override
    public List<DepositoDTO> listarDepositos() {
        return repository.listarDepositos()
                .stream()
                .map(DepositoDTO::desde)
                .collect(Collectors.toList());
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    public Long registrarItem(Long idDeposito, DatosItemInventarioDTO datos) {
        Deposito deposito = obtenerDepositoOFallar(idDeposito);
        validarProducto(datos.producto);
        validarCantidad(datos.cantidadDisponible);
        validarPeso(datos.pesoKg);

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
        item.setPesoKg(datos.pesoKg);
        return repository.guardarItem(item).getId();
    }

    // Para los ítems cargados antes de que existiera el peso, o si se
    // cargó mal. No cambia el peso de los pedidos ya creados: ese se
    // calculó con el valor de ese momento.
    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    public void actualizarPesoItem(Long idItem, BigDecimal pesoKg) {
        ItemInventario item = obtenerItemOFallar(idItem);
        validarPeso(pesoKg);
        item.setPesoKg(pesoKg);
        repository.actualizarItem(item);
    }

    // TODO el stock de un depósito, de todos los comercios — vista de operador.
    @Override
    public List<ItemInventarioDTO> listarItemsPorDeposito(Long idDeposito) {
        obtenerDepositoOFallar(idDeposito);
        return repository.listarItemsPorDeposito(idDeposito)
                .stream()
                .map(ItemInventarioDTO::desde)
                .collect(Collectors.toList());
    }

    @Override
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR", "REPARTIDOR"})
    public List<ItemInventarioDTO> listarItemsPorIds(Collection<Long> idsItems) {
        return repository.listarItemsPorIds(idsItems).stream()
                .map(ItemInventarioDTO::desde)
                .collect(Collectors.toList());
    }

    @Override
    public java.util.Map<Long, BigDecimal> pesosPorItem(Collection<Long> idsItems) {
        java.util.Map<Long, BigDecimal> pesos = new java.util.HashMap<>();
        repository.listarItemsPorIds(idsItems).forEach(item -> pesos.put(item.getId(), item.getPesoKg()));
        return pesos;
    }

    // Stock consignado del comercio que representa el usuario que llama.
    @Override
    @RolesAllowed("COMERCIO")
    public List<ItemInventarioDTO> listarStockDelComercioActual() {
        return listarItemsPorComercio(contextoUsuario.idComercioActual());
    }

    // TODO el stock consignado por un comercio, en todos los depósitos.
    // idComercio == null devuelve lista vacía en vez de fallar: la vista lo
    // usa como "todavía no se eligió comercio", no como un error.
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

    // Stock de un comercio puntual dentro de un depósito puntual (filtro
    // combinado de las dos consultas anteriores).
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

    /**
     * Historial de reservas.
     *
     * Necesita transaccion aunque solo lea: el mapeo a DTO navega
     * ReservaStock.item (LAZY) para sacar el deposito, y sin transaccion
     * abierta eso tira LazyInitializationException.
     *
     * Este es un EJB: todos sus metodos de negocio ya corren
     * con REQUIRED por defecto. Se deja @TransactionAttribute(REQUIRED)
     * explicito por consistencia con el resto de la clase.
     */
    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public List<ReservaStockDTO> listarHistorialReservas(FiltroHistorialDTO filtro) {
        FiltroHistorialDTO criterios = (filtro != null) ? filtro : new FiltroHistorialDTO();
        return repository.listarHistorial(criterios)
                .stream()
                .map(ReservaStockDTO::desde)
                .collect(Collectors.toList());
    }

    // --- Validaciones de negocio ---

    // Kilos por unidad: obligatorio, mayor a cero, hasta 1000 kg y 3
    // decimales (gramos).
    private void validarPeso(BigDecimal pesoKg) {
        if (pesoKg == null) {
            throw new ValidacionException("Ingresá el peso por unidad, en kilos");
        }
        if (pesoKg.signum() <= 0 || pesoKg.compareTo(PESO_MAXIMO_KG) > 0) {
            throw new ValidacionException("El peso por unidad tiene que ser mayor a 0 y de hasta "
                    + PESO_MAXIMO_KG + " kg");
        }
        if (pesoKg.stripTrailingZeros().scale() > 3) {
            throw new ValidacionException("El peso admite hasta 3 decimales (gramos)");
        }
    }

    // Nombre obligatorio, sin más restricción de formato.
    private void validarNombreDeposito(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new ValidacionException("El nombre del depósito es obligatorio");
        }
    }

    // Dirección obligatoria, sin más restricción de formato.
    private void validarDireccionDeposito(String direccion) {
        if (direccion == null || direccion.isBlank()) {
            throw new ValidacionException("La dirección del depósito es obligatoria");
        }
    }

    // Provincia obligatoria, sin más restricción de formato.
    private void validarProvinciaDeposito(String provincia) {
        if (provincia == null || provincia.isBlank()) {
            throw new ValidacionException("La provincia del depósito es obligatoria");
        }
    }

    // Localidad obligatoria, sin más restricción de formato.
    private void validarLocalidadDeposito(String localidad) {
        if (localidad == null || localidad.isBlank()) {
            throw new ValidacionException("La localidad del depósito es obligatoria");
        }
    }

    // Código postal obligatorio, sin más restricción de formato.
    private void validarCodigoPostalDeposito(String codigoPostal) {
        if (codigoPostal == null || codigoPostal.isBlank()) {
            throw new ValidacionException("El código postal del depósito es obligatorio");
        }
    }

    // Nombre de producto obligatorio, sin más restricción de formato.
    private void validarProducto(String producto) {
        if (producto == null || producto.isBlank()) {
            throw new ValidacionException("El nombre del producto es obligatorio");
        }
    }

    // Solo rechaza negativos: 0 es una carga válida (por ejemplo, para
    // reservar el registro de un producto sin stock todavía).
    private void validarCantidad(int cantidad) {
        if (cantidad < 0) {
            throw new ValidacionException("La cantidad no puede ser negativa");
        }
    }

    // Lanza excepción si el depósito no existe — evita repetir este
    // chequeo en cada método que opera sobre uno puntual.
    private Deposito obtenerDepositoOFallar(Long id) {
        Deposito deposito = repository.buscarDepositoPorId(id);
        if (deposito == null) {
            throw new ValidacionException("Depósito no encontrado: " + id);
        }
        return deposito;
    }

    // Lanza excepción si el ítem no existe — evita repetir este chequeo en
    // cada método que opera sobre uno puntual.
    private ItemInventario obtenerItemOFallar(Long id) {
        ItemInventario item = repository.buscarItemPorId(id);
        if (item == null) {
            throw new ValidacionException("Ítem de stock no encontrado: " + id);
        }
        return item;
    }
}
