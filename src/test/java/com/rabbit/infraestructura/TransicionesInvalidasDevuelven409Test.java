package com.rabbit.infraestructura;

import static org.junit.jupiter.api.Assertions.*;

import com.rabbit.pedidos.datos.model.EstadoDelPedido;
import com.rabbit.pedidos.datos.model.EstadoPedido;
import com.rabbit.pedidos.datos.model.Pedido;
import com.rabbit.pedidos.datos.model.TipoEntrega;
import com.rabbit.pedidos.datos.model.TransicionInvalidaException;
import com.rabbit.pedidos.negocio.TransicionNoPermitidaException;
import jakarta.ejb.EJBException;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Patrón State (criterios de testing, eje B): toda transición inválida del pedido se prueba y la API la
 * responde 409. Recorre las 72 combinaciones estado × operación × forma de entrega; las 56 que la máquina
 * no admite tienen que lanzar desde el objeto estado y salir como 409 en Problem Details.
 */
class TransicionesInvalidasDevuelven409Test {

    /** Cada operación de EstadoDelPedido y el estado al que lleva. */
    private enum Operacion {
        CONFIRMAR(EstadoPedido.CONFIRMADO, EstadoDelPedido::confirmar),
        DESPACHAR(EstadoPedido.EN_CAMINO, EstadoDelPedido::despachar),
        ENTREGAR(EstadoPedido.ENTREGADO, EstadoDelPedido::entregar),
        CANCELAR(EstadoPedido.CANCELADO, EstadoDelPedido::cancelar),
        DEVOLVER_A_DEPOSITO(EstadoPedido.PENDIENTE, EstadoDelPedido::devolverADeposito),
        DEVOLVER_AL_COMERCIO(EstadoPedido.DEVUELTO_AL_COMERCIO, EstadoDelPedido::devolverAlComercio);

        final EstadoPedido destino;
        final BiFunction<EstadoDelPedido, Pedido, EstadoDelPedido> aplicar;

        Operacion(EstadoPedido destino, BiFunction<EstadoDelPedido, Pedido, EstadoDelPedido> aplicar) {
            this.destino = destino;
            this.aplicar = aplicar;
        }
    }

    static Stream<Arguments> transicionesInvalidas() {
        List<Arguments> casos = new ArrayList<>();
        for (TipoEntrega tipo : TipoEntrega.values()) {
            for (EstadoPedido origen : EstadoPedido.values()) {
                for (Operacion op : Operacion.values()) {
                    if (!origen.puedePasarA(op.destino, tipo)) {
                        casos.add(Arguments.of(origen, op, tipo));
                    }
                }
            }
        }
        return casos.stream();
    }

    private static TransicionInvalidaException lanzada(EstadoPedido origen, Operacion op, TipoEntrega tipo) {
        Pedido pedido = new Pedido();
        pedido.setEstado(origen);
        pedido.setTipoEntrega(tipo);
        return assertThrows(TransicionInvalidaException.class,
                () -> op.aplicar.apply(EstadoDelPedido.de(origen), pedido));
    }

    private static void es409(Problema problema, String caso) {
        assertEquals(409, problema.status(), caso);
        JsonObject cuerpo = Json.createReader(new StringReader(problema.cuerpo())).readObject();
        assertEquals("/problemas/transicion-no-permitida", cuerpo.getString("type"), caso);
        assertEquals(409, cuerpo.getInt("status"), caso);
    }

    @ParameterizedTest(name = "{0} --{1}--> ({2}): 409")
    @MethodSource("transicionesInvalidas")
    void laMaquinaLaRechazaYLaApiRespondeConflicto(EstadoPedido origen, Operacion op, TipoEntrega tipo) {
        String caso = origen + " " + op + " (" + tipo + ")";
        TransicionInvalidaException e = lanzada(origen, op, tipo);
        assertEquals(origen, e.getEstado(), caso);
        assertEquals(op.destino, e.getDestino(), caso);

        // Tal cual sale del objeto estado, como la traduce PedidoService y envuelta por el contenedor EJB.
        es409(ProblemaMapper.problemaPara(e), caso);
        TransicionNoPermitidaException deNegocio = new TransicionNoPermitidaException(e.getMessage());
        es409(ProblemaMapper.problemaPara(deNegocio), caso);
        es409(ProblemaMapper.problemaPara(new EJBException(deNegocio)), caso);
    }

    @Test
    void sonLas56CombinacionesQueLaMaquinaNoAdmite() {
        // 6 estados × 6 operaciones × 2 formas de recibir = 72; válidas: 9 a domicilio y 7 en el retiro.
        assertEquals(56, transicionesInvalidas().count());
    }
}
