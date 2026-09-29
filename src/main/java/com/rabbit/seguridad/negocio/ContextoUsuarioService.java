package com.rabbit.seguridad.negocio;

/**
 * CAPA DE NEGOCIO — resuelve a quién representa el caller autenticado
 * (EJB @Stateless). El rol lo da el realm de WildFly; el comercio o el
 * repartidor asociado, la tabla "usuarios".
 *
 * Exige las dos cosas: el rol en el realm (isCallerInRole) y una cuenta
 * activa en la tabla con la asociación cargada. Un usuario creado a mano
 * con add-user.sh y rol COMERCIO, sin fila en la tabla, no representa a
 * ningún comercio y no ve nada.
 */

import com.rabbit.seguridad.datos.UsuarioRepository;
import com.rabbit.seguridad.datos.model.Rol;
import com.rabbit.seguridad.datos.model.Usuario;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.ejb.SessionContext;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;

@Stateless
@PermitAll
public class ContextoUsuarioService implements IContextoUsuario {

    @Inject
    private UsuarioRepository repository;

    @Resource
    private SessionContext contexto;

    @Override
    public Long idComercioActual() {
        Usuario usuario = usuarioConRol(Rol.COMERCIO);
        if (usuario == null || usuario.getIdComercio() == null) {
            throw new ValidacionException("Tu usuario no está asociado a ningún comercio");
        }
        return usuario.getIdComercio();
    }

    @Override
    public Long idRepartidorActual() {
        Usuario usuario = usuarioConRol(Rol.REPARTIDOR);
        if (usuario == null || usuario.getIdRepartidor() == null) {
            throw new ValidacionException("Tu usuario no está asociado a ningún repartidor");
        }
        return usuario.getIdRepartidor();
    }

    private Usuario usuarioConRol(Rol rol) {
        if (!contexto.isCallerInRole(rol.name())) {
            return null;
        }
        Usuario usuario = repository.buscarPorUsername(contexto.getCallerPrincipal().getName());
        return usuario != null && usuario.isActivo() && usuario.getRol() == rol ? usuario : null;
    }
}
