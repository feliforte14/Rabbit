package com.rabbit.pedidos.datos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbit.pedidos.datos.model.EventoEstadoPedido;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** La bandeja de salida de los avisos de estado: qué se consulta y qué se actualiza. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EventoEstadoRepositoryTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 8, 12, 0);

    @Mock private EntityManager em;
    @Mock private TypedQuery<EventoEstadoPedido> consultaEventos;
    @Mock private TypedQuery<Long> consultaCuenta;
    @Mock private Query actualizacion;
    @InjectMocks private EventoEstadoRepository repository;

    @BeforeEach
    void preparar() {
        when(em.createQuery(anyString(), eq(EventoEstadoPedido.class))).thenReturn(consultaEventos);
        when(consultaEventos.setMaxResults(any(Integer.class))).thenReturn(consultaEventos);
        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(consultaCuenta);
        when(em.createQuery(anyString())).thenReturn(actualizacion);
        when(actualizacion.setParameter(anyString(), any())).thenReturn(actualizacion);
    }

    @Test
    void guardaElAvisoComoNuevaFila() {
        EventoEstadoPedido evento = new EventoEstadoPedido();

        repository.guardar(evento);

        verify(em).persist(evento);
    }

    @Test
    void listaSoloLosPendientesEnElOrdenEnQuePasaronYConTope() {
        EventoEstadoPedido uno = new EventoEstadoPedido();
        when(consultaEventos.getResultList()).thenReturn(List.of(uno));

        List<EventoEstadoPedido> pendientes = repository.listarPendientes(50);

        assertEquals(List.of(uno), pendientes);
        ArgumentCaptor<String> jpql = ArgumentCaptor.forClass(String.class);
        verify(em).createQuery(jpql.capture(), eq(EventoEstadoPedido.class));
        assertTrue(jpql.getValue().contains("publicadoEn IS NULL"));
        assertTrue(jpql.getValue().contains("ORDER BY e.id"));
        verify(consultaEventos).setMaxResults(50);
    }

    @Test
    void cuentaLosPendientes() {
        when(consultaCuenta.getSingleResult()).thenReturn(3L);

        assertEquals(3L, repository.contarPendientes());
    }

    @Test
    void marcaPublicadoUnSoloAvisoConLaHora() {
        repository.marcarPublicado(7L, AHORA);

        verify(actualizacion).setParameter("cuando", AHORA);
        verify(actualizacion).setParameter("id", 7L);
        verify(actualizacion).executeUpdate();
    }

    @Test
    void sumaUnIntentoFallidoAUnSoloAviso() {
        repository.sumarIntentoFallido(7L);

        verify(actualizacion).setParameter("id", 7L);
        verify(actualizacion).executeUpdate();
    }

    @Test
    void borraSoloLosYaPublicadosAnterioresALaFecha() {
        when(actualizacion.executeUpdate()).thenReturn(4);

        int borrados = repository.borrarPublicadosAnterioresA(AHORA);

        assertEquals(4, borrados);
        ArgumentCaptor<String> jpql = ArgumentCaptor.forClass(String.class);
        verify(em).createQuery(jpql.capture());
        assertTrue(jpql.getValue().contains("publicadoEn IS NOT NULL"));
        verify(actualizacion).setParameter("limite", AHORA);
    }

    @Test
    void laEntidadConservaLosDatosDelAviso() {
        EventoEstadoPedido e = new EventoEstadoPedido();
        e.setIdPedido(1L);
        e.setIdComercio(2L);
        e.setEstado("ENTREGADO");
        e.setFechaCambio(AHORA);
        e.setCreadoEn(AHORA.plusSeconds(1));
        e.setPublicadoEn(AHORA.plusSeconds(2));
        e.setIntentosFallidos(3);

        assertEquals(1L, e.getIdPedido());
        assertEquals(2L, e.getIdComercio());
        assertEquals("ENTREGADO", e.getEstado());
        assertEquals(AHORA, e.getFechaCambio());
        assertEquals(AHORA.plusSeconds(1), e.getCreadoEn());
        assertEquals(AHORA.plusSeconds(2), e.getPublicadoEn());
        assertEquals(3, e.getIntentosFallidos());
    }
}
