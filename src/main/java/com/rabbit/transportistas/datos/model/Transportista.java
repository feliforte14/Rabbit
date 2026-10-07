package com.rabbit.transportistas.datos.model;

/**
 * Entidad JPA: una empresa de envíos externa a la que Rabbit le deriva
 * pedidos que no lleva un repartidor propio. Cada una se integra con su
 * propia tecnología (tipoIntegracion) en su endpoint.
 */

import jakarta.persistence.*;

@Entity
@Table(name = "transportistas")
public class Transportista {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    @Enumerated(EnumType.STRING)
    private TipoIntegracion tipoIntegracion;

    // URL base de la API (REST) o URL del WSDL (SOAP legado).
    @Column(length = 300)
    private String endpoint;

    private boolean activo;

    // Clave con la que el transportista firma sus avisos al webhook de
    // novedades (Authorization: Bearer ...). Null hasta que el personal la
    // genera; sin clave, el webhook rechaza todo y queda solo el polling.
    @Column(length = 64)
    private String claveWebhook;

    public Transportista() {}

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public TipoIntegracion getTipoIntegracion() { return tipoIntegracion; }
    public void setTipoIntegracion(TipoIntegracion tipoIntegracion) { this.tipoIntegracion = tipoIntegracion; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
    public String getClaveWebhook() { return claveWebhook; }
    public void setClaveWebhook(String claveWebhook) { this.claveWebhook = claveWebhook; }
}
