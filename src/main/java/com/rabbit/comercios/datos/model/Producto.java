package com.rabbit.comercios.datos.model;

/**
 * Entidad JPA: cada instancia es una fila de la tabla "productos" — el
 * catálogo de un comercio (ver Sección 1.7 del documento técnico).
 *
 * DISEÑO PENSADO PARA ESCALAR A DISTINTOS RUBROS
 * Un comercio de indumentaria, uno de electrónica y uno de alimentos
 * conviven sin rediseñar esta tabla para cada vertical: los campos
 * comunes a cualquier rubro viven como columnas propias (nombre,
 * categoria, precio, unidadMedida), y lo específico de cada categoría
 * vive en "atributos", una columna JSONB nativa de PostgreSQL.
 *
 *   Indumentaria: atributos = { "talle": "M", "color": "azul" }
 *   Electrónica:  atributos = { "voltaje": 220, "garantiaMeses": 12 }
 *   Alimentos:    atributos = { "fechaVencimiento": "2027-01-01" }
 *
 * Sumar un rubro nuevo no requiere migración de esquema ni tabla
 * adicional: solo se define, del lado de la aplicación, qué claves va a
 * tener el JSONB para esa categoría.
 *
 * @JdbcTypeCode(SqlTypes.JSON): le dice a Hibernate que mapee el campo
 * Java (un Map, cualquier estructura serializable a JSON) contra una
 * columna jsonb de Postgres, serializando/deserializando automáticamente
 * — sin necesidad de manejar el JSON a mano ni de un tipo Hibernate
 * de terceros.
 *
 * IDENTIFICADORES EXTERNOS (ver Sección 1.6)
 * codigoExternoERP y codigoExternoPlataformaVentas resuelven el caso de
 * un comercio con 2 sistemas de origen (ERP administrativo + plataforma
 * de venta separada, sin integrar entre sí): un pedido que llega desde la
 * plataforma de venta referencia el producto con SU propio identificador,
 * que no necesariamente coincide con el código de ese mismo producto en
 * el ERP. Guardar ambos acá permite correlacionar: se completan una vez
 * al importar el catálogo desde el ERP, y los usa el adaptador de la
 * plataforma de venta para resolver a qué Producto de Rabbit corresponde
 * cada pedido entrante.
 *
 * idComercio es una referencia cross-módulo en el sentido amplio (el
 * catálogo es del comercio), pero vive en el mismo módulo Comercios —
 * no hace falta el patrón "Long plano" que se usa entre módulos
 * distintos (ver ReservaStock.idComercio): acá sí es una relación JPA
 * normal dentro del mismo componente.
 */

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.Map;

@Entity
@Table(name = "productos")
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comercio_id")
    private Comercio comercio;

    private String nombre;
    private String categoria;
    private BigDecimal precio;
    private String unidadMedida;

    // Identificadores externos para correlacionar el mismo producto entre
    // el ERP y una plataforma de ventas separada (ver Sección 1.6).
    private String codigoExternoERP;
    private String codigoExternoPlataformaVentas;

    // Atributos específicos del rubro, JSON nativo de PostgreSQL.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> atributos;

    public Producto() {}

    public Long getId() { return id; }
    public Comercio getComercio() { return comercio; }
    public void setComercio(Comercio comercio) { this.comercio = comercio; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }
    public BigDecimal getPrecio() { return precio; }
    public void setPrecio(BigDecimal precio) { this.precio = precio; }
    public String getUnidadMedida() { return unidadMedida; }
    public void setUnidadMedida(String unidadMedida) { this.unidadMedida = unidadMedida; }
    public String getCodigoExternoERP() { return codigoExternoERP; }
    public void setCodigoExternoERP(String codigoExternoERP) { this.codigoExternoERP = codigoExternoERP; }
    public String getCodigoExternoPlataformaVentas() { return codigoExternoPlataformaVentas; }
    public void setCodigoExternoPlataformaVentas(String codigoExternoPlataformaVentas) { this.codigoExternoPlataformaVentas = codigoExternoPlataformaVentas; }
    public Map<String, Object> getAtributos() { return atributos; }
    public void setAtributos(Map<String, Object> atributos) { this.atributos = atributos; }
}
