package com.rabbit.infraestructura;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

/**
 * Cabeceras de seguridad de cada respuesta de Rabbit (SEC-05, criterios de
 * testing, eje D). HSTS va aparte, en FiltroHsts, porque depende de que el
 * pedido llegue por HTTPS; estas van siempre.
 *
 * - Content-Security-Policy: de dónde puede cargar la página scripts,
 *   estilos, imágenes y conexiones (ver CSP).
 * - X-Content-Type-Options: nosniff: el navegador respeta el Content-Type y
 *   no "adivina" que un archivo subido es un script.
 * - X-Frame-Options: DENY (y frame-ancestors 'none' en la CSP, que lo
 *   reemplaza en navegadores nuevos): ninguna página se puede meter en un
 *   iframe ajeno, así no hay clickjacking.
 * - Permissions-Policy: solo la ubicación (repartidor) y la cámara (fotos
 *   del remito y de la visita fallida), y solo para Rabbit; el resto apagado.
 * - Referrer-Policy: a otro sitio (los tiles del mapa) va solo el origen, no
 *   la URL con el número de pedido.
 *
 * La cookie de sesión lleva SameSite=Lax desde el web.xml (cookie-config).
 */
@WebFilter(urlPatterns = "/*")
public class FiltroCabecerasDeSeguridad implements Filter {

    /**
     * 'unsafe-inline' en script-src y style-src es un desvío aceptado: JSF
     * (Mojarra) escribe los manejadores de f:ajax en el HTML (onclick="...")
     * y Faces no ofrece nonce para eso. Lo que sí corta la CSP: scripts de
     * cualquier otro origen, conexiones a otros sitios, plugins (object-src),
     * cambiar la base de las URL, mandar formularios afuera y los iframes.
     *
     * Orígenes externos: Leaflet desde cdnjs (con integrity) y los tiles de
     * OpenStreetMap, en el mapa del personal y en el seguimiento público.
     */
    static final String CSP = String.join("; ",
            "default-src 'self'",
            "script-src 'self' 'unsafe-inline' https://cdnjs.cloudflare.com",
            "style-src 'self' 'unsafe-inline' https://cdnjs.cloudflare.com",
            // data: y blob: para el QR de la etiqueta y la vista previa de las fotos.
            "img-src 'self' data: blob: https://cdnjs.cloudflare.com https://tile.openstreetmap.org"
                    + " https://a.tile.openstreetmap.org https://b.tile.openstreetmap.org"
                    + " https://c.tile.openstreetmap.org",
            "connect-src 'self'",
            "font-src 'self'",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'none'");

    static final String PERMISOS = "geolocation=(self), camera=(self), microphone=(), payment=(), usb=(), "
            + "accelerometer=(), gyroscope=(), magnetometer=()";

    static final Map<String, String> CABECERAS = Map.of(
            "Content-Security-Policy", CSP,
            "X-Content-Type-Options", "nosniff",
            "X-Frame-Options", "DENY",
            "Permissions-Policy", PERMISOS,
            "Referrer-Policy", "strict-origin-when-cross-origin");

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (response instanceof HttpServletResponse http) {
            CABECERAS.forEach(http::setHeader);
        }
        chain.doFilter(request, response);
    }
}
