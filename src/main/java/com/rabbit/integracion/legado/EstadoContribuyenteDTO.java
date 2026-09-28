package com.rabbit.integracion.legado;

/**
 * DTO que viaja en la respuesta SOAP de {@link PadronFiscalService}. Cruza
 * la red como XML (JAXB lo serializa automáticamente a partir de estos
 * getters/setters), no es un DTO interno de Rabbit como los de dto/ de
 * cada componente — este vive del lado del contrato del servicio.
 */
public class EstadoContribuyenteDTO {

    private String cuit;
    private String razonSocial;
    private boolean habilitado;

    // JAXB exige constructor sin argumentos para deserializar la respuesta.
    public EstadoContribuyenteDTO() {}

    public EstadoContribuyenteDTO(String cuit, String razonSocial, boolean habilitado) {
        this.cuit = cuit;
        this.razonSocial = razonSocial;
        this.habilitado = habilitado;
    }

    public String getCuit() { return cuit; }
    public void setCuit(String cuit) { this.cuit = cuit; }
    public String getRazonSocial() { return razonSocial; }
    public void setRazonSocial(String razonSocial) { this.razonSocial = razonSocial; }
    public boolean isHabilitado() { return habilitado; }
    public void setHabilitado(boolean habilitado) { this.habilitado = habilitado; }
}
