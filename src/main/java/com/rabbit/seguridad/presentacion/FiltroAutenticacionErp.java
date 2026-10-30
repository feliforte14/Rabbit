package com.rabbit.seguridad.presentacion;

/**
 * Autenticación HTTP Basic de la API del ERP, con freno contra la fuerza
 * bruta (criterio de seguridad: con demasiados intentos fallidos, 429).
 *
 * Antes la hacía el contenedor con un auth-constraint de web.xml, pero así
 * los intentos fallidos se responden con 401 antes de que corra cualquier
 * código de Rabbit: no había dónde contarlos. Ahora este filtro lee las
 * credenciales, consulta LimiteDeIntentos (el mismo del login web: es la
 * misma contraseña) y recién entonces autentica contra el ApplicationRealm
 * con request.login.
 *
 *   sin credenciales o credenciales mal formadas  401 + WWW-Authenticate
 *   usuario o contraseña incorrectos              401 (y cuenta un fallo)
 *   usuario bloqueado                             429 + Retry-After, sin consultar al realm
 *
 * El 403 (usuario válido sin rol ERP) lo sigue dando @RolesAllowed("ERP") en
 * PedidosExternosResource, que no depende de este filtro: si el filtro no
 * corriera, el EJB igual rechaza al que no tiene rol.
 *
 * La identidad dura solo este pedido (request.logout al terminar): un
 * cliente de API no necesita sesión.
 */

import com.rabbit.infraestructura.Problema;
import jakarta.inject.Inject;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.logging.Logger;

@WebFilter(urlPatterns = {"/api/v1/pedidos-externos", "/api/v1/pedidos-externos/*"})
public class FiltroAutenticacionErp implements Filter {

    private static final Logger LOG = Logger.getLogger(FiltroAutenticacionErp.class.getName());

    static final String DESAFIO = "Basic realm=\"ApplicationRealm\", charset=\"UTF-8\"";
    static final int DEMASIADOS_INTENTOS = 429;

    @Inject
    private LimiteDeIntentos limite;

    record Credenciales(String usuario, String clave) {}

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) resp;
        if (request.getUserPrincipal() != null) {
            chain.doFilter(request, response);
            return;
        }
        Credenciales credenciales = leer(request.getHeader("Authorization"));
        if (credenciales == null) {
            noAutenticado(response, "Mandá usuario y contraseña del ERP por HTTP Basic");
            return;
        }
        Duration espera = limite.esperaPara(credenciales.usuario());
        if (!espera.isZero()) {
            long segundos = Math.max(1, espera.toSeconds() + (espera.toNanosPart() > 0 ? 1 : 0));
            response.setHeader("Retry-After", String.valueOf(segundos));
            escribir(response, Problema.de(DEMASIADOS_INTENTOS, "demasiados-intentos", "Demasiados intentos",
                    "Demasiados intentos fallidos con este usuario. Probá de nuevo en " + segundos + " segundos."));
            return;
        }
        try {
            request.login(credenciales.usuario(), credenciales.clave());
        } catch (ServletException e) {
            limite.registrarFallo(credenciales.usuario());
            LOG.warning("[Seguridad] Credenciales incorrectas en la API del ERP desde " + request.getRemoteAddr());
            noAutenticado(response, "Usuario o contraseña incorrectos");
            return;
        }
        limite.registrarExito(credenciales.usuario());
        try {
            chain.doFilter(request, response);
        } finally {
            request.logout();
        }
    }

    /** @return las credenciales del header Basic, o null si falta o está mal formado. */
    static Credenciales leer(String header) {
        if (header == null || !header.regionMatches(true, 0, "Basic ", 0, 6)) {
            return null;
        }
        String decodificado;
        try {
            decodificado = new String(Base64.getDecoder().decode(header.substring(6).trim()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
        int separador = decodificado.indexOf(':');
        if (separador <= 0) {
            return null;
        }
        return new Credenciales(decodificado.substring(0, separador), decodificado.substring(separador + 1));
    }

    private static void noAutenticado(HttpServletResponse response, String detalle) throws IOException {
        response.setHeader("WWW-Authenticate", DESAFIO);
        escribir(response, Problema.de(HttpServletResponse.SC_UNAUTHORIZED, "no-autenticado", "No autenticado", detalle));
    }

    private static void escribir(HttpServletResponse response, Problema problema) throws IOException {
        response.setStatus(problema.status());
        response.setContentType(Problema.MEDIA_TYPE + ";charset=UTF-8");
        response.getWriter().write(problema.cuerpo());
    }
}
