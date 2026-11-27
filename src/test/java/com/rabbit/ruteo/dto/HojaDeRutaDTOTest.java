package com.rabbit.ruteo.dto;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class HojaDeRutaDTOTest {

    // Las vistas (EL) leen las propiedades por su getter: un campo público sin getter rompe la pantalla del
    // repartidor recién al renderizarla, y ningún otro test lo ve.
    @Test
    void lasPropiedadesQueUsaLaVistaTienenGetter() throws Exception {
        for (String propiedad : new String[] {"instrucciones", "indicaciones", "autorizado"}) {
            PropertyDescriptor d = Arrays.stream(Introspector.getBeanInfo(HojaDeRutaDTO.class).getPropertyDescriptors())
                    .filter(p -> p.getName().equals(propiedad)).findFirst().orElse(null);
            assertNotNull(d, "falta la propiedad " + propiedad);
            assertNotNull(d.getReadMethod(), "falta el getter de " + propiedad);
        }
    }
}
