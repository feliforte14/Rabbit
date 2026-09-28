package com.rabbit.infraestructura;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/**
 * Activa JAX-RS en la aplicación: todos los recursos REST quedan bajo
 * /Rabbit/api. Sin métodos: WildFly descubre solo las clases @Path del WAR.
 */
@ApplicationPath("api")
public class ApiRest extends Application {
}
