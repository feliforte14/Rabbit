package com.rabbit.inventario.negocio;

/**
 * CAPA DE NEGOCIO (EJB Stateless) — ver ComercioService para la explicación
 * completa de @Stateless / @Transactional, se aplica igual acá.
 *
 * Por ahora InventarioService solo cubre alta y consulta de stock
 * (IConsultaStock del TPO). La reserva temporal (IReservaStock:
 * reservarStock/confirmarReserva/liberarReserva) se agrega en el próximo
 * commit, cuando este componente pase a ser @Stateful — el hold es una
 * conversación con estado entre esas llamadas, y no tiene sentido modelarlo
 * todavía sobre un bean sin estado.
 */

import com.rabbit.inventario.dto.*;
import com.rabbit.inventario.datos.model.Deposito;
import com.rabbit.inventario.datos.model.ItemInventario;
import com.rabbit.inventario.datos.InventarioRepository;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@Stateless
public class InventarioService {

    @Inject
    private InventarioRepository repository;

    // --- Depósitos ---

    @Transactional
    public Long registrarDeposito(DatosDepositoDTO datos) {
        validarNombreDeposito(datos.nombre);
        validarDireccionDeposito(datos.direccion);

        Deposito deposito = new Deposito();
        deposito.setNombre(datos.nombre.trim());
        deposito.setDireccion(datos.direccion.trim());
        return repository.guardarDeposito(deposito).getId();
    }

    public DepositoDTO obtenerDeposito(Long idDeposito) {
        return DepositoDTO.desde(obtenerDepositoOFallar(idDeposito));
    }

    public List<DepositoDTO> listarDepositos() {
        return repository.listarDepositos()
                .stream()
                .map(DepositoDTO::desde)
                .collect(Collectors.toList());
    }

    // --- Stock (IConsultaStock) ---

    @Transactional
    public Long registrarItem(Long idDeposito, DatosItemInventarioDTO datos) {
        Deposito deposito = obtenerDepositoOFallar(idDeposito);
        validarProducto(datos.producto);
        validarCantidad(datos.cantidadDisponible);
        if (repository.existeProductoEnDeposito(datos.producto.trim(), idDeposito, null)) {
            throw new ValidacionException(
                    "El producto \"" + datos.producto + "\" ya tiene stock cargado en este depósito");
        }

        ItemInventario item = new ItemInventario();
        item.setProducto(datos.producto.trim());
        item.setCantidadDisponible(datos.cantidadDisponible);
        item.setCantidadReservada(0);
        item.setDeposito(deposito);
        return repository.guardarItem(item).getId();
    }

    public List<ItemInventarioDTO> listarItemsPorDeposito(Long idDeposito) {
        obtenerDepositoOFallar(idDeposito);
        return repository.listarItemsPorDeposito(idDeposito)
                .stream()
                .map(ItemInventarioDTO::desde)
                .collect(Collectors.toList());
    }

    // Cantidad libre para comprometer (disponible - ya reservada)
    public int consultarDisponibilidad(Long idItem) {
        ItemInventario item = obtenerItemOFallar(idItem);
        return item.getCantidadDisponible() - item.getCantidadReservada();
    }

    // Depósitos que tienen stock libre de un producto puntual
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
