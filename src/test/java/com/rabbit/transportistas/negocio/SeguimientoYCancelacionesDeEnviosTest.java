package com.rabbit.transportistas.negocio;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.rabbit.integracion.transportistas.EstadoExterno;
import com.rabbit.integracion.transportistas.IAdaptadorTransportista;
import com.rabbit.transportistas.datos.model.EstadoEnvio;
import com.rabbit.transportistas.datos.model.TipoIntegracion;
import com.rabbit.transportistas.dto.EnvioDTO;
import com.rabbit.transportistas.dto.TransportistaDTO;
import jakarta.ejb.EJBException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SeguimientoYCancelacionesDeEnviosTest {

    @Mock private AdaptadoresTransportista adaptadores;
    @Mock private IAdaptadorTransportista rest;
    @Mock private IAdaptadorTransportista soap;

    @BeforeEach
    void adaptadoresPorTipo() {
        when(adaptadores.para(TipoIntegracion.REST)).thenReturn(rest);
        when(adaptadores.para(TipoIntegracion.SOAP_LEGADO)).thenReturn(soap);
    }

    @Nested
    class Seguimiento {

        @Mock private ISeguimientoEnvios seguimiento;
        @Mock private IGestionTransportistas transportistas;
        @InjectMocks private SeguimientoDeEnvios polling;

        private EnvioDTO envio(Long id, Long idTransportista, String estado) {
            EnvioDTO e = new EnvioDTO();
            e.id = id;
            e.idPedido = 100L + id;
            e.idTransportista = idTransportista;
            e.codigoSeguimiento = "C-" + id;
            e.estado = estado;
            return e;
        }

        private TransportistaDTO transportista(Long id, TipoIntegracion tipo) {
            TransportistaDTO t = new TransportistaDTO();
            t.id = id;
            t.nombre = "T" + id;
            t.tipoIntegracion = tipo.name();
            t.endpoint = "https://t" + id + ".example";
            return t;
        }

        @BeforeEach
        void inyectarAdaptadores() throws Exception {
            var campo = SeguimientoDeEnvios.class.getDeclaredField("adaptadores");
            campo.setAccessible(true);
            campo.set(polling, adaptadores);
        }

        @Test
        void sinEnviosActivosNoConsultaANadie() {
            when(seguimiento.listarEnviosActivos()).thenReturn(List.of());

            polling.consultarEnviosActivos();

            verifyNoInteractions(transportistas);
            verifyNoInteractions(rest, soap);
        }

        @Test
        void registraSoloLosCambiosDeEstado() {
            when(seguimiento.listarEnviosActivos()).thenReturn(List.of(
                    envio(1L, 10L, "SOLICITADO"), envio(2L, 20L, "EN_TRANSITO")));
            when(transportistas.listarTodos()).thenReturn(List.of(
                    transportista(10L, TipoIntegracion.REST), transportista(20L, TipoIntegracion.SOAP_LEGADO)));
            when(rest.consultarEstado("https://t10.example", "C-1")).thenReturn(EstadoExterno.EN_TRANSITO);
            when(soap.consultarEstado("https://t20.example", "C-2")).thenReturn(EstadoExterno.EN_TRANSITO);

            polling.consultarEnviosActivos();

            verify(seguimiento).registrarNovedad(1L, EstadoEnvio.EN_TRANSITO);
            verify(seguimiento, never()).registrarNovedad(2L, EstadoEnvio.EN_TRANSITO);
        }

        @Test
        void traduceEntregadoYCanceladoEIgnoraElEstadoDesconocido() {
            when(seguimiento.listarEnviosActivos()).thenReturn(List.of(
                    envio(1L, 10L, "EN_TRANSITO"), envio(2L, 10L, "EN_TRANSITO"), envio(3L, 10L, "EN_TRANSITO")));
            when(transportistas.listarTodos()).thenReturn(List.of(transportista(10L, TipoIntegracion.REST)));
            when(rest.consultarEstado(any(), any())).thenReturn(
                    EstadoExterno.ENTREGADO, EstadoExterno.CANCELADO, EstadoExterno.DESCONOCIDO);

            polling.consultarEnviosActivos();

            verify(seguimiento).registrarNovedad(1L, EstadoEnvio.ENTREGADO);
            verify(seguimiento).registrarNovedad(2L, EstadoEnvio.CANCELADO);
            verify(seguimiento, never()).registrarNovedad(org.mockito.ArgumentMatchers.eq(3L), any());
        }

        @Test
        void unTransportistaQueFallaNoFrenaAlResto() {
            when(seguimiento.listarEnviosActivos()).thenReturn(List.of(
                    envio(1L, 10L, "SOLICITADO"), envio(2L, 20L, "SOLICITADO")));
            when(transportistas.listarTodos()).thenReturn(List.of(
                    transportista(10L, TipoIntegracion.REST), transportista(20L, TipoIntegracion.SOAP_LEGADO)));
            when(rest.consultarEstado(any(), any())).thenThrow(new IllegalStateException("timeout"));
            when(soap.consultarEstado(any(), any())).thenReturn(EstadoExterno.ENTREGADO);

            polling.consultarEnviosActivos();

            verify(seguimiento, never()).registrarNovedad(org.mockito.ArgumentMatchers.eq(1L), any());
            verify(seguimiento).registrarNovedad(2L, EstadoEnvio.ENTREGADO);
        }

        @Test
        void unaNovedadRechazadaSeReintentaEnLaProximaPasadaSinCortarLaActual() {
            when(seguimiento.listarEnviosActivos()).thenReturn(List.of(
                    envio(1L, 10L, "SOLICITADO"), envio(2L, 10L, "SOLICITADO")));
            when(transportistas.listarTodos()).thenReturn(List.of(transportista(10L, TipoIntegracion.REST)));
            when(rest.consultarEstado(any(), any())).thenReturn(EstadoExterno.EN_TRANSITO);
            doThrow(new NovedadRechazadaException("cambió")).when(seguimiento).registrarNovedad(1L, EstadoEnvio.EN_TRANSITO);
            doThrow(new EJBException("base")).when(seguimiento).registrarNovedad(2L, EstadoEnvio.EN_TRANSITO);

            polling.consultarEnviosActivos();

            verify(seguimiento).registrarNovedad(1L, EstadoEnvio.EN_TRANSITO);
            verify(seguimiento).registrarNovedad(2L, EstadoEnvio.EN_TRANSITO);
        }
    }

    @Nested
    class Compensaciones {

        @InjectMocks private CancelacionesDeEnvios cancelaciones;

        @BeforeEach
        void inyectarAdaptadores() throws Exception {
            var campo = CancelacionesDeEnvios.class.getDeclaredField("adaptadores");
            campo.setAccessible(true);
            campo.set(cancelaciones, adaptadores);
        }

        @Test
        void siLaDerivacionSeDeshaceCancelaElEnvioEnElTransportista() {
            cancelaciones.compensarEnvioDeshecho(
                    new EnvioSolicitado(100L, TipoIntegracion.SOAP_LEGADO, "https://legado.example", "TL-9"));

            verify(soap).cancelarEnvio("https://legado.example", "TL-9");
            verifyNoInteractions(rest);
        }

        @Test
        void alCancelarElPedidoCancelaElEnvio() {
            when(rest.cancelarEnvio(any(), any())).thenReturn(false);

            cancelaciones.avisarCancelacion(
                    new EnvioCancelado(100L, TipoIntegracion.REST, "https://rapido.example", "RP-1"));

            verify(rest).cancelarEnvio("https://rapido.example", "RP-1");
        }
    }
}
