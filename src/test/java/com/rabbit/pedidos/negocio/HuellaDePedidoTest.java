package com.rabbit.pedidos.negocio;

import static org.junit.jupiter.api.Assertions.*;

import com.rabbit.pagos.dto.MedioPago;
import com.rabbit.pedidos.datos.model.OrigenPedido;
import com.rabbit.pedidos.dto.DatosLineaPedidoDTO;
import com.rabbit.pedidos.dto.DatosPedidoExternoDTO;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class HuellaDePedidoTest {

    // Huella del pedido de prueba calculada con el código anterior al peso
    // (ADR-032): una clave de idempotencia de entonces tiene que seguir valiendo.
    private static final String HUELLA_SIN_PESO = "e8fb328acbb123b0bc08642da62c6ea56cf698fe2d11e9fb94bbe3233a7d0f97";

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
    void sinPesoLaHuellaEsLaDeAntesYConPesoCambia() {
        DatosPedidoExternoDTO sinPeso = pedido("1500", "Av. Corrientes 1234", "1043");
        DatosPedidoExternoDTO conPeso = pedido("1500", "Av. Corrientes 1234", "1043");
        conPeso.pesoKg = new BigDecimal("12.5");
        DatosPedidoExternoDTO mismoPeso = pedido("1500", "Av. Corrientes 1234", "1043");
        mismoPeso.pesoKg = new BigDecimal("12.500");
        assertEquals(HUELLA_SIN_PESO, huella(sinPeso));
        assertNotEquals(huella(sinPeso), huella(conPeso));
        assertEquals(huella(conPeso), huella(mismoPeso));
    }

    @Test
    void sinDestinatarioLaHuellaEsLaDeAntesYConDestinatarioCambia() {
        // ADR-039: un ERP que no manda quién recibe sigue con la misma huella.
        DatosPedidoExternoDTO sinDestinatario = pedido("1500", "Av. Corrientes 1234", "1043");
        DatosPedidoExternoDTO conEmail = pedido("1500", "Av. Corrientes 1234", "1043");
        conEmail.emailDestinatario = "ana@correo.com";
        DatosPedidoExternoDTO mismoEmail = pedido("1500", "Av. Corrientes 1234", "1043");
        mismoEmail.emailDestinatario = "  Ana@Correo.com ";
        mismoEmail.nombreDestinatario = "  ";
        assertEquals(HUELLA_SIN_PESO, huella(sinDestinatario));
        assertNotEquals(huella(sinDestinatario), huella(conEmail));
        assertEquals(huella(conEmail), huella(mismoEmail));
    }

    @Test
    void esUnSha256EnHexadecimal() {
        assertTrue(huella(pedido("1500", "Av. Corrientes 1234", "1043")).matches("[0-9a-f]{64}"));
    }
}
