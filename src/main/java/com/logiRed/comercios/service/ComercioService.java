package com.logiRed.comercios.service;

import com.logiRed.comercios.dto.*;
import com.logiRed.comercios.model.Comercio;
import com.logiRed.comercios.repository.ComercioRepository;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@Stateless
public class ComercioService {

    @Inject
    private ComercioRepository repository;

    // IRegistroComercios

    @Transactional
    public Long registrarComercio(DatosComercioDTO datos) {
        Comercio comercio = new Comercio();
        comercio.setNombre(datos.nombre);
        comercio.setRazonSocial(datos.razonSocial);
        comercio.setCuit(datos.cuit);
        comercio.setEmail(datos.email);
        comercio.setTelefono(datos.telefono);
        comercio.setActivo(true);
        return repository.guardar(comercio).getId();
    }

    @Transactional
    public void actualizarDatosFiscales(Long idComercio, DatosFiscalesDTO datos) {
        Comercio comercio = obtenerOFallar(idComercio);
        comercio.setRazonSocial(datos.razonSocial);
        comercio.setCuit(datos.cuit);
        comercio.setEmail(datos.email);
        comercio.setTelefono(datos.telefono);
        repository.actualizar(comercio);
    }

    @Transactional
    public void darDeBajaComercio(Long idComercio) {
        Comercio comercio = obtenerOFallar(idComercio);
        comercio.setActivo(false);
        repository.actualizar(comercio);
    }

    // IConsultaComercios

    public ComercioDTO obtenerComercio(Long idComercio) {
        return ComercioDTO.desde(obtenerOFallar(idComercio));
    }

    public List<SucursalDTO> listarSucursales(Long idComercio) {
        return repository.listarSucursalesActivas(idComercio)
                .stream()
                .map(SucursalDTO::desde)
                .collect(Collectors.toList());
    }

    public boolean validarComercioActivo(Long idComercio) {
        Comercio comercio = repository.buscarPorId(idComercio);
        return comercio != null && comercio.isActivo();
    }

    @Transactional
    public void eliminarComercio(Long idComercio) {
        Comercio comercio = obtenerOFallar(idComercio);
        repository.eliminar(comercio);
    }

    // helper

    private Comercio obtenerOFallar(Long id) {
        Comercio comercio = repository.buscarPorId(id);
        if (comercio == null) {
            throw new IllegalArgumentException("Comercio no encontrado: " + id);
        }
        return comercio;
    }
}
