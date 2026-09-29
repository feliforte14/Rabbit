package com.rabbit.ruteo.datos.model;

/**
 * Entidad JPA: una zona de reparto, definida por un rango de códigos
 * postales (por ejemplo 1400-1499). Dice quién reparte ahí: repartidores
 * propios o un transportista externo. El transportista va por ID (es de
 * otro componente).
 */

import jakarta.persistence.*;

@Entity
@Table(name = "zonas")
public class Zona {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    private int codigoPostalDesde;
    private int codigoPostalHasta;

    @Enumerated(EnumType.STRING)
    private CoberturaZona cobertura;

    // Con cobertura TRANSPORTISTA: a quién se derivan los pedidos. Con
    // cobertura PROPIA: respaldo opcional cuando no hay repartidores libres.
    private Long idTransportista;

    private boolean activa;

    public Zona() {}

    public boolean contiene(int codigoPostal) {
        return codigoPostal >= codigoPostalDesde && codigoPostal <= codigoPostalHasta;
    }

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public int getCodigoPostalDesde() { return codigoPostalDesde; }
    public void setCodigoPostalDesde(int codigoPostalDesde) { this.codigoPostalDesde = codigoPostalDesde; }
    public int getCodigoPostalHasta() { return codigoPostalHasta; }
    public void setCodigoPostalHasta(int codigoPostalHasta) { this.codigoPostalHasta = codigoPostalHasta; }
    public CoberturaZona getCobertura() { return cobertura; }
    public void setCobertura(CoberturaZona cobertura) { this.cobertura = cobertura; }
    public Long getIdTransportista() { return idTransportista; }
    public void setIdTransportista(Long idTransportista) { this.idTransportista = idTransportista; }
    public boolean isActiva() { return activa; }
    public void setActiva(boolean activa) { this.activa = activa; }
}
