# Limpieza Inteligente

Limpieza Inteligente es una aplicación móvil para dispositivos Android diseñada para optimizar el almacenamiento del dispositivo, liberar espacio y ayudar a reducir la lentitud del sistema causada por archivos innecesarios.

## Requisitos previos

Antes de instalar o compilar la aplicación, asegúrate de contar con:

- Android 10 o superior
- Android Studio instalado
- Espacio suficiente en el dispositivo
- Permisos de acceso a archivos y almacenamiento
- Permiso de notificaciones, si aplica

## Instalación del proyecto

1. Clona o descarga el repositorio del proyecto.
2. Abre Android Studio.
3. Selecciona **Open** y abre la carpeta del proyecto.
4. Espera a que Android Studio sincronice las dependencias.
5. Conecta un dispositivo Android o configura un emulador.

## Generar APK

Para generar el archivo instalable APK:

1. En Android Studio, ve a **Build**.
2. Selecciona **Generate Signed App Bundle or APK**.
3. Elige **APK**.
4. Selecciona el archivo keystore correspondiente.
5. Ingresa las credenciales autorizadas del keystore.
6. Selecciona el tipo de compilación **Release**.
7. Finaliza el proceso.

Al terminar, Android Studio mostrará una notificación con acceso directo a la carpeta donde se generó el APK.

## Uso de la aplicación

### Limpieza automática

Dentro de la aplicación:

1. Toca **Cambiar fecha de antigüedad**.
2. Selecciona la fecha deseada.
3. Toca **Cambiar frecuencia de ejecución**.
4. Selecciona la frecuencia de limpieza.

### Limpieza instantánea

Dentro de la aplicación:

1. Toca **Ejecutar limpieza ahora**.

## Configuración de permisos

En el dispositivo Android:

1. Abre **Ajustes**.
2. Ve a **Aplicaciones**.
3. Selecciona **Limpieza Inteligente**.
4. Entra en **Permisos**.
5. Permite las notificaciones y el acceso a archivos, según sea necesario.

## Notas de seguridad

No compartas públicamente contraseñas, archivos keystore ni credenciales internas del área de sistemas.