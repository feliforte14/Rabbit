package com.rabbit.pedidos.negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbit.integracion.ia.IEtiquetadorIa;
import com.rabbit.integracion.ia.IndicacionDeEntrega;
import com.rabbit.pedidos.datos.PedidoRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EtiquetadoDeInstruccionesTest {

    @Mock private IEtiquetadorIa gateway;
    @Mock private PedidoRepository repository;
    @InjectMocks private EtiquetadorEnSegundoPlano segundoPlano;

    @Test
    void guardaLasIndicacionesQueContestaLaIa() {
        when(gateway.etiquetar("Llamame antes, hay un perro"))
                .thenReturn(Optional.of(Set.of(IndicacionDeEntrega.LLAMAR_ANTES)));

        segundoPlano.etiquetar(7L, "Llamame antes, hay un perro");

        verify(repository).guardarIndicaciones(7L, "Llamame antes, hay un perro", "LLAMAR_ANTES");
    }

    @Test
    void sinRespuestaDeLaIaNoSeGuardaNadaYElRepartidorVeElTexto() {
        when(gateway.etiquetar("Dejar en portería")).thenReturn(Optional.empty());

        segundoPlano.etiquetar(8L, "Dejar en portería");

        verify(repository, never()).guardarIndicaciones(any(), any(), any());
    }

    @Test
    void siElTextoCambioMientrasLaIaContestabaLaRespuestaSeDescartaSinFallar() {
        when(gateway.etiquetar("Dejar en portería"))
                .thenReturn(Optional.of(Set.of(IndicacionDeEntrega.DEJAR_EN_PORTERIA)));
        when(repository.guardarIndicaciones(10L, "Dejar en portería", "DEJAR_EN_PORTERIA")).thenReturn(0);

        segundoPlano.etiquetar(10L, "Dejar en portería");

        verify(repository).guardarIndicaciones(10L, "Dejar en portería", "DEJAR_EN_PORTERIA");
    }

    @Test
    void sinIndicacionesConcretasSeGuardaNull() {
        when(gateway.etiquetar("Gracias")).thenReturn(Optional.of(Set.of()));

        segundoPlano.etiquetar(9L, "Gracias");

        verify(repository).guardarIndicaciones(9L, "Gracias", null);
    }

    @Test
    void elObservadorLePasaLasInstruccionesAlSegundoPlano() throws Exception {
        EtiquetadorEnSegundoPlano falso = org.mockito.Mockito.mock(EtiquetadorEnSegundoPlano.class);
        EtiquetadoDeInstrucciones observador = new EtiquetadoDeInstrucciones();
        var campo = EtiquetadoDeInstrucciones.class.getDeclaredField("etiquetador");
        campo.setAccessible(true);
        campo.set(observador, falso);

        observador.alModificar(new InstruccionesModificadas(4L, "Timbre roto"));

        verify(falso).etiquetar(4L, "Timbre roto");
    }

    @Test
    void guardarYMostrarSonInversasYIgnoranNombresViejos() {
        assertEquals(List.of("Llamar antes"), IndicacionDeEntrega.textos("LLAMAR_ANTES"));
        assertEquals(List.of("Llamar antes", "Hay una mascota"), IndicacionDeEntrega.textos("LLAMAR_ANTES, MASCOTA"));
        assertEquals(List.of("Hay una mascota"), IndicacionDeEntrega.textos("YA_NO_EXISTE,MASCOTA"));
        assertEquals(List.of(), IndicacionDeEntrega.textos(null));
        assertEquals(null, IndicacionDeEntrega.guardar(Set.of()));
    }
}
