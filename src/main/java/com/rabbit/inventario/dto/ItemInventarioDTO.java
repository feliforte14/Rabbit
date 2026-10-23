package com.rabbit.inventario.dto;

/**
 * DTO de salida: representa un ítem de stock tal como se muestra en la
 * vista JSF. Nunca se persiste en la BD (ver ItemInventario para la entidad).
 */

import java.math.BigDecimal;

import com.rabbit.inventario.datos.model.CategoriaDeTamano;
import com.rabbit.inventario.datos.model.ItemInventario;

public class ItemInventarioDTO {

    public Long id;
    public String producto;
    public int cantidadDisponible;
    public int cantidadReservada;
    public int cantidadLibre;
    public Long idDeposito;
    public Long idComercio;
    public BigDecimal pesoKg;
    // Medidas de una unidad (ADR-059) y la categoría de tamaño que sale de ellas y del peso.
    public BigDecimal altoCm;
    public BigDecimal anchoCm;
    public BigDecimal largoCm;
    public String categoria;
    public String categoriaEtiqueta;
    public boolean sinMedidas;

    // Convierte una entidad ItemInventario en un DTO listo para la vista,
    // calculando cantidadLibre = disponible - reservada de una vez.
    public static ItemInventarioDTO desde(ItemInventario i) {
        ItemInventarioDTO dto = new ItemInventarioDTO();
        dto.id = i.getId();
        dto.producto = i.getProducto();
        dto.cantidadDisponible = i.getCantidadDisponible();
        dto.cantidadReservada = i.getCantidadReservada();
        dto.cantidadLibre = i.getCantidadDisponible() - i.getCantidadReservada();
        dto.idDeposito = i.getDeposito() != null ? i.getDeposito().getId() : null;
        dto.idComercio = i.getIdComercio();
        dto.pesoKg = i.getPesoKg();
        dto.altoCm = i.getAltoCm();
        dto.anchoCm = i.getAnchoCm();
        dto.largoCm = i.getLargoCm();
        CategoriaDeTamano categoria = i.getCategoria();
        dto.categoria = categoria.name();
        dto.categoriaEtiqueta = categoria.getEtiqueta();
        dto.sinMedidas = !CategoriaDeTamano.tieneMedidas(i.getAltoCm(), i.getAnchoCm(), i.getLargoCm());
        return dto;
    }

    // Getters JavaBean: los requiere Expression Language (JSF)
    public Long getId() { return id; }
    public String getProducto() { return producto; }
    public int getCantidadDisponible() { return cantidadDisponible; }
    public int getCantidadReservada() { return cantidadReservada; }
    public int getCantidadLibre() { return cantidadLibre; }
    public Long getIdDeposito() { return idDeposito; }
    public Long getIdComercio() { return idComercio; }
    public BigDecimal getPesoKg() { return pesoKg; }
    public BigDecimal getAltoCm() { return altoCm; }
    public BigDecimal getAnchoCm() { return anchoCm; }
    public BigDecimal getLargoCm() { return largoCm; }
    public String getCategoria() { return categoria; }
    public String getCategoriaEtiqueta() { return categoriaEtiqueta; }
    public boolean isSinMedidas() { return sinMedidas; }
    /** "25 × 10 × 7 cm", o null si faltan medidas. */
    public String getMedidasTexto() {
        return sinMedidas ? null : formato(altoCm) + " × " + formato(anchoCm) + " × " + formato(largoCm) + " cm";
    }
    private static String formato(BigDecimal n) { return n.stripTrailingZeros().toPlainString(); }
}
