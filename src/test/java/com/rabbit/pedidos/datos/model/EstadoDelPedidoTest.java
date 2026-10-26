package com.rabbit.pedidos.datos.model;

import static com.rabbit.pedidos.datos.model.EstadoPedido.*;
import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * State del pedido. Tres redes de seguridad para el refactor: la máquina nueva decide lo mismo que
 * la tabla anterior (copiada acá, celda por celda), cada operación lleva al estado esperado o lanza, y las
 * acciones de entrada hacen lo mismo que hacía PedidoService.cambiarEstado.
 */
class EstadoDelPedidoTest {

    private enum Operacion {
        CONFIRMAR, DESPACHAR, ENTREGAR, CANCELAR, DEVOLVER_A_DEPOSITO, DEVOLVER_AL_COMERCIO
    }

    private static EstadoDelPedido aplicar(EstadoDelPedido estado, Operacion op, Pedido pedido) {
        return switch (op) {
            case CONFIRMAR -> estado.confirmar(pedido);
            case DESPACHAR -> estado.despachar(pedido);
            case ENTREGAR -> estado.entregar(pedido);
            case CANCELAR -> estado.cancelar(pedido);
            case DEVOLVER_A_DEPOSITO -> estado.devolverADeposito(pedido);
            case DEVOLVER_AL_COMERCIO -> estado.devolverAlComercio(pedido);
        };
    }

    private static Pedido pedido(EstadoPedido estado, TipoEntrega tipo) {
        Pedido p = new Pedido();
        p.setEstado(estado);
        p.setTipoEntrega(tipo);
        return p;
    }

    /** La tabla de EstadoPedido.puedePasarA antes del refactor, sin tocar. */
    private static boolean tablaAnterior(EstadoPedido origen, EstadoPedido destino, TipoEntrega tipo) {
        if (tipo == TipoEntrega.RETIRO_EN_DEPOSITO && origen == CONFIRMADO) {
            return Set.of(ENTREGADO, CANCELADO).contains(destino);
        }
        return switch (origen) {
            case PENDIENTE -> Set.of(CONFIRMADO, CANCELADO).contains(destino);
            case CONFIRMADO -> Set.of(EN_CAMINO, CANCELADO, PENDIENTE, DEVUELTO_AL_COMERCIO).contains(destino);
            case EN_CAMINO -> Set.of(ENTREGADO, PENDIENTE, DEVUELTO_AL_COMERCIO).contains(destino);
            case ENTREGADO, CANCELADO, DEVUELTO_AL_COMERCIO -> false;
        };
    }

    @Test
    void decideLoMismoQueLaTablaAnteriorEnLas72Combinaciones() {
        int combinaciones = 0;
        for (EstadoPedido origen : EstadoPedido.values()) {
            for (EstadoPedido destino : EstadoPedido.values()) {
                for (TipoEntrega tipo : TipoEntrega.values()) {
                    assertEquals(tablaAnterior(origen, destino, tipo), origen.puedePasarA(destino, tipo),
                            origen + " -> " + destino + " (" + tipo + ")");
                    combinaciones++;
                }
            }
        }
        assertEquals(72, combinaciones);
    }

    /** estado -> operación -> estado al que lleva. Lo que falta es una transición inválida. */
    private static final Map<EstadoPedido, Map<Operacion, EstadoPedido>> DOMICILIO = new EnumMap<>(EstadoPedido.class);
    private static final Map<EstadoPedido, Map<Operacion, EstadoPedido>> RETIRO = new EnumMap<>(EstadoPedido.class);

    static {
        for (EstadoPedido e : EstadoPedido.values()) {
            DOMICILIO.put(e, new EnumMap<>(Operacion.class));
        }
        DOMICILIO.get(PENDIENTE).put(Operacion.CONFIRMAR, CONFIRMADO);
        DOMICILIO.get(PENDIENTE).put(Operacion.CANCELAR, CANCELADO);
        DOMICILIO.get(CONFIRMADO).put(Operacion.DESPACHAR, EN_CAMINO);
        DOMICILIO.get(CONFIRMADO).put(Operacion.CANCELAR, CANCELADO);
        DOMICILIO.get(CONFIRMADO).put(Operacion.DEVOLVER_A_DEPOSITO, PENDIENTE);
        DOMICILIO.get(CONFIRMADO).put(Operacion.DEVOLVER_AL_COMERCIO, DEVUELTO_AL_COMERCIO);
        DOMICILIO.get(EN_CAMINO).put(Operacion.ENTREGAR, ENTREGADO);
        DOMICILIO.get(EN_CAMINO).put(Operacion.DEVOLVER_A_DEPOSITO, PENDIENTE);
        DOMICILIO.get(EN_CAMINO).put(Operacion.DEVOLVER_AL_COMERCIO, DEVUELTO_AL_COMERCIO);
        // Retiro en depósito (ADR-053): igual, salvo CONFIRMADO, que solo se entrega al retirarlo o se cancela.
        for (EstadoPedido e : EstadoPedido.values()) {
            RETIRO.put(e, new EnumMap<>(DOMICILIO.get(e)));
        }
        RETIRO.get(CONFIRMADO).clear();
        RETIRO.get(CONFIRMADO).put(Operacion.ENTREGAR, ENTREGADO);
        RETIRO.get(CONFIRMADO).put(Operacion.CANCELAR, CANCELADO);
    }

    @Test
    void cadaOperacionLlevaAlEstadoEsperadoOLanzaTransicionInvalida() {
        int validas = 0;
        int invalidas = 0;
        for (TipoEntrega tipo : TipoEntrega.values()) {
            Map<EstadoPedido, Map<Operacion, EstadoPedido>> esperado =
                    tipo == TipoEntrega.RETIRO_EN_DEPOSITO ? RETIRO : DOMICILIO;
            for (EstadoPedido origen : EstadoPedido.values()) {
                for (Operacion op : Operacion.values()) {
                    EstadoDelPedido estado = EstadoDelPedido.de(origen);
                    Pedido p = pedido(origen, tipo);
                    EstadoPedido destino = esperado.get(origen).get(op);
                    String caso = origen + " " + op + " (" + tipo + ")";
                    if (destino != null) {
                        assertEquals(destino, aplicar(estado, op, p).codigo(), caso);
                        validas++;
                    } else {
                        TransicionInvalidaException e =
                                assertThrows(TransicionInvalidaException.class, () -> aplicar(estado, op, p), caso);
                        assertEquals(origen, e.getEstado(), caso);
                        invalidas++;
                    }
                }
            }
        }
        // 6 estados x 6 operaciones x 2 formas de recibir = 72. A domicilio hay 9 válidas; en el retiro, 7.
        assertEquals(16, validas);
        assertEquals(56, invalidas);
    }

    @Test
    void laTransicionInvalidaDiceAQueEstadoSeQuisoPasar() {
        TransicionInvalidaException e = assertThrows(TransicionInvalidaException.class,
                () -> EstadoDelPedido.de(EN_CAMINO).cancelar(new Pedido()));
        assertEquals(EN_CAMINO, e.getEstado());
        assertEquals(CANCELADO, e.getDestino());
    }

    @Test
    void losEstadosFinalesNoPermitenNada() {
        for (EstadoPedido final_ : new EstadoPedido[] {ENTREGADO, CANCELADO, DEVUELTO_AL_COMERCIO}) {
            for (Operacion op : Operacion.values()) {
                assertThrows(TransicionInvalidaException.class,
                        () -> aplicar(EstadoDelPedido.de(final_), op, new Pedido()), final_ + " " + op);
            }
        }
    }

    // ---- acciones de entrada: lo que hacía PedidoService.cambiarEstado según el destino

    private static Pedido enEspera(EstadoPedido estado) {
        Pedido p = pedido(estado, TipoEntrega.DOMICILIO);
        p.setEnEsperaDesde(LocalDateTime.now());
        p.setMotivoEspera("Ningún repartidor tiene lugar");
        p.setIdZonaEspera(7L);
        return p;
    }

    @Test
    void confirmarSacaAlPedidoDeLaListaDeEspera() {
        Pedido p = enEspera(PENDIENTE);
        p.confirmar();
        assertEquals(CONFIRMADO, p.getEstado());
        assertFalse(p.isEnEspera());
        assertNull(p.getMotivoEspera());
        assertNull(p.getIdZonaEspera());
    }

    @Test
    void despacharMarcaQueSalioAReparto() {
        Pedido p = pedido(CONFIRMADO, TipoEntrega.DOMICILIO);
        assertFalse(p.isSalioAReparto());
        p.despachar();
        assertEquals(EN_CAMINO, p.getEstado());
        assertTrue(p.isSalioAReparto());
    }

    @Test
    void devolverADepositoConservaLoQueCargoQuienLoMandoALaEspera() {
        Pedido p = pedido(EN_CAMINO, TipoEntrega.DOMICILIO);
        p.setEnEsperaDesde(LocalDateTime.now());
        p.setMotivoEspera("Devuelto al depósito: no estaba");
        p.devolverADeposito();
        assertEquals(PENDIENTE, p.getEstado());
        assertTrue(p.isEnEspera(), "PENDIENTE no limpia la espera: la carga quien lo manda ahí");
        assertEquals("Devuelto al depósito: no estaba", p.getMotivoEspera());
    }

    @Test
    void losEstadosFinalesLimpianLaEspera() {
        Pedido cancelado = enEspera(PENDIENTE);
        cancelado.cancelar();
        assertEquals(CANCELADO, cancelado.getEstado());
        assertFalse(cancelado.isEnEspera());

        Pedido devuelto = enEspera(EN_CAMINO);
        devuelto.devolverAlComercio();
        assertEquals(DEVUELTO_AL_COMERCIO, devuelto.getEstado());
        assertFalse(devuelto.isEnEspera());
    }

    // ---- el pedido delega

    @Test
    void unPedidoRecorreElCaminoNormal() {
        Pedido p = pedido(PENDIENTE, TipoEntrega.DOMICILIO);
        p.confirmar();
        p.despachar();
        p.entregar();
        assertEquals(ENTREGADO, p.getEstado());
    }

    @Test
    void unPedidoDeRetiroSeEntregaDesdeConfirmado() {
        Pedido p = pedido(PENDIENTE, TipoEntrega.RETIRO_EN_DEPOSITO);
        p.confirmar();
        assertThrows(TransicionInvalidaException.class, p::despachar);
        p.entregar();
        assertEquals(ENTREGADO, p.getEstado());
    }

    @Test
    void siLaOperacionNoSePermiteElPedidoNoCambia() {
        Pedido p = enEspera(PENDIENTE);
        assertThrows(TransicionInvalidaException.class, p::entregar);
        assertEquals(PENDIENTE, p.getEstado());
        assertTrue(p.isEnEspera(), "no se tocó nada");
    }

    @Test
    void aDomicilioNoSeEntregaDesdeConfirmado() {
        Pedido p = pedido(CONFIRMADO, TipoEntrega.DOMICILIO);
        assertThrows(TransicionInvalidaException.class, p::entregar);
        assertEquals(CONFIRMADO, p.getEstado());
    }

    // ---- reconstrucción desde la base

    @Test
    void cadaValorDelEnumSeReconstruyeConSuObjetoEstado() {
        for (EstadoPedido e : EstadoPedido.values()) {
            assertEquals(e, EstadoDelPedido.de(e).codigo());
            assertSame(EstadoDelPedido.de(e), EstadoDelPedido.de(e), "es una sola instancia por estado");
        }
        assertEquals(6, EstadoPedido.values().length);
    }

    @Test
    void unPedidoSinEstadoNoSeSuponeNada() {
        assertThrows(IllegalStateException.class, () -> new Pedido().confirmar());
    }

    // ---- mensajes de "por qué no se puede cancelar": los textos de antes, ahora en cada estado

    private static final String VOLVER_AL_COMERCIO = "vuelve al comercio, que gestiona la devolución con su cliente.";

    @Test
    void losMotivosParaNoCancelarSonLosDeAntes() {
        assertEquals("El pedido 7 ya salió a reparto y no se puede cancelar: solo queda entregarlo. Si no se puede entregar, "
                + VOLVER_AL_COMERCIO, EstadoDelPedido.de(EN_CAMINO).motivoParaNoCancelar(7L, true));
        assertEquals("El pedido 7 ya fue entregado: no se puede cancelar.", EstadoDelPedido.de(ENTREGADO).motivoParaNoCancelar(7L, true));
        assertEquals("El pedido 7 ya volvió al comercio: no se puede cancelar.",
                EstadoDelPedido.de(DEVUELTO_AL_COMERCIO).motivoParaNoCancelar(7L, true));
        assertEquals("El pedido 7 ya está cancelado.", EstadoDelPedido.de(CANCELADO).motivoParaNoCancelar(7L, false));
    }

    @Test
    void pendienteYConfirmadoSoloSeRechazanSiYaSalieronAReparto() {
        for (EstadoPedido e : new EstadoPedido[] {PENDIENTE, CONFIRMADO}) {
            assertNull(EstadoDelPedido.de(e).motivoParaNoCancelar(7L, false), e + " antes de salir se cancela");
            assertEquals("El pedido 7 ya salió a reparto (volvió al depósito para otra visita) y no se puede cancelar. "
                    + "Si tampoco se puede entregar, " + VOLVER_AL_COMERCIO,
                    EstadoDelPedido.de(e).motivoParaNoCancelar(7L, true), e + " después de salir ya no");
        }
    }

    @Test
    void loQueLeQuedaAlErpDependeDeHastaDondeLlego() {
        assertEquals("Ya salió a reparto: no se puede cancelar.", EstadoDelPedido.de(EN_CAMINO).queLeQuedaAlErp());
        assertEquals("Ya fue entregado: no se puede cancelar.", EstadoDelPedido.de(ENTREGADO).queLeQuedaAlErp());
        assertEquals("No se pudo entregar y ya volvió al comercio.", EstadoDelPedido.de(DEVUELTO_AL_COMERCIO).queLeQuedaAlErp());
        for (EstadoPedido e : new EstadoPedido[] {PENDIENTE, CONFIRMADO, CANCELADO}) {
            assertEquals("Si todavía no salió, lo puede cancelar el personal de Rabbit.", EstadoDelPedido.de(e).queLeQuedaAlErp(), e.toString());
        }
    }
}
