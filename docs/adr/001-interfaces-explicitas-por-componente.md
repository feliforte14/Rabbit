# ADR 001 — Interfaces Java explícitas por componente

- **Estado:** Aceptada
- **Fecha:** 2026-09-05
- **Componente afectado:** ServicioDeComercios (`com.rabbit.comercios`)
- **Aplica como precedente a:** los ocho componentes restantes

## Contexto

`ServicioDeComercios` fue el primer componente implementado y quedó
funcionando de punta a punta en la Entrega Parcial N.º 1. Sin embargo, sus dos
interfaces de negocio —`IRegistroComercios` e `IConsultaComercios`— existían
únicamente como **comentarios** que separaban bloques de métodos dentro de la
clase `ComercioService`:

```java
@Stateless
public class ComercioService {

    // IRegistroComercios
    public Long registrarComercio(...) { ... }

    // IConsultaComercios
    public ComercioDTO obtenerComercio(...) { ... }
}
```

Esto genera dos problemas concretos:

1. **No cumple el checklist de la cátedra.** La Sección 6 de la consigna exige
   "mínimo 6 componentes de negocio identificados, cada uno con una interfaz
   explícita y documentada". Un comentario no es una interfaz.
2. **No hay forma de que un consumidor dependa de un contrato parcial.** Los
   componentes que van a consumir Comercios (Inventario, Pedidos,
   PagosYCobranzas, Notificaciones, UsuariosYSeguridad) solo necesitan
   *consultar* —típicamente preguntar si un comercio está habilitado para
   operar—, pero al inyectar la clase reciben también `darDeBajaComercio()` y
   `eliminarComercio()`.

El diagrama de clases del grupo ya modelaba estas dos interfaces como
elementos de primera clase, con los consumidores dependiendo de
`IConsultaComercios` por flechas punteadas. El código no reflejaba ese diseño.

## Decisión

Extraer las dos interfaces como **interfaces Java reales**, anotadas con
`@Local`, y hacer que `ComercioService` las implemente:

```java
@Stateless
public class ComercioService implements IRegistroComercios, IConsultaComercios
```

Los consumidores —incluida la capa de Presentación— inyectan la interfaz que
necesitan, nunca la clase:

```java
@Inject private IConsultaComercios consulta;   // solo lectura
@Inject private IRegistroComercios registro;   // escritura
```

Las operaciones asignadas a cada interfaz son exactamente las de la tabla del
documento de la Entrega Parcial N.º 1, sin agregados ni omisiones.

## Alternativas consideradas

### A. Dejarlo como estaba, documentando las interfaces solo en el diagrama

Descartada. Incumple el requisito explícito de la consigna, y deja el código
y el diagrama de arquitectura contradiciéndose: el diagrama muestra
dependencias hacia interfaces que en el código no existen. En la defensa oral
es indefendible.

### B. Una sola interfaz `IServicioDeComercios` con todas las operaciones

Descartada. Cumpliría la letra del requisito, pero pierde el beneficio real:
cualquier consumidor que necesite leer obtendría también la capacidad de
eliminar. La segregación en dos interfaces es lo que hace que el contrato
comunique la intención — es la aplicación concreta del principio de
segregación de interfaces.

### C. Agregar `@LocalBean` y conservar la inyección de la clase concreta

Esta alternativa apareció como consecuencia técnica de la decisión. En EJB,
cuando un bean de sesión implementa una o más interfaces, **deja de exponer la
no-interface view**: `@Inject ComercioService` pasa a fallar al desplegar.
`@LocalBean` restituye esa vista y evita tocar los Managed Beans.

Descartada. Habría dejado a `ComercioBean` y `SucursalBean` dependiendo de la
implementación concreta, que es justamente lo que la decisión busca evitar.
Se prefirió actualizar los dos Managed Beans para que inyecten las interfaces:
son cinco líneas y hacen que la capa de Presentación también dependa del
contrato.

## Consecuencias

**Positivas**

- El código refleja el diagrama de arquitectura del grupo.
- Un consumidor de solo lectura no puede modificar el padrón, ni por error:
  el método no existe en el tipo que tiene inyectado.
- La implementación puede cambiar sin tocar a los consumidores.
- Queda el precedente para los ocho componentes restantes.

**Negativas / costos**

- Un método nuevo hay que declararlo en dos lugares (interfaz e
  implementación).
- Obligó a modificar `ComercioBean` y `SucursalBean`, que no eran parte del
  cambio original.

**Neutras**

- Cero cambios en la lógica de negocio: solo se agregó `@Override` a los 13
  métodos del contrato.

## Verificación

Al desplegar sobre WildFly 41, el contenedor registra el EJB por sus dos
contratos en lugar de por la clase:

```
WFLYEJB0473: JNDI bindings for session bean named 'ComercioService':
    java:module/ComercioService!com.rabbit.comercios.negocio.IConsultaComercios
    java:module/ComercioService!com.rabbit.comercios.negocio.IRegistroComercios
```

Es evidencia directa de que las interfaces son las vistas de negocio reales
del componente y no una capa decorativa.
