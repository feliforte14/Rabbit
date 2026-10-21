package com.rabbit.pedidos.negocio;

import com.rabbit.pedidos.datos.model.EstadoPedido;
import com.rabbit.pedidos.datos.model.OrigenPedido;
import com.rabbit.pedidos.datos.model.TipoEntrega;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reglas del retiro en depósito (ADR-053), separadas del servicio para probarlas
 * sin base ni EJB.
 *
 * Un pedido de retiro no sale a reparto: no se le asigna repartidor ni transportista,
 * no pasa por Ruteo y quien lo recibe va a buscarlo a un depósito de Rabbit.
 *
 *  - Solo con stock consignado: ese stock YA está en un depósito de Rabbit. Con
 *    punto de picking la mercadería está en el comercio y habría que traerla primero.
 *  - Todos los productos tienen que estar en el MISMO depósito: se retira en un solo lugar.
 *  - Se entrega con el pedido CONFIRMADO ("listo para retirar") y mostrando el código de
 *    seguimiento, que el personal del depósito compara con el del pedido.
 */
public final class ReglaDeRetiro {

    /** Texto con el que se guarda la dirección de un pedido de retiro hasta saber el depósito. */
    public static final String DIRECCION_PROVISORIA = "Retiro en depósito";

    private ReglaDeRetiro() {
    }

    public static boolean esRetiro(TipoEntrega tipo) {
        return tipo == TipoEntrega.RETIRO_EN_DEPOSITO;
    }

    /** @throws ValidacionException si el retiro en depósito no se puede pedir con ese origen */
    public static void validarOrigen(TipoEntrega tipo, OrigenPedido origen) {
        if (esRetiro(tipo) && origen != OrigenPedido.STOCK_CONSIGNADO) {
            throw new ValidacionException("El retiro en depósito solo se puede pedir con stock consignado en Rabbit: "
                    + "con punto de picking la mercadería está en el comercio, no en un depósito");
        }
    }

    /**
     * El único depósito donde está todo el pedido.
     *
     * @param depositosDeLasLineas el depósito de cada línea (null = el ítem ya no existe)
     * @throws ValidacionException si no hay líneas, falta algún depósito o hay más de uno
     */
    public static Long depositoUnico(Collection<Long> depositosDeLasLineas) {
        if (depositosDeLasLineas == null || depositosDeLasLineas.isEmpty()) {
            throw new ValidacionException("El pedido no tiene productos: no se sabe en qué depósito retirarlo");
        }
        if (depositosDeLasLineas.stream().anyMatch(Objects::isNull)) {
            throw new ValidacionException("Algún producto del pedido ya no está en ningún depósito: no se puede armar el retiro");
        }
        Set<Long> distintos = depositosDeLasLineas.stream().collect(Collectors.toSet());
        if (distintos.size() > 1) {
            throw new ValidacionException("Los productos del pedido están en distintos depósitos: "
                    + "el retiro en depósito se hace en un solo lugar");
        }
        return distintos.iterator().next();
    }

    /** La dirección que se muestra en un pedido de retiro, con el depósito ya conocido. */
    public static String textoDeLaDireccion(String nombreDeposito, String direccionDeposito) {
        String nombre = nombreDeposito == null || nombreDeposito.isBlank() ? "depósito de Rabbit" : nombreDeposito.trim();
        if (direccionDeposito == null || direccionDeposito.isBlank()) {
            return DIRECCION_PROVISORIA + ": " + nombre;
        }
        return DIRECCION_PROVISORIA + ": " + nombre + " (" + direccionDeposito.trim() + ")";
    }

    /**
     * ¿El código que mostró quien retira es el del pedido? Sin importar mayúsculas ni
     * espacios de más ("rb-7kq2m9xhta " sirve). Un código vacío nunca coincide.
     */
    public static boolean codigoCoincide(String codigoDelPedido, String codigoMostrado) {
        if (codigoDelPedido == null || codigoMostrado == null) {
            return false;
        }
        String mostrado = codigoMostrado.trim();
        return !mostrado.isEmpty() && codigoDelPedido.trim().equalsIgnoreCase(mostrado);
    }

    /**
     * Qué se puede hacer con el pedido de retiro al entregarlo.
     *
     * @throws ValidacionException si no es de retiro, si todavía no está listo o ya se resolvió
     */
    public static void validarEntregaEnDeposito(TipoEntrega tipo, EstadoPedido estado) {
        if (!esRetiro(tipo)) {
            throw new ValidacionException("Este pedido no es de retiro en depósito: lo entrega un repartidor o un transportista");
        }
        if (estado == EstadoPedido.PENDIENTE) {
            throw new ValidacionException("El pedido todavía no está listo para retirar: primero hay que confirmarlo");
        }
        if (estado != EstadoPedido.CONFIRMADO) {
            throw new ValidacionException("El pedido está " + estado + ": ya no se puede entregar en el depósito");
        }
    }

    /** Un pedido de retiro no sale a reparto: estas operaciones no le corresponden. */
    public static void exigirADomicilio(TipoEntrega tipo, String operacion) {
        if (esRetiro(tipo)) {
            throw new ValidacionException("Este pedido es de retiro en depósito, no sale a reparto: no se puede " + operacion);
        }
    }
}
