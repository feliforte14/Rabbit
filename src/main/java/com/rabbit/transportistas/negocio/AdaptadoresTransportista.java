package com.rabbit.transportistas.negocio;

/**
 * Elige el adaptador según con qué tecnología se integra el transportista.
 * Es el único lugar que conoce las implementaciones: el resto del
 * componente trabaja con IAdaptadorTransportista.
 */

import com.rabbit.integracion.transportistas.AdaptadorRestTransportista;
import com.rabbit.integracion.transportistas.AdaptadorSoapTransportista;
import com.rabbit.integracion.transportistas.IAdaptadorTransportista;
import com.rabbit.transportistas.datos.model.TipoIntegracion;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class AdaptadoresTransportista {

    @Inject
    private AdaptadorRestTransportista rest;

    @Inject
    private AdaptadorSoapTransportista soap;

    public IAdaptadorTransportista para(TipoIntegracion tipo) {
        return tipo == TipoIntegracion.SOAP_LEGADO ? soap : rest;
    }
}
