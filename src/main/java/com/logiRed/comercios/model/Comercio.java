package com.logiRed.comercios.model;

/**
 * CAPA MODEL (Entidad JPA)
 *
 * Las clases en esta carpeta representan las tablas de la base de datos.
 * Cada instancia de Comercio corresponde a una fila en la tabla "comercios".
 *
 * JPA (Jakarta Persistence API) se encarga de traducir automáticamente
 * entre objetos Java y filas en la BD — no hay SQL manual.
 *
 * Comercio es la entidad principal del sistema. Tiene una relación
 * OneToMany con Sucursal: un comercio puede tener muchas sucursales.
 *
 * IMPORTANTE: las entidades NUNCA se exponen directamente en la API.
 * Para eso existen los DTOs en la carpeta dto/.
 */

import jakarta.persistence.*;
import java.util.List;

@Entity
@Table(name = "comercios")
public class Comercio {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private String nombre;
    private String razonSocial;
    private String cuit;
    private String email;
    private String telefono;
    private boolean activo;

    // Un comercio tiene muchas sucursales. Si se elimina el comercio, se eliminan sus sucursales.
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
