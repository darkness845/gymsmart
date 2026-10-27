# Plan de pruebas — antes de Fase 6

Esta es la lista de todo lo que conviene probar a mano, en Desktop y en Android, antes de seguir con
más desarrollo. Nace de todo lo arreglado en Fases 2-5 (ver [08-backlog-mejoras.md](08-backlog-mejoras.md)
para el detalle técnico de cada hallazgo).

Cada ítem tiene:
- **Qué probar**: el paso o escenario concreto.
- **Qué debería pasar (criterio tras la mejora)**: mi criterio, como quedó tras el arreglo — así sabes
  qué es "correcto" sin tener que releer el código.
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

Para probar en local:

1. Arranca el servidor en tu máquina: `./gradlew :server:run` (lee el `.env` de la raíz solo, no hace
   falta nada más; escucha en el puerto **8080**).
2. Cambia `BASE_URL` a `http://<tu IP de Tailscale>:8080` (la IP de Tailscale de **la máquina donde
   corre el servidor**, no la del móvil).
3. Recompila y reinstala la app en el móvil (`./gradlew :composeApp:installDebug` o desde IntelliJ) y
   relanza Desktop (`./gradlew :composeApp:run`) — este archivo es `commonMain`, así que el mismo cambio
   vale para los dos.
4. Cuando termines de probar, no olvides devolver `BASE_URL` a la URL de Render (o a `localhost:8080`
   para Desktop sin pulsera) antes de dar por buena la sesión de pruebas, si no quieres dejarlo apuntando
   a tu máquina por accidente.

Nota: CORS no afecta aquí — es una restricción de navegador, no de un cliente nativo Kotlin/Android, así
que da igual lo que diga la configuración de CORS del servidor para estas pruebas.

---

## 1. Arranque y sesión

- [ ] **Qué probar**: abrir la app con sesión ya iniciada, con el servidor local **apagado** (simula un
  cold start de Render que tarda o falla).
  **Qué debería pasar**: pantalla de carga con un botón "Reintentar", nunca una pantalla en blanco ni te
  manda al Login o al Onboarding por error (antes, un fallo de red podía interpretarse como "no tienes
  perfil" y mandarte a rehacer el onboarding, sobrescribiendo tu perfil real — RED-1).
  **Tu criterio**: _______________

- [ ] **Qué probar**: login normal, con el servidor local arrancado.
  **Qué debería pasar**: te manda al Dashboard (o al Onboarding si es una cuenta sin perfil aún) sin
  pantallas en blanco intermedias.
  **Tu criterio**: _______________

- [ ] **Qué probar**: provocar que una llamada autenticada reciba 401 a mitad de uso (la forma más fácil:
  reinicia el servidor local mientras tienes la app abierta y navegas a otra pantalla — las sesiones en
  memoria se pierden al reiniciar el proceso).
  **Qué debería pasar**: te manda solo al Login con el aviso "Tu sesión caducó, inicia sesión de nuevo."
  en vez de dejar alguna pantalla con datos vacíos sin explicación (RED-3, cerrado en bloque 4).
  **Tu criterio**: _______________

---

## 2. Autenticación

- [ ] **Qué probar**: registrar una cuenta nueva, no verificar el email todavía, intentar iniciar sesión.
  **Qué debería pasar**: login te lleva al paso de "introducir código de verificación", con opción de
  reenviarlo — no un callejón sin salida (AUTH-1).
  **Tu criterio**: _______________

- [ ] **Qué probar**: "¿Olvidaste tu contraseña?" desde el login.
  **Qué debería pasar**: te pide el email, envía el correo (vía Brevo — comprueba que llega). Ojo: cada
  vez que entras en esta pantalla se manda un email automáticamente (EMAIL-1, sigue sin arreglar) — no
  entres "por curiosidad" varias veces si quieres ahorrar cuota de Brevo.
  **Tu criterio**: _______________

---

## 3. Perfil (datos físicos y personales)

- [ ] **Qué probar**: en "Datos físicos", escribe el peso con coma decimal (`75,5`) y guarda.
  **Qué debería pasar**: error visible "Introduce un peso válido (30–300 kg)" — **no** se guarda un 0 en
  silencio (VAL-1, arreglado).
  **Tu criterio**: _______________

- [ ] **Qué probar**: pon un peso absurdo (p. ej. `9999`) o una edad fuera de rango (`5` años) y guarda.
  **Qué debería pasar**: error concreto para ese campo, nada se envía al servidor.
  **Tu criterio**: _______________

- [ ] **Qué probar**: cambia el objetivo a "Perder peso" pero no selecciones un ritmo, intenta guardar.
  **Qué debería pasar**: error "Selecciona un ritmo semanal", no guarda con ritmo 0 (mantenimiento) por
  error.
  **Tu criterio**: _______________

- [ ] **Qué probar**: cambia solo el nombre/teléfono en "Datos personales" (sin tocar peso/altura/ritmo)
  y guarda, con el objetivo en "perder peso" y el ritmo ya elegido correctamente de antes.
  **Qué debería pasar**: guarda el nombre y de paso recalcula la edad si cambiaste la fecha de
  nacimiento, sin romper ni resetear tu ritmo/objetivo guardado (VAL-2).
  **Tu criterio**: _______________

- [ ] **Qué probar**: lo mismo que arriba, pero con el peso del campo físico vacío o inválido en ese
  momento (p. ej. lo borraste sin guardar).
  **Qué debería pasar**: guarda igualmente el nombre/teléfono; **no** reguarda el perfil físico con un
  peso de 0 por detrás — simplemente no toca esos datos hasta que los arregles y pulses el guardado
  físico tú mismo.
  **Tu criterio**: _______________

---

## 4. Peso corporal y análisis por IA

- [ ] **Qué probar**: en la pantalla de Peso, pulsa "Añadir peso" y deja el campo vacío o escribe texto.
  **Qué debería pasar**: el campo marca error (`isError`) con el mensaje debajo, el teclado numérico
  aparece directamente (antes no fijaba `keyboardType`), y no pasa nada raro al pulsar "Guardar" (antes
  simplemente no hacía nada, sin explicación).
  **Tu criterio**: _______________

- [ ] **Qué probar**: añade un peso válido con el servidor **apagado** (para forzar un fallo real).
  **Qué debería pasar**: el diálogo se queda abierto con un error visible y el peso que escribiste sigue
  ahí — **no** se cierra como si hubiera guardado (arreglado de rebote junto con VAL-1).
  **Tu criterio**: _______________

- [ ] **Qué probar**: análisis corporal por IA, con una cuenta **no premium**.
  **Qué debería pasar**: el botón ya dice "🔒 Solo Premium" (control de cliente, sin cambios), y si de
  alguna forma se llega a llamar al endpoint, el servidor ahora responde 403 igualmente (SEC-1) — no
  debería colarse una llamada real a OpenAI.
  **Tu criterio**: _______________

- [ ] **Qué probar**: análisis corporal por IA con una cuenta premium, misma foto, dos veces.
  **Qué debería pasar**: los porcentajes deberían salir iguales o muy parecidos entre los dos análisis
  (antes podían variar bastante — IA-2). Si la foto no permite estimar bien algo, el campo `summary`
  puede decirlo explícitamente en vez de inventar una cifra con total seguridad.
  **Tu criterio**: _______________

- [ ] **Qué probar**: análisis con una foto de mala calidad/mal encuadrada.
  **Qué debería pasar**: sigue devolviendo una estimación en todos los campos (nunca vacío), pero el
  resumen puede avisar de que la estimación es poco fiable. No debería crashear ni mostrar un error de
  parseo (IA-3).
  **Tu criterio**: _______________

---

## 5. Nutrición

- [ ] **Qué probar**: escanear o buscar un código de barras que no existe en Open Food Facts.
  **Qué debería pasar**: mensaje claro de "no encontrado", **no** se guarda como comida de 0 kcal
  (NUT-1).
  **Tu criterio**: _______________

- [ ] **Qué probar**: añadir una comida nueva y, justo después (sin recargar la pantalla), intentar
  editarla o borrarla.
  **Qué debería pasar**: funciona — ya tiene un id real desde el momento en que se guarda (NUT-2).
  **Tu criterio**: _______________

- [ ] **Qué probar**: abrir Nutrición con el servidor fallando al cargar el perfil (apágalo justo antes
  de abrir esta pantalla).
  **Qué debería pasar**: aviso visible de que los objetivos mostrados no son los reales — nunca cuentas
  silenciosamente contra 2000 kcal fijas pensando que es tu objetivo real (NUT-3).
  **Tu criterio**: _______________

- [ ] **Qué probar**: buscar un alimento sin datos nutricionales útiles (raro, pero existen en OFF).
  **Qué debería pasar**: se filtra de los resultados, no se puede añadir como 0 kcal (NUT-4).
  **Tu criterio**: _______________

- [ ] **Qué probar**: buscar el mismo alimento dos veces seguidas (p. ej. "pollo").
  **Qué debería pasar**: funcionalmente igual que antes — la caché (OFF-1) es invisible para ti, solo
  debería notarse en que la segunda búsqueda es instantánea. No debería haber diferencias de
  comportamiento.
  **Tu criterio**: _______________

- [ ] **Qué probar** (solo si tienes modo pulsera activado): abre Nutrición por la mañana con pocas o
  cero calorías activas, déjala abierta (o la app en segundo plano y vuelve a ella) 10-15 minutos
  mientras haces algo de actividad física real.
  **Qué debería pasar**: el "+N kcal 🔥" junto al objetivo debería subir solo, sin tener que cerrar y
  reabrir la pantalla — se refresca cada 5 minutos mientras está abierta (WEAR-3).
  **Tu criterio**: _______________

---

## 6. GPS — el bloque que más conviene probar a fondo

- [ ] **Qué probar**: iniciar una ruta, **bloquear el móvil** (botón de encendido) y dejarlo bloqueado 2-3
  minutos mientras caminas o te desplazas con el móvil encima.
  **Qué debería pasar**: debería aparecer una notificación ("Grabando ruta GPS") mientras está
  bloqueado, y al desbloquear y volver a la app, la ruta debería haber seguido avanzando (distancia/
  puntos), no quedarse congelada en el momento de bloquear (GPS-1 — **el cambio más importante a
  verificar de todo este trabajo**).
  **Tu criterio**: _______________

- [ ] **Qué probar**: mientras grabas, fíjate en la notificación.
  **Qué debería pasar**: está ahí, es fija (sin botón de "Parar" ni distancia en vivo — decisión
  tomada a propósito, ver GPS-1b si quieres cambiarlo más adelante). Para parar la grabación hay que
  volver a la app.
  **Tu criterio**: _______________

- [ ] **Qué probar**: grabar una ruta en una zona con mala señal GPS (interior, calles estrechas) donde
  sepas que vas a tener algún hueco de señal.
  **Qué debería pasar**: un salto grande puntual ya no debería "matar" el resto de la grabación — antes
  de GPS-2, un solo hueco dejaba el resto de la ruta sin contar distancia.
  **Tu criterio**: _______________

- [ ] **Qué probar**: inicia una ruta, grábala un rato, y **fuerza el cierre de la app** desde el gestor
  de apps recientes de Android (sin pulsar "Parar ruta" antes).
  **Qué debería pasar**: al volver a abrir la pantalla de GPS, debería ofrecerte recuperar la ruta en
  curso (con los puntos que llevaba hasta el último guardado periódico) en vez de perderla entera
  (GPS-3).
  **Tu criterio**: _______________

- [ ] **Qué probar**: graba una ruta razonablemente larga (muchos puntos) y guárdala.
  **Qué debería pasar**: se guarda rápido (unas pocas peticiones por detrás, no una por punto — GPS-4).
  Antes podía tardar bastante con rutas largas.
  **Tu criterio**: _______________

- [ ] **Qué probar** (en Desktop): intenta usar la pantalla de GPS.
  **Qué debería pasar**: no debería crashear — Desktop no tiene GPS real, es una limitación conocida y
  esperada, no un bug de esta fase.
  **Tu criterio**: _______________

---

## 7. Wearable / Health Connect

- [ ] **Qué probar**: con la pulsera/Health Connect conectado, compara los pasos/kcal que muestra la app
  contra lo que muestra la app de la pulsera o Google Fit directamente, en el mismo momento.
  **Qué debería pasar**: deberían coincidir razonablemente — ya no debería contar de más (varias fuentes
  duplicando) ni de menos (truncar en los primeros 1000 registros del día) como antes de WEAR-1/WEAR-2.
  **Tu criterio**: _______________

- [ ] **Qué probar**: Dashboard, con la pantalla abierta un rato largo (15-20 min) mientras caminas.
  **Qué debería pasar**: los pasos/kcal deberían subir solos sin recargar la pantalla (mismo mecanismo
  que en Nutrición, WEAR-3).
  **Tu criterio**: _______________

---

## 8. Cosas transversales (más difíciles de notar, pero conviene vigilar)

- [ ] **Qué probar**: usa la app con normalidad un rato largo (15-20 min), varias pantallas, con buena
  conexión.
  **Qué debería pasar**: nada debería sentirse distinto a antes — HTTP-1 (cliente único) y RED-2
  (timeouts, cambio de motor de red en Android) son cambios internos que no deberían notarse si todo va
  bien. Lo que sí conviene vigilar es que **nada tarde sospechosamente más** que antes al cargar.
  **Tu criterio**: _______________

- [ ] **Qué probar**: con mala conexión (datos móviles débiles, o activa el modo avión 10 segundos y
  desactívalo justo cuando la app esté cargando algo).
  **Qué debería pasar**: debería fallar con un mensaje claro en vez de quedarse cargando para siempre —
  el timeout es de 45 segundos como máximo, generoso por los cold starts de Render, pero no infinito.
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
