package com.rabbit.integracion.legado;

/**
 * Detalle que JAXB serializa dentro de &lt;soap:Fault&gt;&lt;soap:Detail&gt;
 * cuando {@link PadronFiscalService#consultarCuit} lanza
 * {@link CuitInexistenteException} — ver clase 9, slide 20 (Detail:
 * información específica de la aplicación).
 */
public class CuitInexistenteFaultInfo {

    private String cuit;

    public CuitInexistenteFaultInfo() {}

    public CuitInexistenteFaultInfo(String cuit) {
        this.cuit = cuit;
    }

    public String getCuit() { return cuit; }
    public void setCuit(String cuit) { this.cuit = cuit; }
}
