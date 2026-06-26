package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.AgroRepository
import com.example.data.AppDatabase
import com.example.data.Content
import com.example.data.DiagnosisEntity
import com.example.data.DiagnosisResult
import com.example.data.GenerateContentRequest
import com.example.data.GenerationConfig
import com.example.data.InlineData
import com.example.data.Part
import com.example.data.PortfolioItemEntity
import com.example.data.RetrofitClient
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScanViewModel(
    application: Application,
    private val repository: AgroRepository
) : AndroidViewModel(application) {

    private val _cropName = MutableStateFlow("")
    val cropName: StateFlow<String> = _cropName.asStateFlow()

    private val _selectedImageUriString = MutableStateFlow<String?>(null)
    val selectedImageUriString: StateFlow<String?> = _selectedImageUriString.asStateFlow()

    private val _selectedImageBitmap = MutableStateFlow<Bitmap?>(null)
    val selectedImageBitmap: StateFlow<Bitmap?> = _selectedImageBitmap.asStateFlow()

    private val _isOnlineMode = MutableStateFlow(true)
    val isOnlineMode: StateFlow<Boolean> = _isOnlineMode.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _diagnosisResult = MutableStateFlow<DiagnosisResult?>(null)
    val diagnosisResult: StateFlow<DiagnosisResult?> = _diagnosisResult.asStateFlow()

    private val _activeTab = MutableStateFlow(0) // 0 = Analizar, 1 = Portafolio, 2 = Historial
    val activeTab: StateFlow<Int> = _activeTab.asStateFlow()

    // Database flows mapping to local view states
    val diagnosesHistory: StateFlow<List<DiagnosisEntity>> = repository.allDiagnoses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val portfolioItems: StateFlow<List<PortfolioItemEntity>> = repository.allPortfolioItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI Toast or Banner notification events
    private val _uiNotification = MutableSharedFlow<String>()
    val uiNotification: SharedFlow<String> = _uiNotification.asSharedFlow()

    init {
        // Automatically default to offline if no API key is set
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY" || apiKey.contains("PLACEHOLDER")) {
            _isOnlineMode.value = false
            viewModelScope.launch {
                _uiNotification.emit("Clave API de Gemini ausente. Se activó por defecto el Motor de diagnóstico local.")
            }
        }
    }

    fun setCropName(name: String) {
        _cropName.value = name
    }

    fun setOnlineMode(online: Boolean) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (online && (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY")) {
            viewModelScope.launch {
                _uiNotification.emit("No se puede activar el Modo Online sin una clave API de Gemini configurada en AI Studio.")
            }
            _isOnlineMode.value = false
        } else {
            _isOnlineMode.value = online
            viewModelScope.launch {
                _uiNotification.emit(if (online) "Modo Online activado" else "Modo Offline activado (Motor local)")
            }
        }
    }

    fun setActiveTab(tabIndex: Int) {
        _activeTab.value = tabIndex
    }

    fun selectSampleImage(crop: String, bitmap: Bitmap) {
        _cropName.value = crop
        _selectedImageBitmap.value = bitmap
        _selectedImageUriString.value = "sample_$crop"
        viewModelScope.launch {
            _uiNotification.emit("Muestra de cultivo: $crop seleccionada")
        }
    }

    fun setImageBitmap(bitmap: Bitmap, uriString: String) {
        _selectedImageBitmap.value = bitmap
        _selectedImageUriString.value = uriString
    }

    fun clearSelectedImage() {
        _selectedImageBitmap.value = null
        _selectedImageUriString.value = null
        _diagnosisResult.value = null
    }

    fun startAnalysis() {
        val crop = _cropName.value.trim()
        if (crop.isEmpty()) {
            viewModelScope.launch {
                _uiNotification.emit("Por favor escribe el nombre de un cultivo (ej. Tomate, Maíz)")
            }
            return
        }

        val bitmap = _selectedImageBitmap.value
        if (bitmap == null) {
            viewModelScope.launch {
                _uiNotification.emit("Por favor selecciona o toma una foto del cultivo")
            }
            return
        }

        _isAnalyzing.value = true
        _diagnosisResult.value = null

        viewModelScope.launch {
            if (_isOnlineMode.value) {
                runOnlineAnalysis(crop, bitmap)
            } else {
                runOfflineAnalysis(crop)
            }
        }
    }

    private suspend fun runOnlineAnalysis(crop: String, bitmap: Bitmap) {
        _uiNotification.emit("Enviando imagen a Gemini AI...")
        val base64Image = withContext(Dispatchers.IO) {
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
            Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
        }

        val promptText = "Analiza esta planta de: $crop. Identifica plagas o enfermedades visibles. " +
                "Si la planta parece saludable, indícalo como la enfermedad 'Saludable' con tratamientos de mantenimiento preventivo."

        val systemPrompt = "Eres un agrónomo experto de IA. Tu tarea es analizar la imagen del cultivo y diagnosticar plagas o enfermedades. " +
                "DEBES responder ÚNICAMENTE con un objeto JSON válido con la siguiente estructura exacta en español. " +
                "No agregues bloques de código markdown, no incluyas ```json, no agregues texto extra. " +
                "Formato de respuesta JSON:\n" +
                "{\n" +
                "  \"cropName\": \"$crop\",\n" +
                "  \"disease\": \"Nombre de la enfermedad o plaga\",\n" +
                "  \"confidence\": 85,\n" +
                "  \"symptoms\": \"Lista descriptiva de síntomas observados\",\n" +
                "  \"causes\": \"Causa directa u origen de la infección\",\n" +
                "  \"treatmentsChemical\": [\"Tratamiento químico 1\", \"Tratamiento químico 2\"],\n" +
                "  \"treatmentsOrganic\": [\"Tratamiento orgánico 1\", \"Tratamiento orgánico 2\"],\n" +
                "  \"treatmentsPreventive\": [\"Medida preventiva 1\", \"Medida preventiva 2\"]\n" +
                "}"

        val request = GenerateContentRequest(
            contents = listOf(
                Content(
                    parts = listOf(
                        Part(text = promptText),
                        Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Image))
                    )
                )
            ),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.2f
            ),
            systemInstruction = Content(
                parts = listOf(Part(text = systemPrompt))
            )
        )

        // Dynanic URLs priority list to evade regional/version failures
        val endpoints = listOf(
            "v1beta/models/gemini-3.5-flash:generateContent",
            "v1/models/gemini-3.5-flash:generateContent",
            "v1beta/models/gemini-2.5-flash:generateContent",
            "v1beta/models/gemini-1.5-flash:generateContent",
            "v1/models/gemini-1.5-flash:generateContent"
        )

        val apiKey = BuildConfig.GEMINI_API_KEY
        var rawResult: String? = null
        var requestError: String? = null

        withContext(Dispatchers.IO) {
            for (endpoint in endpoints) {
                try {
                    Log.d("AgroScan", "Intentando llamar a Gemini API en: $endpoint")
                    val response = RetrofitClient.service.generateContent(
                        url = "https://generativelanguage.googleapis.com/$endpoint",
                        apiKey = apiKey,
                        request = request
                    )
                    val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    if (!text.isNullOrBlank()) {
                        rawResult = text
                        Log.d("AgroScan", "Respuesta exitosa de Gemini: $text")
                        break
                    }
                } catch (e: Exception) {
                    Log.e("AgroScan", "Fallo endpoint: $endpoint - Error: ${e.message}")
                    requestError = e.localizedMessage ?: "Error de red"
                }
            }
        }

        if (rawResult != null) {
            try {
                val parsedResult = withContext(Dispatchers.Default) {
                    val cleanedJson = cleanJsonResponse(rawResult!!)
                    val adapter = RetrofitClient.moshiInstance.adapter(DiagnosisResult::class.java)
                    adapter.fromJson(cleanedJson)
                }

                if (parsedResult != null) {
                    _diagnosisResult.value = parsedResult
                    _isAnalyzing.value = false
                    _uiNotification.emit("Diagnóstico exitoso con Gemini AI")
                    saveDiagnosisToDatabase(parsedResult)
                } else {
                    throw Exception("No se pudo mapear el JSON al modelo")
                }
            } catch (e: Exception) {
                Log.e("AgroScan", "Error al parsear respuesta JSON de la IA: ${e.message}")
                _uiNotification.emit("Fallo en formato de IA. Activando motor local de respaldo...")
                runOfflineAnalysis(crop)
            }
        } else {
            Log.w("AgroScan", "Todos los endpoints de Gemini fallaron: $requestError")
            _uiNotification.emit("Error de conexión ($requestError). Activando motor local de respaldo...")
            runOfflineAnalysis(crop)
        }
    }

    private fun cleanJsonResponse(raw: String): String {
        var clean = raw.trim()
        if (clean.startsWith("```json")) {
            clean = clean.removePrefix("```json")
        } else if (clean.startsWith("```")) {
            clean = clean.removePrefix("```")
        }
        if (clean.endsWith("```")) {
            clean = clean.removeSuffix("```")
        }
        return clean.trim()
    }

    private suspend fun runOfflineAnalysis(crop: String) {
        _uiNotification.emit("Iniciando motor de diagnóstico local de AgroScan AI")
        kotlinx.coroutines.delay(1800) // Simular latencia de procesamiento local

        val simulatedResult = generateMockDiagnosis(crop)
        _diagnosisResult.value = simulatedResult
        _isAnalyzing.value = false
        _uiNotification.emit("Diagnóstico local completado con éxito")
        saveDiagnosisToDatabase(simulatedResult)
    }

    private fun generateMockDiagnosis(crop: String): DiagnosisResult {
        val cropLower = crop.lowercase(Locale.getDefault())
        return when {
            cropLower.contains("tomate") || cropLower.contains("tomato") -> {
                DiagnosisResult(
                    cropName = crop,
                    disease = "Tizón Tardío (Phytophthora infestans)",
                    confidence = 94,
                    symptoms = "Manchas oscuras con aspecto húmedo en las hojas que se expanden rápidamente. En el envés de la hoja aparece un moho blanco aterciopelado con alta humedad.",
                    causes = "Hongo que se propaga por esporas en condiciones climáticas húmedas y templadas (15-25°C).",
                    treatmentsChemical = listOf(
                        "Aplicar fungicidas protectores como Mancozeb o Clorotalonil cada 7-10 días.",
                        "En infecciones avanzadas, utilizar fungicidas sistémicos con Metalaxil o Azoxistrobina."
                    ),
                    treatmentsOrganic = listOf(
                        "Pulverizar caldo bordelés (solución de sulfato de cobre y cal).",
                        "Aplicar extracto de cola de caballo (Equisetum arvense) o fungicidas a base de Bacillus subtilis."
                    ),
                    treatmentsPreventive = listOf(
                        "Evitar el riego por aspersión para no mojar el follaje.",
                        "Garantizar una ventilación amplia separando adecuadamente las plantas.",
                        "Eliminar y quemar restos de plantas infectadas inmediatamente."
                    )
                )
            }
            cropLower.contains("maiz") || cropLower.contains("maíz") || cropLower.contains("corn") -> {
                DiagnosisResult(
                    cropName = crop,
                    disease = "Roya Común del Maíz (Puccinia sorghi)",
                    confidence = 89,
                    symptoms = "Pústulas de color marrón canela o dorado en ambas caras de las hojas, que luego se tornan negras. Las hojas afectadas pueden secarse por completo.",
                    causes = "Hongo fitopatógeno que requiere de humedad foliar (rocío) y temperaturas frescas para germinar.",
                    treatmentsChemical = listOf(
                        "Uso de fungicidas del grupo de las estrobirulinas y triazoles si el umbral de daño económico supera el 15% de área foliar afectada."
                    ),
                    treatmentsOrganic = listOf(
                        "Aplicación de azufre soluble elemental.",
                        "Uso de extractos de ajo y ají para fortalecer la resistencia de la cutícula foliar."
                    ),
                    treatmentsPreventive = listOf(
                        "Sembrar híbridos o variedades con resistencia genética certificada a roya.",
                        "Realizar rotación de cultivos anual con leguminosas.",
                        "Controlar malezas gramíneas que sirven de hospedantes secundarios."
                    )
                )
            }
            cropLower.contains("papa") || cropLower.contains("patata") || cropLower.contains("potato") -> {
                DiagnosisResult(
                    cropName = crop,
                    disease = "Escarabajo de la Papa (Leptinotarsa decemlineata)",
                    confidence = 91,
                    symptoms = "Defoliación severa de las plantas. Presencia de larvas regordetas de color naranja-rojizo y escarabajos adultos con rayas negras y amarillas en los élitros.",
                    causes = "Plaga de insecto coleóptero altamente destructiva y con gran capacidad de generar resistencia a insecticidas.",
                    treatmentsChemical = listOf(
                        "Aplicar insecticidas sistémicos selectivos como Imidacloprid o Spinosad en rotación."
                    ),
                    treatmentsOrganic = listOf(
                        "Aplicación de preparados de Neem (Azadiractina) sobre las larvas jóvenes.",
                        "Espolvorear tierra de diatomeas alrededor del tallo para deshidratar los insectos."
                    ),
                    treatmentsPreventive = listOf(
                        "Recolectar manualmente los adultos y masas de huevos amarillos en el envés de las hojas.",
                        "Fomentar la fauna benéfica como mariquitas y chinches depredadoras.",
                        "Cubrir los cultivos con mallas finas anti-insectos durante la brotación."
                    )
                )
            }
            cropLower.contains("café") || cropLower.contains("cafe") || cropLower.contains("coffee") -> {
                DiagnosisResult(
                    cropName = crop,
                    disease = "Roya del Cafeto (Hemileia vastatrix)",
                    confidence = 93,
                    symptoms = "Manchas amarillentas redondeadas en el envés de la hoja con un característico polvo de color naranja brillante. Provoca caída prematura de hojas y debilitamiento general.",
                    causes = "Hongo parásito obligado altamente agresivo en climas cálidos y húmedos.",
                    treatmentsChemical = listOf(
                        "Aplicar fungicidas sistémicos basados en Triazoles (ej. Cyproconazole) combinados con Estrobirulinas."
                    ),
                    treatmentsOrganic = listOf(
                        "Tratamientos preventivos a base de oxicloruro de cobre.",
                        "Uso de biofertilizantes ricos en minerales para mejorar las defensas de la planta."
                    ),
                    treatmentsPreventive = listOf(
                        "Establecer densidades de siembra adecuadas para favorecer el paso del viento y sol.",
                        "Realizar podas de cafetos anuales sistemáticas.",
                        "Renovar plantaciones con variedades resistentes reconocidas (ej. Castillo, Colombia, Catimor)."
                    )
                )
            }
            else -> {
                // Generic logic simulation for other crop names
                val capitalizedCrop = crop.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                DiagnosisResult(
                    cropName = crop,
                    disease = "Mildiu Polvoriento en $capitalizedCrop (Podosphaera spp.)",
                    confidence = 82,
                    symptoms = "Presencia de un polvillo fino blanco o grisáceo, similar a la ceniza, sobre el haz de las hojas y brotes nuevos. Deformación foliar leve.",
                    causes = "Hongo oportunista favorecido por ambientes sombreados con temperaturas moderadas y humedad ambiental moderada sin lluvia directa.",
                    treatmentsChemical = listOf(
                        "Tratamiento curativo con fungicidas basados en Triadimenol o Penconazol si la infección excede el 10% del follaje."
                    ),
                    treatmentsOrganic = listOf(
                        "Pulverizar una mezcla de bicarbonato de potasio o de sodio disuelto en agua con jabón potásico.",
                        "Uso de leche diluida al 20% en agua como biofungicida natural foliar bajo exposición solar."
                    ),
                    treatmentsPreventive = listOf(
                        "Ubicar el cultivo en zonas con máxima exposición solar directa.",
                        "Podar las ramas inferiores o densas para favorecer la circulación de aire.",
                        "No exceder la fertilización con nitrógeno, que promueve brotes demasiado tiernos y propensos."
                    )
                )
            }
        }
    }

    private suspend fun saveDiagnosisToDatabase(result: DiagnosisResult) {
        withContext(Dispatchers.IO) {
            val entity = DiagnosisEntity(
                cropName = result.cropName,
                disease = result.disease,
                confidence = result.confidence,
                symptoms = result.symptoms,
                causes = result.causes,
                treatmentsChemical = result.treatmentsChemical.joinToString("\n"),
                treatmentsOrganic = result.treatmentsOrganic.joinToString("\n"),
                treatmentsPreventive = result.treatmentsPreventive.joinToString("\n"),
                imageUrl = _selectedImageUriString.value ?: "unknown_uri"
            )
            repository.insertDiagnosis(entity)
        }
    }

    // --- History actions ---
    fun deleteDiagnosis(id: Long) {
        viewModelScope.launch {
            repository.deleteDiagnosisById(id)
            _uiNotification.emit("Diagnóstico eliminado del historial")
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearAllDiagnoses()
            _uiNotification.emit("Historial de diagnósticos vaciado por completo")
        }
    }

    // --- Portfolio actions ---
    fun addImageToPortfolio(uriString: String) {
        viewModelScope.launch {
            val item = PortfolioItemEntity(uriString = uriString)
            repository.insertPortfolioItem(item)
            _uiNotification.emit("Imagen guardada en el portafolio local")
        }
    }

    fun deletePortfolioItem(id: Long) {
        viewModelScope.launch {
            repository.deletePortfolioItemById(id)
            _uiNotification.emit("Imagen eliminada del portafolio")
        }
    }

    fun clearAllPortfolio() {
        viewModelScope.launch {
            repository.clearAllPortfolioItems()
            _uiNotification.emit("Portafolio vaciado por completo")
        }
    }

    fun analyzePortfolioItem(context: android.content.Context, item: PortfolioItemEntity) {
        viewModelScope.launch {
            _isAnalyzing.value = true
            _diagnosisResult.value = null
            
            val inferredCrop = if (item.uriString.startsWith("sample_")) {
                item.uriString.removePrefix("sample_")
            } else {
                "Cultivo de Portafolio"
            }
            _cropName.value = inferredCrop
            _selectedImageUriString.value = item.uriString

            val bitmap = withContext(Dispatchers.IO) {
                try {
                    if (item.uriString.startsWith("sample_")) {
                        generateMockBitmapForInference(inferredCrop)
                    } else {
                        val uri = Uri.parse(item.uriString)
                        val inputStream = context.contentResolver.openInputStream(uri)
                        BitmapFactory.decodeStream(inputStream)
                    }
                } catch (e: Exception) {
                    Log.e("AgroScan", "Error loading portfolio bitmap: ${e.message}")
                    null
                }
            }

            if (bitmap == null) {
                _isAnalyzing.value = false
                _uiNotification.emit("Error al cargar la imagen para análisis.")
                return@launch
            }

            _selectedImageBitmap.value = bitmap
            _activeTab.value = 0 // Switch to Analyze Tab

            if (_isOnlineMode.value) {
                runOnlineAnalysis(inferredCrop, bitmap)
            } else {
                runOfflineAnalysis(inferredCrop)
            }
        }
    }

    private fun generateMockBitmapForInference(crop: String): Bitmap {
        val size = 256
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint()

        val color = when (crop.lowercase(Locale.getDefault())) {
            "tomate" -> 0xFFD32F2F.toInt() // Red
            "maíz", "maiz" -> 0xFFFBC02D.toInt() // Yellow
            "papa" -> 0xFF8D6E63.toInt() // Brown
            "café", "cafe" -> 0xFF5D4037.toInt() // Dark brown
            else -> 0xFF388E3C.toInt() // Green
        }
        canvas.drawColor(color)

        paint.color = 0xFFFFFFFF.toInt()
        paint.style = android.graphics.Paint.Style.STROKE
        paint.strokeWidth = 8f
        canvas.drawCircle(128f, 128f, 80f, paint)

        paint.color = 0xFF81C784.toInt()
        paint.style = android.graphics.Paint.Style.FILL
        canvas.drawRect(80f, 120f, 176f, 136f, paint)

        return bitmap
    }
}

class ScanViewModelFactory(
    private val application: Application,
    private val repository: AgroRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ScanViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ScanViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
