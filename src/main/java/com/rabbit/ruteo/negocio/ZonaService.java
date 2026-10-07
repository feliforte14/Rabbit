package com.rabbit.ruteo.negocio;

/**
 * CAPA DE NEGOCIO — zonas de reparto del componente Ruteo (EJB @Stateless).
 *
 * Una zona es un rango de códigos postales con una cobertura: PROPIA
 * (reparten repartidores de Rabbit, con un transportista de respaldo
 * opcional) o TRANSPORTISTA (los pedidos se derivan siempre a uno). Las
 * zonas activas no se pueden superponer: cada código postal cae en una sola.
 */

import com.rabbit.ruteo.datos.ZonaRepository;
import com.rabbit.ruteo.datos.model.CoberturaZona;
import com.rabbit.ruteo.datos.model.Zona;
import com.rabbit.ruteo.dto.DatosZonaDTO;
import com.rabbit.ruteo.dto.ZonaDTO;
import com.rabbit.transportistas.dto.TransportistaDTO;
import com.rabbit.transportistas.negocio.IGestionTransportistas;
import jakarta.annotation.security.DeclareRoles;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;

@Stateless
@DeclareRoles({"ADMINISTRADOR", "OPERADOR"})
@PermitAll
public class ZonaService implements IZonas {

    private static final int CP_MINIMO = 1000;
    private static final int CP_MAXIMO = 9999;

    @Inject
    private ZonaRepository repository;

    @Inject
    private IGestionTransportistas transportistas;

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    public Long registrarZona(DatosZonaDTO datos) {
        if (datos.nombre == null || datos.nombre.isBlank()) {
            throw new ValidacionException("El nombre de la zona es obligatorio");
        }
        if (datos.codigoPostalDesde == null || datos.codigoPostalHasta == null) {
            throw new ValidacionException("Indicá el rango de códigos postales de la zona");
        }
        int desde = datos.codigoPostalDesde;
        int hasta = datos.codigoPostalHasta;
        if (desde < CP_MINIMO || hasta > CP_MAXIMO || desde > hasta) {
            throw new ValidacionException("El rango tiene que ir de " + CP_MINIMO + " a " + CP_MAXIMO
                    + " y el \"desde\" no puede ser mayor que el \"hasta\"");
        }
        if (datos.cobertura == null) {
            throw new ValidacionException("Elegí quién reparte en la zona");
        }
        validarTransportista(datos.cobertura, datos.idTransportista);
        exigirSinSuperposicion(desde, hasta, null);

        Zona zona = new Zona();
        zona.setNombre(datos.nombre.trim());
        zona.setCodigoPostalDesde(desde);
        zona.setCodigoPostalHasta(hasta);
        zona.setCobertura(datos.cobertura);
        zona.setIdTransportista(datos.idTransportista);
        zona.setActiva(true);
        return repository.guardar(zona).getId();
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    public void darDeBajaZona(Long idZona) {
        Zona zona = obtenerOFallar(idZona);
        zona.setActiva(false);
        repository.actualizar(zona);
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    public void reactivarZona(Long idZona) {
        Zona zona = obtenerOFallar(idZona);
        exigirSinSuperposicion(zona.getCodigoPostalDesde(), zona.getCodigoPostalHasta(), zona.getId());
        zona.setActiva(true);
        repository.actualizar(zona);
    }

    @Override
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    public List<ZonaDTO> listarTodas() {
        return repository.listarTodas().stream().map(ZonaDTO::desde).collect(Collectors.toList());
    }

    @Override
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    public ZonaDTO zonaDeCodigoPostal(String codigoPostal) {
        if (codigoPostal == null) {
            return null;
        }
        int cp = Integer.parseInt(codigoPostal);
        return repository.listarTodas().stream()
                .filter(Zona::isActiva)
                .filter(z -> z.contiene(cp))
                .findFirst().map(ZonaDTO::desde).orElse(null);
    }

    private void validarTransportista(CoberturaZona cobertura, Long idTransportista) {
        if (cobertura == CoberturaZona.TRANSPORTISTA && idTransportista == null) {
            throw new ValidacionException("Una zona que cubre un transportista necesita el transportista");
        }
        if (idTransportista != null) {
            boolean activo = transportistas.listarActivos().stream()
                    .map(TransportistaDTO::getId).anyMatch(idTransportista::equals);
            if (!activo) {
                throw new ValidacionException("El transportista elegido no existe o está dado de baja");
            }
        }
    }

    private void exigirSinSuperposicion(int desde, int hasta, Long excluida) {
        List<Zona> superpuestas = repository.listarSuperpuestas(desde, hasta, excluida);
        if (!superpuestas.isEmpty()) {
            Zona z = superpuestas.get(0);
            throw new ValidacionException("El rango se superpone con la zona " + z.getNombre() + " ("
                    + z.getCodigoPostalDesde() + "-" + z.getCodigoPostalHasta() + ")");
        }
    }

    private Zona obtenerOFallar(Long id) {
        Zona zona = id != null ? repository.buscarPorId(id) : null;
        if (zona == null) {
            throw new ValidacionException("Zona no encontrada: " + id);
        }
        return zona;
    }
}
