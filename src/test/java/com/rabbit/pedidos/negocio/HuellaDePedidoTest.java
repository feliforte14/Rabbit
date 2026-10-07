package com.rabbit.pedidos.negocio;

import static org.junit.jupiter.api.Assertions.*;

import com.rabbit.pagos.dto.MedioPago;
import com.rabbit.pedidos.datos.model.OrigenPedido;
import com.rabbit.pedidos.dto.DatosLineaPedidoDTO;
import com.rabbit.pedidos.dto.DatosPedidoExternoDTO;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class HuellaDePedidoTest {

    private static DatosPedidoExternoDTO pedido(String importe, String direccion, String cp) {
        DatosPedidoExternoDTO d = new DatosPedidoExternoDTO();
        d.origen = OrigenPedido.STOCK_CONSIGNADO;
        d.importe = new BigDecimal(importe);
        d.medioPago = MedioPago.PREPAGO;
        d.direccionEntrega = direccion;
        d.codigoPostalEntrega = cp;
        DatosLineaPedidoDTO linea = new DatosLineaPedidoDTO();
        linea.idItem = 3L;
        linea.cantidad = 2;
        d.lineas.add(linea);
        return d;
    }

    private static String huella(DatosPedidoExternoDTO d) {
        return HuellaDePedido.de(d, d.origen);
    }

    @Test
    void elMismoPedidoEscritoDistintoDaLaMismaHuella() {
        String base = huella(pedido("1500", "Av. Corrientes 1234", "1043"));
        assertEquals(base, huella(pedido("1500.00", "  Av. Corrientes 1234  ", "1043")));
        // Reintento con el código postal en otro formato (hallazgo 8 de la revisión).
        assertEquals(base, huella(pedido("1500", "Av. Corrientes 1234", "C1043ABC")));
    }

    @Test
    void otroPedidoDaOtraHuella() {
        String base = huella(pedido("1500", "Av. Corrientes 1234", "1043"));
        assertNotEquals(base, huella(pedido("1501", "Av. Corrientes 1234", "1043")));
        assertNotEquals(base, huella(pedido("1500", "Av. Callao 800", "1043")));
        DatosPedidoExternoDTO otraCantidad = pedido("1500", "Av. Corrientes 1234", "1043");
        otraCantidad.lineas.get(0).cantidad = 3;
        assertNotEquals(base, huella(otraCantidad));
    }

    @Test
    void esUnSha256EnHexadecimal() {
        assertTrue(huella(pedido("1500", "Av. Corrientes 1234", "1043")).matches("[0-9a-f]{64}"));
    }
}
