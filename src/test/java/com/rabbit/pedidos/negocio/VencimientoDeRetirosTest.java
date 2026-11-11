package com.rabbit.pedidos.negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** El timer que devuelve al comercio los retiros que nadie buscó: cada pedido por separado, sin frenarse. */
@ExtendWith(MockitoExtension.class)
class VencimientoDeRetirosTest {

    @Mock private IGestionPedidos gestion;
    @InjectMocks private VencimientoDeRetiros timer;

    @Test
    @DisplayName("Devuelve cada retiro vencido y cuenta los que sí se devolvieron")
    void devuelveLosVencidos() {
        when(gestion.idsDeRetirosVencidos()).thenReturn(List.of(1L, 2L, 3L));
        when(gestion.vencerRetiro(1L)).thenReturn(true);
        when(gestion.vencerRetiro(2L)).thenReturn(false);   // lo retiraron mientras tanto
        when(gestion.vencerRetiro(3L)).thenReturn(true);

        assertEquals(2, timer.vencerRetiros());
        verify(gestion).vencerRetiro(2L);
    }

    @Test
    @DisplayName("Si uno falla, los demás igual se procesan")
    void unFalloNoFrenaALosDemas() {
        when(gestion.idsDeRetirosVencidos()).thenReturn(List.of(1L, 2L));
        doThrow(new IllegalStateException("la base se cayó")).when(gestion).vencerRetiro(1L);
        when(gestion.vencerRetiro(2L)).thenReturn(true);

        assertEquals(1, timer.vencerRetiros());
        verify(gestion).vencerRetiro(2L);
    }

    @Test
    @DisplayName("Si no hay ninguno, no hace nada")
    void sinVencidos() {
        when(gestion.idsDeRetirosVencidos()).thenReturn(List.of());

        assertEquals(0, timer.vencerRetiros());
        verify(gestion, never()).vencerRetiro(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Si no se puede ni buscarlos, devuelve 0 y no propaga el error")
    void fallaLaBusqueda() {
        when(gestion.idsDeRetirosVencidos()).thenThrow(new IllegalStateException("sin base"));

        assertEquals(0, timer.vencerRetiros());
        verify(gestion, never()).vencerRetiro(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("El método del @Schedule corre la misma pasada")
    void elDelTimerHaceLoMismo() {
        when(gestion.idsDeRetirosVencidos()).thenReturn(List.of(5L));
        when(gestion.vencerRetiro(5L)).thenReturn(true);

        timer.vencerRetirosDelDia();

        verify(gestion).vencerRetiro(5L);
    }
}
