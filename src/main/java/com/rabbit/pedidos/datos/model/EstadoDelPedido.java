package com.rabbit.pedidos.datos.model;

import java.util.List;
import java.util.function.Function;

/**
 * STATE: el ciclo de vida del pedido, con una clase por estado.
 *
 * Cada estado decide qué operaciones permite y a qué estado lleva cada una; las que no permite lanzan
 * {@link TransicionInvalidaException} por el valor por defecto de acá. Las operaciones son puras: no tocan el
 * pedido, así se pueden "probar" para validar antes de hacer nada (ver {@link #puedePasarA}). Lo que pasa al
 * entrar a un estado va aparte, en {@link #alEntrar(Pedido)}.
 *
 * En la base se sigue guardando el enum {@link EstadoPedido} (columna {@code estado}); el objeto se reconstruye
 * con {@link #de(EstadoPedido)}. Los estados no tienen datos, así que son una sola instancia cada uno.
 *
 * El diagrama de estados y las condiciones de cada transición están documentados en EstadoPedido.
 */
public sealed interface EstadoDelPedido
        permits Pendiente, Confirmado, EnCamino, Entregado, Cancelado, DevueltoAlComercio {

    /** El valor que se guarda en la base. */
    EstadoPedido codigo();

    default EstadoDelPedido confirmar(Pedido pedido) {
        throw new TransicionInvalidaException(codigo(), EstadoPedido.CONFIRMADO);
    }

    default EstadoDelPedido despachar(Pedido pedido) {
        throw new TransicionInvalidaException(codigo(), EstadoPedido.EN_CAMINO);
    }

    default EstadoDelPedido entregar(Pedido pedido) {
        throw new TransicionInvalidaException(codigo(), EstadoPedido.ENTREGADO);
    }

    default EstadoDelPedido cancelar(Pedido pedido) {
        throw new TransicionInvalidaException(codigo(), EstadoPedido.CANCELADO);
    }

    /** Vuelve al depósito para otra visita: queda PENDIENTE, en la lista de espera. */
    default EstadoDelPedido devolverADeposito(Pedido pedido) {
        throw new TransicionInvalidaException(codigo(), EstadoPedido.PENDIENTE);
    }

    /** Vuelve al comercio sin más intentos: estado final. */
    default EstadoDelPedido devolverAlComercio(Pedido pedido) {
        throw new TransicionInvalidaException(codigo(), EstadoPedido.DEVUELTO_AL_COMERCIO);
    }

    /**
     * Lo que le pasa al pedido al entrar a este estado. Por defecto, deja de esperar repartidor: confirmado,
     * derivado, en camino, entregado, cancelado o devuelto ya no están en la lista de espera. PENDIENTE no lo
     * hace (quien lo mande a la espera carga el motivo) y EN_CAMINO además marca que salió a reparto.
     */
    default void alEntrar(Pedido pedido) {
        pedido.setEnEsperaDesde(null);
        pedido.setMotivoEspera(null);
        pedido.setIdZonaEspera(null);
    }

    /**
     * Por qué no se puede cancelar un pedido en este estado, o null si se puede. Por defecto vale para los
     * estados cancelables (PENDIENTE y CONFIRMADO): solo se rechaza si el pedido ya salió a reparto alguna vez
     * y volvió al depósito para otra visita. Los demás estados la sobrescriben con su propio motivo.
     * Lo usan el personal (web) y el ERP (API).
     *
     * @param salioAReparto si alguna vez estuvo EN_CAMINO
     */
    default String motivoParaNoCancelar(Long idPedido, boolean salioAReparto) {
        return salioAReparto
                ? "El pedido " + idPedido + " ya salió a reparto (volvió al depósito para otra visita) y no se puede"
                        + " cancelar. Si tampoco se puede entregar, vuelve al comercio, que gestiona la"
                        + " devolución con su cliente."
                : null;
    }

    /** Qué le queda al ERP cuando ya no puede cancelar: solo un CONFIRMADO lo puede cancelar todavía el personal. */
    default String queLeQuedaAlErp() {
        return "Si todavía no salió, lo puede cancelar el personal de Rabbit.";
    }

    /**
     * Si desde este estado se puede llegar a {@code destino}, según cómo lo recibe quien compró
     * (ADR-053: el retiro en depósito no sale a reparto). Prueba cada operación sobre un pedido de prueba:
     * son puras, así que no hay efectos.
     */
    default boolean puedePasarA(EstadoPedido destino, TipoEntrega tipo) {
        Pedido sonda = new Pedido();
        sonda.setTipoEntrega(tipo);
        List<Function<Pedido, EstadoDelPedido>> operaciones = List.of(
                this::confirmar, this::despachar, this::entregar, this::cancelar,
                this::devolverADeposito, this::devolverAlComercio);
        for (Function<Pedido, EstadoDelPedido> operacion : operaciones) {
            try {
                if (operacion.apply(sonda).codigo() == destino) {
                    return true;
                }
            } catch (TransicionInvalidaException e) {
                // Esa operación no está permitida desde este estado: se prueba la siguiente.
            }
        }
        return false;
    }

    static EstadoDelPedido de(EstadoPedido estado) {
        if (estado == null) {
            throw new IllegalStateException("El pedido no tiene estado");
        }
        return switch (estado) {
            case PENDIENTE -> Pendiente.INSTANCIA;
            case CONFIRMADO -> Confirmado.INSTANCIA;
            case EN_CAMINO -> EnCamino.INSTANCIA;
            case ENTREGADO -> Entregado.INSTANCIA;
            case CANCELADO -> Cancelado.INSTANCIA;
            case DEVUELTO_AL_COMERCIO -> DevueltoAlComercio.INSTANCIA;
        };
    }
}
