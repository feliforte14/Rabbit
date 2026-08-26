package com.rabbit.comercios.datos.model;

/**
 * Entidad JPA: cada instancia es una fila de la tabla "comercios".
 * Un comercio puede tener muchas sucursales (ver campo sucursales).
 * No se expone directo a la vista: para eso están los DTOs (carpeta dto/).
 */

import jakarta.persistence.*;
import java.util.List;

@Entity
@Table(name = "comercios")
public class Comercio {

    // AUTO: JPA elige cómo generar el ID (secuencia, identity, etc.)
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private String nombre;
    private String razonSocial;
    private String cuit;
    private String email;
    private String telefono;

    // Baja lógica: false = dado de baja (sigue en la BD pero no opera).
    // Ver ComercioService.darDeBajaComercio / reactivarComercio.
    private boolean activo;

    // La FK vive del lado de Sucursal (mappedBy = "comercio"), acá solo
    // se refleja. cascade ALL: borrar un comercio borra sus sucursales
    // (por eso eliminarComercio exige que ya esté dado de baja).
    // fetch LAZY: las sucursales se cargan recién cuando se piden.
    @OneToMany(mappedBy = "comercio", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Sucursal> sucursales;

    public Comercio() {}

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getRazonSocial() { return razonSocial; }
    public void setRazonSocial(String razonSocial) { this.razonSocial = razonSocial; }
    public String getCuit() { return cuit; }
    public void setCuit(String cuit) { this.cuit = cuit; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
    public List<Sucursal> getSucursales() { return sucursales; }
    public void setSucursales(List<Sucursal> sucursales) { this.sucursales = sucursales; }
}
