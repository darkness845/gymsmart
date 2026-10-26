# Checklist: de "uso personal" a producción real (Play Store)

Este documento recoge, servicio por servicio, qué es aceptable hoy (app de uso personal, TFG) y qué
habría que cambiar **antes** de publicar la app para terceros o cobrar dinero real. No se actúa sobre
nada de esto salvo que el usuario lo pida explícitamente — es una checklist para el futuro.

## Resumen por servicio

| Servicio | Estado actual | Vale para uso personal | Qué falta para producción real |
|---|---|---|---|
| **Turso** (BBDD) | Cloud, en uso real | Sí | Revisar plan/límites de lectura-escritura bajo carga real de varios usuarios; backups automáticos; plan de rotación de `TURSO_AUTH_TOKEN`. |
| **Render** (servidor) | Plan gratuito | Sí (con "cold starts") | Plan de pago para evitar cold start/sleep; dominio propio + HTTPS gestionado; escalado/monitorización; logs centralizados sin datos sensibles. |
| **Brevo / Resend** (email) | Plan gratuito, límite diario de envíos | Sí | Plan de pago si el volumen de usuarios supera el límite diario; dominio verificado (SPF/DKIM/DMARC) para que los emails no caigan en spam; plantillas revisadas (recuperación de contraseña, verificación). |
| **Stripe** | Integrado pero **no funcional** (no cobra) | Sí, no afecta a nadie | Activar cuenta real, modo live (no test keys), webhooks de confirmación de pago, manejo de reembolsos/cancelaciones, cumplimiento fiscal (IVA, facturación) según país de venta. |
| **OpenAI** (IA) | Créditos personales limitados, uso puntual | Sí | Presupuesto/plan de pago acorde al uso esperado; límites de rate/cost por usuario para evitar abuso; posible fallback si se agotan créditos. |
| **Secretos/.env** | Acceso directo del creador, sin gestor de secretos | Sí | Mover a un gestor de secretos (Render env vars ya es un paso; considerar Doppler/1Password/Vault si crece el equipo); rotación periódica de claves. |

## Cosas que SIEMPRE hay que resolver antes de salir a producción (no dependen del plan gratuito)

- **Política de privacidad y términos de uso** publicados — obligatorio en Play Store, y más al tratar
  datos de salud (peso, comidas, rutas GPS, pulsera).
- **Consentimiento explícito** para recolectar datos de salud/ubicación (GDPR si hay usuarios en la UE),
  y opción real de borrar la cuenta y sus datos.
- **Cuenta de Google Play Console** con la ficha de la app, clasificación de contenido, política de datos
  declarada (Data Safety section).
- **Firma de la app** (keystore de release) gestionada de forma segura, no en el repo.
- **Revisión de seguridad completa** (ver [03-seguridad.md](03-seguridad.md)) antes de exponer el
  servidor a usuarios que no sean el propio creador.
- **Soporte iOS real** si se quiere publicar también en App Store — hoy no compila/funciona (ver
  [01-arquitectura.md](01-arquitectura.md)).
- **Monitorización y alertas** (hoy probablemente no hay nada si Render/Turso fallan en mitad de la noche).
- **Plan de soporte al usuario** (canal de contacto, gestión de incidencias) una vez haya usuarios reales.

## Nota

Nada de esto es urgente mientras la app sea de uso personal. Este documento existe para que, el día que
se decida dar el salto, haya una lista clara en vez de descubrir los problemas sobre la marcha.
