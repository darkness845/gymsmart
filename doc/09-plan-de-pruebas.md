# Plan de pruebas — antes de Fase 6

Esta es la lista de todo lo que conviene probar a mano, en Desktop y en Android, antes de seguir con
más desarrollo. Nace de todo lo arreglado en Fases 2-5 (ver [08-backlog-mejoras.md](08-backlog-mejoras.md)
para el detalle técnico de cada hallazgo).

Cada ítem tiene:
- **Pasos**: la secuencia exacta de toques/pantallas para llegar al escenario, con nombres reales de
  botones y pantallas, para que no haya que adivinar dónde tocar.
- **Qué debería pasar (criterio tras la mejora)**: mi criterio, como quedó tras el arreglo — así sabes
  qué es "correcto" sin tener que releer el código, y qué es justo lo que fallaba antes.
- **Tu criterio**: línea en blanco para que anotes si coincide con lo que tú esperabas cuando construiste
  esa parte originalmente, o si ves algo que prefieres distinto. Este documento es editable — anota
  directamente aquí lo que encuentres, así queda guardado para la próxima sesión.

Marca cada ítem con ✅ (funciona como se espera), ⚠️ (funciona pero no como esperabas / hay un matiz) o
❌ (no funciona) a medida que pruebes.

---

## 0. Cómo probar en local con Tailscale (sin USB, andando por casa)

El archivo es **[composeApp/src/commonMain/kotlin/com/gymsmart/gymsmart/config/AppConfig.kt](../composeApp/src/commonMain/kotlin/com/gymsmart/gymsmart/config/AppConfig.kt)**,
la constante `BASE_URL` (línea 7). Hoy apunta a Render:

```kotlin
const val BASE_URL = "https://gymsmart-server.onrender.com"
```

Pasos para probar en local:

1. Arranca el servidor en tu máquina con `./gradlew :server:run` (lee el `.env` de la raíz automáticamente,
   no hace falta pasarle nada más; se queda escuchando en el puerto **8080** y verás sus logs en esa misma
   terminal — déjala abierta mientras pruebas).
2. En `AppConfig.kt`, cambia el valor de `BASE_URL` a `"http://<IP de Tailscale>:8080"`, donde esa IP es
   la de **la máquina donde arrancaste el servidor en el paso 1** (no la del móvil). La ves en la app de
   Tailscale o con `tailscale ip -4`.
3. Recompila y reinstala la app en el móvil (botón de Run en IntelliJ con el móvil conectado por USB una
   única vez para instalar, o `./gradlew :composeApp:installDebug`) y relanza Desktop
   (`./gradlew :composeApp:run`). Es un archivo `commonMain`, así que el mismo cambio vale para las dos
   plataformas sin tocar nada más.
4. A partir de aquí ya puedes desconectar el cable: el móvil habla con tu servidor por Tailscale igual
   que si estuviera en la misma red.
5. Cuando termines de probar, vuelve a dejar `BASE_URL` como estaba (la URL de Render) antes de olvidarte
   del tema, para no dejar la app apuntando a tu máquina por accidente la próxima vez que la abras fuera
   de casa.

Nota: CORS no afecta aquí — es una restricción de navegador, no de un cliente nativo Kotlin/Android, así
que da igual lo que diga la configuración de CORS del servidor para estas pruebas.

---

## 1. Arranque y sesión

- [ ] **Pasos**: cierra la app del todo. Apaga el servidor local (`Ctrl+C` en su terminal, o simplemente
  no lo arranques). Abre la app con una cuenta que ya tenía sesión iniciada de antes.
  **Qué debería pasar**: aparece una pantalla de carga con un círculo girando y, si tarda, un botón
  "Reintentar" — nunca una pantalla en blanco, y nunca te manda directamente al Login o al Onboarding
  como si no tuvieras cuenta. Antes, un fallo de red en este punto se interpretaba como "no tienes
  perfil" y te mandaba a rehacer el Onboarding, que sobrescribe tu perfil real al guardar (RED-1).
  **Tu criterio**: _______________

- [ ] **Pasos**: arranca el servidor local, abre la app, inicia sesión con email y contraseña normales.
  **Qué debería pasar**: tras pulsar el botón de login, pasas directo al Dashboard (o al Onboarding si
  esa cuenta nunca completó el perfil) — sin ninguna pantalla en blanco ni parpadeo raro entre medias.
  **Tu criterio**: _______________

- [ ] **Pasos**: con la app abierta y una sesión activa, ve a cualquier pantalla que no sea el Dashboard
  (por ejemplo Nutrición). En la terminal del servidor local, para el servidor con `Ctrl+C` y vuélvelo a
  arrancar con `./gradlew :server:run` (al reiniciar el proceso se pierden todas las sesiones en memoria,
  simulando que tu sesión caducó). Ahora, dentro de la app, navega a otra pantalla o pulsa algo que haga
  una llamada al servidor (p. ej. abre Perfil).
  **Qué debería pasar**: te manda directamente a la pantalla de Login con el aviso "Tu sesión caducó,
  inicia sesión de nuevo." en vez de dejarte en una pantalla con datos vacíos o que no carga, sin
  explicación (RED-3).
  **Tu criterio**: _______________

---

## 2. Autenticación

- [ ] **Pasos**: en la pantalla de Login, pulsa "Crear cuenta" y regístrate con un email nuevo. **No**
  introduzcas el código que te llegue por correo. Cierra esa pantalla y vuelve al Login; intenta iniciar
  sesión con ese mismo email y contraseña.
  **Qué debería pasar**: en vez de un error genérico sin salida, el Login te lleva al paso de introducir
  el código de verificación, con un botón para reenviarlo si no te llegó o lo perdiste (AUTH-1).
  **Tu criterio**: _______________

- [ ] **Pasos**: en el Login, pulsa "¿Olvidaste tu contraseña?", introduce tu email y pulsa enviar.
  **Qué debería pasar**: te confirma que se ha enviado un correo, y debería llegarte de verdad (vía
  Brevo) con un enlace/código para resetear la contraseña. **Ojo**: cada vez que entras en esta pantalla
  se dispara un email automáticamente, aunque no pulses nada más (EMAIL-1, sigue sin arreglar) — evita
  entrar y salir varias veces "para ver", porque gasta cuota de Brevo cada vez.
  **Tu criterio**: _______________

---

## 3. Perfil (Dashboard → icono de persona arriba a la derecha → "Mi perfil")

- [ ] **Pasos**: en la sección "Datos físicos y preferencias", borra el campo "Peso (kg)" y escribe
  `75,5` (con coma, como se escribiría en un teclado español). Pulsa "Guardar cambios" de esa sección.
  **Qué debería pasar**: aparece el error "Introduce un peso válido (30–300 kg)" justo encima del botón,
  y **no se guarda nada** — ni un 0 ni ningún otro valor por detrás (VAL-1, antes se enviaba 0 en
  silencio porque `"75,5"` no se podía convertir a número y se sustituía por 0 sin avisar).
  **Tu criterio**: _______________

- [ ] **Pasos**: en el mismo formulario, pon el peso en `9999` (fuera de rango) o la edad en `5` años, y
  pulsa "Guardar cambios".
  **Qué debería pasar**: error concreto para ese campo (p. ej. "Introduce un peso válido (30–300 kg)" o
  "Introduce una edad válida (14–100 años)"), y tampoco se guarda nada.
  **Tu criterio**: _______________

- [ ] **Pasos**: cambia el desplegable "Objetivo" a "Perder peso" pero deja "Ritmo semanal" sin
  seleccionar (en blanco), y pulsa "Guardar cambios".
  **Qué debería pasar**: aparece "⚠️ Selecciona un ritmo semanal" y no guarda — en concreto, no debería
  guardarse un ritmo de 0 (que equivale a "mantener peso") como si lo hubieras elegido tú.
  **Tu criterio**: _______________

- [ ] **Pasos**: en la sección de arriba, "Datos personales", cambia solo el nombre (o el teléfono) —
  sin tocar nada de la sección física — y pulsa el "Guardar cambios" **de esa sección** (la de datos
  personales, no la física). Antes de esto, asegúrate de que tu objetivo/ritmo físico ya estaban
  guardados correctamente de una prueba anterior.
  **Qué debería pasar**: se guarda el nombre sin problema. Si además cambiaste la fecha de nacimiento,
  la edad del perfil físico se actualiza sola por detrás — pero tu ritmo/objetivo de peso no deberían
  cambiar ni resetearse (VAL-2).
  **Tu criterio**: _______________

- [ ] **Pasos**: en "Datos físicos", borra el campo de peso (déjalo vacío o con algo inválido) **sin
  pulsar el "Guardar cambios" de esa sección**. Ahora ve arriba, a "Datos personales", cambia el nombre
  y pulsa "Guardar cambios" de esa sección.
  **Qué debería pasar**: el nombre se guarda igualmente. El perfil físico (peso/altura/ritmo) se queda
  tal y como estaba guardado antes — no se reescribe con un peso de 0 por detrás solo porque el campo
  físico estaba vacío en ese momento en pantalla.
  **Tu criterio**: _______________

---

## 4. Peso corporal y análisis por IA (Dashboard → tarjeta "⚖️ Peso")

- [ ] **Pasos**: pulsa el botón "Añadir peso". En el diálogo que se abre ("Añadir peso — [fecha de
  hoy]"), deja el campo "Peso (kg)" vacío, o escribe letras, y pulsa "Guardar".
  **Qué debería pasar**: al tocar el campo debería salir el teclado numérico directamente (antes no lo
  fijaba). Al pulsar "Guardar" con un valor inválido, el campo se marca en rojo con un mensaje de error
  debajo, y el diálogo se queda abierto — antes, con un valor inválido, el botón simplemente no hacía
  nada, sin ninguna pista de qué estaba mal.
  **Tu criterio**: _______________

- [ ] **Pasos**: con el servidor local **apagado** (para forzar que la petición falle de verdad), escribe
  un peso válido (p. ej. `80`) en ese mismo diálogo y pulsa "Guardar".
  **Qué debería pasar**: el diálogo se queda abierto, con el peso que escribiste todavía en el campo, y
  aparece un mensaje de error ("No se pudo guardar. Inténtalo de nuevo."). Antes, el diálogo se cerraba
  igualmente como si hubiera ido bien, aunque el guardado hubiera fallado de verdad.
  **Tu criterio**: _______________

- [ ] **Pasos**: con una cuenta que **no** sea premium, mira el botón de análisis por IA en esta misma
  pantalla.
  **Qué debería pasar**: el botón dice "🔒 Solo Premium" y no deja subir foto. Esto no ha cambiado en
  esta ronda, pero ahora además, aunque alguien se las ingeniara para llamar directamente al endpoint
  del servidor sin pasar por este botón, el servidor respondería con un error 403 y no llegaría a
  gastar ninguna llamada a OpenAI (SEC-1) — no hay una forma visible de comprobar esto desde la propia
  app, solo que el comportamiento del botón sigue siendo el correcto.
  **Tu criterio**: _______________

- [ ] **Pasos**: con una cuenta premium, pulsa "Subir foto", elige la misma foto de referencia, y pulsa
  "Analizar con IA". Espera el resultado, apúntalo, y repite el proceso completo una segunda vez con
  **la misma foto exacta**.
  **Qué debería pasar**: los dos resultados (porcentaje de grasa, peso estimado, etc.) deberían salir
  iguales o muy parecidos entre sí. Antes, la misma foto podía dar cifras bastante distintas de una vez
  a otra porque no había ningún control de consistencia en la llamada a la IA (IA-2).
  **Tu criterio**: _______________

- [ ] **Pasos**: repite el análisis con una foto de mala calidad, mal encuadrada, o donde no se vea bien
  el cuerpo.
  **Qué debería pasar**: sigue devolviendo un resultado en todos los campos (nunca se queda vacío ni da
  un error de parseo), pero el texto del resumen puede indicar que la estimación es poco fiable por la
  calidad de la foto, en vez de dar una cifra con total seguridad como si la foto fuera perfecta (IA-3).
  **Tu criterio**: _______________

---

## 5. Nutrición (Dashboard → tarjeta "🥗 Nutrición")

- [ ] **Pasos**: en cualquier comida (Desayuno/Almuerzo/Comida/Cena), pulsa el icono de escanear código
  de barras (el de la cámara/QR) y escanea el código de un producto que sepas que no está en Open Food
  Facts, o escribe a mano un código inventado si tu pantalla lo permite.
  **Qué debería pasar**: mensaje claro de que no se ha encontrado el producto — en ningún caso se añade
  solo como una comida de 0 kcal a tu diario (NUT-1).
  **Tu criterio**: _______________

- [ ] **Pasos**: añade cualquier comida nueva (buscándola con el icono "+" → escribe el nombre → elige el
  producto → ajusta la cantidad → "Añadir a [comida]"). Justo después, sin salir de la pantalla ni
  recargar nada, pulsa sobre esa misma entrada que acabas de añadir para editarla o borrarla.
  **Qué debería pasar**: funciona sin error — la entrada ya tiene un identificador real desde el momento
  en que se guardó, así que se puede editar/borrar de inmediato (NUT-2). Antes, una comida recién
  añadida no se podía tocar hasta recargar la pantalla.
  **Tu criterio**: _______________

- [ ] **Pasos**: apaga el servidor local justo antes de abrir la pantalla de Nutrición, y ábrela.
  **Qué debería pasar**: debería aparecer un aviso visible de que los objetivos que ves en pantalla no
  son los reales (porque no se pudieron cargar) — nunca deberías quedarte contando silenciosamente
  contra el valor por defecto (2000 kcal) pensando que es tu objetivo de verdad (NUT-3).
  **Tu criterio**: _______________

- [ ] **Pasos**: busca con el icono "+" un alimento de Open Food Facts que sepas que tiene la ficha
  incompleta (sin kcal ni macros rellenos — algunos productos muy nuevos o poco mantenidos en la base de
  datos son así).
  **Qué debería pasar**: ese producto no debería aparecer en la lista de resultados para elegir, o si
  aparece, no debería poder añadirse como si tuviera 0 kcal (NUT-4).
  **Tu criterio**: _______________

- [ ] **Pasos**: busca el mismo alimento dos veces seguidas (por ejemplo escribe "pollo", borra los
  resultados, y vuelve a escribir "pollo").
  **Qué debería pasar**: no debería notarse ninguna diferencia de comportamiento respecto a antes — la
  caché nueva (OFF-1) es interna, como mucho la segunda búsqueda puede sentirse algo más rápida. Lo
  importante es que los resultados sean correctos igual que siempre.
  **Tu criterio**: _______________

- [ ] **Pasos** (solo si tienes el modo "Pulsera" activado en tu perfil): abre Nutrición por la mañana,
  cuando todavía lleves pocos o cero pasos/calorías activas. Fíjate en el número junto al objetivo de
  kcal (el "+N kcal 🔥"). Deja la app abierta en esa pantalla (o cambia a otra app y vuelve a esta misma
  pantalla de Nutrición) durante 10-15 minutos mientras haces algo de actividad física real andando.
  **Qué debería pasar**: ese número debería subir solo, sin que tengas que salir de la pantalla y volver
  a entrar — se refresca cada 5 minutos mientras la pantalla está abierta (WEAR-3). Antes se quedaba
  congelado en el valor de cuando abriste la pantalla por primera vez, todo el día.
  **Tu criterio**: _______________

---

## 6. GPS (Dashboard → tarjeta "📍 GPS") — el bloque que más conviene probar a fondo

- [ ] **Pasos**: pulsa "Iniciar ruta", espera a que empiece a grabar (verás los primeros puntos/metros),
  y **bloquea el móvil con el botón de encendido**. Llévatelo contigo (en el bolsillo o en la mano) y
  camina o desplázate durante 2-3 minutos con la pantalla bloqueada. Luego desbloquéalo y vuelve a abrir
  la app.
  **Qué debería pasar**: mientras está bloqueado, debería aparecer una notificación fija en la barra de
  notificaciones que diga algo como "Grabando ruta GPS". Al volver a la app, la ruta debería haber
  seguido avanzando durante ese tiempo (más distancia, más puntos) — no debería estar congelada en el
  momento exacto en que bloqueaste el móvil. **Esta es la prueba más importante de todo este trabajo**:
  antes de este arreglo (GPS-1), Android cortaba la ubicación en segundo plano casi de inmediato.
  **Tu criterio**: _______________

- [ ] **Pasos**: mientras grabas una ruta, desliza hacia abajo la barra de notificaciones y mira la que
  dice "Grabando ruta GPS".
  **Qué debería pasar**: está ahí, pero es un texto fijo — no tiene botón de "Parar" ni muestra
  distancia/tiempo en vivo. Esto es una decisión tomada a propósito para esta ronda (ver GPS-1b en
  `08-backlog-mejoras.md` si en algún momento quieres pedir que se añada). Para parar la grabación hay
  que volver a abrir la app y pulsar "Parar ruta" ahí.
  **Tu criterio**: _______________

- [ ] **Pasos**: graba una ruta por una zona donde sepas que el GPS pierde precisión un momento (dentro
  de un edificio, un túnel corto, calles muy estrechas entre edificios altos) y luego recuperas buena
  señal.
  **Qué debería pasar**: tras ese hueco de señal, la ruta debería seguir sumando distancia con
  normalidad en cuanto la señal vuelve. Antes (GPS-2), un solo salto grande de posición dejaba la
  referencia "congelada" y toda la distancia posterior se perdía sin contar, aunque la señal hubiera
  vuelto a ser buena.
  **Tu criterio**: _______________

- [ ] **Pasos**: inicia una ruta, deja que grabe un par de minutos, y **sin pulsar "Parar ruta"**, cierra
  la app por completo desde el gestor de apps recientes de Android (deslizar la tarjeta de la app hacia
  arriba/fuera). Vuelve a abrir la app y entra de nuevo en la pantalla de GPS.
  **Qué debería pasar**: debería ofrecerte un diálogo para recuperar la ruta en curso, con los puntos
  que llevaba grabados hasta el último guardado automático (cada 10 puntos aproximadamente), con opción
  de "Seguir grabando" o "Descartar" (GPS-3). Antes, forzar el cierre de la app así perdía la ruta
  entera sin ninguna opción de recuperarla.
  **Tu criterio**: _______________

- [ ] **Pasos**: graba una ruta razonablemente larga (varios minutos, muchos puntos) y, al terminar,
  pulsa "Parar ruta" y guárdala.
  **Qué debería pasar**: el guardado debería sentirse rápido, sin una espera larga notable — por detrás
  se manda en pocas peticiones agrupadas en vez de una petición por cada punto de la ruta (GPS-4). Antes,
  una ruta larga podía tardar bastante en guardarse.
  **Tu criterio**: _______________

- [ ] **Pasos** (en Desktop): abre la pantalla de GPS e intenta iniciar una ruta.
  **Qué debería pasar**: no debería crashear la app ni quedarse congelada — Desktop no tiene GPS real,
  así que no vas a ver una ruta grabándose de verdad, pero tampoco debería romperse nada. Esto es una
  limitación conocida y esperada de esta plataforma, no algo que se haya tocado en esta fase.
  **Tu criterio**: _______________

---

## 7. Wearable / Health Connect

- [ ] **Pasos**: con la pulsera/reloj conectado a Health Connect y tu perfil en modo "Pulsera", abre a la
  vez la app de tu pulsera (o Google Fit) y GymSmart (Dashboard o Nutrición), en el mismo momento del
  día, y compara los pasos y las calorías activas que muestra cada una.
  **Qué debería pasar**: las cifras deberían ser razonablemente parecidas entre las dos apps. Antes
  (WEAR-1/WEAR-2), GymSmart podía contar de más (si varias fuentes escribían los mismos pasos en Health
  Connect, se sumaban todas sin deduplicar) o de menos (se cortaba en los primeros 1000 registros del
  día si habías hecho muchas lecturas).
  **Tu criterio**: _______________

- [ ] **Pasos**: abre el Dashboard por la mañana con pocos pasos todavía, y déjalo abierto (o sal a otra
  app y vuelve a esta pantalla del Dashboard) 15-20 minutos mientras caminas de verdad.
  **Qué debería pasar**: los pasos y las calorías activas que se muestran ahí deberían subir solos, sin
  que tengas que recargar la pantalla — mismo mecanismo de refresco que en Nutrición (WEAR-3).
  **Tu criterio**: _______________

---

## 8. Cosas transversales (más difíciles de notar, pero conviene vigilar)

- [ ] **Pasos**: usa la app con normalidad durante 15-20 minutos: entra y sal de varias pantallas
  (Dashboard, Nutrición, Perfil, Peso, GPS), con buena conexión a internet.
  **Qué debería pasar**: nada debería sentirse distinto a como lo recordabas antes de esta fase — el
  cambio a un único cliente HTTP para toda la app (HTTP-1) y el cambio de motor de red en Android
  (RED-2) son cambios internos que no deberían notarse si todo funciona bien. Lo único que merece la
  pena vigilar es que **ninguna pantalla tarde sospechosamente más** en cargar que antes.
  **Tu criterio**: _______________

- [ ] **Pasos**: con la app abierta y cargando algo (p. ej. justo al abrir Nutrición o Dashboard),
  activa el modo avión durante unos 10 segundos y luego desactívalo.
  **Qué debería pasar**: la petición debería acabar fallando con un mensaje de error claro, en vez de
  quedarse cargando el círculo de progreso para siempre. El límite máximo de espera es de 45 segundos
  (generoso a propósito por los arranques en frío de Render), pero no infinito.
  **Tu criterio**: _______________

---

## Qué NO hace falta probar todavía

- Nada relacionado con Render (despliegue pendiente, se prueba cuando toque).
- Stripe/pagos (no funcional por diseño, confirmado).
- iOS/Web (no compilan/funcionan todavía, Fase 6).
- Training (no es alcanzable desde el menú).

## Después de probar

Anota aquí mismo (o en `CONTINUAR.md` si prefieres) qué salió mal, y en la próxima sesión seguimos desde
ahí: arreglamos lo que haya fallado antes de tocar los cabos sueltos de Fase 2/3 o entrar en Fase 6.
