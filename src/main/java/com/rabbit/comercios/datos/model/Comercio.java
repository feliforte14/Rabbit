package com.rabbit.comercios.datos.model;

/**
 * ENTIDAD JPA (dentro de la capa de Datos)
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
 * IMPORTANTE: las entidades NUNCA se muestran directamente en la vista.
 * Para eso existen los DTOs en la carpeta dto/.
 */

import jakarta.persistence.*;
import java.util.List;

@Entity
@Table(name = "comercios")
public class Comercio {

    // GenerationType.AUTO: la estrategia de generación del ID (secuencia,
    // identity, etc.) queda a criterio del proveedor JPA/motor de BD.
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private String nombre;
    private String razonSocial;
    private String cuit;
    private String email;
    private String telefono;

    // Baja lógica: false = dado de baja, sigue en la BD pero no opera.
    // Ver ComercioService.darDeBajaComercio / reactivarComercio.
    private boolean activo;

    // Relación inversa (el dueño de la FK es Sucursal.comercio, ver @JoinColumn allá):
    //  - mappedBy = "comercio": le dice a JPA que no gestione una columna
    //    propia para esta relación, que ya existe del lado de Sucursal.
    //  - cascade = CascadeType.ALL: cualquier operación de persistencia
    //    sobre el Comercio (sobre todo remove) se propaga a sus sucursales.
    //    Esto es lo que hace que eliminar un comercio borre en cascada
    //    todas sus sucursales — por eso ComercioService.eliminarComercio
    //    exige que el comercio ya esté dado de baja antes de permitirlo.
    //  - fetch = FetchType.LAZY: las sucursales NO se cargan de la BD hasta
    //    que se llama a getSucursales() (o se accede a la lista). Evita
    //    traer sucursales de más en consultas que solo necesitan datos
    //    del comercio (p. ej. el listado de comercios.xhtml).
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
