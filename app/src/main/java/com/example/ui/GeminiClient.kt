package com.example.ui

import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class Part(
    val text: String? = null,
    val inlineData: InlineData? = null
)

@JsonClass(generateAdapter = true)
data class InlineData(
    val mimeType: String,
    val data: String
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    val responseMimeType: String? = null,
    val temperature: Float? = null
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    val candidates: List<Candidate>? = null
)

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: Content? = null
)

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object GeminiRetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val service: GeminiApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        retrofit.create(GeminiApiService::class.java)
    }
}

@JsonClass(generateAdapter = true)
data class OcrInvoiceResult(
    val vendorName: String = "",
    val companyName: String = "",
    val amount: Double = 0.0,
    val invoiceNumber: String = "",
    val itemsList: String = "",
    val itemType: String = ""
)

@JsonClass(generateAdapter = true)
data class RadiographyFinding(
    val toothNumber: String = "",
    val condition: String = "",
    val severity: String = "Medium", // High, Medium, Low
    val description: String = ""
)

@JsonClass(generateAdapter = true)
data class RadiographyScanResult(
    val overallSummary: String = "",
    val suspectedTeeth: List<String> = emptyList(),
    val findings: List<RadiographyFinding> = emptyList(),
    val recommendedTreatments: String = ""
)

@JsonClass(generateAdapter = true)
data class ParsedClinicalProposal(
    val teethList: List<String> = emptyList(),
    val title: String = "",
    val cost: Long = 0L,
    val notes: String = "",
    val suggestedStages: List<String> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ParsedClinicalResponse(
    val proposals: List<ParsedClinicalProposal> = emptyList(),
    val patientAlerts: String = "",
    val summaryNote: String = ""
)

object GeminiClient {

    suspend fun parseClinicalPrompt(promptText: String): ParsedClinicalResponse? = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "YOUR_GEMINI_API_KEY") {
            return@withContext null
        }

        val systemPrompt = """
            You are an expert dental clinical assistant. Analyze the given voice or text note from a dentist (in Persian or English) and convert it into structured dental treatment proposals.
            FDI Tooth numbering system is used (e.g. 11-18, 21-28, 31-38, 41-48, or primary teeth 51-55, 61-65, 71-75, 81-85).
            
            Extract:
            1. proposals: list of items containing:
               - teethList: array of tooth numbers mentioned (e.g. ["14", "15"])
               - title: Title of treatment in Persian (e.g., "عصب‌کشی و پرکردن", "روکش سرامیکی", "ایمپلنت", "جرم‌گیری")
               - cost: total cost in Tomans as a Long integer (e.g. 3500000). Convert Rial to Toman if specified in Rials.
               - notes: clinical instructions or detail
               - suggestedStages: array of clinical stages if applicable (e.g. ["عصب کشی", "پانسمان", "پرکردن نهایی"])
            2. patientAlerts: medical alerts or specific warnings mentioned in text (e.g. "حساسیت به پنسلین")
            3. summaryNote: brief summary note for the dentist

            Format: Return ONLY a raw JSON object matching:
            {
              "proposals": [
                {
                  "teethList": ["14"],
                  "title": "عصب‌کشی",
                  "cost": 4000000,
                  "notes": "کانال ۴ تایی",
                  "suggestedStages": ["تراش و عصب‌کشی", "پرکردن کانال", "ترمیم نهایی"]
                }
              ],
              "patientAlerts": "",
              "summaryNote": "معاینه دندان ۱۴"
            }
            Do NOT include markdown block markers.
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(
                Content(
                    parts = listOf(
                        Part(text = "$systemPrompt\n\nClinical Note:\n$promptText")
                    )
                )
            ),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.2f
            )
        )

        try {
            val response = GeminiRetrofitClient.service.generateContent(apiKey, request)
            val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (!responseText.isNullOrBlank()) {
                val cleanJson = responseText.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                val adapter = GeminiRetrofitClient.moshi.adapter(ParsedClinicalResponse::class.java)
                adapter.fromJson(cleanJson)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun scanInvoice(imagePathOrBase64: String): OcrInvoiceResult? = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "YOUR_GEMINI_API_KEY") {
            return@withContext null
        }

        val base64Image = if (imagePathOrBase64.startsWith("/")) {
            try {
                val bytes = java.io.File(imagePathOrBase64).readBytes()
                android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            } catch (e: Exception) {
                return@withContext null
            }
        } else {
            imagePathOrBase64
        }

        val prompt = """
            You are an expert financial invoice OCR and parser. Analyze this invoice/receipt image and extract:
            1. Vendor Name (فروشنده)
            2. Company/Store Name (نام فروشگاه/شرکت)
            3. Total Invoice Amount in Tomans (مبلغ کل فاکتور به واحد تومان - if in Rials, convert to Toman by dividing by 10)
            4. Invoice Number (شماره فاکتور - if none, generate a short logical sequence)
            5. List of Items/Parts with costs (لیست اقلام)
            6. Item Type (Choose strictly one of: 'Consumables', 'Instruments', 'Utilities', 'Maintenance', 'Other')

            You must return a raw JSON object containing these exact keys:
            - vendorName: String
            - companyName: String
            - amount: Double
            - invoiceNumber: String
            - itemsList: String
            - itemType: String

            Do NOT include markdown block markers like ```json or ```. Return only the clean JSON string.
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(
                Content(
                    parts = listOf(
                        Part(text = prompt),
                        Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Image.replace("\n", "").trim()))
                    )
                )
            ),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.2f
            )
        )

        try {
            val response = GeminiRetrofitClient.service.generateContent(apiKey, request)
            val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (!responseText.isNullOrBlank()) {
                val cleanJson = responseText.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                val adapter = GeminiRetrofitClient.moshi.adapter(OcrInvoiceResult::class.java)
                adapter.fromJson(cleanJson)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun analyzeRadiographyScan(imagePathOrBase64: String): RadiographyScanResult? = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "YOUR_GEMINI_API_KEY") {
            return@withContext null
        }

        val base64Image = if (imagePathOrBase64.startsWith("/")) {
            try {
                val bytes = java.io.File(imagePathOrBase64).readBytes()
                android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            } catch (e: Exception) {
                return@withContext null
            }
        } else {
            imagePathOrBase64
        }

        val prompt = """
            You are an expert dental radiologist AI assistant analyzing an OPG panoramic or PA dental X-ray.
            Analyze the image for preliminary clinical examination guidance. Identify potential radiolucencies, interproximal caries, bone loss, impacted teeth, or endodontic lesions.
            Use FDI tooth numbering (e.g., 16, 21, 36, 47).

            Return ONLY a raw JSON object matching this exact structure:
            {
              "overallSummary": "خلاصه تحلیل رادیوگرافی در دو جمله به فارسی",
              "suspectedTeeth": ["16", "36"],
              "findings": [
                {
                  "toothNumber": "16",
                  "condition": "پوسیدگی عمیق دیستال و احتمال درگیری عصب",
                  "severity": "High",
                  "description": "ضایعه رادیولوسنت ملموس در تاج و اپکس دندان ۱۶"
                }
              ],
              "recommendedTreatments": "پیشنهاد معاینه بالینی عصب کشی برای ۱۶ و جرم‌گیری کلی فک"
            }
            Do NOT include markdown block markers like ```json or ```.
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(
                Content(
                    parts = listOf(
                        Part(text = prompt),
                        Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Image.replace("\n", "").trim()))
                    )
                )
            ),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.2f
            )
        )

        try {
            val response = GeminiRetrofitClient.service.generateContent(apiKey, request)
            val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (!responseText.isNullOrBlank()) {
                val cleanJson = responseText.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                val adapter = GeminiRetrofitClient.moshi.adapter(RadiographyScanResult::class.java)
                adapter.fromJson(cleanJson)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun generatePostTreatmentCareInstructions(
        patientName: String,
        treatmentTitle: String,
        teeth: String,
        notes: String
    ): String? = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "YOUR_GEMINI_API_KEY") {
            return@withContext null
        }

        val prompt = """
            شما دستیار هوشمند دندانپزشک هستید. یک متن زیبا، مؤدبانه و دقیق آموزشی پس از درمان برای بیمار به نام «$patientName» بنویسید.
            درمان انجام شده: $treatmentTitle ${if (teeth.isNotBlank()) "روی دندان $teeth" else ""}
            توضیحات پزشک: $notes

            متن شامل موارد زیر باشد:
            ۱. پیام احوالپرسی و تشکر
            ۲. کنترل درد و مصرف داروها
            ۳. مراقبت‌های تغذیه‌ای و بهداشتی
            ۴. علائم هشداردهنده یا موارد نیازمند تماس با مطب
            
            متن را کوتاه، روان و آماده برای ارسال مستقیم در پیامک/واتساپ تولید کنید. از هدر و امضای مطب استفاده کنید.
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(
                Content(parts = listOf(Part(text = prompt)))
            ),
            generationConfig = GenerationConfig(temperature = 0.3f)
        )

        try {
            val response = GeminiRetrofitClient.service.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
