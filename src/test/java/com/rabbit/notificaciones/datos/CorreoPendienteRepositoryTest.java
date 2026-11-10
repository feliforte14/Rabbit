package com.rabbit.notificaciones.datos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbit.notificaciones.datos.model.CorreoPendiente;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Las consultas de la bandeja de mails: solo PENDIENTES en orden, y marcas y borrados acotados por estado. */
class CorreoPendienteRepositoryTest {

    private EntityManager em;
    private Query actualizacion;
    private TypedQuery<CorreoPendiente> consulta;
    private final CorreoPendienteRepository repository = new CorreoPendienteRepository();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void preparar() throws Exception {
        em = mock(EntityManager.class);
        actualizacion = mock(Query.class);
        consulta = mock(TypedQuery.class);
        when(em.createQuery(anyString())).thenReturn(actualizacion);
        when(actualizacion.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(actualizacion);
        when(em.createQuery(anyString(), eq(CorreoPendiente.class))).thenReturn(consulta);
        when(consulta.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(consulta);
        when(consulta.setMaxResults(org.mockito.ArgumentMatchers.anyInt())).thenReturn(consulta);
        Field campo = CorreoPendienteRepository.class.getDeclaredField("em");
        campo.setAccessible(true);
        campo.set(repository, em);
    }

    private String jpql() {
        ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
        verify(em).createQuery(texto.capture());
        return texto.getValue();
    }

    @Test
    @DisplayName("Guardar persiste la fila")
    void guardar() {
        CorreoPendiente fila = new CorreoPendiente();

        repository.guardar(fila);

        verify(em).persist(fila);
    }

    @Test
    @DisplayName("Los pendientes salen en el orden en que pasaron y con un máximo")
    void pendientes() {
        CorreoPendiente fila = new CorreoPendiente();
        when(consulta.getResultList()).thenReturn(List.of(fila));

        assertEquals(List.of(fila), repository.listarPendientes(50));

        ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
        verify(em).createQuery(texto.capture(), eq(CorreoPendiente.class));
        assertTrue(texto.getValue().contains("c.estado = :estado"));
        assertTrue(texto.getValue().contains("ORDER BY c.id"));
        verify(consulta).setParameter("estado", CorreoPendiente.PENDIENTE);
        verify(consulta).setMaxResults(50);
    }

    @Test
    @DisplayName("Marcar enviado cambia el estado y guarda cuándo")
    void marcarEnviado() {
        LocalDateTime cuando = LocalDateTime.of(2026, 11, 10, 12, 0);

        repository.marcarEnviado(9L, cuando);

        assertTrue(jpql().startsWith("UPDATE CorreoPendiente c SET c.estado = :estado, c.resueltoEn = :cuando"));
        verify(actualizacion).setParameter("estado", CorreoPendiente.ENVIADO);
        verify(actualizacion).setParameter("cuando", cuando);
        verify(actualizacion).setParameter("id", 9L);
        verify(actualizacion).executeUpdate();
    }

    @Test
    @DisplayName("Sumar un intento fallido incrementa el contador de esa fila")
    void sumarIntento() {
        repository.sumarIntentoFallido(9L);

        assertTrue(jpql().contains("c.intentosFallidos = c.intentosFallidos + 1"));
        verify(actualizacion).setParameter("id", 9L);
        verify(actualizacion).executeUpdate();
    }

    @Test
    @DisplayName("Descartar deja el estado, cuándo y por qué")
    void descartar() {
        LocalDateTime cuando = LocalDateTime.of(2026, 11, 10, 12, 0);

        repository.descartar(9L, cuando, "El servidor rechazó el destinatario");

        verify(actualizacion).setParameter("estado", CorreoPendiente.DESCARTADO);
        verify(actualizacion).setParameter("motivo", "El servidor rechazó el destinatario");
        verify(actualizacion).setParameter("id", 9L);
        verify(actualizacion).executeUpdate();
    }

    @Test
    @DisplayName("Descartar los vencidos toca solo PENDIENTES creados antes del límite y devuelve cuántos")
    void descartarVencidos() {
        LocalDateTime limite = LocalDateTime.of(2026, 11, 9, 12, 0);
        when(actualizacion.executeUpdate()).thenReturn(3);

        int descartados = repository.descartarPendientesCreadosAntesDe(limite, LocalDateTime.now(), "tarde");

        assertEquals(3, descartados);
        String texto = jpql();
        assertTrue(texto.contains("c.estado = :pendiente AND c.creadoEn < :limite"), texto);
        verify(actualizacion).setParameter("pendiente", CorreoPendiente.PENDIENTE);
        verify(actualizacion).setParameter("limite", limite);
    }

    @Test
    @DisplayName("Borrar resueltos toca solo el estado pedido y los anteriores al límite")
    void borrarResueltos() {
        LocalDateTime limite = LocalDateTime.of(2026, 11, 9, 12, 0);
        when(actualizacion.executeUpdate()).thenReturn(5);

        assertEquals(5, repository.borrarResueltosAntesDe(CorreoPendiente.ENVIADO, limite));

        String texto = jpql();
        assertTrue(texto.startsWith("DELETE FROM CorreoPendiente c WHERE c.estado = :estado AND c.resueltoEn < :limite"),
                texto);
        verify(actualizacion).setParameter("estado", CorreoPendiente.ENVIADO);
        verify(actualizacion).setParameter("limite", limite);
    }
}
