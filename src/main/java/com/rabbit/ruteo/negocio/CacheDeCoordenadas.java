package com.rabbit.ruteo.negocio;

/**
 * CAPA DE NEGOCIO — la caché de coordenadas de direcciones (ADR-046), EJB
 * @Stateless del componente Ruteo. La lee RuteoService al armar la hoja de
 * ruta (sin salir a internet) y la llena Geocodificador.
 */

import com.rabbit.ruteo.datos.CoordenadaRepository;
import com.rabbit.ruteo.datos.model.CoordenadaDireccion;
import jakarta.annotation.security.PermitAll;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

// Sin rol: la usan RuteoService (con el usuario que sea) y el timer.
@Stateless
@PermitAll
public class CacheDeCoordenadas {

    // Una dirección no encontrada se vuelve a preguntar después de esto
    // (alguien pudo cargarla en OpenStreetMap, o corregirse el pedido).
    static final int DIAS_PARA_REINTENTAR = 7;

    @Inject
    private CoordenadaRepository repository;

    /** {latitud, longitud} de cada clave encontrada; las demás no están en el mapa. */
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public Map<String, double[]> encontradas(Collection<String> claves) {
        Map<String, double[]> resultado = new HashMap<>();
        for (CoordenadaDireccion c : repository.buscarPorClaves(new HashSet<>(claves))) {
            if (c.isEncontrada() && c.getLatitud() != null && c.getLongitud() != null) {
                resultado.put(c.getClave(), new double[] { c.getLatitud(), c.getLongitud() });
            }
        }
        return resultado;
    }

    /** De esas claves, las que hay que consultar: nunca se preguntaron, o no se encontraron hace rato. */
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public Set<String> faltantes(Collection<String> claves) {
        Set<String> faltan = new HashSet<>(claves);
        LocalDateTime limite = LocalDateTime.now().minusDays(DIAS_PARA_REINTENTAR);
        for (CoordenadaDireccion c : repository.buscarPorClaves(new HashSet<>(claves))) {
            if (c.isEncontrada() || c.getFechaConsulta().isAfter(limite)) {
                faltan.remove(c.getClave());
            }
        }
        return faltan;
    }

    /** Guarda lo que respondió Nominatim; coordenadas null = no la encontró. */
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public void guardar(String clave, double[] coordenadas) {
        CoordenadaDireccion c = new CoordenadaDireccion();
        c.setClave(clave);
        c.setEncontrada(coordenadas != null);
        if (coordenadas != null) {
            c.setLatitud(coordenadas[0]);
            c.setLongitud(coordenadas[1]);
        }
        c.setFechaConsulta(LocalDateTime.now());
        repository.guardar(c);
    }
}
