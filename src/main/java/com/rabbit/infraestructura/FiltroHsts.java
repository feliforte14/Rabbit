package com.rabbit.infraestructura;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * HSTS (SEC-05): le dice al navegador que Rabbit solo se usa por HTTPS, así
 * que no vuelve a probar por HTTP aunque el usuario escriba "http://". Es el
 * complemento de {@code transport-guarantee CONFIDENTIAL}, que redirige un
 * pedido por HTTP *después* de que ya salió en claro: con HSTS ese primer
 * pedido en claro ni se hace.
 *
 * - Solo se manda si el pedido llegó por HTTPS: por HTTP el navegador debe
 *   ignorar la cabecera (RFC 6797), así que no tiene sentido mandarla.
 * - Con el certificado autofirmado de desarrollo el navegador también la
 *   ignora (HSTS exige un certificado válido), por lo que no traba el acceso
 *   local. En producción, con un certificado reconocido, empieza a valer.
 * - {@code -Drabbit.hsts.max.age=<segundos>}: por defecto 1 año; 0 la apaga.
 * - Sin {@code includeSubDomains} ni {@code preload}: Rabbit no controla otros
 *   subdominios del dominio donde se publique.
 */
@WebFilter(urlPatterns = "/*")
public class FiltroHsts implements Filter {

    static final String CABECERA = "Strict-Transport-Security";
    static final long UN_ANIO = 31_536_000L;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String valor = valorDeLaCabecera(request.isSecure(), maxAgeConfigurado());
        if (valor != null && response instanceof HttpServletResponse http) {
            http.setHeader(CABECERA, valor);
        }
        chain.doFilter(request, response);
    }

    /**
     * @return el valor de la cabecera, o null si no hay que mandarla (el pedido
     *         no es seguro, o la configuración la apagó con max-age 0 o negativo)
     */
    static String valorDeLaCabecera(boolean pedidoSeguro, long maxAgeSegundos) {
        if (!pedidoSeguro || maxAgeSegundos <= 0) {
            return null;
        }
        return "max-age=" + maxAgeSegundos;
    }

    private static long maxAgeConfigurado() {
        String configurado = System.getProperty("rabbit.hsts.max.age");
        if (configurado == null || configurado.isBlank()) {
            return UN_ANIO;
        }
        try {
            return Long.parseLong(configurado.trim());
        } catch (NumberFormatException e) {
            return UN_ANIO; // un valor mal escrito no debe apagar la protección
        }
    }
}
