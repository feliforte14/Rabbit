package com.rabbit.pedidos.presentacion.rest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbit.pagos.dto.MedioPago;
import com.rabbit.pedidos.datos.model.TipoEntrega;
import com.rabbit.pedidos.dto.PedidoExternoDTO;
import com.rabbit.pedidos.negocio.CancelacionNoPermitidaException;
import com.rabbit.pedidos.negocio.ClaveIdempotenciaReutilizadaException;
import com.rabbit.pedidos.negocio.ConflictoDeStockException;
import com.rabbit.pedidos.negocio.CuentaErpSinComercioException;
import com.rabbit.pedidos.negocio.IGestionPedidos;
import com.rabbit.pedidos.negocio.ISeguimientoPedido;
import com.rabbit.pedidos.negocio.PedidoNoEncontradoException;
import com.rabbit.pedidos.negocio.TransicionNoPermitidaException;
import com.rabbit.pedidos.negocio.ValidacionException;
import jakarta.ejb.EJBAccessException;
import jakarta.ejb.EJBException;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.validation.Validation;
import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * API del ERP: cada respuesta del recurso (códigos, Problem Details, ETag con If-Match/If-None-Match y
 * HATEOAS). El negocio es un doble: lo que se prueba es la traducción a HTTP, no PedidoService.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PedidosExternosResourceTest {

    private static final String CLAVE = "7f3c9e1a-clave";

    @Mock private IGestionPedidos gestion;
    @Mock private ISeguimientoPedido seguimiento;
    @Mock private UriInfo uri;
    @Mock private Request request;

    private final PedidosExternosResource recurso = new PedidosExternosResource();

    @BeforeEach
    void preparar() throws Exception {
        inyectar("gestion", gestion);
        inyectar("seguimiento", seguimiento);
        inyectar("validador", Validation.buildDefaultValidatorFactory().getValidator());
        when(uri.getBaseUriBuilder()).thenAnswer(i -> UriBuilder.fromUri("https://rabbit.test/Rabbit/api/"));
    }

    private void inyectar(String campo, Object valor) throws Exception {
        var f = PedidosExternosResource.class.getDeclaredField(campo);
        f.setAccessible(true);
        f.set(recurso, valor);
    }

    private static PedidoExternoRequest pedidoValido() {
        PedidoExternoRequest r = new PedidoExternoRequest();
        PedidoExternoRequest.Linea linea = new PedidoExternoRequest.Linea();
        linea.idItem = 3L;
        linea.cantidad = 2;
        r.lineas.add(linea);
        r.importe = new BigDecimal("2500");
        r.medioPago = MedioPago.PREPAGO;
        r.direccionEntrega = "Av. Corrientes 1234";
        r.emailDestinatario = "ana@correo.test";
        return r;
    }

    private static PedidoExternoDTO pendiente(long id) {
        PedidoExternoDTO dto = new PedidoExternoDTO();
        dto.id = id;
        dto.resultado = "Pendiente";
        dto.tipoEntrega = TipoEntrega.DOMICILIO;
        return dto;
    }

    private static JsonObject json(Response r) {
        Object entidad = r.getEntity();
        return entidad instanceof JsonObject o ? o : Json.createReader(new StringReader((String) entidad)).readObject();
    }

    private static String tipo(Response r) {
        return json(r).getString("type");
    }

    private static String etagDe(JsonObject pedido) {
        return '"' + PedidosExternosResource.huella(pedido) + '"';
    }

    @Nested
    class Alta {

        @Test
        void sinCuerpoEs400() {
            Response r = recurso.registrar(null, CLAVE, uri);
            assertEquals(400, r.getStatus());
            assertEquals("/problemas/cuerpo-invalido", tipo(r));
        }

        @Test
        void sinClaveOConClaveInvalidaEs400() {
            for (String clave : new String[] {null, "corta", "con espacios 123"}) {
                Response r = recurso.registrar(pedidoValido(), clave, uri);
                assertEquals(400, r.getStatus(), "clave " + clave);
                assertEquals("/problemas/clave-idempotencia-invalida", tipo(r));
            }
            verify(gestion, never()).registrarPedidoExterno(any(), anyString());
        }

        @Test
        void conCamposInvalidosEs400ConUnErrorPorCampo() {
            PedidoExternoRequest p = pedidoValido();
            p.importe = BigDecimal.ZERO;
            p.lineas.get(0).idItem = 0L;
            Response r = recurso.registrar(p, CLAVE, uri);
            assertEquals(400, r.getStatus());
            assertEquals("/problemas/datos-con-formato-invalido", tipo(r));
            assertEquals(2, json(r).getJsonArray("errores").size());
        }

        @Test
        void creadoEs201ConLocationETagYEnlaceParaCancelar() {
            when(gestion.registrarPedidoExterno(any(), eq(CLAVE))).thenReturn(42L);
            when(seguimiento.consultarPedidoExterno(42L)).thenReturn(pendiente(42));

            Response r = recurso.registrar(pedidoValido(), CLAVE, uri);

            assertEquals(201, r.getStatus());
            assertEquals("https://rabbit.test/Rabbit/api/v1/pedidos-externos/42", r.getLocation().toString());
            JsonObject cuerpo = json(r);
            assertEquals("Pendiente", cuerpo.getString("resultado"));
            assertTrue(cuerpo.getJsonObject("_links").containsKey("cancelar"));
            assertEquals(new EntityTag(PedidosExternosResource.huella(cuerpo)), r.getEntityTag());
        }

        @Test
        void siElAltaChocaConUnReintentoSimultaneoSeReintentaUnaVez() {
            when(gestion.registrarPedidoExterno(any(), eq(CLAVE)))
                    .thenThrow(new EJBException("restricción única")).thenReturn(42L);
            when(seguimiento.consultarPedidoExterno(42L)).thenReturn(pendiente(42));

            assertEquals(201, recurso.registrar(pedidoValido(), CLAVE, uri).getStatus());
            verify(gestion, times(2)).registrarPedidoExterno(any(), eq(CLAVE));
        }

        @Test
        void unAccesoDenegadoNoSeReintenta() {
            when(gestion.registrarPedidoExterno(any(), eq(CLAVE))).thenThrow(new EJBAccessException("sin rol"));
            assertThrows(EJBAccessException.class, () -> recurso.registrar(pedidoValido(), CLAVE, uri));
            verify(gestion, times(1)).registrarPedidoExterno(any(), eq(CLAVE));
        }

        @Test
        void erroresDeNegocio() {
            when(gestion.registrarPedidoExterno(any(), eq(CLAVE)))
                    .thenThrow(new ClaveIdempotenciaReutilizadaException(CLAVE))
                    .thenThrow(new CuentaErpSinComercioException("sin comercio"))
                    .thenThrow(new ValidacionException("sin stock"));

            Response reutilizada = recurso.registrar(pedidoValido(), CLAVE, uri);
            assertEquals(422, reutilizada.getStatus());
            assertEquals("/problemas/clave-idempotencia-reutilizada", tipo(reutilizada));

            Response sinComercio = recurso.registrar(pedidoValido(), CLAVE, uri);
            assertEquals(403, sinComercio.getStatus());
            assertEquals("/problemas/cuenta-erp-sin-comercio", tipo(sinComercio));

            Response invalido = recurso.registrar(pedidoValido(), CLAVE, uri);
            assertEquals(422, invalido.getStatus());
            assertEquals("/problemas/datos-invalidos", tipo(invalido));
        }
    }

    @Nested
    class Consulta {

        @Test
        void devuelveElPedidoConSuETag() {
            when(seguimiento.consultarPedidoExterno(42L)).thenReturn(pendiente(42));
            Response r = recurso.consultar(42L, uri, request);
            assertEquals(200, r.getStatus());
            assertEquals(new EntityTag(PedidosExternosResource.huella(json(r))), r.getEntityTag());
        }

        @Test
        void conIfNoneMatchIgualAlEtagEs304() {
            when(seguimiento.consultarPedidoExterno(42L)).thenReturn(pendiente(42));
            when(request.evaluatePreconditions(any(EntityTag.class))).thenReturn(Response.notModified());
            Response r = recurso.consultar(42L, uri, request);
            assertEquals(304, r.getStatus());
            assertNotNull(r.getEntityTag());
        }

        @Test
        void inexistenteODeOtroComercioEs404YSinComercioEs403() {
            when(seguimiento.consultarPedidoExterno(7L)).thenThrow(new PedidoNoEncontradoException("no"));
            when(seguimiento.consultarPedidoExterno(8L)).thenThrow(new CuentaErpSinComercioException("no"));
            assertEquals("/problemas/pedido-inexistente", tipo(recurso.consultar(7L, uri, request)));
            assertEquals(403, recurso.consultar(8L, uri, request).getStatus());
        }

        @Test
        void unPedidoSincronizadoMuestraSuEstadoSeguimientoYPromesa() {
            PedidoExternoDTO dto = pendiente(42);
            dto.resultado = "Sincronizado";
            dto.idPedido = 118L;
            dto.estadoPedido = "PENDIENTE";
            dto.enListaDeEspera = true;
            dto.codigoSeguimiento = "RB-7KQ2M9XHTA";
            dto.fechaPrometida = LocalDate.of(2026, 11, 10);
            dto.intentosFallidos = 1;
            when(seguimiento.consultarPedidoExterno(42L)).thenReturn(dto);

            JsonObject cuerpo = json(recurso.consultar(42L, uri, request));

            assertEquals(118, cuerpo.getInt("idPedido"));
            assertEquals("PENDIENTE", cuerpo.getString("estadoPedido"));
            assertTrue(cuerpo.getBoolean("enListaDeEspera"));
            assertEquals("2026-11-10", cuerpo.getString("fechaPrometida"));
            assertEquals(1, cuerpo.getInt("intentosFallidos"));
            JsonObject links = cuerpo.getJsonObject("_links");
            assertTrue(links.containsKey("cancelar"), "PENDIENTE y en depósito: se puede cancelar");
            assertEquals("https://rabbit.test/Rabbit/api/v1/seguimiento/RB-7KQ2M9XHTA",
                    links.getJsonObject("seguimiento").getString("href"));
        }

        @Test
        void unDescartadoMuestraElMotivoSinPromesaNiEnlaceParaCancelar() {
            PedidoExternoDTO dto = pendiente(42);
            dto.resultado = "Descartado";
            dto.errorSincronizacion = "Sin stock";
            dto.fechaPrometida = LocalDate.of(2026, 11, 10);
            dto.tipoEntrega = null;
            when(seguimiento.consultarPedidoExterno(42L)).thenReturn(dto);

            JsonObject cuerpo = json(recurso.consultar(42L, uri, request));

            assertEquals("Sin stock", cuerpo.getString("motivo"));
            assertEquals("DOMICILIO", cuerpo.getString("tipoEntrega"));
            assertFalse(cuerpo.containsKey("fechaPrometida"));
            assertFalse(cuerpo.getJsonObject("_links").containsKey("cancelar"));
        }
    }

    @Nested
    class Cancelacion {

        @Test
        void sinIfMatchCancelaYDevuelveElETagNuevo() {
            PedidoExternoDTO cancelado = pendiente(42);
            cancelado.resultado = "Cancelado";
            when(gestion.cancelarPedidoExterno(42L)).thenReturn(cancelado);

            Response r = recurso.cancelar(42L, null, uri, request);

            assertEquals(200, r.getStatus());
            assertEquals(new EntityTag(PedidosExternosResource.huella(json(r))), r.getEntityTag());
            verify(seguimiento, never()).consultarPedidoExterno(any());
        }

        @Test
        void conUnETagDesactualizadoEs412ConElETagActualYNoCancela() {
            when(seguimiento.consultarPedidoExterno(42L)).thenReturn(pendiente(42));
            when(request.evaluatePreconditions(any(EntityTag.class)))
                    .thenReturn(Response.status(Response.Status.PRECONDITION_FAILED));

            Response r = recurso.cancelar(42L, "\"viejo\"", uri, request);

            assertEquals(412, r.getStatus());
            assertEquals("/problemas/pedido-modificado", tipo(r));
            assertNotNull(r.getEntityTag());
            verify(gestion, never()).cancelarPedidoExterno(any());
        }

        @Test
        void conElETagActualCancela() {
            when(seguimiento.consultarPedidoExterno(42L)).thenReturn(pendiente(42));
            when(gestion.cancelarPedidoExterno(42L)).thenReturn(pendiente(42));
            String actual = etagDe(json(recurso.consultar(42L, uri, request)));

            Response r = recurso.cancelar(42L, actual, uri, request);

            assertEquals(200, r.getStatus());
            verify(gestion).cancelarPedidoExterno(42L);
        }

        @Test
        void cadaErrorDeNegocioTieneSuCodigo() {
            when(gestion.cancelarPedidoExterno(42L))
                    .thenThrow(new PedidoNoEncontradoException("no"))
                    .thenThrow(new CancelacionNoPermitidaException("ya salió"))
                    .thenThrow(new TransicionNoPermitidaException("cambió"))
                    .thenThrow(new CuentaErpSinComercioException("no"))
                    .thenThrow(new ConflictoDeStockException("otra sesión"))
                    .thenThrow(new ValidacionException("otra cosa"));

            assertEquals(404, recurso.cancelar(42L, null, uri, request).getStatus());
            assertEquals("/problemas/cancelacion-no-permitida", tipo(recurso.cancelar(42L, null, uri, request)));
            assertEquals("/problemas/transicion-no-permitida", tipo(recurso.cancelar(42L, null, uri, request)));
            assertEquals(403, recurso.cancelar(42L, null, uri, request).getStatus());
            assertEquals("/problemas/stock-en-conflicto", tipo(recurso.cancelar(42L, null, uri, request)));
            Response fallida = recurso.cancelar(42L, null, uri, request);
            assertEquals(422, fallida.getStatus());
            assertEquals("/problemas/cancelacion-fallida", tipo(fallida));
        }

        @Test
        void unPedidoDevueltoAlDepositoNoOfreceCancelar() {
            PedidoExternoDTO dto = pendiente(42);
            dto.resultado = "Sincronizado";
            dto.estadoPedido = "PENDIENTE";
            dto.devueltoADeposito = true;
            when(gestion.cancelarPedidoExterno(42L)).thenReturn(dto);
            JsonObject cuerpo = json(recurso.cancelar(42L, null, uri, request));
            assertFalse(cuerpo.getJsonObject("_links").containsKey("cancelar"));
        }
    }
}
