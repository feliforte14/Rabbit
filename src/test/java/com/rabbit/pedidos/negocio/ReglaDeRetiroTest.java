package com.rabbit.pedidos.negocio;

import static org.junit.jupiter.api.Assertions.*;

import com.rabbit.pedidos.datos.model.EstadoPedido;
import com.rabbit.pedidos.datos.model.OrigenPedido;
import com.rabbit.pedidos.datos.model.TipoEntrega;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** ADR-053: reglas del retiro en depósito. */
class ReglaDeRetiroTest {

    // ---- origen ----

    @Test
    void elRetiroSoloSePideConStockConsignado() {
        assertDoesNotThrow(() -> ReglaDeRetiro.validarOrigen(TipoEntrega.RETIRO_EN_DEPOSITO, OrigenPedido.STOCK_CONSIGNADO));
        ValidacionException e = assertThrows(ValidacionException.class,
                () -> ReglaDeRetiro.validarOrigen(TipoEntrega.RETIRO_EN_DEPOSITO, OrigenPedido.PUNTO_PICKING));
        assertTrue(e.getMessage().contains("stock consignado"));
    }

    @Test
    void aDomicilioSeAdmiteCualquierOrigen() {
        for (OrigenPedido origen : OrigenPedido.values()) {
            assertDoesNotThrow(() -> ReglaDeRetiro.validarOrigen(TipoEntrega.DOMICILIO, origen));
        }
    }

    // ---- depósito único ----

    @Test
    void siTodoEstaEnUnDepositoEsEse() {
        assertEquals(7L, ReglaDeRetiro.depositoUnico(List.of(7L, 7L, 7L)));
        assertEquals(3L, ReglaDeRetiro.depositoUnico(List.of(3L)));
    }

    @Test
    void conProductosEnDistintosDepositosNoSePuedeRetirarEnUnSoloLugar() {
        ValidacionException e = assertThrows(ValidacionException.class, () -> ReglaDeRetiro.depositoUnico(List.of(1L, 2L)));
        assertTrue(e.getMessage().contains("distintos depósitos"));
    }

    @Test
    void sinLineasOConUnItemInexistenteSeRechaza() {
        assertThrows(ValidacionException.class, () -> ReglaDeRetiro.depositoUnico(List.of()));
        assertThrows(ValidacionException.class, () -> ReglaDeRetiro.depositoUnico(null));
        assertThrows(ValidacionException.class, () -> ReglaDeRetiro.depositoUnico(Arrays.asList(1L, null)));
    }

    // ---- texto de la dirección ----

    @Test
    void laDireccionDeUnRetiroNombraElDeposito() {
        assertEquals("Retiro en depósito: Galpón Norte (Av. Siempreviva 742, CABA)",
                ReglaDeRetiro.textoDeLaDireccion(" Galpón Norte ", " Av. Siempreviva 742, CABA "));
        assertEquals("Retiro en depósito: Galpón Norte", ReglaDeRetiro.textoDeLaDireccion("Galpón Norte", null));
        assertEquals("Retiro en depósito: depósito de Rabbit", ReglaDeRetiro.textoDeLaDireccion(null, ""));
    }

    // ---- código ----

    @Test
    void elCodigoSeComparaSinImportarMayusculasNiEspacios() {
        assertTrue(ReglaDeRetiro.codigoCoincide("RB-7KQ2M9XHTA", "RB-7KQ2M9XHTA"));
        assertTrue(ReglaDeRetiro.codigoCoincide("RB-7KQ2M9XHTA", "  rb-7kq2m9xhta "));
    }

    @Test
    void unCodigoDistintoVacioONuloNuncaCoincide() {
        assertFalse(ReglaDeRetiro.codigoCoincide("RB-7KQ2M9XHTA", "RB-7KQ2M9XHTB"));
        assertFalse(ReglaDeRetiro.codigoCoincide("RB-7KQ2M9XHTA", ""));
        assertFalse(ReglaDeRetiro.codigoCoincide("RB-7KQ2M9XHTA", "   "));
        assertFalse(ReglaDeRetiro.codigoCoincide("RB-7KQ2M9XHTA", null));
        assertFalse(ReglaDeRetiro.codigoCoincide(null, "RB-7KQ2M9XHTA"));
    }

    // ---- entrega en el depósito ----

    @Test
    void sePuedeEntregarUnRetiroConfirmado() {
        assertDoesNotThrow(() -> ReglaDeRetiro.validarEntregaEnDeposito(TipoEntrega.RETIRO_EN_DEPOSITO, EstadoPedido.CONFIRMADO));
    }

    @Test
    void unPedidoADomicilioNoSeEntregaEnElDeposito() {
        ValidacionException e = assertThrows(ValidacionException.class,
                () -> ReglaDeRetiro.validarEntregaEnDeposito(TipoEntrega.DOMICILIO, EstadoPedido.CONFIRMADO));
        assertTrue(e.getMessage().contains("no es de retiro"));
    }

    @Test
    void unRetiroPendienteTodaviaNoEstaListo() {
        ValidacionException e = assertThrows(ValidacionException.class,
                () -> ReglaDeRetiro.validarEntregaEnDeposito(TipoEntrega.RETIRO_EN_DEPOSITO, EstadoPedido.PENDIENTE));
        assertTrue(e.getMessage().contains("confirmarlo"));
    }

    @Test
    void unRetiroYaResueltoNoSeVuelveAEntregar() {
        for (EstadoPedido estado : new EstadoPedido[] {EstadoPedido.ENTREGADO, EstadoPedido.CANCELADO,
                EstadoPedido.DEVUELTO_AL_COMERCIO, EstadoPedido.EN_CAMINO}) {
            assertThrows(ValidacionException.class,
                    () -> ReglaDeRetiro.validarEntregaEnDeposito(TipoEntrega.RETIRO_EN_DEPOSITO, estado), "estado " + estado);
        }
    }

    // ---- operaciones de reparto ----

    @Test
    void unRetiroNoAdmiteOperacionesDeReparto() {
        ValidacionException e = assertThrows(ValidacionException.class,
                () -> ReglaDeRetiro.exigirADomicilio(TipoEntrega.RETIRO_EN_DEPOSITO, "derivar a un transportista"));
        assertTrue(e.getMessage().contains("derivar a un transportista"));
        assertDoesNotThrow(() -> ReglaDeRetiro.exigirADomicilio(TipoEntrega.DOMICILIO, "derivar a un transportista"));
    }
}
