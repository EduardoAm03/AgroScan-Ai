# AgroScan AI 🍅📱

AgroScan AI es una solución móvil avanzada diseñada para revolucionar el monitoreo agrícola mediante el uso de Inteligencia Artificial. Originalmente concebido como una plataforma web, el proyecto ha evolucionado hacia una aplicación móvil nativa optimizada para que agricultores y agrónomos puedan diagnosticar enfermedades en plantas de tomate en tiempo real directamente desde el campo.

Utilizando modelos de visión computacional y procesamiento de lenguaje natural de última generación, la aplicación analiza imágenes capturadas con la cámara del dispositivo para proporcionar diagnósticos precisos, planes de mitigación y recomendaciones de tratamiento inmediatos.

---

## ✨ Características Principales

* **Diagnóstico Basado en IA:** Integración directa con la API de Google Gemini para la clasificación de imágenes y análisis contextual de fitopatologías en tiempo real.
* **Interfaz Moderna y Fluida:** Construida enteramente con Jetpack Compose, garantizando una experiencia de usuario (UX) intuitiva, reactiva y adaptada a condiciones de campo.
* **Arquitectura Robusta (MVVM):** Separación clara de responsabilidades utilizando `ScanViewModel` para la gestión de estados y reactividad de la UI.
* **Capa de Datos Abstraída:** Implementación del patrón *Repository* (`AgroRepository`) que centraliza el flujo de datos entre la API local, el almacenamiento y la interfaz de usuario.
* **Diseño UI Adaptativo:** Temas personalizados dinámicos (`Theme.kt`, `Color.kt`, `Type.kt`) con soporte nativo para modo claro y oscuro.
* **Seguridad y Escalabilidad:** Aislamiento de credenciales sensibles mediante variables de entorno (`.env`) y optimización de código en producción con reglas ProGuard personalizadas.

---

## 🏗️ Arquitectura del Software

El proyecto sigue las directrices oficiales de desarrollo de Android, estructurado bajo una arquitectura **MVVM (Model-View-ViewModel)** limpia:

```text
AgroScan-Ai/
│
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/
│   │   │   │   ├── data/            # Capa de Datos (Modelos, Repositorios e interfaces de API)
│   │   │   │   │   ├── AgroRepository.kt
│   │   │   │   │   ├── GeminiApi.kt
│   │   │   │   │   └── Models.kt
│   │   │   │   ├── ui/              # Capa de Presentación (ViewModels y Vistas en Compose)
│   │   │   │   │   ├── ScanViewModel.kt
│   │   │   │   │   ├── Screens.kt
│   │   │   │   │   └── theme/        # Sistema de Diseño y Estilos
│   │   │   │   └── MainActivity.kt  # Punto de entrada de la aplicación
│   │   │   └── AndroidManifest.xml  # Permisos de cámara, internet y componentes
│   │   └── test/                    # Pruebas unitarias, integradas y Screenshot Testing
│   └── build.gradle.kts             # Dependencias específicas del módulo de la app
│
├── gradle/                          # Configuración de Gradle y Catálogo de Versiones (libs.versions.toml)
├── .env.example                     # Plantilla para variables de entorno de desarrollo
└── build.gradle.kts                 # Configuración de Gradle a nivel de proyecto
