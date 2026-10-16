# Propuesta: cobro por almacenamiento en el depósito

> **Estado: PROPUESTA, sin implementar.** La pidió el equipo (Trello,
> "Testing NO OK") el 16/10/2026. No se tocó el código de la cobranza: se
> implementa cuando el equipo confirme el modelo y los valores. Todos los
> rangos y precios de abajo son **ejemplos** para discutir; los tiene que
> definir el equipo.

## La idea

Al estilo de Mercado Libre Full: el comercio paga por el **lugar que ocupa
su mercadería en el depósito de Rabbit**, con un precio por unidad y por
día que depende de la **categoría de tamaño** de cada producto (pequeño,
mediano o grande). La categoría sale del peso y de las medidas (alto ×
ancho × largo).

## ¿Reemplaza al modelo actual o se suma?

**Recomendación: se suma.** El modelo actual (ADR-034: abono + cargo por
envío + cargo por kilo entregado) cobra el **reparto**. El almacenamiento
es **otro servicio**, con otro costo para Rabbit (metros de depósito, no
kilómetros):

| | Modelo actual (reparto) | Almacenamiento (propuesta) |
|---|---|---|
| Qué se cobra | Lo que se entregó en el mes | Lo que estuvo guardado, día por día |
| Lo genera | Un pedido entregado | Una unidad en el depósito |
| A quién le toca | Todo comercio con entregas | Solo con **stock consignado** (el de punto de picking no lo guarda Rabbit) |

Si se reemplazara, un comercio que trabaja solo con punto de picking no
pagaría nada por los repartos, y uno con mucho stock que rota rápido
pagaría poco por muchas entregas. Sumados, cada comercio paga lo que usa.

Para que la factura no suba de golpe, el equipo puede **bajar el abono**
(por ejemplo, de $15.000 a $8.000) cuando entre el almacenamiento. Los
tres valores ya se cambian sin tocar el código (`rabbit.tarifa.*`).

Alternativa si el equipo prefiere reemplazar: dejar solo **cargo por
envío + almacenamiento** (sin abono ni cargo por kilo, como Mercado Libre).
El cambio de código es el mismo; solo cambia la tarifa.

## Categorías de tamaño (ejemplo)

Un producto entra en la **categoría más grande** que le toque por
cualquiera de sus datos: un almohadón liviano pero voluminoso es
mediano aunque pese 300 g.

| Categoría | Peso por unidad | Lado mayor | Volumen (alto × ancho × largo) |
|---|---|---|---|
| Pequeño | hasta 1 kg | hasta 25 cm | hasta 3 litros (3.000 cm³) |
| Mediano | hasta 10 kg | hasta 60 cm | hasta 50 litros |
| Grande | hasta 50 kg | hasta 150 cm | hasta 500 litros |
| Fuera de rango | más | más | más: no se acepta en consignación, o se cotiza aparte |

Ejemplos: una yerba de 1 kg (25 × 10 × 7 cm, 1,75 L) es pequeña; un
microondas (8 kg, 50 × 40 × 30 cm, 60 L) es grande por volumen.

## Precios de ejemplo

Por **unidad y por día** guardada:

| Categoría | Precio de ejemplo |
|---|---|
| Pequeño | $10 |
| Mediano | $30 |
| Grande | $90 |

Ejemplo: 100 yerbas (pequeñas) durante todo un mes de 30 días = 100 × 30
× $10 = **$30.000** de almacenamiento, más lo que corresponda por los
envíos. Si se venden 50 a mitad de mes: 100 × 15 + 50 × 15 = 2.250
unidades-día = $22.500.

Ideas que el equipo puede sumar después, como Mercado Libre:
- **Días gratis** al ingresar el stock (por ejemplo, los primeros 7).
- **Recargo por stock antiguo** (más de 60 días sin moverse), para que no
  se use el depósito como guardamuebles.

## Qué cambia en el stock

- **Medidas por unidad** en cada ítem: `altoCm`, `anchoCm` y `largoCm`
  (`ItemInventario`), como hoy `pesoKg` (ADR-032): obligatorias en las
  altas nuevas, y "Corregir medidas" para lo ya cargado. Columnas que
  admiten NULL: las versiones anteriores siguen funcionando.
- **Categoría calculada**, no cargada a mano: una clase sin estado
  (`CategoriaDeTamano`) la deduce del peso y las medidas, con los rangos
  configurables por system property, igual que la tarifa.
- **Un ítem sin medidas** se cobra como **mediano** hasta que se carguen,
  así no sale gratis por un dato faltante (y el comercio tiene un motivo
  para completarlo). En pantalla se marca "medidas sin cargar".
- **Ocupación diaria:** hoy Rabbit solo sabe cuánto stock hay *ahora*. Para
  cobrar por día hace falta una foto por día: un timer de Inventario (por
  ejemplo, a las 23:55) guarda, por comercio y categoría, cuántas unidades
  había (tabla `ocupacion_diaria`: comercio, fecha, categoría, unidades).
  Si WildFly estuvo apagado un día, ese día se completa con la foto
  anterior (mismo criterio que la liquidación: completar lo que falte).
- Cuentan las unidades **disponibles y reservadas**: una reservada sigue
  ocupando lugar hasta que sale con el repartidor.

## Qué cambia en la liquidación

- `TarifaMensual` suma tres precios: `rabbit.tarifa.almacenamiento.pequeno`,
  `.mediano` y `.grande`.
- `Liquidacion` guarda las unidades-día de cada categoría y el
  `cargoAlmacenamiento`, con los importes del momento (como hoy: una
  liquidación ya hecha no cambia si después cambia la tarifa).
- Total = abono + envíos + kilos + **almacenamiento**.
- La vista previa del mes en curso (personal y comercio) muestra la nueva
  línea con lo acumulado hasta hoy.
- El cobro por el banco legado no cambia: es un importe más en el total.

## Impacto en el código actual

| Componente | Cambio | Riesgo |
|---|---|---|
| Inventario | 3 columnas en `ItemInventario`; `CategoriaDeTamano`; entidad y repositorio de `ocupacion_diaria`; timer diario; `IConsultaStock` suma una consulta de unidades-día por categoría | Bajo: todo nuevo o columnas opcionales |
| Inventario (vistas) | `items.xhtml` y `mi-stock.xhtml`: medidas en el alta, "Corregir medidas" y la categoría en la tabla | Bajo |
| Pagos | `TarifaMensual` (3 precios), `Liquidacion` (columnas nuevas), `LiquidacionService` (calcular el cargo) | Medio: es plata; tests de cálculo obligatorios |
| Pagos (vistas) | `liquidaciones.xhtml` y `mis-liquidaciones.xhtml`: una línea más | Bajo |
| Pedidos, Ruteo, Repartidores | Nada | — |
| API REST del ERP | Nada (el ERP no carga stock; lo carga Rabbit) | — |
| Docs | ADR nuevo, README (tarifa), pruebas | — |
| Tests | `CategoriaDeTamanoTest` (bordes de cada rango), `TarifaMensualTest` (almacenamiento), cálculo de unidades-día | — |

Estimación: un día de trabajo, más las pruebas de punta a punta. Se
puede hacer en dos entregas: primero las medidas y la categoría (sin
cobrar nada), después la ocupación diaria y el cargo.

## Para decidir antes de implementar

1. ¿Se suma (recomendado) o reemplaza al abono + envío + kilo? ¿Se baja
   el abono?
2. Rangos de cada categoría (peso, lado mayor y volumen) y qué hacer con
   lo que queda fuera de rango.
3. Precio por unidad y por día de cada categoría.
4. ¿Días gratis al ingresar? ¿Recargo por stock antiguo?
5. ¿Desde qué mes se cobra? Recomendación: desde el mes siguiente a
   cargar las medidas, para no cobrar un mes con datos a medias.
