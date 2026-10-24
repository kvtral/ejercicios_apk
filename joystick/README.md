# Mando Xbox 🎮

App Android que convierte el móvil en un **mando inalámbrico para tu Xbox** a través
de la red. Se conecta a la consola como un cliente de **Remote Play** (el mismo
protocolo que usa xbox.com/play) y le envía los botones y sticks 60 veces por segundo.
Para la Xbox es un mando más: funciona en el menú y dentro de los juegos.

## Por qué Remote Play

La Xbox no acepta mandos genéricos ni por Bluetooth ni por la red: solo mandos con el
protocolo inalámbrico propio de Microsoft. El único camino solo por software es
Remote Play, que sí acepta mandos desde un cliente remoto. Los servidores de Xbox
solo intervienen para presentar el móvil a la consola. El mando (y el vídeo, si lo
activas) va directo por tu red local con WebRTC.

## Antes de empezar (en la Xbox)

1. **Ajustes › Dispositivos y conexiones › Funciones remotas** → activa
   *Habilitar funciones remotas*.
2. En **Modo de energía** elige *Inicio inmediato* o *Suspensión* si quieres
   encenderla desde el móvil.
3. Inicia sesión en la consola con la misma cuenta Microsoft que vayas a usar en la app.

## Uso

1. Abre la app y pulsa **Iniciar sesión con Microsoft**. Aparece un código: ábrelo en
   `microsoft.com/link` (el botón copia el código y abre el navegador) y confirma.
   La app nunca ve tu contraseña.
2. Elige tu consola y pulsa **Conectar**.
3. Juega con el mando táctil:
   - Sticks **flotantes**: el centro está donde apoyas el dedo, en la mitad
     izquierda o en la zona inferior derecha.
   - Cruceta, A/B/X/Y, LB/RB, LT/RT, View, Menu, botón Xbox y L3/R3.
   - Admite multitáctil: puedes mover el stick y pulsar botones a la vez.
4. Opciones en la barra superior:
   - 🎥 **Vídeo**: muestra la imagen de la consola detrás de los controles. Por
     defecto está apagado para ahorrar batería y red, porque lo normal es mirar la tele.
   - 📳 **Vibración**: el móvil vibra cuando el juego hace vibrar el mando.
   - ✖ **Desconectar**.

**Mando físico:** si conectas un mando Bluetooth o USB al móvil, la app también lo
reenvía a la Xbox. Así puedes usar en la consola mandos que no admite directamente.

## Limitaciones

- Necesita Internet para iniciar la sesión, además de la red local.
- La app envía un único mando (jugador 1 de la sesión de Remote Play).
- Necesita que el móvil decodifique vídeo H.264 por hardware, aunque no muestres
  el vídeo (lo tienen casi todos los móviles).
- Si tu cuenta no tiene perfil de Xbox o está en un país sin Xbox Live, el inicio de
  sesión falla con un mensaje explicativo.
- Usa APIs no documentadas de Microsoft, las mismas que el cliente web. Si Microsoft
  las cambia, puede dejar de funcionar.

## Estructura

| Ruta | Contenido |
| --- | --- |
| `auth/XboxAuth.kt` | Código de dispositivo de Microsoft → token Xbox Live → XSTS → token de streaming |
| `xhome/XHomeApi.kt` | Señalización de Remote Play: consolas, sesión, intercambio SDP/ICE, keepalive |
| `stream/StreamClient.kt` | WebRTC: canales `input`, `control`, `message` y `chat`, y bucle de envío del mando |
| `stream/InputPacket.kt` | Formato binario de los paquetes de mando y vibración |
| `stream/MessageProtocol.kt` | Handshake y mensajes JSON de los canales `message` y `control` |
| `stream/Teredo.kt` | Saca las IPv4 de los candidatos Teredo de la consola |
| `ui/` | Pantallas Compose: inicio de sesión, consolas y mando |

Protocolo portado de los clientes libres [XStreaming](https://github.com/Geocld/XStreaming)
y [xbox-xcloud-player](https://github.com/unknownskl/xbox-xcloud-player).

## Compilar

```bash
./gradlew :joystick:assembleDebug        # joystick/build/outputs/apk/debug/joystick-debug.apk
./gradlew :joystick:testDebugUnitTest    # tests de paquetes, Teredo y protocolo de mensajes
```
